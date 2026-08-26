package io.aule.android.core.model

import io.aule.android.core.geo.PolylineProjection
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Ce qu'un arrêt est devenu, vu du véhicule qu'on suit.
 *
 * Trois états et pas deux : « desservi » et « à desservir » suffisent à
 * couper la desserte en deux, mais pas à dire *où on en est*. Le prochain
 * arrêt est la seule ligne du plan qu'on regarde vraiment — c'est là que le
 * véhicule sera dans une minute — et il mérite d'être nommé plutôt que d'être
 * le premier d'un tas.
 */
enum class RunStopState {
    /** Le véhicule y est déjà passé. */
    SERVED,

    /** Celui vers lequel il roule en ce moment. */
    NEXT,

    /** Encore devant, après le prochain. */
    AHEAD,
}

/**
 * Un arrêt du plan de ligne, avec l'heure qu'on peut en dire.
 *
 * [plannedAt] est l'horaire du dépôt GTFS : il ne bouge pas de la journée.
 * [expectedAt] n'existe que si le retard est **mesuré** — c'est-à-dire si le
 * véhicule remonte sa position et qu'on a su la poser sur le tracé de la
 * course. Le reste du temps il vaut `null`, et l'écran affiche l'horaire sans
 * prétendre qu'il est tenu.
 */
data class VehicleRunStop(
    val id: String,
    val name: String,
    val plannedAt: Instant,
    val expectedAt: Instant? = null,
    val state: RunStopState = RunStopState.AHEAD,
) {
    /** L'heure à afficher : la mesurée si on l'a, l'horaire sinon. */
    val at: Instant get() = expectedAt ?: plannedAt
}

/**
 * La course d'un véhicule, telle qu'on peut la montrer pendant qu'on le suit.
 *
 * Le modèle ne connaît qu'un fait supplémentaire par rapport à [ScheduledTrip] :
 * **où en est le véhicule**. C'est ce fait, et lui seul, qui coupe la desserte
 * en deux et qui décale les heures à venir.
 */
data class VehicleRun(
    val stops: List<VehicleRunStop>,

    /**
     * L'index du prochain arrêt, ou `stops.size` si la course est finie.
     *
     * Les arrêts avant lui sont desservis, ceux après restent à desservir.
     */
    val nextIndex: Int,

    /**
     * De combien la course est en retard sur son horaire, quand c'est mesurable.
     *
     * Négatif quand le véhicule est en avance — cela arrive, et l'écrire ainsi
     * évite d'avoir à décider ici comment on le dit.
     */
    val delay: Duration? = null,

    /** Vrai quand tout ce qui précède vient d'une position mesurée. */
    val isLive: Boolean = false,
) {
    val nextStop: VehicleRunStop? get() = stops.getOrNull(nextIndex)

    /** Le terminus est derrière : il n'y a plus rien à desservir. */
    val isFinished: Boolean get() = nextIndex >= stops.size

    /** Combien d'arrêts restent à desservir, prochain compris. */
    val remaining: Int get() = (stops.size - nextIndex).coerceAtLeast(0)
}

/**
 * Le plan de ligne du véhicule : sa course, coupée là où il en est.
 *
 * ## D'où sort la coupure, par ordre de confiance
 *
 * 1. **La position mesurée, posée sur le tracé.** C'est la seule source qui
 *    décrive le véhicule qu'on regarde, et non la course en général. Au-delà de
 *    [HANDOVER_OFF_PATH_METERS] du tracé, on considère qu'il ne roule pas la
 *    course qu'on croit et on n'en tire rien — le même seuil que la relève,
 *    parce que c'est le même jugement.
 * 2. **Le prochain arrêt annoncé par le flux.** Le serveur le publie pour les
 *    véhicules qu'il mesure comme pour ceux qu'il calcule, et il vaut mieux que
 *    l'horloge : un bus en retard de six minutes a un prochain arrêt juste et
 *    une heure fausse.
 * 3. **L'heure qu'il est.** Le repli du théorique pur : la course avance sur le
 *    papier, faute de savoir où elle est vraiment.
 *
 * Le retard, lui, ne vient **que** du premier cas. Le déduire d'un nom d'arrêt
 * reviendrait à supposer qu'on y arrive à l'instant où on le lit.
 */
fun ScheduledTrip.runFor(vehicle: TransportVehicle, now: Instant): VehicleRun {
    val progress = progressOf(vehicle)
    val delay = progress?.let { scheduleAt(it) }?.let { Duration.between(it, now) }
    val next = when {
        progress != null -> nextIndexAt(progress)
        else -> nextIndexNamed(vehicle.nextStop) ?: nextStopIndex(now)
    }

    val stops = stops.mapIndexed { index, stop ->
        VehicleRunStop(
            id = stop.stopId,
            name = stop.name,
            plannedAt = stop.passageAt,
            // Le retard ne se reporte que devant. L'appliquer aux arrêts
            // desservis réécrirait une heure qui a déjà eu lieu, et le
            // conducteur qui compare avec sa feuille de route ne s'y
            // retrouverait plus.
            expectedAt = delay?.takeIf { index >= next }?.let { stop.passageAt.plus(it) },
            state = when {
                index < next -> RunStopState.SERVED
                index == next -> RunStopState.NEXT
                else -> RunStopState.AHEAD
            },
        )
    }
    return VehicleRun(
        stops = stops,
        nextIndex = next.coerceIn(0, stops.size),
        delay = delay,
        isLive = progress != null,
    )
}

/**
 * Où le véhicule en est du tracé, de 0 à 1 — ou `null` si on ne peut pas le dire.
 *
 * Une position calculée depuis l'horaire se projetterait très bien, elle aussi :
 * elle est *fabriquée* à partir du tracé. On la refuse quand même, sinon le plan
 * afficherait un retard nul avec l'aplomb d'une mesure, alors qu'il n'aurait
 * fait que retrouver l'horaire d'où la position sortait.
 */
private fun ScheduledTrip.progressOf(vehicle: TransportVehicle): Double? {
    if (!vehicle.isLive) return null
    val points = path?.points ?: return null
    if (points.size < 2) return null
    val match = PolylineProjection.project(position = vehicle.coordinate, onto = points)
        ?: return null
    if (match.deviationMeters > HANDOVER_OFF_PATH_METERS) return null
    return match.t
}

/** Le premier arrêt encore devant, à cet avancement. */
private fun ScheduledTrip.nextIndexAt(progress: Double): Int {
    val fractions = path?.stopFractions ?: return stops.size
    val index = fractions.indexOfFirst { it > progress + STOP_REACHED_EPSILON }
    return if (index < 0) stops.size else index
}

/**
 * L'arrêt que le flux annonce comme prochain, retrouvé dans la desserte.
 *
 * Comparaison sur le nom normalisé : le flux temps réel et le catalogue GTFS
 * ne parlent pas des mêmes identifiants, et « Gare de Chantenay » y arrive avec
 * deux casses et parfois deux accentuations.
 *
 * Sur une course qui repasse par le même arrêt, on prend la **première**
 * occurrence : un véhicule qu'on suit n'a pas encore fait le second tour.
 */
private fun ScheduledTrip.nextIndexNamed(name: String?): Int? {
    val wanted = name?.let { normalizeStopName(it) }?.takeIf { it.isNotEmpty() } ?: return null
    val index = stops.indexOfFirst { normalizeStopName(it.name) == wanted }
    return index.takeIf { it >= 0 }
}

/**
 * L'heure théorique de la course à cet avancement, interpolée entre les arrêts.
 *
 * C'est la référence à laquelle on compare l'heure qu'il est pour obtenir un
 * retard. Hors du tracé connu, on retombe sur les extrémités : avant le
 * premier arrêt, la course n'a pas commencé ; après le dernier, elle est finie.
 */
fun ScheduledTrip.scheduleAt(progress: Double): Instant? {
    val fractions = path?.stopFractions ?: return null
    if (fractions.size != stops.size || stops.size < 2) return null
    if (progress <= fractions.first()) return stops.first().passageAt
    if (progress >= fractions.last()) return stops.last().passageAt
    for (i in 0 until fractions.size - 1) {
        val from = fractions[i]
        val to = fractions[i + 1]
        if (progress < from || progress > to) continue
        val span = to - from
        if (span <= 0) return stops[i].passageAt
        val millis = Duration.between(stops[i].passageAt, stops[i + 1].passageAt).toMillis()
        val fraction = (progress - from) / span
        return stops[i].passageAt.plusMillis((millis * fraction).roundToLong())
    }
    return null
}

/**
 * De combien il faut avoir dépassé la fraction d'un arrêt pour l'avoir desservi.
 *
 * Sans cette marge, un véhicule à quai — dont la projection tombe pile sur
 * l'arrêt — bascule l'arrêt en « desservi » alors que les portes sont encore
 * ouvertes. Un cent-millième de course, soit une poignée de mètres sur une
 * ligne urbaine : assez pour absorber l'arrondi, trop peu pour retarder la
 * bascule d'un arrêt au suivant.
 */
private const val STOP_REACHED_EPSILON = 1e-5
