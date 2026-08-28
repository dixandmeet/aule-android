package io.aule.android.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Le service de premier plan du guidage.
 *
 * Sans lui, Android coupe le flux de positions dès que l'écran s'éteint ou
 * que l'app passe en fond — exactement le moment où un trajet posé sur un
 * support en a besoin. La notification est permanente et non dismissible :
 * c'est le contrat du type `location`, et c'est ce qui le justifie à la
 * revue Play.
 *
 * Il ne lit **aucune** position lui-même. [FusedLocationProvider] reste le
 * seul lecteur ; ce service ne fait que tenir le processus vivant.
 */
class NavigatingForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var onDuty = false

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            onDuty = intent.getBooleanExtra(EXTRA_ON_DUTY, false)
        }
        ensureChannel()
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        acquireWakeLock()
        // **`START_NOT_STICKY`, et non `START_STICKY`.**
        //
        // Ce service ne lit aucune position : il ne fait que tenir le processus
        // vivant pour que [FusedLocationProvider] continue de le faire. Un
        // service relancé seul, après que le système a tué le processus, n'a
        // donc plus rien à garder en vie — mais il reconstruisait quand même sa
        // notification « Navigation en cours » et reprenait un verrou de six
        // heures. Relevé en recette : un balayage du multitâche laissait
        // exactement ce fantôme, qui consomme sans rien produire.
        //
        // Android relance `START_STICKY` avec un `intent` nul, ce qui faisait
        // en plus retomber `onDuty` à faux — la notification mentait aussi sur
        // ce qu'elle gardait.
        return START_NOT_STICKY
    }

    /**
     * L'application balayée du multitâche.
     *
     * Le système ne détruit pas forcément un service de premier plan à ce
     * moment-là ; il faut le dire. Sans cette ligne, le guidage disparaissait
     * de l'écran mais sa notification, son verrou et sa part de batterie
     * restaient — pour un guidage que plus personne ne pouvait reprendre.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.nav_foreground_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.nav_foreground_text)
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(
                    if (onDuty) R.string.duty_foreground_title else R.string.nav_foreground_title,
                ),
            )
            .setContentText(
                getString(
                    if (onDuty) R.string.duty_foreground_text else R.string.nav_foreground_text,
                ),
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(launch)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(PowerManager::class.java) ?: return
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "aule:navigating",
        ).also {
            it.setReferenceCounted(false)
            it.acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    companion object {
        const val CHANNEL_ID = "aule_navigating_v1"
        const val NOTIFICATION_ID = 0xA11E01

        /** Six heures : un trajet plus long reprendra le verrou au prochain tick. */
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context, onDuty: Boolean = false) {
            val intent = Intent(context, NavigatingForegroundService::class.java)
                .putExtra(EXTRA_ON_DUTY, onDuty)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigatingForegroundService::class.java))
        }

        private const val EXTRA_ON_DUTY = "io.aule.android.location.on_duty"
    }
}
