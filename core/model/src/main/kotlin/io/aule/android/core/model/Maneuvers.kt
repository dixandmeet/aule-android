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

/**
 * Écart relatif au-delà duquel le routeur de voirie ne décrit plus la même jambe.
 */
const val MANEUVER_LENGTH_TOLERANCE = 0.25

/**
 * Écart absolu toléré quoi qu'il arrive, en mètres.
 *
 * Sur une jambe courte, vingt-cinq pour cent ne font que quelques mètres, et
 * deux routeurs ont le droit de ne pas être d'accord sur le côté de la rue où
 * s'arrête une jambe. Cinquante mètres, c'est le grain d'un carrefour.
 */
const val MANEUVER_LENGTH_SLACK_M = 50.0

/**
 * Le routeur de voirie a-t-il décrit **la jambe qu'on peint** ?
 *
 * ## Le défaut que cette fonction ferme
 *
 * Les manœuvres viennent d'un second serveur, interrogé pour la même paire de
 * points. Rien ne garantissait qu'il réponde pour le même **trajet**. Mesuré le
 * 28/08/2026 sur `router.project-osrm.org` : les chemins `driving`, `walking` et
 * `foot` rendent la **même réponse au mètre près** — le serveur de démonstration
 * ne charge que le profil voiture et ignore le profil demandé. Une jambe à pied
 * recevait donc des consignes de voiture, calculées sur des sens interdits qui
 * ne la concernent pas.
 *
 * Le symptôme était silencieux, et c'est ce qui le rendait grave :
 * [pinManeuvers] écarte ce qui tombe à plus de vingt-cinq mètres du tracé, donc
 * la plupart des manœuvres disparaissaient sans un mot — et **celles qui
 * coïncidaient par hasard restaient, fausses**.
 *
 * ## Pourquoi la longueur suffit à trancher
 *
 * Deux routeurs qui décrivent le même chemin s'accordent sur sa longueur ; deux
 * routeurs qui décrivent des chemins différents s'en écartent tout de suite. Le
 * relevé consigné dans `Route.kt` le montre sur une même paire de points
 * nantais : 713,5 m à pied contre 1 198,6 m en voiture, soit deux tiers de plus.
 * C'est un test à un nombre, sans géométrie à comparer, et il vaut pour la
 * voiture aussi — un OSRM qui contourne un chantier que le BFF ignore décrit
 * lui aussi un autre trajet.
 */
fun roadRouteDescribesLeg(roadMeters: Double, paintedMeters: Double): Boolean {
    if (!roadMeters.isFinite() || !paintedMeters.isFinite()) return false
    if (roadMeters <= 0.0 || paintedMeters <= 0.0) return false
    val tolerated = maxOf(MANEUVER_LENGTH_SLACK_M, paintedMeters * MANEUVER_LENGTH_TOLERANCE)
    return kotlin.math.abs(roadMeters - paintedMeters) <= tolerated
}

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
    /** La sortie à prendre sur un rond-point, comptée à partir de 1. */
    val exit: Int? = null,
)

data class PinnedManeuver(
    val kind: ManeuverKind,
    val t: Double,
    val streetName: String? = null,
    /**
     * La sortie du rond-point.
     *
     * Un nombre, pas une phrase : « la troisième » se dit autrement en anglais,
     * et l'ordinal se fabrique à l'affichage (ADR-011).
     */
    val exit: Int? = null,
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
            exit = maneuver.exit,
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
