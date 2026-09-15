package com.obfs.encrypt.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.obfs.encrypt.MainActivity
import com.obfs.encrypt.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * CryptoService - Foreground service for long-running encryption/decryption operations.
 *
 * Why a foreground service:
 * Encryption/decryption of large files can take several minutes. A foreground service
 * ensures the operation continues even if the user navigates away from the app,
 * and provides a persistent notification showing progress.
 */
@AndroidEntryPoint
class CryptoService : Service() {

    @Inject
    lateinit var notificationManager: NotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val CHANNEL_ID = "crypto_service_channel"
        const val NOTIFICATION_ID = 1001
        
        const val ACTION_CANCEL = "com.obfs.encrypt.ACTION_CANCEL"
        const val ACTION_PAUSE = "com.obfs.encrypt.ACTION_PAUSE"
        const val ACTION_RESUME = "com.obfs.encrypt.ACTION_RESUME"
        const val ACTION_UPDATE_PROGRESS = "com.obfs.encrypt.ACTION_UPDATE_PROGRESS"
        
        const val ACTION_BROADCAST_CANCEL = "com.obfs.encrypt.BROADCAST_CANCEL"
        const val ACTION_BROADCAST_PAUSE = "com.obfs.encrypt.BROADCAST_PAUSE"
        
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_IS_PAUSED = "extra_is_paused"

        fun startService(context: Context) {
            val intent = Intent(context, CryptoService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CryptoService::class.java)
            context.stopService(intent)
        }

        fun updateProgress(context: Context, progress: Int, status: String, isPaused: Boolean = false) {
            val intent = Intent(context, CryptoService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_PROGRESS, progress)
                putExtra(EXTRA_STATUS, status)
                putExtra(EXTRA_IS_PAUSED, isPaused)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                sendBroadcast(Intent(ACTION_BROADCAST_CANCEL).apply { setPackage(packageName) })
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE, ACTION_RESUME -> {
                sendBroadcast(Intent(ACTION_BROADCAST_PAUSE).apply { setPackage(packageName) })
            }
            ACTION_UPDATE_PROGRESS -> {
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                val status = intent.getStringExtra(EXTRA_STATUS) ?: ""
                val isPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)
                updateNotification(progress, status, isPaused)
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification(0, "Initializing..."))
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Encryption/Decryption Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of encryption and decryption operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Int, status: String, isPaused: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Fixed HIGH-07: the old code used getBroadcast(ACTION_CANCEL /
        // ACTION_PAUSE), but the only receiver filters ACTION_BROADCAST_* and
        // nothing listens for ACTION_CANCEL. Buttons were dead. Target the
        // service directly — onStartCommand already handles these actions.
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CryptoService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CryptoService::class.java)
                .setAction(if (isPaused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isPaused) "Operation Paused" else "Operation in Progress")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            // File names are sensitive; keep them off the lockscreen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setProgress(100, progress, progress == 0)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            
        return builder.build()
    }

    fun updateNotification(progress: Int, status: String, isPaused: Boolean = false) {
        val notification = createNotification(progress, status, isPaused)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
