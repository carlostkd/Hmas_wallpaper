package com.hmas.api

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkerScheduler {

    fun scheduleMessageSync(context: Context) {
        val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val intervalPref = prefs.getString("sync_interval", "15m") ?: "15m"

        val intervalMinutes = when (intervalPref) {
            "5m" -> 5L
            "15m" -> 15L
            "30m" -> 30L
            "1h" -> 60L
            "4h" -> 240L
            "6h" -> 360L
            "12h" -> 720L
            else -> 15L
        }

        val workRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "HMAS_MessageSync",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
