package dev.freedom.app

import android.app.Application
import dev.freedom.app.diagnostics.CrashReporter

class FreedomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
