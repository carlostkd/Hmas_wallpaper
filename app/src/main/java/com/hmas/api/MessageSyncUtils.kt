package com.hmas.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


object MessageSyncUtils {

    fun performSync(context: Context) {
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val apiBase = prefs.getString("api_base_url", "https://carlostkd.ch/hmas/api.php")

        if (apiKey.length < 16 || apiBase.isNullOrEmpty()) {
            Log.w("HMAS", "API key or base URL missing")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$apiBase?apikey=$apiKey")
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch


                val json = JSONObject(body)
                val message = json.toString(4) // Pretty format

                if (prefs.getBoolean("show_notifications", false)) {
                    val preview = message.take(300)
                    NotificationUtils.sendNotification(context, "HMAS Message", preview)
                } else {

                    prefs.edit().putString("last_card_message", message).apply()
                }

            } catch (e: Exception) {

            }
        }
    }
}
