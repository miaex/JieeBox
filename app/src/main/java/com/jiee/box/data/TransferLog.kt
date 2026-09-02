package com.jiee.box.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList

enum class TransferType { DOWNLOAD, UPLOAD, ZIP }

data class TransferLogEntry(
    val type: TransferType,
    val fileName: String,
    val ip: String,
    val timestamp: Long
)

/**
 * Rolling log of the last transfers in both directions — purely informational
 * for the host ("garder le contrôle sur ce qui se passe"), capped so it never
 * grows unbounded. Logged when a transfer *starts* (for downloads/zip we
 * can't reliably know from the request thread when streaming finishes), so
 * this reads as "activity", not a guaranteed completion record.
 */
class TransferLogRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jiee_box_transfer_log", Context.MODE_PRIVATE)
    private val maxEntries = 200
    private val _entries = CopyOnWriteArrayList<TransferLogEntry>()
    val entries: List<TransferLogEntry> get() = _entries.sortedByDescending { it.timestamp }

    init {
        _entries.addAll(loadFromDisk())
    }

    fun log(type: TransferType, fileName: String, ip: String) {
        _entries.add(0, TransferLogEntry(type, fileName, ip, System.currentTimeMillis()))
        while (_entries.size > maxEntries) _entries.removeAt(_entries.size - 1)
        saveToDisk()
    }

    fun clear() {
        _entries.clear()
        saveToDisk()
    }

    private fun saveToDisk() {
        val array = JSONArray()
        for (e in _entries) {
            val obj = JSONObject()
            obj.put("type", e.type.name)
            obj.put("name", e.fileName)
            obj.put("ip", e.ip)
            obj.put("ts", e.timestamp)
            array.put(obj)
        }
        prefs.edit().putString("log", array.toString()).apply()
    }

    private fun loadFromDisk(): List<TransferLogEntry> {
        val raw = prefs.getString("log", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TransferLogEntry(
                    type = TransferType.valueOf(obj.optString("type", "DOWNLOAD")),
                    fileName = obj.getString("name"),
                    ip = obj.getString("ip"),
                    timestamp = obj.getLong("ts")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
