package io.aule.android.core.location

import io.aule.android.core.geo.Coordinate

/**
 * L'état de l'autorisation, tel que les écrans en ont besoin.
 *
 * Le booléen `ACCESS_FINE_LOCATION` ne suffit pas : « autorisé mais en
 * précision approchée » (`ACCESS_COARSE` seul, Android 12+) y ressemble à
 * « autorisé », alors qu'à précision réduite on ne peut ni guider ni afficher
 * les arrêts autour de soi. Le cas mérite son propre état et son propre
 * message.
 *
 * **On ne redemande jamais après un refus.** Un second dialogue système après
 * un non n'est plus une demande, c'est du harcèlement — et depuis Android 11
 * le système l'ignore de toute façon dès que l'utilisateur a coché « Ne plus
 * demander ».
 */
enum class LocationAuthorization {
    /** On n'a pas encore demandé. */
    UNKNOWN,

    /** Refusé. Seuls les Réglages peuvent le changer. */
    DENIED,

    /**
     * Accordé, mais en précision approchée — utilisable pour cadrer une ville,
     * pas pour suivre une rue.
     */
    REDUCED_ACCURACY,

    /** Accordé, précision complète. */
    GRANTED,

    /**
     * La localisation est coupée au niveau de l'appareil. Rien à demander :
     * ce n'est pas une question d'autorisation.
     */
    SERVICES_DISABLED,
    ;

    val allowsPreciseTracking: Boolean get() = this == GRANTED

    /** Vrai quand redemander l'autorisation a une chance d'aboutir. */
    val canRequest: Boolean get() = this == UNKNOWN

    /** Vrai quand un flux de positions a le droit de démarrer. */
    val allowsUpdates: Boolean get() = this == GRANTED || this == REDUCED_ACCURACY
}

/**
 * Ce que la localisation coûte en énergie, selon ce que l'app est en train
 * de faire.
 *
 * Port de `SAE/lib/navigation/navigation_power.dart` et de
 * `LocationPurpose` iOS. Le palier [READY] ne demande **rien** de particulier :
 * la caméra qui suit l'utilisateur est devenue le mode par défaut, et un
 * service de premier plan sans besoin fonctionnel se ferait refuser à la
 * revue Play — et viderait la batterie d'un agent qui consulte un horaire.
 */
enum class LocationPurpose {
    /** Un coup d'œil : on cadre la carte et on s'arrête. */
    GLANCE,

    /** La carte suit l'utilisateur, rien n'est engagé. Régime d'accueil. */
    READY,

    /**
     * Un service est ouvert. La position alimente l'information voyageurs
     * et Relève, y compris écran éteint : d'où le service de premier plan.
     */
    ON_DUTY,

    /**
     * Une navigation est en cours. L'arrière-plan et le service de
     * premier plan sont les mêmes qu'en service, pour le guidage.
     */
    NAVIGATING,
    ;

    /** Intervalle cible entre deux positions, en millisecondes. */
    val intervalMillis: Long
        get() = when (this) {
            GLANCE -> 2_000L
            READY, ON_DUTY, NAVIGATING -> 1_000L
        }

    /**
     * Vrai quand ce palier exige un service de premier plan.
     *
     * [FusedLocationProvider] démarre et arrête [NavigatingForegroundService]
     * en lisant ce booléen : aucun écran n'a à connaître le service.
     */
    val allowsBackground: Boolean get() = this == NAVIGATING || this == ON_DUTY
}

/**
 * Une position, réduite à ce dont la carte a besoin.
 *
 * [coordinate] est déjà passé par [MotionAnchor] : c'est la position à
 * afficher, pas la mesure brute. Le cap lissé vit à part ([stabilizedHeading],
 * [isHeadingFrozen]) parce que le puck et la caméra n'en font pas le même
 * usage — le cône disparaît dès que le cap est gelé ; la caméra, elle, garde
 * le dernier cap valable pour ne pas pivoter au nord à chaque feu rouge.
 */
data class LocationFix(
    val coordinate: Coordinate,
    val accuracyMeters: Double,
    val courseDegrees: Double?,
    val speedMetersPerSecond: Double,
    val timestampMillis: Long,
    val stabilizedHeading: Double?,
    val isHeadingFrozen: Boolean,
    val isMocked: Boolean = false,
) {
    val isUsable: Boolean
        get() = accuracyMeters > 0.0 && accuracyMeters <= MAX_USABLE_ACCURACY_METERS

    companion object {
        /**
         * Au-delà, la position n'est plus assez sûre pour orienter une caméra
         * ou décider qu'on est arrivé.
         */
        const val MAX_USABLE_ACCURACY_METERS = 50.0
    }
}
