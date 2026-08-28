package io.aule.android.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.aule.android.R
import io.aule.android.core.model.DepartureWatchAlertKind

/**
 * Bannière système d'une veille de passage.
 *
 * Son propre canal, distinct de celui de la relève (`aule_pro_releve_v1`) : ce
 * ne sont pas les mêmes alertes, elles ne s'adressent pas au même usage, et un
 * conducteur qui coupe les bannières de service ne doit pas perdre au passage
 * l'alerte du bus qu'il attend en tant qu'usager — ni l'inverse. Android scelle
 * son et vibration à la création du canal ; deux canaux, c'est aussi deux
 * réglages séparés dans les paramètres du téléphone.
 *
 * L'importance est **haute** et non maximale : « votre bus arrive dans trois
 * minutes » mérite de sonner, pas d'interrompre par-dessus tout ce qui est à
 * l'écran comme le fait une relève à prendre.
 *
 * **Ne lève jamais.** Une bannière manquante ne doit pas faire tomber l'écran.
 */
class DepartureWatchNotifier(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun show(title: String, body: String, kind: DepartureWatchAlertKind) {
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(launch)
                .build()
            // Un identifiant par genre : « dans 3 min » puis « à l'approche »
            // sont deux nouvelles successives, et la seconde doit remplacer la
            // première plutôt que s'empiler sous elle — mais seulement la sienne.
            manager.notify(NOTIFICATION_BASE + kind.ordinal, notification)
        } catch (_: Throwable) {
            // Bannière manquante : la veille continue.
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.watch_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = appContext.getString(R.string.watch_alert_channel_detail)
                enableVibration(true)
                // Deux impulsions courtes, distinctes du motif de la relève
                // (deux longues) : à l'oreille comme au poignet, on doit savoir
                // laquelle des deux alertes vient de parler sans sortir le
                // téléphone.
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "aule_approche_v1"
        const val NOTIFICATION_BASE = 8200
    }
}
