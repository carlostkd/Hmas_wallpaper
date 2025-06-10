package com.hmas.api

import android.content.Context
import android.util.Log // For logging, optional
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch




object ApiSettingsOptions {
    val CHAT_STYLES = listOf("neutral", "casual", "sarcastic", "panic", "misc")
    val DIFF_MODES = listOf("classic", "strike", "redacted", "corrupt", "delta", "tagged")
    val FORMAT_MODES = listOf("json", "text", "html", "yaml")
    val SYNC_OPTIONS = listOf("5m", "15m", "30m", "1h", "4h", "6h", "12h")
}

class ApiSettingsViewModel : ViewModel() {


    var enableChat by mutableStateOf(false)
    var selectedChatStyle by mutableStateOf(ApiSettingsOptions.CHAT_STYLES.first())

    var enableTalk by mutableStateOf(false)
    var talkResponderName by mutableStateOf("")
    var talkColor by mutableStateOf("")

    var senderLabel by mutableStateOf("")
    var messageQuery by mutableStateOf("")
    var maxMessages by mutableStateOf("")

    var compareKeywords by mutableStateOf("")
    var selectedDiffMode by mutableStateOf(ApiSettingsOptions.DIFF_MODES.first())

    var selectedFormatMode by mutableStateOf(ApiSettingsOptions.FORMAT_MODES.first())

    var enableAlerts by mutableStateOf(false)
    var severity by mutableStateOf("")
    var includeTypes by mutableStateOf(false)

    var sendLastLike by mutableStateOf(false)
    var sendLiked by mutableStateOf(false)

    var selectedSyncInterval by mutableStateOf(ApiSettingsOptions.SYNC_OPTIONS.first())
    var apiKey by mutableStateOf("")


    val isGeneralConfigEnabled: Boolean
        get() = !sendLastLike && !sendLiked


    val isTalkSectionVisible: Boolean
        get() = enableTalk


    fun loadSettings(context: Context) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val configString = prefs.getString("api_config", "") ?: ""
            apiKey = prefs.getString("api_key", "") ?: ""
            selectedSyncInterval = prefs.getString("sync_interval", ApiSettingsOptions.SYNC_OPTIONS.first()) ?: ApiSettingsOptions.SYNC_OPTIONS.first()

            enableChat = configString.contains("chat=on")
            ApiSettingsOptions.CHAT_STYLES.find { configString.contains("chatstyle=$it") }?.let { selectedChatStyle = it }

            enableTalk = configString.contains("talk=on")
            talkResponderName = extractValue(configString, "talk=")
            talkColor = extractValue(configString, "talkcolor=")

            senderLabel = extractValue(configString, "as=")
            messageQuery = extractValue(configString, "msg=")
            maxMessages = extractValue(configString, "max=")

            compareKeywords = extractValue(configString, "diff=")
            ApiSettingsOptions.DIFF_MODES.find { configString.contains("diffmode=$it") }?.let { selectedDiffMode = it }

            ApiSettingsOptions.FORMAT_MODES.find { configString.contains("format=$it") }?.let { selectedFormatMode = it }
                ?: run { selectedFormatMode = "json" }

            enableAlerts = configString.contains("alerts")
            severity = extractValue(configString, "severity=")
            includeTypes = configString.contains("types=on")

            sendLastLike = configString.contains("last=like")
            sendLiked = configString.contains("liked") && !configString.contains("last=like")
        }
    }

    private fun extractValue(config: String, key: String): String {
        return config.split('&')
            .find { it.startsWith(key) && it.length > key.length && it[key.length] == '=' }
            ?.substringAfter("=") ?: ""
    }

    fun saveSettings(context: Context, onSavedCallback: () -> Unit) {
        viewModelScope.launch {
            val params = mutableListOf<String>()

            if (sendLastLike) {
                params.add("last=like")
            } else if (sendLiked) {
                params.add("liked")
            } else {
                if (senderLabel.isNotEmpty()) params.add("as=$senderLabel")
                if (messageQuery.isNotEmpty()) params.add("msg=$messageQuery")

                if (enableChat) {
                    params.add("chat=on")
                    params.add("chatstyle=$selectedChatStyle")
                }

                if (enableTalk) {
                    params.add("talk=on")
                    if (talkResponderName.isNotEmpty()) params.add("talk=$talkResponderName")
                    if (talkColor.isNotEmpty()) params.add("talkcolor=$talkColor")
                }

                if (maxMessages.isNotEmpty()) {

                    params.add("max=$maxMessages")
                }


                if (compareKeywords.isNotEmpty()) {
                    params.add("diff=$compareKeywords")
                    params.add("diffmode=$selectedDiffMode")
                }



                if (enableAlerts) params.add("alerts")
                if (severity.isNotEmpty()) params.add("severity=$severity")
                if (includeTypes) params.add("types=on")
            }


            if (selectedFormatMode != "json") {
                if (sendLastLike || sendLiked || (!sendLastLike && !sendLiked)) { // Simpler: always add if not json
                    params.add("format=$selectedFormatMode")
                }
            }


            val finalQuery = params.joinToString("&")



            val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("api_config", finalQuery)
                .putString("api_key", apiKey)
                .putString("sync_interval", selectedSyncInterval)
                .apply()


            try {
                WorkerScheduler.scheduleMessageSync(context.applicationContext)

            } catch (e: Exception) {


            }

            onSavedCallback()
        }
    }
}