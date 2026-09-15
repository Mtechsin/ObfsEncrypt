package com.obfs.encrypt.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.obfs.encrypt.BuildConfig
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.EncryptionHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Builds a privacy-safe, paste-ready markdown bug report.
 *
 * See docs/BUG_REPORTING.md for the privacy contract.
 */
@Singleton
class DiagnosticsCollector @Inject constructor(
    private val historyRepository: EncryptionHistoryRepository
) {
    companion object {
        private const val HISTORY_LIMIT = 15
        private const val LOG_LIMIT = 100
        private const val REPORT_TIMEOUT_MS = 2_000L
    }

    data class SettingsSnapshot(
        val themeMode: String? = null,
        val language: String? = null,
        val appLockEnabled: Boolean? = null,
        val biometricEnabled: Boolean? = null,
        val secureDelete: Boolean? = null,
        val integrityCheck: Boolean? = null,
        val customOutputFolder: Boolean? = null
    )

    suspend fun buildReport(
        context: Context,
        userSummary: String = "",
        settings: SettingsSnapshot? = null
    ): String {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()

        sb.appendLine("# Obfs Encrypt Bug Report")
        sb.appendLine()
        sb.appendLine("Generated: ${timeFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine(
            if (userSummary.isNotBlank()) userSummary.trim()
            else "<!-- Describe what happened, what you expected, and the steps to reproduce. -->"
        )
        sb.appendLine()
        sb.appendLine("## App")
        sb.appendLine("- package: ${BuildConfig.APPLICATION_ID}")
        sb.appendLine("- versionName: ${BuildConfig.VERSION_NAME}")
        sb.appendLine("- versionCode: ${BuildConfig.VERSION_CODE}")
        sb.appendLine("- buildType: ${BuildConfig.BUILD_TYPE}")
        sb.appendLine("- debuggable: ${BuildConfig.DEBUG}")
        sb.appendLine()

        sb.appendLine("## Device")
        sb.appendLine("- manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("- brand: ${Build.BRAND}")
        sb.appendLine("- model: ${Build.MODEL}")
        sb.appendLine("- device: ${Build.DEVICE}")
        sb.appendLine("- api: ${Build.VERSION.SDK_INT}")
        sb.appendLine("- release: ${Build.VERSION.RELEASE}")
        sb.appendLine("- abi: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("- locale: ${Locale.getDefault().toLanguageTag()}")
        sb.appendLine("- lowRam: ${isLowRam(context)}")
        sb.appendLine()

        appendSettings(context, sb, settings)
        appendHistory(context, sb)
        appendLastCrash(context, sb, timeFormat)
        appendLogs(sb, timeFormat)

        sb.appendLine("---")
        sb.appendLine("_Privacy: no passwords, keys, file contents, or full file paths are included._")

        return sb.toString()
    }

    private fun isLowRam(context: Context): Any {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.isLowRamDevice
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun appendSettings(context: Context, sb: StringBuilder, snapshot: SettingsSnapshot? = null) {
        sb.appendLine("## Settings (non-secret)")
        try {
            val hasStorage = if (Build.VERSION.SDK_INT >= 30) {
                android.os.Environment.isExternalStorageManager()
            } else {
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
            }
            sb.appendLine("- storagePermission: $hasStorage")
            sb.appendLine("- filesDirFreeBytes: ${context.filesDir.freeSpace}")
        } catch (e: Exception) {
            AppLogger.w("DiagnosticsCollector", "Failed to read storage flags", e)
            sb.appendLine("- storagePermission: unknown")
        }
        if (snapshot != null) {
            sb.appendLine("- themeMode: ${snapshot.themeMode ?: "unknown"}")
            sb.appendLine("- language: ${snapshot.language ?: "unknown"}")
            sb.appendLine("- appLockEnabled: ${snapshot.appLockEnabled ?: "unknown"}")
            sb.appendLine("- biometricEnabled: ${snapshot.biometricEnabled ?: "unknown"}")
            sb.appendLine("- secureDeleteOriginals: ${snapshot.secureDelete ?: "unknown"}")
            sb.appendLine("- integrityCheckDefault: ${snapshot.integrityCheck ?: "unknown"}")
            sb.appendLine("- customOutputFolder: ${snapshot.customOutputFolder ?: "unknown"}")
        }
        sb.appendLine()
    }

    private suspend fun appendHistory(context: Context, sb: StringBuilder) {
        sb.appendLine("## Recent operations (sanitized)")
        val items = try {
            withTimeoutOrNull(REPORT_TIMEOUT_MS) {
                historyRepository.historyItems.first()
            }.orEmpty().take(HISTORY_LIMIT)
        } catch (e: Exception) {
            AppLogger.w("DiagnosticsCollector", "History unavailable for report", e)
            emptyList()
        }

        if (items.isEmpty()) {
            sb.appendLine("_No operations recorded._")
            sb.appendLine()
            return
        }

        sb.appendLine("| time | op | method | ok | size | name | error |")
        sb.appendLine("|------|----|--------|----|------|------|-------|")
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        items.forEach { item ->
            val time = fmt.format(Date(item.timestamp))
            val op = item.operationType.name.lowercase()
            val method = item.encryptionMethod?.name ?: "-"
            val ok = if (item.success) "yes" else "no"
            val size = item.formattedSize
            val name = AppLogger.sanitizeFileName(item.fileName)
            val err = item.errorMessage
                ?.let { AppLogger.sanitizePath(it) }
                ?.replace("|", "/")
                ?.take(80)
                ?: "-"
            sb.appendLine("| $time | $op | $method | $ok | $size | $name | $err |")
        }
        sb.appendLine()
    }

    private fun appendLastCrash(context: Context, sb: StringBuilder, timeFormat: SimpleDateFormat) {
        sb.appendLine("## Last crash")
        val crash = CrashHandler.readLastCrash(context)
        if (crash.isNullOrBlank()) {
            sb.appendLine("_None recorded since install / last clear._")
        } else {
            sb.appendLine("```")
            sb.append(crash.trim())
            sb.appendLine()
            sb.appendLine("```")
        }
        sb.appendLine()
    }

    private fun appendLogs(sb: StringBuilder, timeFormat: SimpleDateFormat) {
        sb.appendLine("## Recent log events")
        val events = AppLogger.snapshot(LOG_LIMIT)
        if (events.isEmpty()) {
            sb.appendLine("_No events in the current session buffer._")
        } else {
            sb.appendLine("```")
            events.asReversed().forEach { event ->
                sb.appendLine(event.formatLine(timeFormat))
            }
            sb.appendLine("```")
        }
        sb.appendLine()
    }
}
