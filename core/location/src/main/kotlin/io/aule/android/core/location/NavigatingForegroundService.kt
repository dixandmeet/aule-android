package io.aule.android.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val arret = intent?.action == ACTION_STOP
        if (intent != null && !arret) {
            onDuty = intent.getBooleanExtra(EXTRA_ON_DUTY, false)
        }
        ensureChannel()
        val notification = buildNotification()
        // ⚠️ **`startForeground` d'abord, même quand on vient pour s'arrêter.**
        //
        // Le système a promis un service de premier plan dès l'appel à
        // `startForegroundService()` ; il exige la notification dans les
        // secondes qui suivent, et tue l'application sinon —
        // `ForegroundServiceDidNotStartInTimeException`. Un arrêt qui
        // court-circuiterait cet appel serait exactement le défaut qu'on
        // corrige ici : voir [stop].
        //
        // ⚠️ **Mais le système peut le refuser, et il ne faut alors pas insister.**
        //
        // Un service de premier plan de type `location` exige la permission de
        // localisation : sans elle, `startForeground` lève `SecurityException`
        // — non rattrapée, sur le fil principal, elle **tue l'application**.
        // Relevé sur le S21 le 18/09/2026, position refusée : Aule plantait en
        // boucle au lancement (« Aule s'arrête systématiquement ») avant même
        // d'avoir peint sa carte, parce que l'arbitre demandait l'arrêt d'un
        // service qu'on n'avait plus le droit de démarrer.
        //
        // Le refus n'est pas une erreur à remonter : c'est la réponse du
        // système à une demande devenue illégitime. On s'en va proprement.
        val posee = try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (refuse: SecurityException) {
            false
        } catch (refuse: IllegalStateException) {
            // Android 12+ : démarrage depuis l'arrière-plan hors des cas permis.
            false
        }
        if (arret || !posee) {
            // Le contrat est tenu — ou n'a jamais pu l'être ; on peut partir.
            // `onDestroy` retire la notification et rend le verrou.
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
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
        running = false
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
            .setSmallIcon(smallIcon())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(launch)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Le glyphe de la barre d'état.
     *
     * ## ⚠️ C'était une icône d'Android, pas la marque
     *
     * `android.R.drawable.ic_menu_mylocation` : le réticule du système. Le volet des
     * notifications montrait donc une application sans nom à côté de celles qui portent le
     * leur, pendant que le notifieur de descente, lui, posait bien la monochrome d'Aule
     * (recette du 18/09/2026, BUG-AND-010).
     *
     * ⚠️ **Une bibliothèque ne peut pas nommer une ressource de l'application.** `:core:location`
     * est partagé par deux binaires, et la marque vit dans chacun d'eux. L'icône se déclare donc
     * dans le manifeste, sous [ICON_META_DATA], et le service la lit à l'exécution — c'est le
     * seul point où les deux se rencontrent.
     *
     * Sans déclaration, on retombe sur l'icône du lanceur : la marque plutôt que le système,
     * même si elle n'est pas monochrome.
     */
    private fun smallIcon(): Int {
        val info = runCatching {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        }.getOrNull()
        val declared = info?.metaData?.getInt(ICON_META_DATA, 0) ?: 0
        if (declared != 0) return declared
        return info?.icon?.takeIf { it != 0 } ?: android.R.drawable.ic_menu_mylocation
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

        /**
         * La clé sous laquelle une application déclare son glyphe de barre d'état.
         *
         * ```xml
         * <meta-data
         *     android:name="io.aule.location.NOTIFICATION_ICON"
         *     android:resource="@mipmap/ic_launcher_monochrome" />
         * ```
         *
         * ⚠️ **Elle se pose dans `<application>`**, pas sur le service : c'est là que
         * `getApplicationInfo(GET_META_DATA)` la lit.
         */
        const val ICON_META_DATA = "io.aule.location.NOTIFICATION_ICON"
        const val NOTIFICATION_ID = 0xA11E01

        /** Six heures : un trajet plus long reprendra le verrou au prochain tick. */
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L

        fun start(context: Context, onDuty: Boolean = false) {
            val intent = Intent(context, NavigatingForegroundService::class.java)
                .putExtra(EXTRA_ON_DUTY, onDuty)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Arrête le service **sans jamais le détruire avant sa notification**.
         *
         * ## ⚠️ Pourquoi pas `stopService()` tout court
         *
         * `startForegroundService()` est asynchrone : entre l'appel et la
         * livraison de `onStartCommand`, il s'écoule quelques millisecondes
         * pendant lesquelles le système attend déjà sa notification. Un
         * `stopService()` qui tombe dans cette fenêtre détruit le service avant
         * son `startForeground()`, et Android **tue le processus** —
         * `ForegroundServiceDidNotStartInTimeException`, fatale, sur le fil
         * principal, sans rien que l'application puisse rattraper.
         *
         * Ce n'est pas théorique : relevé sur le S21 le 18/09/2026, l'écran
         * revenant d'une veille pendant que l'arbitre rendait le palier —
         * démarrage à 13:46:11.871, `Bringing down service while still waiting
         * for start foreground` sept millisecondes plus tard, plantage à
         * 13:46:12.025. Les deux gestes venaient de deux causes qui ne se
         * connaissent pas (le cycle de vie de l'écran, la fin de la veille),
         * donc aucun ordre ne peut être garanti à l'appelant.
         *
         * On passe donc **par le service** : il honore son contrat, puis
         * `stopSelf()`. Le repli garde l'ancien geste pour le cas où le système
         * refuse un démarrage depuis l'arrière-plan — refus qui ne survient que
         * si aucun service n'est en cours, c'est-à-dire précisément quand il
         * n'y a rien à arrêter.
         */
        fun stop(context: Context) {
            // ⚠️ **On ne réveille pas un service qui ne tourne pas.**
            //
            // `startForegroundService` **promet** un service de premier plan, et
            // la promesse doit être tenue par une notification — avec le type
            // `location`, donc avec la permission de localisation. Demander
            // l'arrêt de ce qui n'a jamais démarré faisait donc faire cette
            // promesse pour rien, et la rendait intenable dès que la position
            // était refusée. C'est le chemin exact du plantage en boucle relevé
            // le 18/09/2026 : l'arbitre appelle `stop()` au lancement, par
            // symétrie, sans qu'aucun guidage n'ait jamais commencé.
            //
            // `stopService` sur un service arrêté ne fait rien, et ne promet rien.
            if (!running) {
                context.stopService(Intent(context, NavigatingForegroundService::class.java))
                return
            }
            val arret = Intent(context, NavigatingForegroundService::class.java)
                .setAction(ACTION_STOP)
            runCatching { ContextCompat.startForegroundService(context, arret) }
                .onFailure {
                    context.stopService(Intent(context, NavigatingForegroundService::class.java))
                }
        }

        /**
         * Le service tient-il actuellement son premier plan ?
         *
         * ⚠️ **Lu depuis un autre fil que celui qui l'écrit**, d'où `@Volatile` : l'arbitre
         * appelle [stop] depuis le fil principal, `onStartCommand` s'exécute sur le même — mais
         * rien dans le contrat d'Android ne le garantit, et une valeur retenue en cache dirait
         * qu'un service arrêté tourne encore.
         *
         * La mort du processus le remet à faux, ce qui est exact : un processus mort n'a pas de
         * service.
         */
        @Volatile
        private var running = false

        private const val EXTRA_ON_DUTY = "io.aule.android.location.on_duty"

        /** L'ordre d'arrêt, porté par l'action plutôt que par un extra : il doit
         *  survivre à un `onStartCommand` qui ne lit plus les extras. */
        private const val ACTION_STOP = "io.aule.android.location.stop"
    }
}
