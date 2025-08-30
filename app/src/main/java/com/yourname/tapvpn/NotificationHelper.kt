package com.yourname.tapvpn.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yourname.tapvpn.R
import com.yourname.tapvpn.MainActivity

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID_ALERT = "vpn_alerts"
    private const val NOTIF_ID_ALERT = 2001  // reuse same ID so it updates

    fun createChannel(context: Context) {
        // High importance for alerts (sound/heads-up)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chanAlert = NotificationChannel(
                CHANNEL_ID_ALERT,
                "VPN Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for session expiry or unexpected disconnect"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(chanAlert)
        }
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /** Build a tappable alert that opens the app */
    private fun buildAlert(
        context: Context,
        title: String,
        text: String
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= 31)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_stat_vpn) // white-only glyph in res/drawable
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)                // dismiss when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
    }

    fun showSessionExpired(context: Context) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skip session-expired alert")
            return
        }
        val n = buildAlert(
            context,
            title = "Session ended",
            text = "Your VPN session has expired. Tap to reconnect."
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ALERT, n)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException showing session-expired", se)
        }
    }

    fun showUnexpectedDrop(context: Context) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skip drop alert")
            return
        }
        val n = buildAlert(
            context,
            title = "VPN disconnected",
            text = "Connection dropped unexpectedly. Tap to reconnect."
        )
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_ID_ALERT, n)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException showing drop alert", se)
        }
    }
}
