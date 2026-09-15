package com.obfs.encrypt.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.obfs.encrypt.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Share / copy / save helpers for a sanitized diagnostic report.
 * Uses ACTION_SEND text/plain so no FileProvider or network is required.
 */
object BugReportExporter {

    fun share(context: Context, report: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.bug_report_share_subject))
                putExtra(Intent.EXTRA_TEXT, report)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(
                intent,
                context.getString(R.string.bug_report_share_chooser)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            AppLogger.e("BugReportExporter", "Share failed", e)
            Toast.makeText(context, R.string.bug_report_share_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun copy(context: Context, report: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Obfs bug report", report))
            Toast.makeText(context, R.string.bug_report_copied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e("BugReportExporter", "Copy failed", e)
            Toast.makeText(context, R.string.bug_report_copy_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun saveToInternal(context: Context, report: String): File? {
        return try {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(context.filesDir, "bug_report_$stamp.md")
            file.writeText(report)
            AppLogger.i("BugReportExporter", "Saved report to internal files")
            file
        } catch (e: Exception) {
            AppLogger.e("BugReportExporter", "Save failed", e)
            null
        }
    }
}
