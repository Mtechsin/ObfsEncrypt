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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // Handle cancellation - broadcast to ViewModel
                sendBroadcast(Intent("com.obfs.encrypt.ACTION_CANCEL_OPERATION"))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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

    private fun createNotification(progress: Int, status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Encryption in Progress")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }

    fun updateNotification(progress: Int, status: String) {
        val notification = createNotification(progress, status)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
