package com.jiee.box.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiee.box.data.BoxSettings
import com.jiee.box.data.PublishedFile
import com.jiee.box.data.ReceivedFile
import com.jiee.box.data.TransferLogEntry
import com.jiee.box.data.TransferType
import com.jiee.box.data.UploadProgress
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
    transferLog: List<TransferLogEntry>,
    serverState: BoxServerState,
    settings: BoxSettings,
    totalSize: Long,
    isImporting: Boolean,
    uploadProgress: UploadProgress?,
    showHelpInitially: Boolean,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onRemoveFiles: (Set<String>) -> Unit,
    onRenameFile: (String, String) -> Unit,
    onPublishReceived: (String) -> Unit,
    onRemoveReceived: (String) -> Unit,
    onRenameReceived: (String, String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCopyAddress: () -> Unit,
    onSaveSettings: (BoxSettings) -> Unit,
    onHelpDismissed: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(showHelpInitially) }
    var showStopConfirm by remember { mutableStateOf(false) }
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
                Row {
                    IconButton(onClick = { showHelp = true }) {
                        Text("❓", fontSize = 18.sp)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }
            }

            StatusCard(serverState, onCopyAddress)

            if (uploadProgress != null) {
                Spacer(Modifier.height(10.dp))
                UploadProgressBanner(uploadProgress)
            }

            Spacer(Modifier.height(16.dp))

            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = JieeSurface,
                contentColor = JieeBlue
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text("Publiés (${files.size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text("📥 Reçus (${receivedFiles.count { !it.published }})", fontSize = 12.sp) }
                )
                Tab(
                    selected = tabIndex == 2,
                    onClick = { tabIndex = 2 },
                    text = { Text("🕓 Historique", fontSize = 12.sp) }
                )
            }

            Spacer(Modifier.height(8.dp))

            when (tabIndex) {
                0 -> {
                    PublishedTab(
                        files = files,
                        totalSize = totalSize,
                        onRemoveFile = onRemoveFile,
                        onRemoveFiles = onRemoveFiles,
                        onRenameFile = onRenameFile,
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
                }
                1 -> ReceivedTab(
                    receivedFiles = receivedFiles,
                    onPublish = onPublishReceived,
                    onRemove = onRemoveReceived,
                    onRename = onRenameReceived,
                    modifier = Modifier.weight(1f)
                )
                else -> HistoryTab(entries = transferLog, modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            if (serverState.isRunning) {
                Button(
                    onClick = {
                        if (serverState.activeTransfers > 0) showStopConfirm = true else onStop()
                    },
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

            Spacer(Modifier.height(10.dp))
            Text(
                "JIEE BOX · conçu par Jérémie K. ETSO",
                fontSize = 10.sp, color = JieeTextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
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

    if (showHelp) {
        HelpDialog(onDismiss = {
            showHelp = false
            onHelpDismissed()
        })
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Transfert en cours") },
            text = {
                Text("${serverState.activeTransfers} transfert(s) en cours. Arrêter la Box va les interrompre. Continuer ?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    onStop()
                }) { Text("Arrêter quand même", color = JieeTerracottaDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comment ça marche") },
        text = {
            Column {
                Text("1. Active le hotspot Wi-Fi du téléphone.", fontSize = 13.sp, color = JieeTextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("2. Ajoute des fichiers ou un dossier, puis appuie sur Démarrer la Box.", fontSize = 13.sp, color = JieeTextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("3. Sur l'autre appareil : connecte-toi au hotspot, puis scanne le QR code (bouton bleu) ou tape l'adresse technique.", fontSize = 13.sp, color = JieeTextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("4. Le client peut télécharger tes fichiers, ou t'en envoyer via le bouton en bas de sa page — tu les retrouves dans l'onglet Reçus.", fontSize = 13.sp, color = JieeTextPrimary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Compris") }
        }
    )
}

@Composable
private fun UploadProgressBanner(progress: UploadProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JieeSurface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            "📥 Réception depuis ${progress.fromIp}... ${progress.percent}%",
            color = JieeTerracotta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(JieeOutline, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (progress.percent / 100f).coerceIn(0f, 1f))
                    .background(JieeTerracotta, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun PublishedTab(
    files: List<PublishedFile>,
    totalSize: Long,
    onRemoveFile: (String) -> Unit,
    onRemoveFiles: (Set<String>) -> Unit,
    onRenameFile: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    var folderPendingDelete by remember { mutableStateOf<String?>(null) }
    var fileBeingRenamed by remember { mutableStateOf<PublishedFile?>(null) }

    LaunchedEffect(files) {
        val validIds = files.map { it.id }.toSet()
        val filtered = selectedIds.filter { it in validIds }.toSet()
        if (filtered != selectedIds) selectedIds = filtered
    }

    val rootFiles = remember(files) { files.filter { it.folderPath.isEmpty() }.sortedBy { it.displayName.lowercase() } }
    val folderGroups = remember(files) {
        files.filter { it.folderPath.isNotEmpty() }
            .groupBy { it.folderPath.first() }
            .toSortedMap(compareBy { it.lowercase() })
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
                items(rootFiles, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        selectionMode = selectionMode,
                        selected = file.id in selectedIds,
                        onToggleSelect = {
                            selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                        },
                        onRemove = { onRemoveFile(file.id) },
                        onRename = { fileBeingRenamed = file }
                    )
                }

                folderGroups.forEach { (folderName, filesInFolder) ->
                    item(key = "folder_$folderName") {
                        FolderGroupRow(
                            name = folderName,
                            files = filesInFolder,
                            expanded = folderName in expandedFolders,
                            selectionMode = selectionMode,
                            allSelected = filesInFolder.isNotEmpty() && filesInFolder.all { it.id in selectedIds },
                            onToggleExpand = {
                                expandedFolders = if (folderName in expandedFolders) expandedFolders - folderName
                                else expandedFolders + folderName
                            },
                            onToggleSelectAll = {
                                val ids = filesInFolder.map { it.id }.toSet()
                                selectedIds = if (ids.all { it in selectedIds }) selectedIds - ids else selectedIds + ids
                            },
                            onDeleteFolder = { folderPendingDelete = folderName }
                        )
                    }
                    if (folderName in expandedFolders) {
                        items(filesInFolder, key = { it.id }) { file ->
                            FileRow(
                                file = file,
                                selectionMode = selectionMode,
                                selected = file.id in selectedIds,
                                onToggleSelect = {
                                    selectedIds = if (file.id in selectedIds) selectedIds - file.id else selectedIds + file.id
                                },
                                onRemove = { onRemoveFile(file.id) },
                                onRename = { fileBeingRenamed = file },
                                indented = true
                            )
                        }
                    }
                }
            }
        }
    }

    folderPendingDelete?.let { folderName ->
        val idsToDelete = folderGroups[folderName]?.map { it.id }?.toSet() ?: emptySet()
        AlertDialog(
            onDismissRequest = { folderPendingDelete = null },
            title = { Text("Supprimer le dossier") },
            text = {
                Text(
                    "Retirer les ${idsToDelete.size} fichier(s) de \"$folderName\" de la liste publiée ?\n" +
                        "Les fichiers originaux sur ton téléphone ne seront pas touchés."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFiles(idsToDelete)
                    folderPendingDelete = null
                }) { Text("Supprimer", color = JieeTerracottaDeep) }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingDelete = null }) { Text("Annuler") }
            }
        )
    }

    fileBeingRenamed?.let { file ->
        RenameDialog(
            currentName = file.displayName,
            onDismiss = { fileBeingRenamed = null },
            onConfirm = {
                onRenameFile(file.id, it)
                fileBeingRenamed = null
            }
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("Renommer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun FolderGroupRow(
    name: String,
    files: List<PublishedFile>,
    expanded: Boolean,
    selectionMode: Boolean,
    allSelected: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    val count = files.size
    val size = files.sumOf { it.size }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(JieeSurface, RoundedCornerShape(10.dp))
            .clickable { onToggleExpand() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (selectionMode) {
                Checkbox(checked = allSelected, onCheckedChange = { onToggleSelectAll() })
                Spacer(Modifier.width(2.dp))
            }
            Text(if (expanded) "📂" else "📁", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(name, color = JieeTextPrimary, fontSize = 14.sp, maxLines = 1)
                Text("$count fichier(s) · ${size.toHumanSize()}", color = JieeTextSecondary, fontSize = 11.sp)
            }
        }
        if (!selectionMode) {
            TextButton(onClick = onDeleteFolder) {
                Text("🗑", fontSize = 14.sp)
            }
        }
        Text(if (expanded) "︿" else "﹀", color = JieeTextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun ReceivedTab(
    receivedFiles: List<ReceivedFile>,
    onPublish: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRename: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var fileBeingRenamed by remember { mutableStateOf<ReceivedFile?>(null) }

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
                    ReceivedFileRow(
                        file,
                        onPublish = { onPublish(file.id) },
                        onRemove = { onRemove(file.id) },
                        onRename = { fileBeingRenamed = file }
                    )
                }
            }
        }
    }

    fileBeingRenamed?.let { file ->
        RenameDialog(
            currentName = file.displayName,
            onDismiss = { fileBeingRenamed = null },
            onConfirm = {
                onRename(file.id, it)
                fileBeingRenamed = null
            }
        )
    }
}

private val historyTimeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

@Composable
private fun HistoryTab(entries: List<TransferLogEntry>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Activité récente — téléchargements, envois et archives .zip.",
            fontSize = 11.sp, color = JieeTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Aucune activité pour le moment.", color = JieeTextSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(entries, key = { "${it.timestamp}_${it.fileName}" }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(JieeSurface, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (entry.type) {
                                TransferType.DOWNLOAD -> "⬇️"
                                TransferType.UPLOAD -> "⬆️"
                                TransferType.ZIP -> "📦"
                            },
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.fileName, color = JieeTextPrimary, fontSize = 13.sp, maxLines = 1)
                            Text(
                                "${historyTimeFormat.format(Date(entry.timestamp))} · ${entry.ip}",
                                color = JieeTextSecondary, fontSize = 11.sp
                            )
                        }
                    }
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
    var showAddress by remember { mutableStateOf(false) }
    var showDevices by remember { mutableStateOf(false) }

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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    qrBitmap = QrCodeGenerator.generate(state.address)
                    showQr = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = JieeBlue),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("📷 Connecter un appareil (scanner)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showAddress = !showAddress }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (showAddress) "Masquer l'adresse technique" else "🔧 Voir l'adresse technique",
                    fontSize = 11.sp, color = JieeTextSecondary
                )
            }
            if (showAddress) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(JieeBackground, RoundedCornerShape(8.dp))
                        .clickable { onCopyAddress() }
                        .padding(10.dp)
                ) {
                    Text(state.address, color = JieeBlue, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Copier", color = JieeTextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDevices = !showDevices },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${state.connectedDevices} appareil(s) connecté(s)", color = JieeTextSecondary, fontSize = 12.sp)
                if (state.devices.isNotEmpty()) {
                    Text(if (showDevices) "︿" else "﹀", color = JieeTextSecondary, fontSize = 12.sp)
                }
            }
            if (showDevices && state.devices.isNotEmpty()) {
                Column(Modifier.animateContentSize().padding(top = 4.dp)) {
                    state.devices.forEach { device ->
                        val secondsAgo = ((System.currentTimeMillis() - device.lastSeenMs) / 1000).coerceAtLeast(0)
                        Text(
                            "• ${device.ip} — vu il y a ${secondsAgo}s",
                            fontSize = 11.sp, color = JieeTextSecondary,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
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
    onRemove: () -> Unit,
    onRename: () -> Unit,
    indented: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indented) 20.dp else 0.dp, top = 4.dp, bottom = 4.dp)
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
            val folderLabel = if (!indented && file.folderPath.isNotEmpty()) "📁 ${file.folderPath.joinToString(" / ")} · " else ""
            Text(
                folderLabel + file.size.toHumanSize() + if (!file.available) " · indisponible" else "",
                color = if (file.available) JieeTextSecondary else JieeTerracottaDeep,
                fontSize = 11.sp
            )
        }
        if (!selectionMode) {
            TextButton(onClick = onRename) {
                Text("✏️", fontSize = 13.sp)
            }
            TextButton(onClick = onRemove) {
                Text("Retirer", color = JieeTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private val receivedDateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)

@Composable
private fun ReceivedFileRow(file: ReceivedFile, onPublish: () -> Unit, onRemove: () -> Unit, onRename: () -> Unit) {
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
            TextButton(onClick = onRename, modifier = Modifier.height(34.dp)) {
                Text("✏️ Renommer", fontSize = 12.sp, color = JieeTextSecondary)
            }
            TextButton(onClick = onRemove, modifier = Modifier.height(34.dp)) {
                Text("Supprimer", color = JieeTerracottaDeep, fontSize = 12.sp)
            }
        }
    }
}
