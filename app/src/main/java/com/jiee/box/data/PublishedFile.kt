package com.jiee.box.data

/**
 * A file the user has chosen to make available through the local server.
 *
 * We never copy the underlying bytes: [uri] is a persisted SAF (Storage Access
 * Framework) content URI that points straight at the original file wherever it
 * lives (Download, DCIM, a custom folder, an SD card, etc). [id] is a short
 * stable token derived from the URI, used in download links instead of the raw
 * content:// URI (shorter, and doesn't leak the provider's internal path shape).
 */
data class PublishedFile(
    val id: String,
    val uri: String,
    val displayName: String,
    val size: Long,
    val mimeType: String,
    /** Path segments of the folder this file lives in, e.g. ["PSP", "Saves"]
     *  for a file published as part of a folder. Empty = shown at the root
     *  of the web client (individually-picked files, spec section 5). */
    val folderPath: List<String> = emptyList(),
    /** Set to false by [com.jiee.box.data.FileRepository.refreshAvailability] if the
     *  file or its permission has disappeared since it was published. */
    val available: Boolean = true
) {
    companion object {
        fun idFor(uri: String): String {
            // A short, URL-safe, stable id. Collisions are astronomically unlikely
            // for a personal file list and are not security sensitive (worst case:
            // two files temporarily share one link, quickly resolved by re-publishing).
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(uri.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(12)
        }
    }
}

/** Human readable size, e.g. "1.4 Go", matching the wording used in the spec. */
fun Long.toHumanSize(): String {
    if (this <= 0) return "0 o"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    var value = this.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${value.toInt()} ${units[unitIndex]}"
    else "%.1f %s".format(value, units[unitIndex])
}
