package io.aule.android.core.model

import java.time.Duration
import java.time.Instant

/**
 * « Quand vais-je arriver, et que me reste-t-il ? » — la barre du bas.
 *
 * Port de `SAE/lib/navigation/trip_summary.dart`.
 *
 * Une table de résolveurs, et non un `switch` : l'ordre de déclaration **est**
 * la règle de priorité. Ajouter un mode, c'est ajouter un résolveur.
 *
 * On n'invente jamais une heure d'arrivée. Sans source, [TripSummary.arrivalAt]
 * est nul et la colonne se replie.
 */

/**
 * La vitesse du conducteur, en km/h, telle qu'un cadran l'affiche.
 *
 * ## Pourquoi elle n'existait pas
 *
 * Aucune vitesse n'était montrée. `speedMetersPerSecond` ne servait qu'à la
 * courbe de zoom de la caméra et à la fusion de cap ; le seul chiffre en km/h à
 * l'écran appartenait à la fiche d'un **véhicule du réseau**. Un guidage
 * automobile sans vitesse laisse le conducteur sans le seul repère qu'il
 * vérifie sans quitter la route des yeux.
 *
 * ## Deux choix, et ils ne sont pas les mêmes que pour un bus
 *
 * [TransportVehicle.speedKmh] rend `null` sous un mètre par seconde : sur une
 * fiche, « À l'arrêt » dit mieux la chose que « 2 km/h ». Ici c'est l'inverse —
 * **un cadran qui disparaît au feu rouge est un cadran cassé.** On rend donc
 * zéro, et le chiffre reste à sa place.
 *
 * Le seuil est celui du cap ([HEADING_MIN_SPEED_MPS] côté localisation, 0,7 m/s)
 * : en dessous, le GPS ne mesure plus une vitesse, il mesure sa propre dérive,
 * et le cadran battrait entre 1 et 4 à l'arrêt.
 */
fun drivingSpeedKmh(speedMps: Double): Int {
    if (!speedMps.isFinite() || speedMps < DRIVING_SPEED_FLOOR_MPS) return 0
    return Math.round(speedMps * 3.6).toInt()
}

/** Sous cette vitesse, on affiche zéro : le GPS ne mesure plus que son bruit. */
const val DRIVING_SPEED_FLOOR_MPS = 0.7

enum class SummaryMetricKind {
    DISTANCE,
    STOPS,
    LEGS,
    TRANSFER_IN,
}

data class SummaryMetric(
    val kind: SummaryMetricKind,
    val meters: Double? = null,
    val stops: Int? = null,
    val legs: Int? = null,
    val transferMinutes: Int? = null,
)

data class SummaryInputs(
    val plan: JourneyPlan,
    val progress: JourneyProgress,
    val stopsRemaining: Int? = null,
    val untilTransfer: Duration? = null,
) {
    val leg: JourneyLeg
        get() = plan.legs[progress.legIndex.coerceIn(0, plan.legs.lastIndex)]
}

typealias SummaryMetricResolver = (SummaryInputs) -> SummaryMetric?

/** Distance sous laquelle la fin du trajet reprend la main. */
const val SUMMARY_APPROACH_M = 400.0

val SUMMARY_METRIC_RESOLVERS: List<SummaryMetricResolver> = listOf(
    ::transferSoon,
    ::nearDestination,
    ::stopsRemaining,
    ::legsRemaining,
    ::distanceRemaining,
)

private fun transferSoon(inputs: SummaryInputs): SummaryMetric? {
    val until = inputs.untilTransfer ?: return null
    if (until.isNegative) return null
    return SummaryMetric(kind = SummaryMetricKind.TRANSFER_IN, transferMinutes = until.toMinutes().toInt())
}

private fun nearDestination(inputs: SummaryInputs): SummaryMetric? {
    if (inputs.progress.remainingLegs > 1) return null
    if (inputs.progress.remainingMeters > SUMMARY_APPROACH_M) return null
    return SummaryMetric(kind = SummaryMetricKind.DISTANCE, meters = inputs.progress.remainingMeters)
}

private fun stopsRemaining(inputs: SummaryInputs): SummaryMetric? {
    if (inputs.leg.mode != LegMode.TRANSIT) return null
    val stops = inputs.stopsRemaining ?: return null
    if (stops <= 0) return null
    return SummaryMetric(kind = SummaryMetricKind.STOPS, stops = stops)
}

private fun legsRemaining(inputs: SummaryInputs): SummaryMetric? {
    val legs = inputs.progress.remainingLegs
    if (legs < 2) return null
    return SummaryMetric(kind = SummaryMetricKind.LEGS, legs = legs)
}

private fun distanceRemaining(inputs: SummaryInputs): SummaryMetric =
    SummaryMetric(kind = SummaryMetricKind.DISTANCE, meters = inputs.progress.remainingMeters)

fun resolveSummaryMetric(
    inputs: SummaryInputs,
    resolvers: List<SummaryMetricResolver> = SUMMARY_METRIC_RESOLVERS,
): SummaryMetric {
    for (resolver in resolvers) {
        val metric = resolver(inputs)
        if (metric != null) return metric
    }
    return distanceRemaining(inputs)
}

data class TripSummary(
    val third: SummaryMetric,
    val arrivalAt: Instant? = null,
    val remaining: Duration? = null,
    val estimated: Boolean = false,
)

fun tripSummary(
    plan: JourneyPlan,
    progress: JourneyProgress,
    now: Instant,
    remainingOverride: Duration? = null,
    scheduledArrival: Instant? = null,
    scheduleDelay: Duration? = null,
    stopsRemaining: Int? = null,
    untilTransfer: Duration? = null,
): TripSummary {
    val third = resolveSummaryMetric(
        SummaryInputs(
            plan = plan,
            progress = progress,
            stopsRemaining = stopsRemaining,
            untilTransfer = untilTransfer,
        ),
    )

    var remaining: Duration? = null
    var arrival: Instant? = null
    var estimated = false

    when {
        remainingOverride != null -> {
            remaining = remainingOverride.atLeastZero()
            arrival = now.plus(remaining)
        }
        scheduledArrival != null -> {
            arrival = scheduledArrival.plus(scheduleDelay ?: Duration.ZERO)
            remaining = arrival.minusMillis(now.toEpochMilli()).let {
                // Instant.minus isn't Duration; use Duration.between
                java.time.Duration.between(now, arrival).atLeastZero()
            }
        }
        plan.arrivalAt != null -> {
            arrival = plan.arrivalAt
            remaining = java.time.Duration.between(now, arrival).atLeastZero()
        }
        plan.duration != null -> {
            remaining = Duration.ofMillis(
                (plan.duration.toMillis() * (1 - progress.routeT)).roundToLongSafe(),
            ).atLeastZero()
            arrival = now.plus(remaining)
            estimated = true
        }
    }

    return TripSummary(
        arrivalAt = arrival,
        remaining = remaining,
        third = third,
        estimated = estimated,
    )
}

private fun Duration.atLeastZero(): Duration = if (isNegative) Duration.ZERO else this

private fun Double.roundToLongSafe(): Long = kotlin.math.round(this).toLong()
