package com.jiee.box.server

import com.jiee.box.data.PublishedFile
import com.jiee.box.data.toHumanSize

/**
 * Produces the plain HTML/CSS page served at "/". No JS framework, no build step,
 * no external CDN (the client may have zero internet access, only the local
 * hotspot) — everything is inlined so a single GET fully renders the page.
 *
 * Files carry a [PublishedFile.folderPath]; this renders one directory level
 * at a time (like a normal file browser) instead of one long flat list —
 * folders are clicked into via "?dir=A/B", decoded server-side in
 * [com.jiee.box.server.JieeHttpServer].
 */
object WebUi {

    fun render(boxName: String, files: List<PublishedFile>, currentDir: List<String>): String {
        val available = files.filter { it.available }

        val filesHere = available.filter { it.folderPath == currentDir }
        val subfolderNames = available
            .filter { it.folderPath.size > currentDir.size && it.folderPath.subList(0, currentDir.size) == currentDir }
            .map { it.folderPath[currentDir.size] }
            .distinct()
            .sorted()

        val subfolderStats = subfolderNames.associateWith { name ->
            val childDir = currentDir + name
            val filesUnder = available.filter { it.folderPath.size >= childDir.size && it.folderPath.subList(0, childDir.size) == childDir }
            filesUnder.size to filesUnder.sumOf { it.size }
        }

        val folderRows = subfolderNames.joinToString("\n") { name ->
            val (count, size) = subfolderStats[name]!!
            val childDir = currentDir + name
            """
            <a class="file-row folder-row" href="/?dir=${encodeDir(childDir)}">
              <div class="file-info">
                <span class="file-icon">📁</span>
                <span class="file-name">${escape(name)}</span>
                <span class="file-size">$count fichier(s) · ${size.toHumanSize()}</span>
              </div>
              <span class="chevron">›</span>
            </a>
            """.trimIndent()
        }

        val fileRows = filesHere.joinToString("\n") { f ->
            """
            <div class="file-row">
              <div class="file-info">
                <span class="file-icon">${iconFor(f.mimeType)}</span>
                <span class="file-name">${escape(f.displayName)}</span>
                <span class="file-size">${f.size.toHumanSize()}</span>
              </div>
              <a class="dl-btn" href="/download?id=${f.id}" download="${escape(f.displayName)}">Télécharger</a>
            </div>
            """.trimIndent()
        }

        val body = if (folderRows.isBlank() && fileRows.isBlank()) {
            "<p class=\"empty\">Ce dossier est vide.</p>"
        } else {
            "$folderRows\n$fileRows"
        }

        val breadcrumb = buildBreadcrumb(currentDir)
        val itemCount = available.count { it.folderPath == currentDir } + subfolderNames.size

        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>${escape(boxName)}</title>
          <style>
            :root { color-scheme: light; }
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background: #0f1117; color: #e8eaf0;
              min-height: 100vh;
            }
            header {
              padding: 24px 20px 16px; text-align: center;
              background: linear-gradient(180deg, #171a23, #0f1117);
              border-bottom: 1px solid #262a36;
            }
            header h1 { margin: 0; font-size: 22px; letter-spacing: 0.5px; }
            header p { margin: 4px 0 0; color: #8b90a0; font-size: 13px; }
            .breadcrumb {
              max-width: 640px; margin: 12px auto 0; padding: 0 16px;
              font-size: 13px; color: #8b90a0; display: flex; flex-wrap: wrap; gap: 4px;
            }
            .breadcrumb a { color: #6e93ff; text-decoration: none; }
            .breadcrumb span.sep { color: #565b6b; }
            main { max-width: 640px; margin: 0 auto; padding: 16px; }
            .file-row {
              display: flex; align-items: center; justify-content: space-between;
              background: #171a23; border: 1px solid #262a36; border-radius: 12px;
              padding: 12px 14px; margin-bottom: 10px;
            }
            .folder-row { text-decoration: none; color: inherit; }
            .chevron { color: #565b6b; font-size: 18px; padding-left: 8px; }
            .file-info { display: flex; align-items: center; gap: 10px; overflow: hidden; }
            .file-icon { font-size: 20px; }
            .file-name { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 260px; }
            .file-size { font-size: 12px; color: #8b90a0; flex-shrink: 0; }
            .dl-btn {
              flex-shrink: 0; margin-left: 10px; text-decoration: none;
              background: #4f7cff; color: white; font-size: 13px; font-weight: 600;
              padding: 8px 14px; border-radius: 8px;
            }
            .dl-btn:active { opacity: 0.8; }
            .empty { text-align: center; color: #8b90a0; margin-top: 40px; }
            footer { text-align: center; color: #565b6b; font-size: 11px; padding: 24px; }
          </style>
        </head>
        <body>
          <header>
            <h1>📦 ${escape(boxName)}</h1>
            <p>$itemCount élément(s) — connexion locale, sans internet</p>
          </header>
          <div class="breadcrumb">$breadcrumb</div>
          <main>
            $body
          </main>
          <footer>JIEE BOX — your personal offline file hub</footer>
        </body>
        </html>
        """.trimIndent()
    }

    private fun buildBreadcrumb(currentDir: List<String>): String {
        val root = if (currentDir.isEmpty()) {
            "<span>🏠 Racine</span>"
        } else {
            "<a href=\"/\">🏠 Racine</a>"
        }
        val parts = mutableListOf(root)
        for (i in currentDir.indices) {
            parts.add("<span class=\"sep\">/</span>")
            val segmentDir = currentDir.subList(0, i + 1)
            val label = escape(currentDir[i])
            parts.add(
                if (i == currentDir.lastIndex) "<span>$label</span>"
                else "<a href=\"/?dir=${encodeDir(segmentDir)}\">$label</a>"
            )
        }
        return parts.joinToString(" ")
    }

    private fun encodeDir(dir: List<String>): String =
        dir.joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

    private fun iconFor(mime: String): String = when {
        mime.startsWith("video") -> "🎬"
        mime.startsWith("audio") -> "🎵"
        mime.startsWith("image") -> "🖼️"
        mime.contains("pdf") -> "📄"
        mime.contains("zip") || mime.contains("iso") -> "🗜️"
        else -> "📄"
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

