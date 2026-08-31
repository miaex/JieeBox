package com.jiee.box.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiee.box.JieeBoxApplication
import com.jiee.box.data.BoxSettings
import com.jiee.box.data.PublishedFile
import com.jiee.box.service.BoxService
import com.jiee.box.service.BoxServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoxViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JieeBoxApplication).fileRepository
    private val settingsRepository = (application as JieeBoxApplication).settingsRepository

    private val _files = MutableStateFlow<List<PublishedFile>>(emptyList())
    val files: StateFlow<List<PublishedFile>> = _files.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepository.get())
    val settings: StateFlow<BoxSettings> = _settings.asStateFlow()

    val serverState: StateFlow<BoxServerState> = BoxService.state

    init {
        refreshFiles()
    }

    private fun refreshFiles() {
        _files.value = repository.files
    }

    fun saveSettings(newSettings: BoxSettings) {
        settingsRepository.save(newSettings)
        _settings.value = newSettings
        // Takes effect on the next "Démarrer la Box" — changing it while the
        // server is already running would need a restart to update the
        // notification/realm anyway, so we keep this simple and predictable.
    }

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            repository.addFiles(uris)
            refreshFiles()
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            repository.addFolder(treeUri)
            refreshFiles()
        }
    }

    fun removeFile(id: String) {
        repository.removeFile(id)
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
