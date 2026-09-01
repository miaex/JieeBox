package com.jiee.box.server

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.jiee.box.data.FileRepository
import com.jiee.box.data.ReceivedFile
import com.jiee.box.data.ReceivedFileRepository
import com.jiee.box.data.UploadProgressTracker
import com.jiee.box.data.UploadStorage
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The actual local file server. Built on NanoHTTPD (a small embeddable HTTP
 * server library) rather than a hand-rolled socket loop, so we get correct
 * HTTP parsing, keep-alive, and thread-per-connection handling for free —
 * which is what lets several devices download at once (spec section 11)
 * without one big transfer blocking everything else.
 *
 * Routes:
 *   GET  /                  -> HTML listing of the current folder (WebUi)
 *   GET  /download?id=xxx   -> streams one published file, with HTTP Range support
 *   GET  /zip?dir=A/B       -> streams a .zip of everything in that folder (V1.1)
 *   GET  /logo.png          -> the app icon artwork, for branding the client page
 *   POST /upload            -> client -> box upload (V2 bidirectional transfer)
 */
class JieeHttpServer(
    port: Int,
    private val context: Context,
    private val repository: FileRepository,
    private val receivedRepository: ReceivedFileRepository,
    private val boxName: String = "JIEE BOX",
    private val password: String? = null
) : NanoHTTPD(port) {

    // IP -> last seen timestamp (ms). Used to approximate "N devices connected"
    // in the UI. NanoHTTPD doesn't expose device identity beyond the socket's
    // remote address, so this is a best-effort presence indicator, not a
    // security-relevant session list.
    private val recentClients = ConcurrentHashMap<String, Long>()
    private val presenceWindowMs = 60_000L

    val connectedDeviceCount: Int
        get() {
            val cutoff = System.currentTimeMillis() - presenceWindowMs
            recentClients.entries.removeAll { it.value < cutoff }
            return recentClients.size
        }

    override fun serve(session: IHTTPSession): Response {
        session.remoteIpAddress?.let { recentClients[it] = System.currentTimeMillis() }

        if (!password.isNullOrBlank() && !isAuthorized(session)) {
            val response = newFixedLengthResponse(
                Response.Status.UNAUTHORIZED, MIME_PLAINTEXT,
                "Mot de passe requis."
            )
            // Prompts the browser's own native login popup — no custom login
            // page needed, works the same in Chrome, Safari, Firefox, etc.
            response.addHeader("WWW-Authenticate", "Basic realm=\"${escapeHeader(boxName)}\"")
            return response
        }

        return try {
            when {
                session.uri == "/" || session.uri.isEmpty() -> serveIndex(session)
                session.uri == "/download" -> serveDownload(session)
                session.uri == "/zip" -> serveZip(session)
                session.uri == "/logo.png" -> serveLogo()
                session.uri == "/upload" && session.method == Method.POST -> serveUpload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Erreur serveur: ${e.message}"
            )
        }
    }

    /**
     * Standard HTTP Basic Auth check. The username is ignored on purpose —
     * V1 only needs a single shared access password, not per-user accounts.
     */
    private fun isAuthorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: return false
        if (!header.startsWith("Basic ", ignoreCase = true)) return false
        return try {
            val decoded = String(android.util.Base64.decode(header.removePrefix("Basic ").trim(), android.util.Base64.DEFAULT))
            val suppliedPassword = decoded.substringAfter(":", missingDelimiterValue = "")
            suppliedPassword == password
        } catch (_: Exception) {
            false
        }
    }

    private fun escapeHeader(value: String): String = value.replace("\"", "'")

    private fun serveIndex(session: IHTTPSession): Response {
        val dirParam = session.parms["dir"]
        val currentDir = dirParam?.split("/")?.filter { it.isNotBlank() } ?: emptyList()
        val sort = session.parms["sort"] ?: "name_asc"
        val html = WebUi.render(boxName, repository.files, currentDir, sort)
        val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        addNoCacheHeaders(response)
        return response
    }

    /**
     * Streams a published file straight from its content:// URI to the HTTP
     * response, never loading it fully into memory (spec section 10), and
     * honoring Range requests so browsers can show progress and resume
     * interrupted downloads on large files (spec section 10/11).
     */
    private fun serveDownload(session: IHTTPSession): Response {
        val id = session.parms["id"]
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing id")

        val file = repository.getById(id)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Fichier introuvable")

        if (!file.available) {
            return newFixedLengthResponse(
                Response.Status.GONE, MIME_PLAINTEXT,
                "Ce fichier n'est plus accessible sur le téléphone hôte."
            )
        }

        val uri = Uri.parse(file.uri)
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT,
                "Impossible d'ouvrir le fichier: ${e.message}"
            )
        } ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Fichier introuvable")

        val totalLength = file.size
        val rangeHeader = session.headers["range"]

        if (rangeHeader == null) {
            // Full-file streaming response. AutoCloseInputStream closes the underlying
            // ParcelFileDescriptor when NanoHTTPD closes the stream after sending —
            // a plain FileInputStream(pfd.fileDescriptor) would leak the fd.
            val stream: InputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val response = newFixedLengthResponse(Response.Status.OK, file.mimeType, stream, totalLength)
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Content-Disposition", contentDispositionFor(file.displayName))
            return response
        }

        // --- Range request: "bytes=START-END" (END optional) ---
        val (start, end) = parseRange(rangeHeader, totalLength)
            ?: run {
                pfd.close()
                return newFixedLengthResponse(
                    Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "Invalid range"
                ).also { it.addHeader("Content-Range", "bytes */$totalLength") }
            }

        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        stream.skip(start)
        val chunkLength = end - start + 1

        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT, file.mimeType, stream, chunkLength
        )
        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Range", "bytes $start-$end/$totalLength")
        response.addHeader("Content-Disposition", contentDispositionFor(file.displayName))
        return response
    }

    /**
     * Streams a .zip of every available file under the given folder (spec
     * section 20, V1.1: "téléchargement de dossiers"). Built on the fly with
     * a pipe so we never hold the whole archive in memory or on disk first —
     * consistent with the streaming requirement for large content (section 10).
     */
    private fun serveZip(session: IHTTPSession): Response {
        val dirParam = session.parms["dir"]
        val dir = dirParam?.split("/")?.filter { it.isNotBlank() } ?: emptyList()

        val filesToZip = repository.files.filter {
            it.available && it.folderPath.size >= dir.size && it.folderPath.subList(0, dir.size) == dir
        }
        if (filesToZip.isEmpty()) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Dossier vide ou introuvable")
        }

        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 128 * 1024)

        Thread {
            try {
                ZipOutputStream(pipedOut).use { zos ->
                    for (f in filesToZip) {
                        val relativeParts = f.folderPath.drop(dir.size) + f.displayName
                        val entryName = relativeParts.joinToString("/")
                        try {
                            zos.putNextEntry(ZipEntry(entryName))
                            context.contentResolver.openInputStream(Uri.parse(f.uri))?.use { it.copyTo(zos) }
                            zos.closeEntry()
                        } catch (_: Exception) {
                            // Skip a file that failed mid-zip (e.g. permission revoked)
                            // rather than aborting the whole archive for the others.
                        }
                    }
                }
            } catch (_: Exception) {
                // Reader side (NanoHTTPD) will simply see the pipe end/error out.
            }
        }.start()

        val zipName = (dir.lastOrNull() ?: boxName) + ".zip"
        val response = newChunkedResponse(Response.Status.OK, "application/zip", pipedIn)
        response.addHeader("Content-Disposition", contentDispositionFor(zipName))
        return response
    }

    /**
     * Serves the app's own icon artwork so the client page can show real
     * branding instead of a generic emoji — decoded from the app resource
     * each time rather than cached on disk, since it's a small image
     * requested rarely (browsers cache it via the header below anyway).
     */
    private fun serveLogo(): Response {
        return try {
            val resId = context.resources.getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "logo introuvable")
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            val response = newFixedLengthResponse(
                Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong()
            )
            response.addHeader("Cache-Control", "public, max-age=86400")
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "logo introuvable")
        }
    }

    /**
     * Receives a file uploaded by a client (V2: bidirectional transfer). Saved
     * straight into the public Downloads area (see [UploadStorage]) and
     * recorded in [receivedRepository] — visible to the host in the app's
     * "Fichiers reçus" list, NOT auto-published to other clients (the host
     * stays in control of what gets re-shared, per the request).
     */
    private fun serveUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        val fromIp = session.remoteIpAddress ?: "inconnu"
        val totalExpected = session.headers["content-length"]?.toLongOrNull()
        val progressWatcher = if (totalExpected != null && totalExpected > 0) {
            startProgressWatcher(totalExpected, fromIp)
        } else null

        return try {
            session.parseBody(files)

            val tempPath = files["file"]
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Aucun fichier reçu")
            val originalName = session.parms["file"]?.takeIf { it.isNotBlank() } ?: "fichier_recu"
            val tempFile = File(tempPath)

            val saved = UploadStorage.saveIncomingFile(context, tempFile, originalName)
                ?: return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Échec de l'enregistrement"
                )
            val (savedUri, mimeType) = saved

            receivedRepository.add(
                ReceivedFile(
                    id = java.util.UUID.randomUUID().toString(),
                    uri = savedUri.toString(),
                    displayName = originalName,
                    size = tempFile.length(),
                    mimeType = mimeType,
                    receivedAt = System.currentTimeMillis(),
                    fromIp = fromIp
                )
            )

            val response = newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "OK")
            addNoCacheHeaders(response)
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Erreur d'envoi: ${e.message}")
        } finally {
            progressWatcher?.interrupt()
            UploadProgressTracker.clear()
        }
    }

    /**
     * NanoHTTPD parses multipart bodies synchronously with no progress
     * callback, but it does write the incoming file to a real temp file in
     * our own cache dir (see the java.io.tmpdir fix in JieeBoxApplication) as
     * it reads. Polling that file's growing size against the request's
     * Content-Length gives a reasonably accurate live percentage to show on
     * the host's own screen without touching NanoHTTPD internals.
     */
    private fun startProgressWatcher(totalExpected: Long, fromIp: String): Thread {
        val cacheDir = context.cacheDir
        val before = cacheDir.listFiles()?.map { it.name }?.toSet() ?: emptySet()
        val thread = Thread {
            try {
                var trackedFile: File? = null
                while (!Thread.currentThread().isInterrupted) {
                    if (trackedFile == null) {
                        trackedFile = cacheDir.listFiles()
                            ?.filter { it.name !in before }
                            ?.maxByOrNull { it.lastModified() }
                    }
                    val written = trackedFile?.length() ?: 0L
                    val percent = ((written * 100) / totalExpected).toInt()
                    UploadProgressTracker.update(percent, fromIp)
                    Thread.sleep(250)
                }
            } catch (_: InterruptedException) {
                // Normal: interrupted once the upload finishes (see `finally` above).
            } catch (_: Exception) {
                // Best-effort progress only — never let this affect the actual upload.
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }

    /**
     * Builds an RFC 6266-safe Content-Disposition header. Many of the user's real
     * filenames contain accented characters (é, è, ', …). A raw, un-encoded
     * filename in this header is technically invalid HTTP (headers are meant to be
     * ASCII/Latin-1) and some HTTP stacks/browsers silently abort the download
     * instead of erroring visibly — exactly the "nothing happens on tap" symptom.
     * We send both a plain ASCII fallback (filename=) and the correctly
     * percent-encoded UTF-8 version (filename*=), per RFC 6266 — this is the
     * standard, broadly-supported way to serve non-ASCII filenames over HTTP.
     */
    private fun contentDispositionFor(displayName: String): String {
        val asciiFallback = displayName
            .map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }
            .joinToString("")
            .ifBlank { "fichier" }
        val encoded = java.net.URLEncoder.encode(displayName, "UTF-8").replace("+", "%20")
        return "attachment; filename=\"$asciiFallback\"; filename*=UTF-8''$encoded"
    }

    private fun parseRange(header: String, totalLength: Long): Pair<Long, Long>? {
        // Expected form: "bytes=START-END" or "bytes=START-"
        val spec = header.removePrefix("bytes=").trim()
        val parts = spec.split("-")
        if (parts.isEmpty()) return null
        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: (totalLength - 1)
        if (start < 0 || end >= totalLength || start > end) return null
        return start to end
    }

    /**
     * A browser on a network that just failed (no internet, dropped Wi-Fi, wrong
     * SIM routing, etc.) can cache that failed response and keep serving it from
     * cache even after the real box comes back — which looks like "only works in
     * incognito" to the user. Telling the browser never to cache these responses
     * avoids that class of confusing, hard-to-diagnose stale-failure behaviour.
     */
    private fun addNoCacheHeaders(response: Response) {
        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
    }
}
