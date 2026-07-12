package com.orbit.smartkeyboard

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private var installed = false
    private var appContext: Context? = null

    fun install(context: Context? = null) {
        if (context != null) appContext = context
        if (installed) return
        installed = true
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(thread, throwable)
            default?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val body = buildString {
            append("==== crash @ $stamp (${thread.name}) ====\n")
            append("SDK: ${Build.VERSION.SDK_INT}  Model: ${Build.MODEL}\n")
            append(sw.toString())
            append("\n")
        }
        val candidates = mutableListOf<String>()
        // internal storage (always writable by the app)
        appContext?.cacheDir?.let { candidates += File(it, "crash.log").absolutePath }
        appContext?.filesDir?.let { candidates += File(it, "crash.log").absolutePath }
        // world-readable temp (Termux can read)
        candidates += "/data/local/tmp/crash_orbit.log"
        // external files
        appContext?.getExternalFilesDir(null)?.let { candidates += File(it, "crash.log").absolutePath }
        for (p in candidates) {
            try {
                val f = File(p)
                f.parentFile?.mkdirs()
                FileWriter(f, true).use { w -> w.append(body) }
                try { f.setReadable(true, false) } catch (_: Exception) { }
            } catch (_: Exception) {
            }
        }
    }

    fun readLastCrash(): String? {
        val ctx = appContext ?: return null
        for (dir in listOf(ctx.cacheDir, ctx.filesDir, ctx.getExternalFilesDir(null))) {
            val f = File(dir, "crash.log")
            if (f.exists()) return runCatching { f.readText().takeLast(6000) }.getOrNull()
        }
        val tmp = File("/data/local/tmp/crash_orbit.log")
        if (tmp.exists()) return runCatching { tmp.readText().takeLast(6000) }.getOrNull()
        return null
    }
}
