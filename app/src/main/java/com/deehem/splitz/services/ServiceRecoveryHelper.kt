package com.deehem.splitz.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ServiceRecoveryHelper {
    private const val CHANNEL_ID = "splitz_service_recovery"
    private const val NOTIFICATION_ID = 4040
    private const val PREFS_NAME = "splitz_recovery_prefs"
    private const val KEY_DISCONNECT_NOTIFIED = "key_disconnect_notified"

    private fun getPrefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Splitz Service Recovery"
            val descriptionText = "Notifies when the split-screen automation service needs reconnection"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Called when the service successfully connects. Resets the notification state
     * and clears any existing recovery notifications.
     */
    fun onServiceConnected(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_DISCONNECT_NOTIFIED, false).apply()
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Called when service disconnects or is destroyed unexpectedly while enabled in Settings.
     */
    fun onServiceDisconnected(context: Context) {
        if (SplitScreenService.isSettingsEnabled(context)) {
            notifyDisconnectIfNeeded(context)
        }
    }

    /**
     * Triggers the recovery notification if not already shown for the current disconnect event.
     */
    fun notifyDisconnectIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val alreadyNotified = prefs.getBoolean(KEY_DISCONNECT_NOTIFIED, false)
        if (alreadyNotified) {
            return
        }

        createNotificationChannel(context)

        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Splitz: Automation Service Paused")
            .setContentText("Tap to reconnect the split-screen service in Settings.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Android system memory optimization paused the Splitz background service. Tap here to re-enable it."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val nm = NotificationManagerCompat.from(context)
            nm.notify(NOTIFICATION_ID, notification)
            prefs.edit().putBoolean(KEY_DISCONNECT_NOTIFIED, true).apply()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
