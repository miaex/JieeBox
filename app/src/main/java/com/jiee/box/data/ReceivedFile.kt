package com.jiee.box.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

data class ReceivedFile(
    val id: String,
    /** content:// URI (MediaStore, API 29+) or file:// path (legacy, API 26-28)
     *  of the physically saved file — always in the public Downloads area, so
     *  it's visible in any file manager even outside the app. */
    val uri: String,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    val receivedAt: Long,
    val fromIp: String,
    /** True once the host has chosen to re-share this file with other clients
     *  (spec-driven control: uploads are reviewed, not auto broadcast). */
    val published: Boolean = false
)

/**
 * Tracks files clients have uploaded to the box. Kept separate from
 * [FileRepository] (published files) on purpose: an incoming upload is not
 * visible to other clients until the host explicitly publishes it — "avoir le
 * contrôle" on what leaves the phone vs what merely arrives on it.
 */
class ReceivedFileRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("jiee_box_received_files", Context.MODE_PRIVATE)
    private val _files = CopyOnWriteArrayList<ReceivedFile>()
    val files: List<ReceivedFile> get() = _files.sortedByDescending { it.receivedAt }

    init {
        _files.addAll(loadFromDisk())
    }

    fun add(file: ReceivedFile) {
        _files.add(file)
        saveToDisk()
    }

    fun markPublished(id: String) {
        val idx = _files.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _files[idx] = _files[idx].copy(published = true)
            saveToDisk()
        }
    }

    fun remove(id: String) {
        _files.removeAll { it.id == id }
        saveToDisk()
    }

    fun getById(id: String): ReceivedFile? = _files.find { it.id == id }

    /** Renames before publishing (or after — harmless either way), per the
     *  request to be able to rename a received file before sharing it back out. */
    fun rename(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val idx = _files.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _files[idx] = _files[idx].copy(displayName = trimmed)
            saveToDisk()
        }
    }

    private fun saveToDisk() {
        val array = JSONArray()
        for (f in _files) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("uri", f.uri)
            obj.put("name", f.displayName)
            obj.put("size", f.size)
            obj.put("mime", f.mimeType)
            obj.put("receivedAt", f.receivedAt)
            obj.put("fromIp", f.fromIp)
            obj.put("published", f.published)
            array.put(obj)
        }
        prefs.edit().putString("received", array.toString()).apply()
    }

    private fun loadFromDisk(): List<ReceivedFile> {
        val raw = prefs.getString("received", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                ReceivedFile(
                    id = obj.getString("id"),
                    uri = obj.getString("uri"),
                    displayName = obj.getString("name"),
                    size = obj.getLong("size"),
                    mimeType = obj.optString("mime", "application/octet-stream"),
                    receivedAt = obj.optLong("receivedAt", 0L),
                    fromIp = obj.optString("fromIp", ""),
                    published = obj.optBoolean("published", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
