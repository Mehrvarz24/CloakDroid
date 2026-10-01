package com.cloakdroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CloakDroidApplication : Application() {
    override fun onCreate() {
        // Must be FIRST so even crashes during Hilt init are captured.
        CrashLogger.install(this)
        super.onCreate()
    }
}
