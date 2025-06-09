package com.hmas.api

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.graphics.*
import android.os.*
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class HackerWallpaperService : WallpaperService() {
    companion object {
        @JvmStatic
        fun triggerImmediateFetch(context: Context) {
            Log.d("HMAS", "[DEBUG] triggerImmediateFetch called")
            val intent = Intent("com.hmas.api.ACTION_FORCE_SYNC").apply {
                `package` = context.packageName
            }
            context.sendBroadcast(intent)
        }
    }


    override fun onCreateEngine(): Engine = HackerEngine()

    inner class HackerEngine : Engine() {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = object : Runnable {
            override fun run() {
                fetchMessage()
                draw()
                if (visible) handler.postDelayed(this, getSyncIntervalMs())
            }
        }

        private var visible = false
        private var message = "Loading..."
        private var backgroundBitmap: Bitmap? = null
        private var isFetching = false
        private var lastSyncTime: Long = 0L
        private var scrollIndex = 0
        private val linesPerPage = 6
        private var wrappedLines: List<String> = listOf()

        private var cardX = 50f
        private var cardY = 300f

        private val textPaint = Paint().apply {
            color = Color.GREEN
            textSize = 40f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        private val cardPaint = Paint().apply {
            isAntiAlias = true
        }

        private val settingsUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("HMAS", "[SYNC] Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    "com.hmas.api.ACTION_SETTINGS_UPDATED" -> {
                        Log.d("HMAS", "[SYNC] Handling ACTION_SETTINGS_UPDATED — redraw")
                        loadBackgroundImage()
                        draw()
                    }
                    "com.hmas.api.ACTION_FORCE_SYNC" -> {
                        Log.d("HMAS", "[SYNC] Handling ACTION_FORCE_SYNC — calling fetchMessage(force = true)")
                        fetchMessage(force = true)
                    }
                    else -> {
                        Log.w("HMAS", "[SYNC] Unknown broadcast received: ${intent?.action}")
                    }
                }
            }

        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

            if (visible) {
                loadBackgroundImage()
                loadWrappedLinesFromPrefsIfAvailable()
                draw()

                WorkerScheduler.scheduleMessageSync(applicationContext)

                if (prefs.getBoolean("do_initial_sync", false)) {
                    prefs.edit().putBoolean("do_initial_sync", false).apply()
                    fetchMessage(force = true)
                } else if (prefs.getBoolean("do_force_sync", false)) {
                    prefs.edit().putBoolean("do_force_sync", false).apply()
                    fetchMessage(force = true)
                }

                val filter = IntentFilter().apply {
                    addAction("com.hmas.api.ACTION_FORCE_SYNC")
                    addAction("com.hmas.api.ACTION_SETTINGS_UPDATED")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(settingsUpdateReceiver, filter, RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(settingsUpdateReceiver, filter)
                }

                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
                try {
                    unregisterReceiver(settingsUpdateReceiver)
                } catch (_: Exception) { }
            }
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            try {
                unregisterReceiver(settingsUpdateReceiver)
            } catch (_: Exception) { }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val maxStart = max(0, wrappedLines.size - linesPerPage)
                scrollIndex = if (scrollIndex >= maxStart) 0 else scrollIndex + linesPerPage
                draw()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post { draw() }
        }

        private fun draw() {
            val canvas = surfaceHolder.lockCanvas()
            if (canvas != null) {
                try {
                    applyPreferences(canvas)
                    drawBackground(canvas)
                    drawCard(canvas)
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawBackground(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            backgroundBitmap?.let { bmp ->
                val scale = 1.02f * max(
                    canvas.width.toFloat() / bmp.width,
                    canvas.height.toFloat() / bmp.height
                )
                val scaledWidth = bmp.width * scale
                val scaledHeight = bmp.height * scale
                val left = (canvas.width - scaledWidth) / 2f
                val top = (canvas.height - scaledHeight) / 2f
                val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                canvas.drawBitmap(bmp, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))
            }
        }

        private fun drawCard(canvas: Canvas) {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val padding = 50
            val cardPadding = 40
            val cornerRadius = 30f
            val lineHeight = (textPaint.textSize + 10).toInt()
            val cardWidth = canvas.width - (padding * 2)
            val cardHeight = (lineHeight * linesPerPage) + (cardPadding * 2)

            val cardY = when (prefs.getString("card_position", "Center")) {
                "Top" -> 100f
                "Bottom" -> canvas.height - cardHeight - 100f
                else -> (canvas.height - cardHeight) / 2f
            }

            val cardRect = RectF(cardX, cardY, cardX + cardWidth, cardY + cardHeight)
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

            var textY = cardY + cardPadding + textPaint.textSize
            val pageLines = wrappedLines.drop(scrollIndex).take(linesPerPage)
            for (line in pageLines) {
                canvas.drawText(line, cardX + cardPadding, textY, textPaint)
                textY += lineHeight
            }
        }

        private fun applyPreferences(canvas: Canvas) {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

            textPaint.textSize = when (prefs.getString("font_size", "Medium")) {
                "Small" -> 30f
                "Large" -> 56f
                else -> 40f
            }

            cardY = when (prefs.getString("card_position", "Center")) {
                "Top" -> 100f
                "Bottom" -> canvas.height - 600f
                else -> canvas.height / 2f - 300f
            }

            when (prefs.getString("card_color", "Dark Gray")) {
                "Dark Gray" -> {
                    cardPaint.color = Color.parseColor("#1E1E1E")
                    cardPaint.setShadowLayer(10f, 0f, 0f, Color.GREEN)
                }
                "Green Glow" -> {
                    cardPaint.color = Color.parseColor("#002200")
                    cardPaint.setShadowLayer(20f, 0f, 0f, Color.GREEN)
                }
                "Red Alert" -> {
                    cardPaint.color = Color.parseColor("#330000")
                    cardPaint.setShadowLayer(15f, 0f, 0f, Color.RED)
                }
                "Transparent" -> {
                    cardPaint.color = Color.TRANSPARENT
                    cardPaint.clearShadowLayer()
                }
            }
        }

        private fun loadWrappedLinesFromPrefsIfAvailable() {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val rawMessage = prefs.getString("last_card_message", null) ?: return
            try {
                val json = JSONObject(rawMessage)
                val pretty = json.toString(4)
                val maxWidth = surfaceHolder.surfaceFrame.width()
                wrappedLines = wrapText(pretty, maxWidth)
                scrollIndex = 0
                Log.d("HMAS", "Loaded wrapped lines from cached prefs")
            } catch (e: Exception) {
                Log.e("HMAS", "Failed to parse cached message", e)
            }
        }

        private fun wrapText(text: String, maxWidth: Int): List<String> {
            val words = text.split(" ")
            val lines = mutableListOf<String>()
            var currentLine = ""
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (textPaint.measureText(testLine) < maxWidth - 180f) {
                    currentLine = testLine
                } else {
                    lines.add(currentLine)
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            return lines
        }

        private fun fetchMessage(force: Boolean = false) {
            Log.d("HMAS", "[SYNC] fetchMessage called, force = $force, thread = ${Thread.currentThread().name}")
            val currentTime = System.currentTimeMillis()
            if (!force && currentTime - lastSyncTime < getSyncIntervalMs()) return
            if (isFetching) scope.coroutineContext.cancelChildren()

            isFetching = true
            lastSyncTime = currentTime

            scope.launch {
                try {
                    val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                    val query = prefs.getString("api_config", "") ?: ""
                    val key = prefs.getString("api_key", "") ?: ""
                    val alerts = prefs.getBoolean("api_alerts", false)
                    val severity = prefs.getString("api_severity", "") ?: ""
                    val types = prefs.getBoolean("api_types", false)

                    val extraParams = mutableListOf<String>()
                    if (alerts) extraParams.add("alerts")
                    if (severity.isNotEmpty()) extraParams.add("severity=$severity")
                    if (types) extraParams.add("types=on")

                    val joinedParams = listOf(query, *extraParams.toTypedArray())
                        .filter { it.isNotBlank() }
                        .joinToString("&")

                    val urlString = "https://carlostkd.ch/hmas/api.php?$joinedParams&apikey=$key"
                    val conn = URL(urlString).openConnection() as HttpURLConnection
                    val data = conn.inputStream.bufferedReader().readText()

                    prefs.edit().putString("last_card_message", data).apply()

                    wrappedLines = wrapText(JSONObject(data).toString(4), surfaceHolder.surfaceFrame.width())

                    if (prefs.getBoolean("show_notifications", false) && !isPreview) {
                        val preview = wrappedLines.joinToString(" ").take(300)
                        sendNotification("HMAS Update", preview)
                    }

                    scrollIndex = 0
                    handler.post { draw() }
                } catch (e: Exception) {
                    wrappedLines = listOf("Error loading message")
                    scrollIndex = 0
                    handler.post { draw() }
                } finally {
                    isFetching = false
                }
            }
        }

        private fun loadBackgroundImage() {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val path = prefs.getString("background_path", null) ?: return
            val file = File(path)
            if (file.exists()) {
                backgroundBitmap = BitmapFactory.decodeFile(file.absolutePath)
            } else {
                backgroundBitmap = null
            }
            handler.post { draw() }
        }

        private fun getSyncIntervalMs(): Long {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            return when (prefs.getString("sync_interval", "15m")) {
                "5m" -> 5 * 60_000L
                "15m" -> 15 * 60_000L
                "30m" -> 30 * 60_000L
                "1h" -> 60 * 60_000L
                "4h" -> 4 * 60 * 60_000L
                "6h" -> 6 * 60 * 60_000L
                "12h" -> 12 * 60 * 60_000L
                else -> 15 * 60_000L
            }
        }
    }

    private fun sendNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "hmas_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "HMAS Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}


