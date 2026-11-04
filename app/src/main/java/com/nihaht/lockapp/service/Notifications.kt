package com.nihaht.lockapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nihaht.lockapp.MainActivity
import com.nihaht.lockapp.R
import com.nihaht.lockapp.diagnostics.ErrorLog

/** Canales y notificaciones de LockApp. */
object Notifications {

    const val SERVICE_ID = 1
    private const val ALERT_ID = 2
    private const val SERVICE_CHANNEL = "lock_service"
    private const val ALERT_CHANNEL = "alertas"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL,
                context.getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /** Notificación fija del servicio en segundo plano, con el estado actual. */
    fun service(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setSilent(true)
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    fun updateService(context: Context, text: String) {
        notify(context, SERVICE_ID, service(context, text))
    }

    /** Aviso visible cuando LockApp deja de proteger las apps. */
    fun showProtectionProblem(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        notify(context, ALERT_ID, notification)
    }

    fun cancelProtectionProblem(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(ALERT_ID)
        } catch (e: RuntimeException) {
            ErrorLog.record("Quitar aviso", e)
        }
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (e: RuntimeException) {
            ErrorLog.record("Mostrar notificación", e)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_DIAGNOSTICS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
