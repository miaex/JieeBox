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

    /** Add one or more SAF-selected files (from OpenMultipleDocuments / OpenDocumentTree results). */
    fun addFiles(uris: List<Uri>) {
        for (uri in uris) {
            try {
                // Persist read access across reboots / app restarts (section 14 of the spec).
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers don't support persistable permissions; the file will
                // still work for this session, but may need re-selecting after a restart.
            }

            val doc = DocumentFile.fromSingleUri(context, uri) ?: continue
            val name = doc.name ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "fichier"
            val size = doc.length()
            val mime = doc.type ?: "application/octet-stream"
            val id = PublishedFile.idFor(uri.toString())

            if (_files.none { it.id == id }) {
                _files.add(PublishedFile(id, uri.toString(), name, size, mime, available = true))
            }
        }
        saveToDisk()
    }

    /** Add every file inside a picked SAF tree (folder), recursively. */
    fun addFolder(treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val collected = mutableListOf<Uri>()
        collectFilesRecursively(root, collected)
        addFiles(collected)
    }

    private fun collectFilesRecursively(dir: DocumentFile, out: MutableList<Uri>) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                collectFilesRecursively(child, out)
            } else if (child.isFile) {
                out.add(child.uri)
            }
        }
    }

    /** Remove a file from the published list. Never touches the file on disk. */
    fun removeFile(id: String) {
        _files.removeAll { it.id == id }
        saveToDisk()
    }

    fun getById(id: String): PublishedFile? = _files.find { it.id == id }

    /**
     * Re-check that every published file is still reachable (permission not revoked,
     * file not deleted/moved). Files that fail are flagged `available = false` rather
     * than silently dropped or crashing the server (spec section 14).
     */
    fun refreshAvailability() {
        val updated = _files.map { pf ->
            val stillThere = try {
                val doc = DocumentFile.fromSingleUri(context, Uri.parse(pf.uri))
                doc != null && doc.exists()
            } catch (_: Exception) {
                false
            }
            if (stillThere != pf.available) pf.copy(available = stillThere) else pf
        }
        _files.clear()
        _files.addAll(updated)
        saveToDisk()
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
                PublishedFile(
                    id = obj.getString("id"),
                    uri = obj.getString("uri"),
                    displayName = obj.getString("name"),
                    size = obj.getLong("size"),
                    mimeType = obj.optString("mime", "application/octet-stream"),
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
