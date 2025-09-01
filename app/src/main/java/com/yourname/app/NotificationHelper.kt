package com.ozovpn.app.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import android.provider.Settings
import com.ozovpn.app.R

object NotificationHelper {
    private const val TAG = "NotificationHelper"
    private const val CHANNEL_ID_ALERT = "vpn_alerts"
    private const val NOTIF_ID_ALERT = 2001
    private const val DEFAULT_ICON_SIZE_PX = 192

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERT, "VPN Alerts", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alerts for session expiry or unexpected disconnect" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun buildAlert(context: Context, title: String, text: String): Notification {
        // Launch whatever Activity is marked as LAUNCHER in your manifest.
        // If somehow null, fall back to the app’s settings page.
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) }
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            .setSmallIcon(R.drawable.ic_stat_vpn) // your white brand glyph (or ic_stat_vpn)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(loadLargeIconBitmap(context)) // uses updated ic_launcher
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showSessionExpired(context: Context) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skip session-expired alert")
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIF_ID_ALERT,
                buildAlert(context, "Session ended", "Your VPN session has expired. Tap to reconnect.")
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException showing session-expired", se)
        }
    }

    @SuppressLint("MissingPermission")
    fun showUnexpectedDrop(context: Context) {
        if (!hasPostNotificationsPermission(context)) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; skip drop alert")
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(
                NOTIF_ID_ALERT,
                buildAlert(context, "VPN disconnected", "Connection dropped unexpectedly. Tap to reconnect.")
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException showing drop alert", se)
        }
    }

    private fun loadLargeIconBitmap(context: Context): Bitmap? {
        val pm = context.packageManager
        val appDrawable: Drawable? = try { pm.getApplicationIcon(context.packageName) } catch (_: Throwable) { null }
        appDrawable?.let { drawableToBitmap(it, DEFAULT_ICON_SIZE_PX)?.let { bmp -> return bmp } }
        val fallback = try { R.mipmap.ic_launcher } catch (_: Throwable) { 0 }
        if (fallback != 0) {
            ContextCompat.getDrawable(context, fallback)?.let { d ->
                return drawableToBitmap(d, DEFAULT_ICON_SIZE_PX)
            }
        }
        return null
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap? = when {
        drawable is BitmapDrawable && drawable.bitmap != null -> drawable.bitmap
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable -> {
            val b = Bitmap.createBitmap(sizePx, sizePx, ARGB_8888)
            val c = Canvas(b); drawable.setBounds(0, 0, c.width, c.height); drawable.draw(c); b
        }
        drawable is VectorDrawable -> {
            val b = Bitmap.createBitmap(sizePx, sizePx, ARGB_8888)
            val c = Canvas(b); drawable.setBounds(0, 0, c.width, c.height); drawable.draw(c); b
        }
        else -> {
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else sizePx
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else sizePx
            val b = Bitmap.createBitmap(w, h, ARGB_8888)
            val c = Canvas(b); drawable.setBounds(0, 0, c.width, c.height); drawable.draw(c); b
        }
    }
}
