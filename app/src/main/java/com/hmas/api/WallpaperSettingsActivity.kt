package com.hmas.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

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

        val spinnerSync = Spinner(this).apply {
            adapter = ArrayAdapter(this@WallpaperSettingsActivity, android.R.layout.simple_spinner_dropdown_item, syncOptions)
            setSelection(syncOptions.indexOf(prefs.getString("sync_interval", "15m")))
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
            setBackgroundColor(Color.parseColor("#F44336")) // red tone to differentiate
            setTextColor(Color.WHITE)
            setPadding(0, 20, 0, 20)
            setOnClickListener {
                // Clear shared preference and delete the background file if exists
                val prefsEditor = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                prefsEditor.remove("background_image_uri")
                prefsEditor.remove("background_path")
                prefsEditor.apply()

                val file = File(filesDir, "wallpaper_background.jpg")
                if (file.exists()) {
                    file.delete()
                }

                Toast.makeText(this@WallpaperSettingsActivity, "Background restored to default.", Toast.LENGTH_SHORT).show()
            }
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
                val selectedSync = spinnerSync.selectedItem.toString()
                prefs.edit()
                    .putString("card_position", selectedPos)
                    .putString("font_size", selectedFont)
                    .putString("card_color", selectedColor)
                    .putString("sync_interval", selectedSync)
                    .apply()
                Toast.makeText(this@WallpaperSettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
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
            addView(sectionHeader("Time Sync Interval"))
            addView(spinnerSync)
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

                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                    .putString("background_image_uri", uri.toString())
                    .apply()
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    if (originalBitmap != null) {
                        // Hardcoded 90° rotation
                        val matrix = Matrix().apply { postRotate(90f) }
                        val rotated = Bitmap.createBitmap(
                            originalBitmap, 0, 0,
                            originalBitmap.width, originalBitmap.height,
                            matrix, true
                        )
                        val file = File(filesDir, "wallpaper_background.jpg")
                        file.outputStream().use { output ->
                            rotated.compress(Bitmap.CompressFormat.JPEG, 100, output)
                        }
                        getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                            .putString("background_path", file.absolutePath)
                            .apply()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Toast.makeText(this, "Image selected successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

