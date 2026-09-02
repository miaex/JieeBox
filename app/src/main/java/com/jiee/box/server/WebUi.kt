package com.jiee.box.server

import com.jiee.box.data.PublishedFile
import com.jiee.box.data.toHumanSize

/**
 * Produces the plain HTML/CSS/small-JS page served at "/". No framework, no
 * build step, no external CDN (the client may have zero internet access,
 * only the local hotspot) — everything is inlined so a single GET fully
 * renders the page.
 *
 * Files carry a [PublishedFile.folderPath]; this renders one directory level
 * at a time (like a normal file browser) instead of one long flat list.
 * Search/sort are handled without extra round-trips where possible (search
 * is instant client-side JS; sort re-renders server-side via a query param
 * so the ordering also applies to "download all as zip").
 */
object WebUi {

    fun render(boxName: String, files: List<PublishedFile>, currentDir: List<String>, sort: String): String {
        val available = files.filter { it.available }

        val filesHere = available.filter { it.folderPath == currentDir }
        val subfolderNames = available
            .filter { it.folderPath.size > currentDir.size && it.folderPath.subList(0, currentDir.size) == currentDir }
            .map { it.folderPath[currentDir.size] }
            .distinct()

        val subfolderStats = subfolderNames.associateWith { name ->
            val childDir = currentDir + name
            val filesUnder = available.filter { it.folderPath.size >= childDir.size && it.folderPath.subList(0, childDir.size) == childDir }
            filesUnder.size to filesUnder.sumOf { it.size }
        }

        val sortedFolderNames = sortFolders(subfolderNames, subfolderStats, sort)
        val sortedFiles = sortFiles(filesHere, sort)

        val folderRows = sortedFolderNames.joinToString("\n") { name ->
            val (count, size) = subfolderStats[name]!!
            val childDir = currentDir + name
            """
            <a class="file-row folder-row" href="/?dir=${encodeDir(childDir)}&sort=$sort">
              <div class="file-info">
                <span class="file-icon">📁</span>
                <span class="file-name">${escape(name)}</span>
                <span class="file-size">$count fichier(s) · ${size.toHumanSize()}</span>
              </div>
              <span class="chevron">›</span>
            </a>
            """.trimIndent()
        }

        val fileRows = sortedFiles.joinToString("\n") { f ->
            val iconHtml = if (f.mimeType.startsWith("image")) {
                """<span class="file-icon-wrap"><img class="file-thumb" src="/thumbnail?id=${f.id}" loading="lazy" alt=""
                     onerror="this.style.display='none';this.nextElementSibling.style.display='inline'">
                   <span class="file-icon" style="display:none">${iconFor(f.mimeType)}</span></span>"""
            } else {
                """<span class="file-icon">${iconFor(f.mimeType)}</span>"""
            }
            """
            <div class="file-row">
              <div class="file-info">
                $iconHtml
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

        val breadcrumb = buildBreadcrumb(currentDir, sort)
        val itemCount = filesHere.size + sortedFolderNames.size
        val (nameSortHref, nameSortLabel) = sortLink("name", sort, currentDir)
        val (sizeSortHref, sizeSortLabel) = sortLink("size", sort, currentDir)
        val zipHref = "/zip?dir=${encodeDir(currentDir)}"

        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <meta name="theme-color" content="#241C17">
          <link rel="icon" type="image/png" href="/logo.png">
          <link rel="apple-touch-icon" href="/logo.png">
          <title>${escape(boxName)}</title>
          <style>
            :root { color-scheme: dark; }
            * { box-sizing: border-box; }
            body {
              margin: 0; padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
              background: linear-gradient(180deg, #241C17, #2A211B);
              color: #F3E9DD;
              min-height: 100vh;
            }
            header {
              padding: 28px 20px 18px; text-align: center;
              background: linear-gradient(180deg, #2E2620, #241C17);
              border-bottom: 1px solid #3D332B;
              position: relative;
            }
            .logo { width: 56px; height: 56px; border-radius: 16px; display: block; margin: 0 auto 10px; box-shadow: 0 4px 14px rgba(240, 130, 78, 0.25); }
            header h1 { margin: 0; font-size: 21px; letter-spacing: 0.5px; color: #F3E9DD; }
            header p.tagline { margin: 3px 0 0; color: #8A796C; font-size: 12px; font-style: italic; }
            header p.stats { margin: 8px 0 0; color: #B7A48F; font-size: 13px; }
            .help-btn {
              position: absolute; top: 16px; right: 16px;
              background: #2E2620; border: 1px solid #3D332B; color: #B7A48F;
              font-size: 12px; padding: 7px 11px; border-radius: 20px; cursor: pointer;
            }
            .toolbar {
              max-width: 640px; margin: 14px auto 0; padding: 0 16px;
              display: flex; gap: 8px; flex-wrap: wrap; align-items: center;
            }
            #searchBox {
              flex: 1; min-width: 140px; background: #2E2620; border: 1px solid #3D332B;
              color: #F3E9DD; border-radius: 8px; padding: 9px 12px; font-size: 14px;
            }
            #searchBox::placeholder { color: #8A7A6C; }
            .sort-link, .zip-link {
              font-size: 12px; color: #B7A48F; text-decoration: none;
              padding: 8px 10px; background: #2E2620; border: 1px solid #3D332B; border-radius: 8px;
              white-space: nowrap;
            }
            .sort-link.active { color: #7FA6E8; border-color: #7FA6E8; }
            .zip-link { color: #F0824E; border-color: #F0824E; }
            .breadcrumb {
              max-width: 640px; margin: 12px auto 0; padding: 0 16px;
              font-size: 13px; color: #B7A48F; display: flex; flex-wrap: wrap; gap: 4px;
            }
            .breadcrumb a { color: #7FA6E8; text-decoration: none; font-weight: 600; }
            .breadcrumb span.sep { color: #55483C; }
            main { max-width: 640px; margin: 0 auto; padding: 16px; }
            .file-row {
              display: flex; align-items: center; justify-content: space-between;
              background: #2E2620; border: 1px solid #3D332B; border-radius: 12px;
              padding: 12px 14px; margin-bottom: 10px;
              animation: fadeInUp 0.25s ease both;
              transition: transform 0.12s ease, background 0.12s ease;
            }
            .file-row:hover { background: #35291F; transform: translateY(-1px); }
            @keyframes fadeInUp {
              from { opacity: 0; transform: translateY(6px); }
              to { opacity: 1; transform: translateY(0); }
            }
            .folder-row { text-decoration: none; color: inherit; }
            .chevron { color: #F0824E; font-size: 18px; padding-left: 8px; }
            .file-info { display: flex; align-items: center; gap: 10px; overflow: hidden; }
            .file-icon { font-size: 20px; }
            .file-icon-wrap { display: inline-flex; }
            .file-thumb { width: 32px; height: 32px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
            .file-name { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; color: #F3E9DD; }
            .file-size { font-size: 12px; color: #B7A48F; flex-shrink: 0; }
            .dl-btn {
              flex-shrink: 0; margin-left: 10px; text-decoration: none;
              background: #7FA6E8; color: #1B1512; font-size: 13px; font-weight: 700;
              padding: 8px 14px; border-radius: 8px;
            }
            .dl-btn:active { opacity: 0.8; }
            .empty { text-align: center; color: #B7A48F; margin-top: 40px; }
            footer { text-align: center; padding: 28px 20px 24px; border-top: 1px solid #3D332B; margin-top: 8px; }
            .brand-name { font-size: 13px; letter-spacing: 1.5px; color: #B7A48F; font-weight: 700; }
            .brand-tagline { font-size: 11px; color: #6E5E4F; margin-top: 6px; }
            .brand-tagline strong { color: #8A796C; font-weight: 600; }
            .brand-sub { font-size: 10.5px; color: #55483C; margin-top: 4px; font-style: italic; }

            .upload-section {
              max-width: 640px; margin: 8px auto 0; padding: 0 16px 4px;
            }
            .upload-btn {
              width: 100%; padding: 13px; border-radius: 12px; border: 1.5px dashed #F0824E;
              background: rgba(240, 130, 78, 0.08); color: #F0824E; font-size: 14px; font-weight: 700;
              cursor: pointer;
            }
            .upload-row {
              display: flex; align-items: center; gap: 8px;
              background: #2E2620; border: 1px solid #3D332B; border-radius: 10px;
              padding: 10px 12px; margin-top: 8px; font-size: 12px;
            }
            .upload-name { flex-shrink: 0; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #F3E9DD; }
            .upload-bar-track { flex: 1; height: 6px; background: #3D332B; border-radius: 4px; overflow: hidden; }
            .upload-bar-fill { height: 100%; width: 0%; background: #7FA6E8; transition: width 0.15s ease; }
            .upload-status { flex-shrink: 0; width: 56px; text-align: right; color: #B7A48F; }

            .modal-overlay {
              display: none; position: fixed; inset: 0; background: rgba(15, 11, 8, 0.72);
              align-items: center; justify-content: center; z-index: 100; padding: 20px;
            }
            .modal-card {
              background: #2E2620; border: 1px solid #3D332B; border-radius: 16px;
              max-width: 480px; width: 100%; max-height: 80vh; overflow-y: auto;
              padding: 20px;
            }
            .modal-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
            .modal-header h2 { margin: 0; font-size: 17px; color: #F3E9DD; }
            .modal-close { background: none; border: none; color: #B7A48F; font-size: 20px; cursor: pointer; padding: 4px 8px; }
            .lang-tabs { display: flex; gap: 6px; margin-bottom: 14px; }
            .lang-tab {
              flex: 1; text-align: center; padding: 7px; border-radius: 8px; font-size: 12px;
              background: #241C17; color: #B7A48F; cursor: pointer; border: 1px solid #3D332B;
            }
            .lang-tab.active { background: #7FA6E8; color: #1B1512; font-weight: 700; border-color: #7FA6E8; }
            .howto-body { font-size: 13.5px; line-height: 1.6; color: #E4D9CB; }
            .howto-body h3 { font-size: 14px; color: #F0824E; margin: 14px 0 6px; }
            .howto-body ol, .howto-body ul { margin: 4px 0; padding-left: 20px; }
            .howto-lang { display: none; }
            .howto-lang.active { display: block; }
          </style>
        </head>
        <body>
          <header>
            <button class="help-btn" onclick="document.getElementById('howToModal').style.display='flex'">❓ Aide</button>
            <img class="logo" src="/logo.png" alt="${escape(boxName)}">
            <h1>${escape(boxName)}</h1>
            <p class="tagline">Votre espace de partage local, privé et sans internet</p>
            <p class="stats">$itemCount élément(s) disponible(s)</p>
          </header>

          <div class="toolbar">
            <input type="text" id="searchBox" placeholder="🔍 Rechercher..." oninput="filterRows()">
            <a class="sort-link $nameSortLabel" href="$nameSortHref">Nom</a>
            <a class="sort-link $sizeSortLabel" href="$sizeSortHref">Taille</a>
            ${if (itemCount > 0) "<a class=\"zip-link\" href=\"$zipHref\">⬇️ .zip</a>" else ""}
          </div>

          <div class="breadcrumb">$breadcrumb</div>

          <main>
            $body
          </main>

          <div class="upload-section">
            <input type="file" id="uploadInput" multiple style="display:none" onchange="jieeHandleFiles(this.files)">
            <button class="upload-btn" onclick="document.getElementById('uploadInput').click()">📤 Envoyer des fichiers vers la Box</button>
            <div id="uploadList"></div>
          </div>

          <footer>
            <div class="brand-name">JIEE BOX</div>
            <div class="brand-tagline">Une solution conçue par <strong>Jérémie K. ETSO</strong></div>
            <div class="brand-sub">Penser le partage de fichiers autrement — local, instantané, sans dépendre d'internet.</div>
          </footer>

          ${howToModalHtml()}

          <script>
            function filterRows() {
              var q = document.getElementById('searchBox').value.toLowerCase();
              document.querySelectorAll('.file-row').forEach(function (row) {
                var nameEl = row.querySelector('.file-name');
                var name = nameEl ? nameEl.textContent.toLowerCase() : '';
                row.style.display = name.indexOf(q) !== -1 ? '' : 'none';
              });
            }

            function jieeHandleFiles(fileList) {
              var list = document.getElementById('uploadList');
              Array.prototype.forEach.call(fileList, function (file) {
                var row = document.createElement('div');
                row.className = 'upload-row';
                row.innerHTML =
                  '<span class="upload-name"></span>' +
                  '<div class="upload-bar-track"><div class="upload-bar-fill"></div></div>' +
                  '<span class="upload-status">0%</span>';
                row.querySelector('.upload-name').textContent = file.name;
                list.appendChild(row);

                var fill = row.querySelector('.upload-bar-fill');
                var status = row.querySelector('.upload-status');

                var xhr = new XMLHttpRequest();
                xhr.open('POST', '/upload', true);
                xhr.upload.onprogress = function (e) {
                  if (e.lengthComputable) {
                    var pct = Math.round((e.loaded / e.total) * 100);
                    fill.style.width = pct + '%';
                    status.textContent = pct + '%';
                  }
                };
                xhr.onload = function () {
                  if (xhr.status === 200) {
                    status.textContent = '✅ Envoyé';
                    fill.style.background = '#F0824E';
                    fill.style.width = '100%';
                  } else {
                    status.textContent = '❌ ' + xhr.status;
                    row.title = xhr.responseText || 'Erreur inconnue';
                  }
                };
                xhr.onerror = function () { status.textContent = '❌ Réseau'; };

                var formData = new FormData();
                formData.append('file', file, file.name);
                xhr.send(formData);
              });
            }

            function jieeSetLang(lang) {
              document.querySelectorAll('.lang-tab').forEach(function (t) { t.classList.remove('active'); });
              document.querySelectorAll('.howto-lang').forEach(function (b) { b.classList.remove('active'); });
              document.getElementById('langTab-' + lang).classList.add('active');
              document.getElementById('howtoBody-' + lang).classList.add('active');
            }

            document.getElementById('howToModal').addEventListener('click', function (e) {
              if (e.target === this) this.style.display = 'none';
            });
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    /**
     * A short bilingual usage guide the client can read without leaving the
     * page and without it being a downloadable "file" mixed in with real
     * content — a modal fits the "lit puis ferme sans quitter la page"
     * request more cleanly than pretending it's an entry in the file list.
     */
    private fun howToModalHtml(): String = """
        <div class="modal-overlay" id="howToModal">
          <div class="modal-card">
            <div class="modal-header">
              <h2>Comment utiliser / How To Use</h2>
              <button class="modal-close" onclick="document.getElementById('howToModal').style.display='none'">✕</button>
            </div>
            <div class="lang-tabs">
              <div class="lang-tab active" id="langTab-fr" onclick="jieeSetLang('fr')">Français</div>
              <div class="lang-tab" id="langTab-en" onclick="jieeSetLang('en')">English</div>
            </div>
            <div class="howto-body howto-lang active" id="howtoBody-fr">
              <h3>📥 Télécharger un fichier</h3>
              <p>Ouvre un dossier si besoin, puis appuie sur <strong>Télécharger</strong> à côté du fichier voulu.</p>
              <h3>📤 Envoyer un fichier vers la Box</h3>
              <p>En bas de la page, appuie sur <strong>Envoyer des fichiers vers la Box</strong>, choisis un ou plusieurs fichiers. Une barre de progression s'affiche pendant l'envoi.</p>
              <h3>🔍 Rechercher / trier</h3>
              <p>Utilise la barre de recherche en haut, ou les liens <strong>Nom</strong> / <strong>Taille</strong> pour trier.</p>
              <h3>📦 Tout télécharger d'un coup</h3>
              <p>Le bouton <strong>.zip</strong> télécharge tout le dossier affiché en une seule archive.</p>
              <h3>ℹ️ À savoir</h3>
              <p>Tout se passe en local, sur le Wi-Fi du téléphone hôte — aucune connexion internet n'est utilisée ni nécessaire.</p>
            </div>
            <div class="howto-body howto-lang" id="howtoBody-en">
              <h3>📥 Download a file</h3>
              <p>Open a folder if needed, then tap <strong>Télécharger</strong> next to the file you want.</p>
              <h3>📤 Send a file to the Box</h3>
              <p>At the bottom of the page, tap <strong>Envoyer des fichiers vers la Box</strong> and pick one or more files. A progress bar shows while it uploads.</p>
              <h3>🔍 Search / sort</h3>
              <p>Use the search box at the top, or the <strong>Nom</strong> (Name) / <strong>Taille</strong> (Size) links to sort.</p>
              <h3>📦 Download everything at once</h3>
              <p>The <strong>.zip</strong> button downloads the whole current folder as one archive.</p>
              <h3>ℹ️ Good to know</h3>
              <p>Everything happens locally over the host phone's Wi-Fi — no internet connection is used or required.</p>
            </div>
          </div>
        </div>
    """.trimIndent()

    private fun sortFiles(list: List<PublishedFile>, sort: String): List<PublishedFile> = when (sort) {
        "name_desc" -> list.sortedByDescending { it.displayName.lowercase() }
        "size_asc" -> list.sortedBy { it.size }
        "size_desc" -> list.sortedByDescending { it.size }
        else -> list.sortedBy { it.displayName.lowercase() } // name_asc default
    }

    private fun sortFolders(names: List<String>, stats: Map<String, Pair<Int, Long>>, sort: String): List<String> = when (sort) {
        "name_desc" -> names.sortedByDescending { it.lowercase() }
        "size_asc" -> names.sortedBy { stats[it]?.second ?: 0L }
        "size_desc" -> names.sortedByDescending { stats[it]?.second ?: 0L }
        else -> names.sortedBy { it.lowercase() }
    }

    private fun sortLink(field: String, currentSort: String, dir: List<String>): Pair<String, String> {
        val isActiveField = currentSort.startsWith(field)
        val nextSort = if (isActiveField && currentSort.endsWith("asc")) "${field}_desc" else "${field}_asc"
        val href = "/?dir=${encodeDir(dir)}&sort=$nextSort"
        val cssClass = if (isActiveField) "active" else ""
        return href to cssClass
    }

    private fun buildBreadcrumb(currentDir: List<String>, sort: String): String {
        val root = if (currentDir.isEmpty()) {
            "<span>🏠 Racine</span>"
        } else {
            "<a href=\"/?sort=$sort\">🏠 Racine</a>"
        }
        val parts = mutableListOf(root)
        for (i in currentDir.indices) {
            parts.add("<span class=\"sep\">/</span>")
            val segmentDir = currentDir.subList(0, i + 1)
            val label = escape(currentDir[i])
            parts.add(
                if (i == currentDir.lastIndex) "<span>$label</span>"
                else "<a href=\"/?dir=${encodeDir(segmentDir)}&sort=$sort\">$label</a>"
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
