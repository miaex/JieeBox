package com.jiee.box.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UploadProgress(val percent: Int, val fromIp: String)

/**
 * Best-effort progress indicator for an in-flight client upload, shown on the
 * host's own screen ("j'aurais aimé voir la progression chez moi aussi").
 * NanoHTTPD parses multipart bodies synchronously with no progress callback
 * we can hook into directly, so [com.jiee.box.server.JieeHttpServer] instead
 * watches the size of the temp file NanoHTTPD is actively writing to and
 * reports it here — a close approximation, not an exact byte count.
 */
object UploadProgressTracker {
    private val _state = MutableStateFlow<UploadProgress?>(null)
    val state: StateFlow<UploadProgress?> = _state.asStateFlow()

    fun update(percent: Int, fromIp: String) {
        _state.value = UploadProgress(percent.coerceIn(0, 100), fromIp)
    }

    fun clear() {
        _state.value = null
    }
}
