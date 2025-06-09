package com.hmas.api

import com.hmas.api.HackerWallpaperService
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial




class ApiSettingsActivity : AppCompatActivity() {

    private val chatStyles = arrayOf("neutral", "casual", "sarcastic", "panic", "misc")
    private val diffModes = arrayOf("classic", "strike", "redacted", "corrupt", "delta", "tagged")
    private val formatModes = arrayOf("json", "text", "html", "yaml")
    private val syncOptions = arrayOf("5m", "15m", "30m", "1h", "4h", "6h", "12h")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
        val config = prefs.getString("api_config", "") ?: ""
        val savedKey = prefs.getString("api_key", "") ?: ""

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

        val enableChat = CheckBox(this).apply {
            text = "Enable Chat Mode (chat=on)"
            isChecked = config.contains("chat=on")
        }


        val chatStyleLabel = TextView(this).apply { text = "Chat Style (chatstyle=)" }
        val spinnerStyle = Spinner(this).apply {
            adapter = ArrayAdapter(this@ApiSettingsActivity, android.R.layout.simple_spinner_dropdown_item, chatStyles)
        }

        val enableTalk = CheckBox(this).apply {
            text = "Enable Talk Mode (talk=on)"
            isChecked = config.contains("talk=on")
        }

        val talkInput = EditText(this).apply {
            hint = "Responder Name (talk=)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val talkColorInput = EditText(this).apply {
            hint = "Talk Color (talkcolor=)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val senderInput = EditText(this).apply {
            hint = "Sender Label (as=)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val msgInput = EditText(this).apply {
            hint = "Message Query (msg=)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val maxInput = EditText(this).apply {
            hint = "Max Messages (max=)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val diffInput = EditText(this).apply {
            hint = "Compare Keywords (e.g. ssh+shell)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val spinnerDiff = Spinner(this).apply {
            adapter = ArrayAdapter(this@ApiSettingsActivity, android.R.layout.simple_spinner_dropdown_item, diffModes)
        }

        val spinnerFormat = Spinner(this).apply {
            adapter = ArrayAdapter(this@ApiSettingsActivity, android.R.layout.simple_spinner_dropdown_item, formatModes)
        }

        val alertsSwitch = CheckBox(this).apply {
            text = "Enable Alerts (alerts)"
            isChecked = config.contains("alerts")
        }

        val severityInput = EditText(this).apply {
            hint = "Severity (e.g. high, medium, low)"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        val typesSwitch = CheckBox(this).apply {
            text = "Include Types (types=on)"
            isChecked = config.contains("types=on")
        }

        val lastLikeSwitch = CheckBox(this).apply {
            text = "Send Last=like"
        }

        val likedSwitch = CheckBox(this).apply {
            text = "Send liked"
        }

        // Uncheck others if last=like or liked are checked
        fun enforceExclusiveSwitches() {
            if (lastLikeSwitch.isChecked) {
                enableChat.isEnabled = false
                enableTalk.isEnabled = false
                senderInput.isEnabled = false
                msgInput.isEnabled = false
                maxInput.isEnabled = false
                diffInput.isEnabled = false
                spinnerDiff.isEnabled = false
                spinnerFormat.isEnabled = true
                alertsSwitch.isEnabled = false
                severityInput.isEnabled = false
                typesSwitch.isEnabled = false
            } else if (likedSwitch.isChecked) {
                enableChat.isEnabled = false
                enableTalk.isEnabled = false
                senderInput.isEnabled = false
                msgInput.isEnabled = false
                maxInput.isEnabled = false
                diffInput.isEnabled = false
                spinnerDiff.isEnabled = false
                spinnerFormat.isEnabled = true  // ✅ allow format
                alertsSwitch.isEnabled = false
                severityInput.isEnabled = false
                typesSwitch.isEnabled = false
            } else {
                enableChat.isEnabled = true
                enableTalk.isEnabled = true
                senderInput.isEnabled = true
                msgInput.isEnabled = true
                maxInput.isEnabled = true
                diffInput.isEnabled = true
                spinnerDiff.isEnabled = true
                spinnerFormat.isEnabled = true
                alertsSwitch.isEnabled = true
                severityInput.isEnabled = true
                typesSwitch.isEnabled = true
            }
        }


        lastLikeSwitch.setOnCheckedChangeListener { _, _ -> enforceExclusiveSwitches() }
        likedSwitch.setOnCheckedChangeListener { _, _ -> enforceExclusiveSwitches() }

        val syncLabel = TextView(this).apply { text = "Sync Interval" }

        val syncSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@ApiSettingsActivity, android.R.layout.simple_spinner_dropdown_item, syncOptions)
        }

        val apiKeyInput = EditText(this).apply {
            hint = "API Key (required)"
            setText(savedKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }

        fun updateTalkVisibility() {
            val show = enableTalk.isChecked
            talkInput.visibility = if (show) View.VISIBLE else View.GONE
            talkColorInput.visibility = if (show) View.VISIBLE else View.GONE
        }

        enableTalk.setOnCheckedChangeListener { _, _ -> updateTalkVisibility() }
        updateTalkVisibility()

        val saveBtn = Button(this).apply {
            text = "Save Configuration"
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val params = mutableListOf<String>()
                val apiKey = apiKeyInput.text.toString()

                val hasLike = lastLikeSwitch.isChecked
                val hasLiked = likedSwitch.isChecked

                if (hasLike) {
                    params.add("last=like")
                } else if (hasLiked) {
                    params.add("liked")
                } else {
                    val asValue = senderInput.text.toString()
                    if (asValue.isNotEmpty()) params.add("as=$asValue")

                    val msgValue = msgInput.text.toString()
                    if (msgValue.isNotEmpty()) params.add("msg=$msgValue")

                    if (enableChat.isChecked) {
                        params.add("chat=on")
                        params.add("chatstyle=" + spinnerStyle.selectedItem.toString())
                    }

                    if (enableTalk.isChecked) {
                        params.add("talk=on")
                        if (talkInput.text.isNotEmpty()) params.add("talk=" + talkInput.text.toString())
                        if (talkColorInput.text.isNotEmpty()) params.add("talkcolor=" + talkColorInput.text.toString())
                    }

                    if (maxInput.text.isNotEmpty()) params.add("max=" + maxInput.text.toString())

                    val diffText = diffInput.text.toString()
                    if (diffText.isNotEmpty()) {
                        params.add("diff=$diffText")
                        params.add("diffmode=" + spinnerDiff.selectedItem.toString())
                    }

                    if (spinnerFormat.selectedItem.toString() != "json") {
                        params.add("format=" + spinnerFormat.selectedItem.toString())
                    }

                    if (alertsSwitch.isChecked) params.add("alerts")
                    if (severityInput.text.isNotEmpty()) params.add("severity=" + severityInput.text.toString())
                    if (typesSwitch.isChecked) params.add("types=on")
                }

                val finalQuery = params.joinToString("&")

                prefs.edit()
                    .putString("api_config", finalQuery)
                    .putString("api_key", apiKey)
                    .putString("sync_interval", syncSpinner.selectedItem.toString())
                    .apply()


                WorkerScheduler.scheduleMessageSync(applicationContext)











                Toast.makeText(this@ApiSettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }

        }

        layout.apply {
            addView(sectionHeader("Chat Configuration"))
            addView(enableChat)
            addView(chatStyleLabel)
            addView(spinnerStyle)
            addView(sectionHeader("Talk Responder"))
            addView(enableTalk)
            addView(talkInput)
            addView(talkColorInput)
            addView(sectionHeader("Message Options"))
            addView(senderInput)
            addView(msgInput)
            addView(maxInput)
            addView(sectionHeader("Diff & Comparisons"))
            addView(diffInput)
            addView(spinnerDiff)
            addView(sectionHeader("Output Format"))
            addView(spinnerFormat)
            addView(sectionHeader("Alerts and Types"))
            addView(alertsSwitch)
            addView(severityInput)
            addView(typesSwitch)
            addView(sectionHeader("Special Commands"))
            addView(lastLikeSwitch)
            addView(likedSwitch)
            addView(sectionHeader("Sync Interval"))
            addView(syncLabel)
            addView(syncSpinner)
            addView(sectionHeader("API Key"))
            addView(apiKeyInput)
            addView(saveBtn)
        }

        setContentView(ScrollView(this).apply { addView(layout) })
    }
}
