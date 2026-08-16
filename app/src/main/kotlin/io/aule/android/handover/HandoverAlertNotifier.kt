package io.aule.android.handover

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import io.aule.android.R
import io.aule.android.core.model.HandoverAlertKind

/**
 * Bannière système d'une alerte de relève.
 *
 * Canal aligné sur Flutter (`aule_pro_releve_v1`) : Android 8+ scelle son
 * et vibration à la création. En premier plan, [io.aule.android.core.location.AlertTone]
 * complète ; en fond, c'est ce canal qui porte le son.
 *
 * **Ne lève jamais.** Une bannière manquante ne doit pas faire tomber l'écran.
 */
class HandoverAlertNotifier(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun show(title: String, body: String, kind: HandoverAlertKind) {
        show(title = title, body = body, notificationId = NOTIFICATION_BASE + kind.ordinal)
    }

    /** Confirmation de passation (« Service repris »), hors des seuils d'approche. */
    fun showCompleted(title: String, body: String) {
        show(title = title, body = body, notificationId = NOTIFICATION_COMPLETED)
    }

    private fun show(title: String, body: String, notificationId: Int) {
        try {
            val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(manager)
            val launch = appContext.packageManager
                .getLaunchIntentForPackage(appContext.packageName)
                ?.let { intent ->
                    PendingIntent.getActivity(
                        appContext,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }
            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(launch)
                .build()
            manager.notify(notificationId, notification)
        } catch (_: Throwable) {
            // Bannière manquante : l'écran continue.
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.handover_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(R.string.handover_alert_channel_detail)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "aule_pro_releve_v1"
        const val NOTIFICATION_BASE = 8100
        const val NOTIFICATION_COMPLETED = 8199
    }
}
