package com.hmas.api

import android.annotation.SuppressLint
import android.content.*
import android.graphics.*
import android.os.*
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import android.graphics.BitmapFactory
import android.content.IntentFilter
import android.content.Context.RECEIVER_NOT_EXPORTED

import android.net.Uri
import androidx.core.content.ContextCompat

class HackerWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return HackerEngine()
    }

    companion object {
        fun triggerImmediateFetch(context: Context) {
            val intent = Intent(context, HackerWallpaperService::class.java).apply {
                action = "com.hmas.api.ACTION_FORCE_SYNC"
            }
            context.startService(intent)
        }
    }

    inner class HackerEngine : Engine() {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var visible = false
        private var message = "Loading..."
        private var isFetching = false
        private var lastSyncTime: Long = 0L
        private var backgroundBitmap: Bitmap? = null

        private val textPaint = Paint().apply {
            color = Color.GREEN
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }

        private val cardPaint = Paint().apply {
            isAntiAlias = true
        }

        private var cardX = 50f
        private var cardY = 300f

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = object : Runnable {
            override fun run() {
                fetchMessage()
                draw()
                if (visible) {
                    handler.postDelayed(this, getSyncIntervalMs())
                }
            }
        }

        private val wrappedLines = mutableListOf<String>()
        private var scrollIndex = 0
        private val linesPerPage = 6

        private val settingsUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                Log.d("HMAS", "Broadcast received: $action")

                when (action) {
                    "com.hmas.api.ACTION_SETTINGS_UPDATED" -> {
                        Log.d("HMAS", "Handling ACTION_SETTINGS_UPDATED: loading background and redrawing")
                        loadBackgroundImage()
                        draw()
                    }
                    "com.hmas.api.ACTION_FORCE_SYNC" -> {
                        Log.d("HMAS", "Handling ACTION_FORCE_SYNC: forcing API sync")
                        fetchMessage(force = true)
                    }
                    else -> {
                        Log.w("HMAS", "Received unknown broadcast: $action")
                    }
                }
            }
        }





        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)

            if (visible) {
                Log.d("HMAS", "Wallpaper is visible — preparing sync and drawing")

                loadBackgroundImage()
                draw()

                if (prefs.getBoolean("do_initial_sync", false)) {
                    Log.d("HMAS", "Initial sync triggered")
                    fetchMessage(force = true) {
                        prefs.edit().putBoolean("do_initial_sync", false).apply()
                    }
                }

                if (prefs.getBoolean("do_force_sync", false)) {
                    Log.d("HMAS", "Forced sync triggered from prefs")
                    fetchMessage(force = true) {
                        prefs.edit().putBoolean("do_force_sync", false).apply()
                    }
                }

                Log.d("HMAS", "Registering broadcast receiver with both actions")

                val intentFilter = IntentFilter().apply {
                    addAction("com.hmas.api.ACTION_FORCE_SYNC")
                    addAction("com.hmas.api.ACTION_SETTINGS_UPDATED")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                    ContextCompat.registerReceiver(
                        this@HackerWallpaperService,
                        settingsUpdateReceiver,
                        intentFilter,
                        RECEIVER_NOT_EXPORTED.toString(),
                        null,
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                } else {

                    registerReceiver(
                        settingsUpdateReceiver,
                        intentFilter
                    )
                }

                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
                try {
                    unregisterReceiver(settingsUpdateReceiver)
                } catch (_: Exception) {

                }
            }
        }







        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post { draw() }
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunner)
            scope.cancel()
            try {
                this@HackerWallpaperService.unregisterReceiver(settingsUpdateReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w("HMAS", "Receiver already unregistered.")
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val maxStart = maxOf(0, wrappedLines.size - linesPerPage)
                scrollIndex = if (scrollIndex >= maxStart) 0 else scrollIndex + linesPerPage
                draw()
            }
            super.onTouchEvent(event)
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    applyPreferences(canvas)

                    if (backgroundBitmap != null) {
                        backgroundBitmap?.let { bmp ->
                            val scale = maxOf(
                                canvas.width.toFloat() / bmp.width,
                                canvas.height.toFloat() / bmp.height
                            )
                            val scaledWidth = bmp.width * scale
                            val scaledHeight = bmp.height * scale
                            val left = (canvas.width - scaledWidth) / 2f
                            val top = (canvas.height - scaledHeight) / 2f
                            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                            canvas.drawBitmap(bmp, null, destRect, null)
                        }
                    } else {
                        canvas.drawColor(Color.BLACK)
                    }

                    drawCard(canvas)
                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        private fun applyPreferences(canvas: Canvas) {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            when (prefs.getString("font_size", "Medium")) {
                "Small" -> textPaint.textSize = 30f
                "Medium" -> textPaint.textSize = 40f
                "Large" -> textPaint.textSize = 56f
            }

            cardX = 50f
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

        private fun drawCard(canvas: Canvas) {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val padding = 50
            val cardPadding = 40
            val cornerRadius = 30f
            val pageLines = wrappedLines.drop(scrollIndex).take(linesPerPage)



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
            for (line in pageLines) {
                Log.d("HMAS", "Drawing line: $line")
                canvas.drawText(line, cardX + cardPadding, textY, textPaint)
                textY += lineHeight
            }
        }


        private fun fetchMessage(force: Boolean = false, onSuccess: (() -> Unit)? = null) {
            val currentTime = System.currentTimeMillis()
            val interval = getSyncIntervalMs()



            if (!force && currentTime - lastSyncTime < interval) {

                return
            }

            if (isFetching) {

                scope.coroutineContext.cancelChildren()
            }

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
                    //Log.d("HMAS", "Request URL: $urlString")

                    val url = URL(urlString)
                    val conn = url.openConnection() as HttpURLConnection
                    val data = conn.inputStream.bufferedReader().readText()



                    val rawLines = data.trim().split("\n")
                    wrappedLines.clear()

                    val canvas = surfaceHolder.lockCanvas()
                    val maxWidth = canvas?.width ?: 1080
                    surfaceHolder.unlockCanvasAndPost(canvas)



                    for (line in rawLines) {
                        val words = line.split(" ")
                        var current = ""
                        for (word in words) {
                            val test = if (current.isEmpty()) word else "$current $word"
                            if (textPaint.measureText(test) < maxWidth - 180f) {
                                current = test
                            } else {
                                wrappedLines.add(current)
                                current = word
                            }
                        }
                        if (current.isNotEmpty()) wrappedLines.add(current)
                    }

                    scrollIndex = 0
                    message = data



                    withContext(Dispatchers.Main) {

                        draw()

                        handler.postDelayed({

                            draw()
                        }, 300)

                        onSuccess?.invoke()
                    }
                } catch (e: Exception) {
                    // let this log for future updates if any error occurs
                    //Log.e("HMAS", "Error during fetchMessage", e)
                    message = "Error loading message"
                    wrappedLines.clear()
                    wrappedLines.add(message)
                    scrollIndex = 0

                    withContext(Dispatchers.Main) {
                        draw()
                    }
                } finally {
                    isFetching = false
                }
            }
        }





        private fun loadBackgroundImage() {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val path = prefs.getString("background_path", null) ?: return
            try {
                val file = File(path)
                if (file.exists()) {
                    backgroundBitmap = BitmapFactory.decodeFile(file.absolutePath)
                    handler.post {

                        draw()
                    }
                }
            } catch (e: Exception) {
                Log.e("HMAS", "Failed to load background image", e)
            }
        }


        private fun getSyncIntervalMs(): Long {
            val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            return when (prefs.getString("sync_interval", "15m")) {
                "5m" -> 5 * 60 * 1000L
                "15m" -> 15 * 60 * 1000L
                "30m" -> 30 * 60 * 1000L
                "1h" -> 60 * 60 * 1000L
                "4h" -> 4 * 60 * 60 * 1000L
                "6h" -> 6 * 60 * 60 * 1000L
                "12h" -> 12 * 60 * 60 * 1000L
                else -> 15 * 60 * 1000L
            }
        }
    }
}

