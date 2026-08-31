package com.jiee.box.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

/**
 * Physically stores a file uploaded by a client into the public Downloads
 * area, under a "JieeBox Reçus" subfolder — visible in any file manager,
 * not hidden inside app-private storage.
 */
object UploadStorage {

    private const val SUBFOLDER = "JieeBox Reçus"

    fun saveIncomingFile(context: Context, sourceTempFile: File, originalName: String): Pair<Uri, String>? {
        val safeName = sanitizeFileName(originalName)
        val mimeType = guessMimeType(safeName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceTempFile, safeName, mimeType)
        } else {
            saveLegacy(sourceTempFile, safeName)
        }?.let { it to mimeType }
    }

    private fun saveViaMediaStore(context: Context, source: File, name: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(itemUri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        } ?: return null

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(itemUri, values, null, null)
        return itemUri
    }

    private fun saveLegacy(source: File, name: String): Uri? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, SUBFOLDER)
            if (!targetDir.exists()) targetDir.mkdirs()
            var target = File(targetDir, name)
            var counter = 1
            while (target.exists()) {
                val dotIndex = name.lastIndexOf('.')
                val base = if (dotIndex >= 0) name.substring(0, dotIndex) else name
                val ext = if (dotIndex >= 0) name.substring(dotIndex) else ""
                target = File(targetDir, "$base ($counter)$ext")
                counter++
            }
            FileOutputStream(target).use { out -> source.inputStream().use { it.copyTo(out) } }
            Uri.fromFile(target)
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return cleaned.ifBlank { "fichier_recu_${System.currentTimeMillis()}" }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
