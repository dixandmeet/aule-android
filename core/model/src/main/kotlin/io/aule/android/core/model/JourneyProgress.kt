package io.aule.android.core.model

/**
 * Où l'on en est dans le trajet — une lecture, pas un état.
 *
 * Port de `SAE/lib/navigation/journey_progress.dart`.
 *
 * `RouteProgress` **est** l'autorité. Ce module ne fait que lire son `t`.
 */

/** Fraction au-delà de laquelle on considère le trajet abouti. Pas 1,0 : le dernier point est le centre de la rue. */
const val JOURNEY_ARRIVED_T = 0.999

data class JourneyProgress(
    val legIndex: Int,
    val legT: Double,
    val routeT: Double,
    val remainingMeters: Double,
    val legRemainingMeters: Double,
    /** Celle en cours comprise : au départ d'un marche → tram → marche, il reste trois étapes. */
    val remainingLegs: Int,
    val arrived: Boolean,
)

fun journeyProgressAt(plan: JourneyPlan, routeT: Double): JourneyProgress? {
    if (plan.isEmpty) return null
    val t = if (routeT.isFinite()) routeT.coerceIn(0.0, 1.0) else 0.0
    val legs = plan.legs

    var index = 0
    for (i in legs.indices.reversed()) {
        if (t >= legs[i].startT) {
            index = i
            break
        }
    }

    val leg = legs[index]
    val span = leg.spanT
    val legT = if (span <= 0) 1.0 else ((t - leg.startT) / span).coerceIn(0.0, 1.0)

    return JourneyProgress(
        legIndex = index,
        legT = legT,
        routeT = t,
        remainingMeters = plan.distanceMeters * (1 - t),
        legRemainingMeters = leg.distanceMeters * (1 - legT),
        remainingLegs = legs.size - index,
        arrived = t >= JOURNEY_ARRIVED_T,
    )
}
