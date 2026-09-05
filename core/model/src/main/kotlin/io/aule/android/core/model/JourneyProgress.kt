package io.aule.android.core.model

/**
 * Où l'on en est dans le trajet — une lecture, pas un état.
 *
 * Port de `SAE/lib/navigation/journey_progress.dart`.
 *
 * `RouteProgress` **est** l'autorité. Ce module ne fait que lire son `t`.
 */

/**
 * Distance à la fin du tracé sous laquelle le trajet est abouti.
 *
 * ## Pourquoi des mètres, et non une fraction
 *
 * Le seuil valait `0,999` d'avancement. Une fraction ne dit pas la même chose
 * selon la longueur : sur les 1 832 m du trajet de recette elle réclamait
 * **moins de deux mètres**, sur 50 km elle en aurait laissé cinquante. Or ce
 * qu'on veut savoir tient en une phrase — « suis-je assez près pour que ce soit
 * fini ? » —, et cette phrase se mesure en mètres.
 *
 * La campagne du 28/08/2026 a montré ce que la fraction coûtait. Le routeur
 * arrête son tracé sur la voirie ; la destination, elle, est l'adresse — 34 m
 * plus loin pour « Gare de Nantes ». Un véhicule qui va au bout de la route
 * praticable n'atteignait donc jamais `0,999`, l'arrivée n'était jamais
 * déclarée, et le service de premier plan, son verrou et le GPS haute précision
 * survivaient au trajet : deux heures après l'arrivée, ils tournaient encore,
 * l'appareil à 41,9 °C. C'est `MapScreen` qui fait retomber le palier de
 * localisation sur `arrived` — sans ce drapeau, rien ne le fait retomber.
 *
 * Cinquante mètres, c'est le rayon que les guidages grand public retiennent, et
 * il couvre l'écart mesuré entre la fin d'un tracé et l'adresse visée.
 */
const val JOURNEY_ARRIVED_M = 50.0

/**
 * Fraction en deçà de laquelle on n'est de toute façon pas arrivé.
 *
 * Garde-fou pour les trajets plus courts que [JOURNEY_ARRIVED_M] : sans elle, un
 * itinéraire de quarante mètres serait « abouti » avant d'avoir commencé.
 */
const val JOURNEY_ARRIVED_T_FLOOR = 0.5

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

    val remainingMeters = plan.distanceMeters * (1 - t)

    return JourneyProgress(
        legIndex = index,
        legT = legT,
        routeT = t,
        remainingMeters = remainingMeters,
        legRemainingMeters = leg.distanceMeters * (1 - legT),
        remainingLegs = legs.size - index,
        arrived = remainingMeters <= JOURNEY_ARRIVED_M && t >= JOURNEY_ARRIVED_T_FLOOR,
    )
}
