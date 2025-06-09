package com.hmas.api

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MessageSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("HMAS", "[WORKER] WorkManager triggered MessageSyncWorker")

            // Trigger the same sync mechanism used in settings
            HackerWallpaperService.triggerImmediateFetch(context)

            Result.success()
        } catch (e: Exception) {
            Log.e("HMAS", "WorkManager trigger failed", e)
            Result.retry()
        }
    }

}
