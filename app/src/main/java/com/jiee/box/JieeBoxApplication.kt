package com.jiee.box

import android.app.Application
import com.jiee.box.data.FileRepository

class JieeBoxApplication : Application() {
    // Single shared instance: both the UI (MainActivity/ViewModel) and the
    // background HTTP server (running in BoxService) need to see the same
    // published-file list.
    lateinit var fileRepository: FileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        fileRepository = FileRepository(this)
    }
}
