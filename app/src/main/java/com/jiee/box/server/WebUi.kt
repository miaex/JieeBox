package com.jiee.box.server

import com.jiee.box.data.PublishedFile
import com.jiee.box.data.toHumanSize

/**
 * Produces the plain HTML/CSS page served at "/". No JS framework, no build step,
 * no external CDN (the client may have zero internet access, only the local
 * hotspot) — everything is inlined so a single GET fully renders the page.
 */
object WebUi {

    fun render(boxName: String, files: List<PublishedFile>): String {
        val rows = if (files.isEmpty()) {
            "<p class=\"empty\">Aucun fichier publié pour le moment.</p>"
        } else {
            files.filter { it.available }.joinToString("\n") { f ->
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
        }

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
            main { max-width: 640px; margin: 0 auto; padding: 16px; }
            .file-row {
              display: flex; align-items: center; justify-content: space-between;
              background: #171a23; border: 1px solid #262a36; border-radius: 12px;
              padding: 12px 14px; margin-bottom: 10px;
            }
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
            <p>${files.count { it.available }} fichier(s) disponible(s) — connexion locale, sans internet</p>
          </header>
          <main>
            $rows
          </main>
          <footer>JIEE BOX — your personal offline file hub</footer>
        </body>
        </html>
        """.trimIndent()
    }

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
