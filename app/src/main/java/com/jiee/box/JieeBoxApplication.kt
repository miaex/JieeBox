package com.jiee.box

import android.app.Application
import com.jiee.box.data.FileRepository
import com.jiee.box.data.ReceivedFileRepository
import com.jiee.box.data.SettingsRepository

class JieeBoxApplication : Application() {
    // Single shared instances: both the UI (MainActivity/ViewModel) and the
    // background HTTP server (running in BoxService) need to see the same
    // published-file list, received-file list, and box settings.
    lateinit var fileRepository: FileRepository
        private set
    lateinit var receivedFileRepository: ReceivedFileRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // NanoHTTPD (used for the local server) writes multipart file uploads
        // to a temp directory it reads from the "java.io.tmpdir" system
        // property — which Android does not set to anything writable by
        // default. Left unset, every upload from a client silently fails.
        // cacheDir is always writable by this app, with no permission needed.
        System.setProperty("java.io.tmpdir", cacheDir.absolutePath)

        fileRepository = FileRepository(this)
        receivedFileRepository = ReceivedFileRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
