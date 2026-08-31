package com.jiee.box

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
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

        setContent {
            JieeBoxTheme {
                val files by viewModel.files.collectAsState()
                val serverState by viewModel.serverState.collectAsState()
                val settings by viewModel.settings.collectAsState()

                HomeScreen(
                    files = files,
                    serverState = serverState,
                    settings = settings,
                    totalSize = viewModel.totalSize,
                    onAddFiles = { pickFiles.launch(arrayOf("*/*")) },
                    onAddFolder = { pickFolder.launch(null) },
                    onRemoveFile = viewModel::removeFile,
                    onStart = viewModel::startBox,
                    onStop = viewModel::stopBox,
                    onCopyAddress = { copyAddressToClipboard(serverState.address) },
                    onSaveSettings = viewModel::saveSettings
                )
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
