package com.orbit.smartkeyboard

import android.app.Application
import android.content.Context

class OrbitApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLogger.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
