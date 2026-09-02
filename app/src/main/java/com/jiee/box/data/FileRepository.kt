package com.jiee.box.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the list of published files.
 *
 * Files are never moved or copied. We only ever hold a `content://` URI plus the
 * metadata needed to display it, and we ask Android to persist our read
 * permission on that URI across app/device restarts (see [addFiles]).
 */
class FileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("jiee_box_published_files", Context.MODE_PRIVATE)
    private val resolver: ContentResolver = context.contentResolver

    // In-memory list kept in sync with disk. Thread-safe: the HTTP server (background
    // threads) reads this concurrently with the UI (main thread).
    private val _files = CopyOnWriteArrayList<PublishedFile>()
    val files: List<PublishedFile> get() = _files.toList()

    init {
        _files.addAll(loadFromDisk())
    }

    /** Add one or more SAF-selected files (from OpenMultipleDocuments), shown at
     *  the root of the web client — they weren't part of any picked folder. */
    fun addFiles(uris: List<Uri>) {
        publish(uris.map { Entry(it, emptyList(), grantPermission = true) })
    }

    /** Add every file inside a picked SAF tree (folder), recursively, preserving
     *  the folder structure so the web client can browse it the same way
     *  (spec-driven addition: "je veux pouvoir mettre les fichiers dans des
     *  dossiers au niveau de l'affichage"). */
    fun addFolder(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val rootName = root.name ?: "Dossier"
        val collected = mutableListOf<Entry>()
        collectFilesRecursively(root, listOf(rootName), collected)
        publish(collected)
    }

    /** Directly registers a file we already have trustworthy metadata for
     *  (e.g. one we just saved ourselves via MediaStore after a client
     *  upload) — skips the SAF/DocumentFile lookup entirely, since that path
     *  assumes a document picked through the Storage Access Framework. */
    fun addKnownFile(uri: String, displayName: String, size: Long, mimeType: String, folderPath: List<String> = emptyList()) {
        val id = PublishedFile.idFor(uri)
        if (_files.none { it.id == id }) {
            _files.add(PublishedFile(id, uri, displayName, size, mimeType, folderPath, available = true))
            saveToDisk()
        }
    }

    private data class Entry(val uri: Uri, val folderPath: List<String>, val grantPermission: Boolean)

    private fun collectFilesRecursively(
        dir: DocumentFile,
        path: List<String>,
        out: MutableList<Entry>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                collectFilesRecursively(child, path + (child.name ?: "dossier"), out)
            } else if (child.isFile) {
                // grantPermission = false: this file's access already comes from
                // the ONE persistable grant taken on the parent tree above —
                // requesting it again per-file is redundant, slow at scale (was
                // the direct cause of a crash/ANR importing ~3000 files at
                // once), and silently exhausts Android's ~128-grant-per-app quota.
                out.add(Entry(child.uri, path, grantPermission = false))
            }
        }
    }

    /** Shared publish logic: persists permission (only when actually needed),
     *  reads metadata, avoids duplicates. */
    private fun publish(entries: List<Entry>) {
        for (entry in entries) {
            if (entry.grantPermission) {
                try {
                    // Persist read access across reboots / app restarts (section 14 of the spec).
                    context.contentResolver.takePersistableUriPermission(
                        entry.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers don't support persistable permissions; the file will
                    // still work for this session, but may need re-selecting after a restart.
                }
            }

            val doc = DocumentFile.fromSingleUri(context, entry.uri) ?: continue
            val name = doc.name ?: queryDisplayName(entry.uri) ?: entry.uri.lastPathSegment ?: "fichier"
            val size = doc.length()
            val mime = doc.type ?: "application/octet-stream"
            val id = PublishedFile.idFor(entry.uri.toString())

            if (_files.none { it.id == id }) {
                _files.add(PublishedFile(id, entry.uri.toString(), name, size, mime, entry.folderPath, available = true))
            }
        }
        saveToDisk()
    }

    /** Remove a file from the published list. Never touches the file on disk. */
    fun removeFile(id: String) {
        _files.removeAll { it.id == id }
        saveToDisk()
    }

    /** Bulk removal — one disk write for the whole batch, not one per file,
     *  so clearing hundreds/thousands of entries stays fast and doesn't
     *  hammer SharedPreferences. */
    fun removeFiles(ids: Set<String>) {
        if (ids.isEmpty()) return
        _files.removeAll { it.id in ids }
        saveToDisk()
    }

    fun getById(id: String): PublishedFile? = _files.find { it.id == id }

    /** Renames a file for display/download purposes only — never touches the
     *  original file on disk, just the label the web client sees. */
    fun renameFile(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val idx = _files.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _files[idx] = _files[idx].copy(displayName = trimmed)
            saveToDisk()
        }
    }

    /**
     * Re-check that every published file is still reachable (permission not revoked,
     * file not deleted/moved). Files that fail are flagged `available = false` rather
     * than silently dropped or crashing the server (spec section 14).
     */
    fun refreshAvailability() {
        val updated = _files.map { pf ->
            if (checkFileExists(Uri.parse(pf.uri)) != pf.available) pf.copy(available = !pf.available) else pf
        }
        _files.clear()
        _files.addAll(updated)
        saveToDisk()
    }

    /** DocumentFile assumes a SAF document; files added via [addKnownFile]
     *  (e.g. MediaStore-origin uploads) aren't necessarily one, so this falls
     *  back to a plain ContentResolver query when the DocumentFile check is
     *  inconclusive, instead of wrongly marking them unavailable. */
    private fun checkFileExists(uri: Uri): Boolean {
        val viaDocumentFile = try {
            DocumentFile.fromSingleUri(context, uri)?.exists() == true
        } catch (_: Exception) {
            false
        }
        if (viaDocumentFile) return true
        return try {
            resolver.query(uri, null, null, null, null)?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    val totalSize: Long get() = _files.sumOf { it.size }

    // --- persistence -------------------------------------------------------

    private fun saveToDisk() {
        val array = JSONArray()
        for (f in _files) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("uri", f.uri)
            obj.put("name", f.displayName)
            obj.put("size", f.size)
            obj.put("mime", f.mimeType)
            obj.put("folder", JSONArray(f.folderPath))
            array.put(obj)
        }
        prefs.edit().putString("files", array.toString()).apply()
    }

    private fun loadFromDisk(): List<PublishedFile> {
        val raw = prefs.getString("files", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val folderArray = obj.optJSONArray("folder")
                val folderPath = if (folderArray != null) {
                    (0 until folderArray.length()).map { folderArray.getString(it) }
                } else emptyList()
                PublishedFile(
                    id = obj.getString("id"),
                    uri = obj.getString("uri"),
                    displayName = obj.getString("name"),
                    size = obj.getLong("size"),
                    mimeType = obj.optString("mime", "application/octet-stream"),
                    folderPath = folderPath,
                    available = true // re-validated by refreshAvailability() at startup
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
