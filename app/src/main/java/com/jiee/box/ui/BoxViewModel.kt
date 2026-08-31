package com.jiee.box.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiee.box.JieeBoxApplication
import com.jiee.box.data.BoxSettings
import com.jiee.box.data.PublishedFile
import com.jiee.box.data.ReceivedFile
import com.jiee.box.service.BoxService
import com.jiee.box.service.BoxServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

class BoxViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JieeBoxApplication).fileRepository
    private val receivedRepository = (application as JieeBoxApplication).receivedFileRepository
    private val settingsRepository = (application as JieeBoxApplication).settingsRepository

    private val _files = MutableStateFlow<List<PublishedFile>>(emptyList())
    val files: StateFlow<List<PublishedFile>> = _files.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFile>> = _receivedFiles.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepository.get())
    val settings: StateFlow<BoxSettings> = _settings.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    val serverState: StateFlow<BoxServerState> = BoxService.state

    init {
        refreshFiles()
        refreshReceivedFiles()
        // Uploads land via the background HTTP server thread, so the "Reçus"
        // list needs polling rather than push updates to stay current while
        // the host is looking at the screen.
        viewModelScope.launch {
            while (true) {
                delay(3_000)
                refreshReceivedFiles()
            }
        }
    }

    private fun refreshFiles() {
        _files.value = repository.files
    }

    fun refreshReceivedFiles() {
        _receivedFiles.value = receivedRepository.files
    }

    /** Moves a received (uploaded) file into the published list, making it
     *  downloadable by other clients — the host's explicit "share this" action. */
    fun publishReceivedFile(id: String) {
        val received = receivedRepository.getById(id) ?: return
        repository.addKnownFile(received.uri, received.displayName, received.size, received.mimeType)
        receivedRepository.markPublished(id)
        refreshFiles()
        refreshReceivedFiles()
    }

    fun removeReceivedFile(id: String) {
        receivedRepository.remove(id)
        refreshReceivedFiles()
    }

    fun saveSettings(newSettings: BoxSettings) {
        settingsRepository.save(newSettings)
        _settings.value = newSettings
        // Takes effect on the next "Démarrer la Box" — changing it while the
        // server is already running would need a restart to update the
        // notification/realm anyway, so we keep this simple and predictable.
    }

    /** Runs on Dispatchers.IO: importing a large folder means many sequential
     *  ContentResolver lookups, which must never run on the main thread (this
     *  is what caused the crash/freeze importing ~3000 files at once — see
     *  also the per-file-permission fix in FileRepository). */
    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                repository.addFiles(uris)
            } finally {
                refreshFiles()
                _isImporting.value = false
            }
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            try {
                repository.addFolder(treeUri)
            } finally {
                refreshFiles()
                _isImporting.value = false
            }
        }
    }

    fun removeFile(id: String) {
        repository.removeFile(id)
        refreshFiles()
    }

    /** Bulk delete for the multi-select mode — one disk write for the whole
     *  batch instead of one per file, so clearing hundreds/thousands of
     *  entries (e.g. after an accidental mass-import) stays fast. */
    fun removeFiles(ids: Set<String>) {
        repository.removeFiles(ids)
        refreshFiles()
    }

    fun startBox() {
        BoxService.start(getApplication())
    }

    fun stopBox() {
        BoxService.stop(getApplication())
    }

    val totalSize: Long get() = repository.totalSize
}
