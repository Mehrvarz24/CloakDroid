package com.cloakdroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CloakDroidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
