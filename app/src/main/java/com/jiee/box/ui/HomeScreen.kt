package com.jiee.box.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiee.box.data.PublishedFile
import com.jiee.box.data.toHumanSize
import com.jiee.box.service.BoxServerState
import com.jiee.box.ui.theme.*

@Composable
fun HomeScreen(
    files: List<PublishedFile>,
    serverState: BoxServerState,
    totalSize: Long,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit
) {
    Scaffold(containerColor = JieeBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("JIEE BOX", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = JieeTextPrimary)
            Text(
                "Serveur de fichiers local portable",
                fontSize = 13.sp, color = JieeTextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            StatusCard(serverState, onCopyAddress)

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fichiers publiés",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = JieeTextPrimary
                )
                Text(
                    "${files.size} fichier(s) · ${totalSize.toHumanSize()}",
                    fontSize = 12.sp, color = JieeTextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aucun fichier pour le moment.", color = JieeTextSecondary)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(files, key = { it.id }) { file ->
                        FileRow(file, onRemove = { onRemoveFile(file.id) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAddFiles, modifier = Modifier.weight(1f)) {
                    Text("+ Fichiers")
                }
                OutlinedButton(onClick = onAddFolder, modifier = Modifier.weight(1f)) {
                    Text("+ Dossier")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (serverState.isRunning) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = JieeRed),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("🛑 ARRÊTER LA BOX", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = files.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = JieeBlue),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text("🚀 DÉMARRER LA BOX", fontWeight = FontWeight.Bold)
                }
            }

            serverState.error?.let {
                Text(it, color = JieeRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(state: BoxServerState, onCopyAddress: () -> Unit) {
    var showQr by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JieeSurface, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (state.isRunning) JieeGreen else JieeRed, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isRunning) "BOX ACTIVE" else "BOX INACTIVE",
                color = JieeTextPrimary, fontWeight = FontWeight.Bold
            )
        }

        if (state.isRunning && state.address != null) {
            Spacer(Modifier.height(10.dp))
            Text("Connectez vos appareils au hotspot Wi-Fi, puis ouvrez :", color = JieeTextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JieeBackground, RoundedCornerShape(8.dp))
                    .clickable { onCopyAddress() }
                    .padding(10.dp)
            ) {
                Text(state.address, color = JieeBlue, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("Copier", color = JieeTextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    qrBitmap = QrCodeGenerator.generate(state.address)
                    showQr = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📷 Afficher le QR code")
            }
            Spacer(Modifier.height(6.dp))
            Text("${state.connectedDevices} appareil(s) connecté(s)", color = JieeTextSecondary, fontSize = 12.sp)
        }
    }

    if (showQr && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("Fermer") }
            },
            title = { Text("Scannez pour rejoindre la BOX") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code d'accès à JIEE BOX",
                        modifier = Modifier.size(240.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "L'appareil qui scanne doit d'abord être connecté au même hotspot Wi-Fi.",
                        fontSize = 11.sp, color = JieeTextSecondary
                    )
                }
            }
        )
    }
}

@Composable
private fun FileRow(file: PublishedFile, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(JieeSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                file.displayName, color = JieeTextPrimary, fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                file.size.toHumanSize() + if (!file.available) " · indisponible" else "",
                color = if (file.available) JieeTextSecondary else JieeRed,
                fontSize = 11.sp
            )
        }
        TextButton(onClick = onRemove) {
            Text("Retirer", color = JieeTextSecondary, fontSize = 12.sp)
        }
    }
}
