package com.hmas.api

import android.content.Context
import android.util.Log // Import Android's Log utility
import androidx.work.*
import java.util.concurrent.TimeUnit

object WorkerScheduler {


    private const val TAG = "WorkerScheduler"

    private const val UNIQUE_WORK_NAME = "HMAS_MessageSync"

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
            else -> {

                15L
            }
        }


        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()


        val workRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints)

            .build()



        val workManagerInstance = WorkManager.getInstance(context.applicationContext)

        workManagerInstance.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )



    }
}