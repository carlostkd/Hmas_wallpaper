package com.hmas.api

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.system.exitProcess


class HmasApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->


            val crashId = UUID.randomUUID().toString()
            val crashData = JSONObject().apply {
                put("id", crashId)
                put("timestamp", System.currentTimeMillis())
                put("version", applicationContext.packageManager
                    .getPackageInfo(applicationContext.packageName, 0).versionName)
                put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})")
                put("message", throwable.message ?: "No message")
                put("stacktrace", Log.getStackTraceString(throwable))
            }


            sendCrashReport(crashData.toString(), 0)


            Handler(Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }, 3000)
        }
    }

    private fun sendCrashReport(jsonBody: String, attempt: Int) {
        Thread {
            try {
                val url = URL("https://carlostkd.ch/hmas/crash/crash.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")

                OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {

                    retryIfNeeded(jsonBody, attempt)
                } else {

                }
            } catch (e: Exception) {

                retryIfNeeded(jsonBody, attempt)
            }
        }.start()
    }

    private fun retryIfNeeded(jsonBody: String, attempt: Int) {
        if (attempt < 3) {
            val delay = (2000L * (attempt + 1))
            Handler(Looper.getMainLooper()).postDelayed({
                sendCrashReport(jsonBody, attempt + 1)
            }, delay)
        }
    }
}
