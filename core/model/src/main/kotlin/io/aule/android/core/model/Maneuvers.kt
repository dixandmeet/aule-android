package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.PolylineProjection

/**
 * Les manœuvres, agrafées sur le tracé qu'on voit.
 *
 * Port de `SAE/lib/navigation/maneuvers.dart`.
 *
 * `/api/route` ne rend aucune manœuvre. Un routeur de voirie (OSRM) les
 * décrit pour **la même jambe**, puis on les agrafe. Une manœuvre qui ne
 * tombe pas sur le tracé n'est pas rapprochée : elle est **écartée**.
 */

/** Écart maximal, en mètres. Vingt-cinq : le grain d'un carrefour, pas une rue voisine. */
const val MANEUVER_SNAP_M = 25.0

enum class ManeuverKind {
    DEPART,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT,
    FORK,
    MERGE,
    RAMP,
    ARRIVE,
    UNKNOWN,
}

data class RoadManeuver(
    val instruction: String,
    val location: Coordinate,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val streetName: String? = null,
    val modifier: String? = null,
)

data class PinnedManeuver(
    val kind: ManeuverKind,
    val t: Double,
    val streetName: String? = null,
)

data class UpcomingManeuver(
    val maneuver: PinnedManeuver,
    val meters: Double,
)

fun maneuverKindOf(type: String, modifier: String? = null): ManeuverKind {
    val t = type.trim().lowercase()
    val m = modifier?.trim()?.lowercase()
    return when (t) {
        "depart" -> ManeuverKind.DEPART
        "arrive" -> ManeuverKind.ARRIVE
        "merge" -> ManeuverKind.MERGE
        "fork" -> ManeuverKind.FORK
        "on ramp", "off ramp" -> ManeuverKind.RAMP
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
            ManeuverKind.ROUNDABOUT
        "turn", "new name", "continue", "end of road", "notification" -> fromModifier(m)
        else -> {
            val fromModifier = fromModifier(m)
            if (fromModifier == ManeuverKind.STRAIGHT && m == null) ManeuverKind.UNKNOWN else fromModifier
        }
    }
}

private fun fromModifier(modifier: String?): ManeuverKind = when (modifier) {
    "left" -> ManeuverKind.LEFT
    "slight left" -> ManeuverKind.SLIGHT_LEFT
    "sharp left" -> ManeuverKind.SHARP_LEFT
    "right" -> ManeuverKind.RIGHT
    "slight right" -> ManeuverKind.SLIGHT_RIGHT
    "sharp right" -> ManeuverKind.SHARP_RIGHT
    "uturn", "u-turn" -> ManeuverKind.U_TURN
    "straight", null -> ManeuverKind.STRAIGHT
    else -> ManeuverKind.UNKNOWN
}

/**
 * Agrafe [raw] sur [painted], et écarte ce qui ne tient pas.
 *
 * [minT] et [maxT] bornent la recherche à une jambe. Le plancher n'avance
 * que sur une manœuvre **retenue**.
 */
fun pinManeuvers(
    painted: List<Coordinate>,
    raw: List<RoadManeuver>,
    toleranceMeters: Double = MANEUVER_SNAP_M,
    minT: Double = 0.0,
    maxT: Double = 1.0,
): List<PinnedManeuver> {
    if (painted.size < 2 || raw.isEmpty()) return emptyList()
    val lower = minT.coerceIn(0.0, 1.0)
    val upper = maxT.coerceIn(lower, 1.0)
    val out = mutableListOf<PinnedManeuver>()
    var floor = lower
    for (maneuver in raw) {
        val match = PolylineProjection.projectWithin(
            maneuver.location,
            onto = painted,
            minT = floor,
            maxT = upper,
        ) ?: continue
        if (match.deviationMeters > toleranceMeters) continue
        out += PinnedManeuver(
            kind = maneuverKindOf(maneuver.instruction, maneuver.modifier),
            t = match.t,
            streetName = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() },
        )
        floor = match.t
    }
    return out
}

private val notAhead = setOf(ManeuverKind.DEPART)

fun nextManeuver(
    pinned: List<PinnedManeuver>,
    routeT: Double,
    routeLengthMeters: Double,
): UpcomingManeuver? {
    if (pinned.isEmpty()) return null
    val t = if (routeT.isFinite()) routeT.coerceIn(0.0, 1.0) else 0.0
    for (maneuver in pinned) {
        if (maneuver.t < t) continue
        if (maneuver.kind in notAhead) continue
        val meters = (maneuver.t - t) * routeLengthMeters
        return UpcomingManeuver(maneuver, if (meters < 0) 0.0 else meters)
    }
    return null
}
