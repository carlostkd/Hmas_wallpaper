package com.hmas.api

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.graphics.*
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.exifinterface.media.ExifInterface
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max // Ensure this is kotlin.math.max


object WallpaperUIOptions {
    val POSITIONS = listOf("Top", "Center", "Bottom")
    val FONT_SIZES = listOf("Small", "Medium", "Large")
    val CARD_COLORS = listOf("Dark Gray", "Green Glow", "Red Alert", "Transparent")
    val CARD_WIDTHS = listOf("Full", "Medium", "Narrow")
    val CARD_LINES = listOf("4", "6", "8", "10")

}

class WallpaperSettingsViewModel(application: Application) : AndroidViewModel(application) {


    var selectedPosition by mutableStateOf(WallpaperUIOptions.POSITIONS.first())
    var selectedFontSize by mutableStateOf(WallpaperUIOptions.FONT_SIZES[1])
    var selectedCardColor by mutableStateOf(WallpaperUIOptions.CARD_COLORS.first())
    var selectedCardWidth by mutableStateOf(WallpaperUIOptions.CARD_WIDTHS.first())
    var selectedCardLines by mutableStateOf(WallpaperUIOptions.CARD_LINES[1])
    var showNotifications by mutableStateOf(false)
    var currentBackgroundImageUri by mutableStateOf<Uri?>(null)
    var isProcessingImage by mutableStateOf(false)


    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            selectedPosition = prefs.getString("card_position", WallpaperUIOptions.POSITIONS[1]) ?: WallpaperUIOptions.POSITIONS[1]
            selectedFontSize = prefs.getString("font_size", WallpaperUIOptions.FONT_SIZES[1]) ?: WallpaperUIOptions.FONT_SIZES[1]
            selectedCardColor = prefs.getString("card_color", WallpaperUIOptions.CARD_COLORS.first()) ?: WallpaperUIOptions.CARD_COLORS.first()
            selectedCardWidth = prefs.getString("card_width", WallpaperUIOptions.CARD_WIDTHS.first()) ?: WallpaperUIOptions.CARD_WIDTHS.first()
            selectedCardLines = prefs.getString("card_lines", WallpaperUIOptions.CARD_LINES[1]) ?: WallpaperUIOptions.CARD_LINES[1]
            showNotifications = prefs.getBoolean("show_notifications", false)
            prefs.getString("background_image_uri", null)?.let {
                currentBackgroundImageUri = Uri.parse(it)
            }
        }
    }

    fun saveSettings(onSettingsSaved: () -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: "" // API key is not set in this screen directly, but checked

            if (apiKey.length < 32) {

            }

            prefs.edit()
                .putString("card_position", selectedPosition)
                .putString("font_size", selectedFontSize)
                .putString("card_color", selectedCardColor)
                .putString("card_width", selectedCardWidth)
                .putString("card_lines", selectedCardLines)
                .putBoolean("show_notifications", showNotifications)
                .putBoolean("do_initial_sync", true) // As per original logic
                .apply()

            context.sendBroadcast(Intent("com.hmas.api.ACTION_RELOAD_WALLPAPER"))


            prefs.edit().putBoolean("do_force_sync", true).apply()
            context.sendBroadcast(Intent("com.hmas.api.ACTION_FORCE_SYNC"))



            onSettingsSaved()
        }
    }

    fun processAndSetBackgroundImage(uri: Uri) {
        viewModelScope.launch {
            isProcessingImage = true
            val context = getApplication<Application>()
            try {

                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                    .putString("background_image_uri", uri.toString())
                    .apply()

                currentBackgroundImageUri = uri

                val screenWidth = context.resources.displayMetrics.widthPixels
                val screenHeight = context.resources.displayMetrics.heightPixels


                val finalBitmapPath = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }

                    val inSampleSize = calculateInSampleSize(bounds, screenWidth, screenHeight)
                    val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }

                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    } ?: throw IllegalStateException("Could not decode bitmap stream")

                    val exif = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                    val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
                    val matrix = Matrix()
                    when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                    }
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    if (bitmap != rotatedBitmap) bitmap.recycle()

                    val finalBitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(finalBitmap)
                    canvas.drawColor(Color.BLACK)

                    val scale = 1.01f * max(screenWidth.toFloat() / rotatedBitmap.width, screenHeight.toFloat() / rotatedBitmap.height)
                    val scaledWidth = (rotatedBitmap.width * scale).toInt()
                    val scaledHeight = (rotatedBitmap.height * scale).toInt()
                    val left = (screenWidth - scaledWidth) / 2f
                    val top = (screenHeight - scaledHeight) / 2f
                    val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
                    val paint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }
                    canvas.drawBitmap(rotatedBitmap, null, destRect, paint)
                    rotatedBitmap.recycle()

                    val file = File(context.filesDir, "wallpaper_background.jpg")
                    file.outputStream().use { output ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                    }
                    finalBitmap.recycle()
                    file.absolutePath
                }


                context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
                    .putString("background_path", finalBitmapPath)
                    .apply()


                context.sendBroadcast(Intent("com.hmas.api.ACTION_RELOAD_WALLPAPER"))

            } catch (e: Exception) {



            } finally {
                isProcessingImage = false
            }
        }
    }

    fun clearBackgroundImage() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val prefsEditor = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE).edit()
            prefsEditor.remove("background_image_uri")
            prefsEditor.remove("background_path")
            prefsEditor.apply()

            currentBackgroundImageUri = null

            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "wallpaper_background.jpg")
                if (file.exists()) {
                    file.delete()
                }
            }
            context.sendBroadcast(Intent("com.hmas.api.ACTION_RELOAD_WALLPAPER"))

        }
    }


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