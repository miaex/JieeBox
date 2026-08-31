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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiee.box.data.BoxSettings
import com.jiee.box.data.PublishedFile
import com.jiee.box.data.ReceivedFile
import com.jiee.box.data.toHumanSize
import com.jiee.box.service.BoxServerState
import com.jiee.box.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    files: List<PublishedFile>,
    receivedFiles: List<ReceivedFile>,
    serverState: BoxServerState,
    settings: BoxSettings,
    totalSize: Long,
    isImporting: Boolean,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onRemoveFiles: (Set<String>) -> Unit,
    onPublishReceived: (String) -> Unit,
    onRemoveReceived: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit,
    onSaveSettings: (BoxSettings) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableStateOf(0) }

    Scaffold(containerColor = JieeBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(settings.boxName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = JieeTextPrimary)
                    Text(
                        "Serveur de fichiers local portable",
                        fontSize = 13.sp, color = JieeTextSecondary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Text("⚙️", fontSize = 20.sp)
                }
            }

            StatusCard(serverState, onCopyAddress)

            Spacer(Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = JieeSurface,
                contentColor = JieeBlue
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Publiés (${files.size})") }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("📥 Reçus (${receivedFiles.count { !it.published }})") }
                )
            }

            Spacer(Modifier.height(8.dp))

            if (tabIndex == 0) {
                PublishedTab(
                    files = files,
                    totalSize = totalSize,
                    onRemoveFile = onRemoveFile,
                    onRemoveFiles = onRemoveFiles,
                    modifier = Modifier.weight(1f)
                )
                if (isImporting) {
                    Text(
                        "⏳ Importation en cours...",
                        color = JieeTerracotta, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onAddFiles, enabled = !isImporting, modifier = Modifier.weight(1f)) {
                        Text("+ Fichiers")
                    }
                    OutlinedButton(onClick = onAddFolder, enabled = !isImporting, modifier = Modifier.weight(1f)) {
                        Text("+ Dossier")
                    }
                }
            } else {
                ReceivedTab(
                    receivedFiles = receivedFiles,
                    onPublish = onPublishReceived,
                    onRemove = onRemoveReceived,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            if (serverState.isRunning) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = JieeTerracottaDeep),
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
                Text(it, color = JieeTerracottaDeep, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            current = settings,
            serverRunning = serverState.isRunning,
            onDismiss = { showSettings = false },
            onSave = {
                onSaveSettings(it)
                showSettings = false
            }
        )
    }
}

@Composable
private fun PublishedTab(
    files: List<PublishedFile>,
    totalSize: Long,
    onRemoveFile: (String) -> Unit,
    onRemoveFiles: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // Keep the selection in sync if the underlying list changes (e.g. a file
    // disappears after refreshAvailability, or the bulk-delete just ran).
    LaunchedEffect(files) {
        val validIds = files.map { it.id }.toSet()
        val filtered = selectedIds.filter { it in validIds }.toSet()
        if (filtered != selectedIds) selectedIds = filtered
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Accessibles aux clients", fontSize = 12.sp, color = JieeTextSecondary)
            Text(totalSize.toHumanSize(), fontSize = 12.sp, color = JieeTextSecondary)
        }

        if (files.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds = emptySet()
                }) {
                    Text(if (selectionMode) "Annuler" else "☑️ Sélectionner", fontSize = 12.sp, color = JieeBlue)
                }
                if (selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == files.size) emptySet()
                            else files.map { it.id }.toSet()
                        }) {
                            Text(
                                if (selectedIds.size == files.size) "Aucun" else "Tout (${files.size})",
                                fontSize = 12.sp, color = JieeBlue
                            )
                        }
                        if (selectedIds.isNotEmpty()) {
                            TextButton(onClick = {
                                onRemoveFiles(selectedIds)
                                selectedIds = emptySet()
                                selectionMode = false
                            }) {
                                Text("Supprimer (${selectedIds.size})", fontSize = 12.sp, color = JieeTerracottaDeep)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Aucun fichier pour le moment.", color = JieeTextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        selectionMode = selectionMode,
                        selected = file.id in selectedIds,
                        onToggleSelect = {
                            selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                        },
                        onRemove = { onRemoveFile(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedTab(
    receivedFiles: List<ReceivedFile>,
    onPublish: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            "Fichiers envoyés par des appareils clients — restent privés tant que tu ne cliques pas sur \"Publier\".",
            fontSize = 11.sp, color = JieeTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (receivedFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Aucun fichier reçu pour le moment.", color = JieeTextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(receivedFiles, key = { it.id }) { file ->
                    ReceivedFileRow(file, onPublish = { onPublish(file.id) }, onRemove = { onRemove(file.id) })
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    current: BoxSettings,
    serverRunning: Boolean,
    onDismiss: () -> Unit,
    onSave: (BoxSettings) -> Unit
) {
    var name by remember { mutableStateOf(current.boxName) }
    var password by remember { mutableStateOf(current.password ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la Box") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe (optionnel)") },
                    placeholder = { Text("Laisser vide = accès libre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (serverRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "La Box est active : redémarre-la (Arrêter puis Démarrer) pour appliquer ces changements.",
                        fontSize = 11.sp, color = JieeTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(BoxSettings(boxName = name.trim().ifBlank { "JIEE BOX" }, password = password.trim().ifBlank { null }))
            }) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
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
                    .background(if (state.isRunning) JieeTerracotta else JieeOutline, RoundedCornerShape(50))
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
private fun FileRow(
    file: PublishedFile,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(JieeSurface, RoundedCornerShape(10.dp))
            .let { if (selectionMode) it.clickable { onToggleSelect() } else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                file.displayName, color = JieeTextPrimary, fontSize = 14.sp,
                maxLines = 1
            )
            val folderLabel = if (file.folderPath.isNotEmpty()) "📁 ${file.folderPath.joinToString(" / ")} · " else ""
            Text(
                folderLabel + file.size.toHumanSize() + if (!file.available) " · indisponible" else "",
                color = if (file.available) JieeTextSecondary else JieeTerracottaDeep,
                fontSize = 11.sp
            )
        }
        if (!selectionMode) {
            TextButton(onClick = onRemove) {
                Text("Retirer", color = JieeTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private val receivedDateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

@Composable
private fun ReceivedFileRow(file: ReceivedFile, onPublish: () -> Unit, onRemove: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(JieeSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(file.displayName, color = JieeTextPrimary, fontSize = 14.sp, maxLines = 1)
        Text(
            "${file.size.toHumanSize()} · ${receivedDateFormat.format(Date(file.receivedAt))} · depuis ${file.fromIp}" +
                if (file.published) " · publié" else "",
            color = if (file.published) JieeTerracotta else JieeTextSecondary,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!file.published) {
                OutlinedButton(onClick = onPublish, modifier = Modifier.height(34.dp)) {
                    Text("Publier", fontSize = 12.sp)
                }
            }
            TextButton(onClick = onRemove, modifier = Modifier.height(34.dp)) {
                Text("Supprimer", color = JieeTerracottaDeep, fontSize = 12.sp)
            }
        }
    }
}
