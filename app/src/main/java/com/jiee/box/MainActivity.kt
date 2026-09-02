package com.jiee.box

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.jiee.box.ui.BoxViewModel
import com.jiee.box.ui.HomeScreen
import com.jiee.box.ui.theme.JieeBoxTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BoxViewModel by viewModels()

    // SAF: pick several individual files (spec section 5).
    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) viewModel.addFiles(uris) }

    // SAF: pick an entire folder, e.g. "PSP" or "Movies" (spec section 4/5).
    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.addFolder(it) } }

    // Android 13+ requires this to show the "BOX active" notification.
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: the foreground service still runs without it, just silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        requestBatteryOptimizationExemptionOnce()

        val onboardingPrefs = getSharedPreferences("jiee_box_onboarding", Context.MODE_PRIVATE)
        val showHelpInitially = !onboardingPrefs.getBoolean("seen_help", false)

        setContent {
            JieeBoxTheme {
                val files by viewModel.files.collectAsState()
                val receivedFiles by viewModel.receivedFiles.collectAsState()
                val transferLog by viewModel.transferLog.collectAsState()
                val serverState by viewModel.serverState.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val isImporting by viewModel.isImporting.collectAsState()
                val uploadProgress by viewModel.uploadProgress.collectAsState()

                HomeScreen(
                    files = files,
                    receivedFiles = receivedFiles,
                    transferLog = transferLog,
                    serverState = serverState,
                    settings = settings,
                    totalSize = viewModel.totalSize,
                    isImporting = isImporting,
                    uploadProgress = uploadProgress,
                    showHelpInitially = showHelpInitially,
                    onAddFiles = { pickFiles.launch(arrayOf("*/*")) },
                    onAddFolder = { pickFolder.launch(null) },
                    onRemoveFile = viewModel::removeFile,
                    onRemoveFiles = viewModel::removeFiles,
                    onRenameFile = viewModel::renameFile,
                    onPublishReceived = viewModel::publishReceivedFile,
                    onRemoveReceived = viewModel::removeReceivedFile,
                    onRenameReceived = viewModel::renameReceivedFile,
                    onStart = viewModel::startBox,
                    onStop = viewModel::stopBox,
                    onCopyAddress = { copyAddressToClipboard(serverState.address) },
                    onSaveSettings = viewModel::saveSettings,
                    onHelpDismissed = { onboardingPrefs.edit().putBoolean("seen_help", true).apply() }
                )
            }
        }
    }

    /** Asks once (not every launch) to be exempted from battery optimization —
     *  the single biggest cause of uploads/downloads stalling once the app
     *  isn't in the foreground on Samsung/Xiaomi/etc. Never re-prompts if the
     *  user dismisses it; they can still enable it later from Android's own
     *  battery settings. */
    private fun requestBatteryOptimizationExemptionOnce() {
        val prefs = getSharedPreferences("jiee_box_onboarding", Context.MODE_PRIVATE)
        if (prefs.getBoolean("asked_battery_optimization", false)) return
        prefs.edit().putBoolean("asked_battery_optimization", true).apply()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (_: Exception) {
                // Some OEM firmwares block this intent; nothing more we can do
                // automatically, the user can still allow it manually.
            }
        }
    }

    private fun copyAddressToClipboard(address: String?) {
        if (address == null) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("JIEE BOX address", address))
        Toast.makeText(this, "Adresse copiée", Toast.LENGTH_SHORT).show()
    }
}

