package com.obfs.encrypt.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the most recent uncaught exception so a crash report survives process death
 * and can be included in the next in-app diagnostic export.
 */
object CrashHandler {

    private const val FILE_NAME = "crash_last.txt"
    private const val MAX_STACK_FRAMES = 40
    private const val MAX_FILE_BYTES = 64 * 1024

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                persist(appContext, thread, throwable)
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
            AppLogger.i("CrashHandler", "Installed uncaught exception handler")
        }
    }

    fun readLastCrash(context: Context): String? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists() && file.length() > 0) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun clearLastCrash(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    private fun persist(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date())
            val sb = StringBuilder()
            sb.appendLine("time=$time")
            sb.appendLine("thread=${thread.name}")
            sb.appendLine("exception=${throwable.javaClass.name}")
            sb.appendLine("message=${sanitizeFreeText(throwable.message.orEmpty())}")
            sb.appendLine("stack:")
            throwable.stackTrace.take(MAX_STACK_FRAMES).forEach { sb.appendLine("  at $it") }
            throwable.cause?.let { cause ->
                sb.appendLine("cause=${cause.javaClass.name}: ${sanitizeFreeText(cause.message.orEmpty())}")
            }

            val recent = AppLogger.snapshot(60).joinToString("\n") { event ->
                val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                event.formatLine(fmt)
            }
            if (recent.isNotBlank()) {
                sb.appendLine("--- breadcrumbs ---")
                sb.appendLine(recent)
            }

            val text = sb.toString().take(MAX_FILE_BYTES)
            File(context.filesDir, FILE_NAME).writeText(text)
            AppLogger.e("CrashHandler", "Uncaught exception persisted", throwable)
        } catch (_: Exception) {
            // Never throw from the crash path.
        }
    }

    private fun sanitizeFreeText(raw: String): String {
        if (raw.isBlank()) return "<none>"
        var s = raw.replace('\\', '/')
        s = Regex("""(?:/[\w.\-]+){2,}""").replace(s) { match ->
            val path = match.value
            val ext = path.substringAfterLast('.', "")
            if (ext.length in 1..8 && !path.endsWith("/")) "<path>.$ext" else "<path>"
        }
        s = Regex("""(?:content|file)://\S+""").replace(s, "<content-uri>")
        return s.take(300)
    }
}
