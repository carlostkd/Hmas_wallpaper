package com.hmas.api

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// Removed: import androidx.compose.animation.core.copy // Not used
// Removed: import androidx.compose.foundation.background // Not directly used
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
// Removed redundant fillMaxSize, fillMaxWidth, padding from foundation.layout if covered by M3
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
// Removed redundant getValue/setValue imports
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
// Removed: import androidx.compose.ui.semantics.disabled // Not directly used
// Removed: import androidx.compose.ui.text.TextStyle // Can be inferred or use MaterialTheme.typography
// Removed: import androidx.compose.ui.text.font.FontWeight // Should come from shared SectionHeader's styling
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// Removed: import androidx.compose.ui.unit.sp // Should come from shared SectionHeader's styling
import androidx.lifecycle.viewmodel.compose.viewModel


class ApiSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ApiSettingsScreen(
                    onSave = {
                        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiSettingsScreen(
    viewModel: ApiSettingsViewModel = viewModel(),
    onSave: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadSettings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5F5F5)
                )
            )
        },
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {


            SectionHeader("Chat Options")
            SwitchSetting(
                text = "Enable Chat Mode (chat=on)",
                checked = viewModel.enableChat,
                onCheckedChange = { viewModel.enableChat = it },
                enabled = viewModel.isGeneralConfigEnabled
            )

            DropdownSetting(
                label = "Chat Style (chatstyle=)",
                options = ApiSettingsOptions.CHAT_STYLES,
                selectedOption = viewModel.selectedChatStyle,
                onOptionSelected = { viewModel.selectedChatStyle = it },
                enabled = viewModel.enableChat && viewModel.isGeneralConfigEnabled
            )

            SectionHeader("Talk Responder")
            SwitchSetting(
                text = "Enable Talk Mode (talk=on)",
                checked = viewModel.enableTalk,
                onCheckedChange = { viewModel.enableTalk = it },
                enabled = viewModel.isGeneralConfigEnabled
            )
            if (viewModel.isTalkSectionVisible) {
                TextFieldSetting(
                    value = viewModel.talkResponderName,
                    onValueChange = { viewModel.talkResponderName = it },
                    label = "Responder Name (talk=)",
                    enabled = viewModel.isGeneralConfigEnabled
                )
                TextFieldSetting(
                    value = viewModel.talkColor,
                    onValueChange = { viewModel.talkColor = it },
                    label = "Talk Color (talkcolor=)",
                    enabled = viewModel.isGeneralConfigEnabled
                )
            }

            SectionHeader("Message Options")
            TextFieldSetting(
                value = viewModel.senderLabel,
                onValueChange = { viewModel.senderLabel = it },
                label = "Sender Label (as=)",
                enabled = viewModel.isGeneralConfigEnabled
            )
            TextFieldSetting(
                value = viewModel.messageQuery,
                onValueChange = { viewModel.messageQuery = it },
                label = "Message Query (msg=)",
                enabled = viewModel.isGeneralConfigEnabled
            )
            TextFieldSetting(
                value = viewModel.maxMessages,
                onValueChange = { viewModel.maxMessages = it },
                label = "Max Messages (max=)",
                keyboardType = KeyboardType.Number,
                enabled = viewModel.isGeneralConfigEnabled
            )

            SectionHeader("Diff & Comparisons")
            TextFieldSetting(
                value = viewModel.compareKeywords,
                onValueChange = { viewModel.compareKeywords = it },
                label = "Compare Keywords (e.g. ssh+shell)",
                enabled = viewModel.isGeneralConfigEnabled
            )
            DropdownSetting(
                label = "Diff Mode",
                options = ApiSettingsOptions.DIFF_MODES,
                selectedOption = viewModel.selectedDiffMode,
                onOptionSelected = { viewModel.selectedDiffMode = it },
                enabled = viewModel.compareKeywords.isNotEmpty() && viewModel.isGeneralConfigEnabled
            )

            SectionHeader("Output Format")
            DropdownSetting(
                label = "Format",
                options = ApiSettingsOptions.FORMAT_MODES,
                selectedOption = viewModel.selectedFormatMode,
                onOptionSelected = { viewModel.selectedFormatMode = it },
                enabled = true
            )

            SectionHeader("Alerts and Types")
            CheckboxSetting(
                text = "Enable Alerts (alerts)",
                checked = viewModel.enableAlerts,
                onCheckedChange = { viewModel.enableAlerts = it },
                enabled = viewModel.isGeneralConfigEnabled
            )
            TextFieldSetting(
                value = viewModel.severity,
                onValueChange = { viewModel.severity = it },
                label = "Severity (e.g. high, medium, low)",
                enabled = viewModel.enableAlerts && viewModel.isGeneralConfigEnabled
            )
            CheckboxSetting(
                text = "Include Types (types=on)",
                checked = viewModel.includeTypes,
                onCheckedChange = { viewModel.includeTypes = it },
                enabled = viewModel.isGeneralConfigEnabled
            )

            SectionHeader("Special Commands")
            CheckboxSetting(
                text = "Send Last=like",
                checked = viewModel.sendLastLike,
                onCheckedChange = {
                    viewModel.sendLastLike = it
                    if (it) viewModel.sendLiked = false
                }
            )
            CheckboxSetting(
                text = "Send liked",
                checked = viewModel.sendLiked,
                onCheckedChange = {
                    viewModel.sendLiked = it
                    if (it) viewModel.sendLastLike = false
                }
            )

            SectionHeader("Sync Interval")
            DropdownSetting(
                label = "Sync Interval",
                options = ApiSettingsOptions.SYNC_OPTIONS,
                selectedOption = viewModel.selectedSyncInterval,
                onOptionSelected = { viewModel.selectedSyncInterval = it },
                enabled = true
            )

            SectionHeader("API Key")
            TextFieldSetting(
                value = viewModel.apiKey,
                onValueChange = { viewModel.apiKey = it },
                label = "API Key (required)",
                isPassword = false, // Set true if you want visual transformation
                enabled = true
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveSettings(context) {
                        onSave()

                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                enabled = viewModel.apiKey.isNotBlank()
            ) {
                Text("Save Configuration", color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}



@Composable
fun SwitchSetting(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun CheckboxSetting(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}



@Composable
fun TextFieldSetting(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true
    )
}

@Preview(showBackground = true, device = "spec:width=360dp,height=1200dp,dpi=480")
@Composable
fun ApiSettingsScreenPreview() {
    MaterialTheme {
        ApiSettingsScreen(onSave = {})
    }
}