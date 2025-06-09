package com.hmas.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.ceil

class WallpaperSettingsActivity : AppCompatActivity() {
    private val positions = arrayOf("Top", "Center", "Bottom")
    private val fontSizes = arrayOf("Small", "Medium", "Large")
    private val cardColors = arrayOf("Dark Gray", "Green Glow", "Red Alert", "Transparent")
    private val syncOptions = arrayOf("5m", "15m", "30m", "1h", "4h", "6h", "12h")
    private val PICK_IMAGE_REQUEST = 42

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        fun sectionHeader(title: String): TextView {
            return TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#3F51B5"))
                setPadding(0, 30, 0, 10)
            }
        }

        val spinnerPosition = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, positions)
            setSelection(positions.indexOf(prefs.getString("card_position", "Center")))
        }

        val spinnerFont = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, fontSizes)
            setSelection(fontSizes.indexOf(prefs.getString("font_size", "Medium")))
        }

        val spinnerColor = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, cardColors)
            setSelection(cardColors.indexOf(prefs.getString("card_color", "Dark Gray")))
        }

        val cardWidths = arrayOf("Full", "Medium", "Narrow")
        val cardLines = arrayOf("4", "6", "8", "10")

        val spinnerCardWidth = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, cardWidths)
            setSelection(cardWidths.indexOf(prefs.getString("card_width", "Full")))
        }

        val spinnerCardLines = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, cardLines)
            setSelection(cardLines.indexOf(prefs.getString("card_lines", "6")))
        }

        val selectImageBtn = Button(this).apply {
            text = "Select Background Image"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, PICK_IMAGE_REQUEST)
            }
        }

        val clearImageBtn = Button(this).apply {
            text = "Restore Default Background"
            setBackgroundColor(Color.parseColor("#F44336"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                val prefsEditor = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                prefsEditor.remove("background_image_uri")
                prefsEditor.remove("background_path")
                prefsEditor.apply()

                val file = File(filesDir, "wallpaper_background.jpg")
                if (file.exists()) {
                    file.delete()
                }


                sendBroadcast(Intent("com.hmas.api.ACTION_RELOAD_WALLPAPER"))

                Toast.makeText(this@WallpaperSettingsActivity, "Background restored to default.", Toast.LENGTH_SHORT).show()
            }

        }

        val notificationSwitch = Switch(this).apply {
            text = "Show messages as notifications too."
            isChecked = prefs.getBoolean("show_notifications", false)
        }

        val saveButton = Button(this).apply {
            text = "Save"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                val selectedPos = spinnerPosition.selectedItem.toString()
                val selectedFont = spinnerFont.selectedItem.toString()
                val selectedColor = spinnerColor.selectedItem.toString()
                val cardWidth = spinnerCardWidth.selectedItem.toString()
                val cardLines = spinnerCardLines.selectedItem.toString()
                val apiKey = prefs.getString("api_key", "") ?: ""
                if (apiKey.length < 32) {
                    Toast.makeText(this@WallpaperSettingsActivity, "Warning: API key is too short or missing. Some features may not work.", Toast.LENGTH_LONG).show()
                }
                prefs.edit()
                    .putString("card_position", selectedPos)
                    .putString("font_size", selectedFont)
                    .putString("card_color", selectedColor)
                    .putString("card_width", cardWidth)
                    .putString("card_lines", cardLines)
                    .putString("api_key", apiKey)
                    .putBoolean("show_notifications", notificationSwitch.isChecked)
                    .putBoolean("do_initial_sync", true)
                    .apply()
                sendBroadcast(Intent("com.hmas.api.ACTION_RELOAD_WALLPAPER"))
                Toast.makeText(this@WallpaperSettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
                prefs.edit().putBoolean("do_force_sync", true).apply()
                sendBroadcast(Intent("com.hmas.api.ACTION_FORCE_SYNC"))
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

        val advancedButton = Button(this).apply {
            text = "Advanced API Config"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                startActivity(Intent(this@WallpaperSettingsActivity, ApiSettingsActivity::class.java))
            }
        }

        layout.apply {
            addView(sectionHeader("Card Position"))
            addView(spinnerPosition)
            addView(sectionHeader("Font Size"))
            addView(spinnerFont)
            addView(sectionHeader("Card Color"))
            addView(spinnerColor)
            addView(sectionHeader("Card Width"))
            addView(spinnerCardWidth)
            addView(sectionHeader("Card Lines"))
            addView(spinnerCardLines)
            addView(sectionHeader("Notification Display"))
            addView(notificationSwitch)
            addView(sectionHeader("Wallpaper Background"))
            addView(selectImageBtn)
            addView(clearImageBtn)
            addView(saveButton.apply {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 30, 0, 20)
                layoutParams = params
            })
            addView(advancedButton)
        }

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val uri = data.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                        .putString("background_image_uri", uri.toString())
                        .apply()

                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels

                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }

                    val inSampleSize = calculateInSampleSize(bounds, screenWidth, screenHeight)
                    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }

                    val bitmap = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    } ?: return

                    val exif = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                    val orientation = exif?.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    ) ?: ExifInterface.ORIENTATION_NORMAL

                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    }

                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )

                    // Create a screen-sized blank canvas
                    val finalBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(finalBitmap)
                    canvas.drawColor(Color.BLACK)

                    // Compute scale to fill screen with aspect ratio preserved (center crop)
                    val scale = 1.01f * maxOf(
                        screenWidth.toFloat() / rotatedBitmap.width,
                        screenHeight.toFloat() / rotatedBitmap.height
                    )

                    val scaledWidth = (rotatedBitmap.width * scale).toInt()
                    val scaledHeight = (rotatedBitmap.height * scale).toInt()
                    val left = (screenWidth - scaledWidth) / 2f
                    val top = (screenHeight - scaledHeight) / 2f
                    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

                    val paint = Paint().apply {
                        isFilterBitmap = true
                        isAntiAlias = true
                    }

                    canvas.drawBitmap(rotatedBitmap, null, destRect, paint)

                    val file = File(filesDir, "wallpaper_background.jpg")
                    file.outputStream().use { output ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }

                    getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                        .putString("background_path", file.absolutePath)
                        .apply()

                    Toast.makeText(this, "Image selected successfully", Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    // Efficient downsampling calculator
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize

    }
}
