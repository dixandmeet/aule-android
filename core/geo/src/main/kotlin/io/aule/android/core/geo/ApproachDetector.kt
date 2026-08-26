package io.aule.android.core.geo

import java.time.Duration
import java.time.Instant

/**
 * Une position, réduite à ce dont une décision a besoin.
 *
 * Le type existe pour que les modules de décision restent **purs** : le point de
 * localisation vit dans un module qui connaît Android, et la règle de `:core:geo`
 * est de n'en dépendre d'aucun. L'appelant fait la traduction, qui tient en
 * quatre champs.
 *
 * Port de `PositionSample` dans `Native/Aule/Core/Padeur/PadeurWatch.swift`.
 */
data class PositionSample(
    val coordinate: Coordinate,
    /**
     * Le rayon dans lequel le point est cru.
     *
     * **C'est lui qui décide du seuil d'arrivée** : exiger la même distance sous
     * un canyon de bâtiments qu'en vue dégagée ferait attendre indéfiniment.
     */
    val accuracyMeters: Double,
    val speedMetersPerSecond: Double,
    val at: Instant,
)

/** Où l'on en est vis-à-vis d'un point qu'on rejoint. */
enum class ApproachState {
    /**
     * **Ce n'est pas une réponse** : c'est l'absence de réponse. Aucun point
     * exploitable n'est encore arrivé, et l'appelant doit attendre plutôt que de
     * choisir.
     */
    UNDECIDED,
    AWAY,
    ARRIVED,
}

/**
 * Ce qu'on peut décider à partir de positions, et ce qu'on refuse de décider.
 *
 * ## Deux règles éprouvées en service
 *
 * Elles viennent du guidage Flutter (`approach_detector.dart`), et rien ici ne
 * justifie de les redécouvrir :
 *
 * 1. **Le rayon d'arrivée suit la précision.** Un point à 40 m ne prouve pas qu'on
 *    est à 40 m. D'où `précision × 1,7`, borné entre 40 et 80 m.
 * 2. **Une seule mesure ne fait pas basculer d'état.** Deux confirmations
 *    consécutives — ou une distance si courte (22 m) qu'elle tranche seule. Un
 *    unique point optimiste ferait annoncer l'arrivée deux rues trop tôt.
 */
object ApproachRules {
    /** En deçà, on est arrivé. C'est aussi le plancher du rayon adaptatif. */
    const val ARRIVE_METERS = 40.0

    /** Distance si courte qu'elle tranche seule, sans seconde confirmation. */
    const val CERTAIN_METERS = 22.0

    const val MAX_ARRIVE_METERS = 80.0
    const val ACCURACY_FACTOR = 1.7

    /**
     * Fraîcheur et précision minimales du point qui **décide**. Un point sorti du
     * cache système ou trop imprécis ne décide de rien : on attend le flux vivant.
     */
    const val MAX_FIX_AGE_SECONDS = 60L
    const val MAX_FIX_ACCURACY = 100.0

    fun arrivalRadius(accuracyMeters: Double): Double =
        (accuracyMeters * ACCURACY_FACTOR).coerceIn(ARRIVE_METERS, MAX_ARRIVE_METERS)

    fun isUsable(sample: PositionSample, now: Instant): Boolean =
        sample.accuracyMeters > 0 &&
            sample.accuracyMeters <= MAX_FIX_ACCURACY &&
            Duration.between(sample.at, now).seconds <= MAX_FIX_AGE_SECONDS &&
            sample.coordinate.isValidPosition()
}

/**
 * « Suis-je arrivé au quai ? »
 *
 * Une classe à état parce que la seconde confirmation en est un : deux appels
 * successifs donnent un verdict que le premier seul ne donne pas. L'état est
 * minuscule — mais rien n'y touche à l'horloge, et c'est ce qui le rend
 * vérifiable.
 */
class ApproachDetector {

    var state: ApproachState = ApproachState.UNDECIDED
        private set

    private var pending: ApproachState = ApproachState.UNDECIDED
    private var confirmations = 0

    /** @return l'état **après** cette mesure. Inchangé si le point n'est pas exploitable. */
    fun update(sample: PositionSample, target: Coordinate, now: Instant): ApproachState {
        if (!ApproachRules.isUsable(sample, now)) return state

        val distance = GeoMath.distance(sample.coordinate, target)

        // Assez près pour trancher seule : deux mètres d'incertitude ne changent
        // rien à vingt-deux mètres de distance.
        if (distance <= ApproachRules.CERTAIN_METERS) {
            state = ApproachState.ARRIVED
            pending = ApproachState.ARRIVED
            confirmations = 2
            return state
        }

        val radius = ApproachRules.arrivalRadius(sample.accuracyMeters)
        val observed = if (distance <= radius) ApproachState.ARRIVED else ApproachState.AWAY

        if (observed == pending) {
            confirmations += 1
        } else {
            pending = observed
            confirmations = 1
        }
        if (confirmations >= 2) state = observed
        return state
    }

    fun reset() {
        state = ApproachState.UNDECIDED
        pending = ApproachState.UNDECIDED
        confirmations = 0
    }
}

/** Une coordonnée plausible : hors bornes, ce n'est pas une position mais un défaut. */
internal fun Coordinate.isValidPosition(): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
