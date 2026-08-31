package com.jiee.box.server

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.jiee.box.data.FileRepository
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * The actual local file server. Built on NanoHTTPD (a small embeddable HTTP
 * server library) rather than a hand-rolled socket loop, so we get correct
 * HTTP parsing, keep-alive, and thread-per-connection handling for free —
 * which is what lets several devices download at once (spec section 11)
 * without one big transfer blocking everything else.
 *
 * Two routes only, on purpose (V1 = simple & reliable, per spec section 19):
 *   GET /                 -> HTML listing of published files (WebUi)
 *   GET /download?id=xxx  -> streams one file, with HTTP Range support
 */
class JieeHttpServer(
    port: Int,
    private val context: Context,
    private val repository: FileRepository,
    private val boxName: String = "JIEE BOX"
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

        return try {
            when {
                session.uri == "/" || session.uri.isEmpty() -> serveIndex()
                session.uri == "/download" -> serveDownload(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                "Erreur serveur: ${e.message}"
            )
        }
    }

    private fun serveIndex(): Response {
        val html = WebUi.render(boxName, repository.files)
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
