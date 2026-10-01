package com.cloakdroid

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash catcher: any uncaught exception in any thread is written to
 * `Download/cloakdroid-crash.log` (public storage, readable without adb) before
 * the process dies. Keeps the last 5 crash reports.
 */
object CrashLogger {

    private const val DIR_NAME = "cloakdroid_logs"
    private const val FILE_NAME = "crash.log"
    private const val MAX_FILES = 5

    fun install(appContext: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val report = buildString {
            appendLine("==== CLOAKDROID CRASH ====")
            appendLine("time     : ${sdf.format(Date())}")
            appendLine("thread   : ${thread.name}")
            appendLine("device   : ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
            appendLine("app      : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("exception: ${t.javaClass.name}")
            appendLine("message  : ${t.message}")
            appendLine()
            appendLine(sw.toString())
            appendLine("===========================")
            appendLine()
        }

        val dir = externalLogDir(context) ?: internalLogDir(context)
        dir?.let { d ->
            d.mkdirs()
            val f = File(d, FILE_NAME)
            f.appendText(report)
            rotate(d)
        }
    }

    private fun externalLogDir(context: Context): File? {
        val storageDirs = context.getExternalFilesDirs(null)
        return storageDirs.firstOrNull()?.parentFile?.let { parent ->
            File(parent, DIR_NAME)
        }
    }

    private fun internalLogDir(context: Context): File? {
        return try {
            File(context.filesDir, DIR_NAME)
        } catch (_: Throwable) {
            null
        }
    }

    private fun rotate(dir: File) {
        val logs = dir.listFiles { f -> f.name.startsWith("crash") } ?: return
        if (logs.size <= MAX_FILES) return
        logs.sortedBy { it.lastModified() }
            .take(logs.size - MAX_FILES)
            .forEach { it.delete() }
    }
}
