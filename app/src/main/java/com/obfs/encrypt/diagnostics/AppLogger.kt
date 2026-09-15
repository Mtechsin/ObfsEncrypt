package com.obfs.encrypt.diagnostics

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lightweight structured logger with an in-memory ring buffer.
 *
 * Privacy: never pass passwords, key material, file contents, or full user paths.
 * Use [sanitizePath] / [sanitizeFileName] when a path or name is unavoidable.
 */
object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Event(
        val timestampMs: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwableClass: String? = null,
        val throwableMessage: String? = null,
        val stackSnippet: String? = null
    ) {
        fun formatLine(timeFormat: SimpleDateFormat): String {
            val time = timeFormat.format(Date(timestampMs))
            val base = "$time ${level.name.padEnd(5)} [$tag] $message"
            val err = when {
                throwableClass != null && throwableMessage != null -> " | $throwableClass: $throwableMessage"
                throwableClass != null -> " | $throwableClass"
                else -> ""
            }
            val stack = stackSnippet?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
            return base + err + stack
        }
    }

    private const val LOGCAT_TAG = "ObfsLog"
    private const val MAX_EVENTS = 400
    private const val MAX_MESSAGE_LEN = 500
    private const val MAX_STACK_FRAMES = 25

    private val buffer = ConcurrentLinkedDeque<Event>()

    fun v(tag: String, message: String) = log(Level.DEBUG, tag, message, null)

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = log(Level.INFO, tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val safeMessage = sanitizeMessage(message).take(MAX_MESSAGE_LEN)
        val event = Event(
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = safeMessage,
            throwableClass = throwable?.javaClass?.simpleName,
            throwableMessage = throwable?.message?.let { sanitizeMessage(it).take(200) },
            stackSnippet = throwable?.let { stackSnippet(it) }
        )

        buffer.addFirst(event)
        while (buffer.size > MAX_EVENTS) {
            buffer.pollLast()
        }

        when (level) {
            Level.DEBUG -> runCatching { Log.d(LOGCAT_TAG, "[$tag] $safeMessage") }
            Level.INFO -> runCatching { Log.i(LOGCAT_TAG, "[$tag] $safeMessage") }
            Level.WARN -> runCatching {
                if (throwable != null) Log.w(LOGCAT_TAG, "[$tag] $safeMessage", throwable)
                else Log.w(LOGCAT_TAG, "[$tag] $safeMessage")
            }
            Level.ERROR -> runCatching {
                if (throwable != null) Log.e(LOGCAT_TAG, "[$tag] $safeMessage", throwable)
                else Log.e(LOGCAT_TAG, "[$tag] $safeMessage")
            }
        }
    }

    fun snapshot(limit: Int = 120): List<Event> = buffer.take(limit.coerceAtMost(MAX_EVENTS))

    fun clear() = buffer.clear()

    private fun stackSnippet(t: Throwable): String {
        val frames = t.stackTrace.take(MAX_STACK_FRAMES).joinToString("\n") { "  at $it" }
        val cause = t.cause?.let { "\nCaused by: ${it.javaClass.name}: ${sanitizeMessage(it.message.orEmpty())}" }
            .orEmpty()
        return frames + cause
    }

    private fun sanitizeMessage(raw: String): String {
        var s = raw
        // Collapse absolute Unix/Android paths down to a non-identifying form.
        s = ABS_PATH.replace(s) { match ->
            val path = match.value
            val ext = path.substringAfterLast('.', "")
            if (ext.length in 1..8 && !path.endsWith("/")) "<path>.$ext" else "<path>"
        }
        // content:// and file:// URIs
        s = CONTENT_URI.replace(s, "<content-uri>")
        // email-like tokens
        s = EMAIL.replace(s, "<email>")
        return s
    }

    private val ABS_PATH = Regex("""(?:/[\w.\-]+){2,}""")
    private val CONTENT_URI = Regex("""(?:content|file)://\S+""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.-]+""")

    fun sanitizeFileName(name: String): String {
        if (name.isBlank()) return "<empty>"
        val dot = name.lastIndexOf('.')
        return if (dot > 0) {
            val base = name.substring(0, dot)
            val ext = name.substring(dot + 1)
            "${base.take(1)}….$ext"
        } else {
            "${name.take(1)}…"
        }
    }

    fun sanitizePath(path: String?): String {
        if (path.isNullOrBlank()) return "<none>"
        val normalized = path.replace('\\', '/')
        val ext = normalized.substringAfterLast('.', "")
        return if (ext.length in 1..8 && !normalized.endsWith("/")) "<path>.$ext" else "<path>"
    }
}
