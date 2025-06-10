package com.hmas.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
// import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
// import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
// import com.hmas.api.ui.common.SectionHeader

class WallpaperSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

            MaterialTheme {
                WallpaperSettingsScreen(
                    onSave = {
                        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onNavigateToApiSettings = {
                        startActivity(Intent(this, ApiSettingsActivity::class.java))
                    }
                )
            }
            // }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsScreen(
    viewModel: WallpaperSettingsViewModel = viewModel(),
    onSave: () -> Unit,
    onNavigateToApiSettings: () -> Unit
) {
    val context = LocalContext.current

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                viewModel.processAndSetBackgroundImage(it)
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        SectionHeader("Card Position")
        DropdownSetting(
            label = "Position",
            options = WallpaperUIOptions.POSITIONS,
            selectedOption = viewModel.selectedPosition,
            onOptionSelected = { viewModel.selectedPosition = it }
        )

        SectionHeader("Font Size")
        DropdownSetting(
            label = "Font Size",
            options = WallpaperUIOptions.FONT_SIZES,
            selectedOption = viewModel.selectedFontSize,
            onOptionSelected = { viewModel.selectedFontSize = it }
        )

        SectionHeader("Card Color")
        DropdownSetting(
            label = "Color",
            options = WallpaperUIOptions.CARD_COLORS,
            selectedOption = viewModel.selectedCardColor,
            onOptionSelected = { viewModel.selectedCardColor = it }
        )

        SectionHeader("Card Width")
        DropdownSetting(
            label = "Width",
            options = WallpaperUIOptions.CARD_WIDTHS,
            selectedOption = viewModel.selectedCardWidth,
            onOptionSelected = { viewModel.selectedCardWidth = it }
        )

        SectionHeader("Card Lines")
        DropdownSetting(
            label = "Lines",
            options = WallpaperUIOptions.CARD_LINES,
            selectedOption = viewModel.selectedCardLines,
            onOptionSelected = { viewModel.selectedCardLines = it }
        )

        SectionHeader("Notification Display")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Show notifications.")
            Switch(
                checked = viewModel.showNotifications,
                onCheckedChange = { viewModel.showNotifications = it }
            )
        }

        SectionHeader("Wallpaper Background")
        Button(
            onClick = {
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("Select Background Image")
        }

        if (viewModel.isProcessingImage) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        }

        Button(
            onClick = {
                viewModel.clearBackgroundImage()
                Toast.makeText(context, "Background restored to default.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Restore Default Background")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val apiKey = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
                    .getString("api_key", "") ?: ""
                if (apiKey.length < 32) {
                    Toast.makeText(context, "Warning: API key is too short or missing.", Toast.LENGTH_LONG).show()
                }
                viewModel.saveSettings(onSave)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Save Configuration")
        }

        OutlinedButton(
            onClick = onNavigateToApiSettings,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text("Advanced API Config")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun WallpaperSettingsScreenPreview() {
    MaterialTheme {
        WallpaperSettingsScreen(onSave = {}, onNavigateToApiSettings = {})
    }
}