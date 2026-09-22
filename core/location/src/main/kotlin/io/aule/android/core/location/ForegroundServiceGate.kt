package io.aule.android.core.location

/**
 * Ce que le service de premier plan porte déjà, et qu'il est donc inutile de lui redemander.
 *
 * ## ⚠️ L'arrêt avait sa garde, le démarrage non
 *
 * `stopForegroundService` a toujours regardé s'il y avait quelque chose à arrêter ;
 * `startForegroundService`, lui, repartait à chaque passage de l'arbitre. Or un seul retour au
 * premier plan pendant un guidage en déclenche trois d'affilée : [LocationProvider.start] relit
 * l'autorisation — ce qui synchronise une première fois —, puis synchronise lui-même, et l'écran
 * rend son palier par-dessus. Relevé sur le S21 le 22/09/2026 : trois « Service de premier plan
 * démarré » en dix millisecondes (BUG-AND-220).
 *
 * Ce que chacun coûtait : un aller-retour au système, une notification reconstruite, un
 * `startForeground` de plus — et autant d'occasions qu'Android refuse un démarrage depuis
 * l'arrière-plan là où le premier était passé.
 *
 * ## Pourquoi le libellé compte, et pas seulement le fait de tourner
 *
 * La notification ne dit pas la même chose selon le palier : « Trajet en cours » pour un guidage,
 * la phrase de service pour [LocationPurpose.ON_DUTY]. Passer de l'un à l'autre **doit** donc
 * repartir. Une garde qui ne retiendrait que « ça tourne » laisserait la barre d'état mentir sur
 * ce qu'elle garde — ce serait échanger un défaut contre un autre, moins visible.
 *
 * La règle vit ici, en pur Kotlin, pour rester vérifiable hors framework Android — comme
 * [AlertTonePolicy].
 */
internal class ForegroundServiceGate {

    /** Le palier posé : `null` quand rien ne tourne, sinon le `onDuty` de la notification. */
    private var posted: Boolean? = null

    /** Y a-t-il quelque chose à arrêter ? */
    val isActive: Boolean get() = posted != null

    /**
     * Faut-il demander le service pour ce palier ?
     *
     * Vrai quand rien ne tourne, et vrai encore quand ce qui tourne ne porte pas le bon libellé.
     */
    fun needsStart(onDuty: Boolean): Boolean = posted != onDuty

    /** Le service a été demandé, et il porte ce libellé. */
    fun started(onDuty: Boolean) {
        posted = onDuty
    }

    /**
     * Plus rien n'est posé.
     *
     * Sert aussi au **refus** : un démarrage qui a échoué ne laisse rien derrière lui, et la
     * prochaine synchronisation doit pouvoir réessayer.
     */
    fun stopped() {
        posted = null
    }
}
