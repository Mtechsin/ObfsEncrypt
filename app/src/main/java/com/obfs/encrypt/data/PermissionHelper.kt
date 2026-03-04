package com.obfs.encrypt.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper object to manage storage permissions across different Android versions.
 *
 * Android 10 (API 28) and below: Uses READ/WRITE_EXTERNAL_STORAGE
 * Android 11+ (API 30+): Uses MANAGE_EXTERNAL_STORAGE
 */
object PermissionHelper {

    /**
     * Check if the app has storage access permission.
     * For Android 11+, checks MANAGE_EXTERNAL_STORAGE.
     * For Android 10 and below, checks READ/WRITE_EXTERNAL_STORAGE.
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Check MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // Android 10 and below: Check READ/WRITE_EXTERNAL_STORAGE
            val readPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val writePermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            readPermission == PackageManager.PERMISSION_GRANTED &&
                    writePermission == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request storage permission.
     * For Android 11+: Opens settings page for MANAGE_EXTERNAL_STORAGE.
     * For Android 10 and below: Shows runtime permission dialog.
     */
    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Open settings for MANAGE_EXTERNAL_STORAGE
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        } else {
            // Android 10 and below: Request runtime permissions
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_STORAGE_PERMISSION_CODE
            )
        }
    }

    /**
     * Get the intent to open storage permission settings.
     * For Android 11+: Opens All Files Access settings.
     * For Android 10 and below: Returns null (use runtime permission).
     */
    fun getStoragePermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * Check if storage permission rationale should be shown.
     */
    fun shouldShowPermissionRationale(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, always show rationale since user must manually grant in settings
            true
        } else {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Permission request code for runtime permissions.
     */
    const val REQUEST_STORAGE_PERMISSION_CODE = 100
}
