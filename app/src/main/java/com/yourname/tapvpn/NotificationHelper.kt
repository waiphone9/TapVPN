package com.yourname.tapvpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "tapvpn_session"
    private const val CHANNEL_NAME = "TapVPN Session"
    private const val CHANNEL_DESC = "Session and connection updates"
    private const val NOTI_ID_SESSION = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = CHANNEL_DESC }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showSessionExpired(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // your app icon
            .setContentTitle("VPN session ended")
            .setContentText("Tap to reconnect and continue using TapVPN.")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTI_ID_SESSION, notification)
    }
}
