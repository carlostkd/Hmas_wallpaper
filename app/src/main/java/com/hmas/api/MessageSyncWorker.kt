package com.hmas.api

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// import org.json.JSONObject
// import java.net.HttpURLConnection
// import java.net.URL

class MessageSyncWorker(

    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {


    companion object {
        private const val TAG = "MessageSyncWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {



        try {



            HackerWallpaperService.triggerImmediateFetch(applicationContext)


            Result.success()
        } catch (e: Exception) {


            Result.retry()
        }
    }
}