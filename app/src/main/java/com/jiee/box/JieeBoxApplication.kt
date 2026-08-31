package com.jiee.box

import android.app.Application
import com.jiee.box.data.FileRepository
import com.jiee.box.data.SettingsRepository

class JieeBoxApplication : Application() {
    // Single shared instances: both the UI (MainActivity/ViewModel) and the
    // background HTTP server (running in BoxService) need to see the same
    // published-file list and the same box settings.
    lateinit var fileRepository: FileRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        fileRepository = FileRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
