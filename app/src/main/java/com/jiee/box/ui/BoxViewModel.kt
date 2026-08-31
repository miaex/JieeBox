package com.jiee.box.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiee.box.JieeBoxApplication
import com.jiee.box.data.PublishedFile
import com.jiee.box.service.BoxService
import com.jiee.box.service.BoxServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoxViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as JieeBoxApplication).fileRepository

    private val _files = MutableStateFlow<List<PublishedFile>>(emptyList())
    val files: StateFlow<List<PublishedFile>> = _files.asStateFlow()

    val serverState: StateFlow<BoxServerState> = BoxService.state

    init {
        refreshFiles()
    }

    private fun refreshFiles() {
        _files.value = repository.files
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
