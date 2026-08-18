package com.example.audioambientglow

import android.app.Application
import com.example.audioambientglow.util.CrashHandler

class AudioAmbientGlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
    }
}
