package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylineProjection
import kotlin.math.abs

/**
 * Les manœuvres, agrafées sur le tracé qu'on voit.
 *
 * Port de `SAE/lib/navigation/maneuvers.dart`.
 *
 * ⚠️ **Elles viennent du même appel que la géométrie.** `mode=foot` et
 * `mode=car` rendent une liste `maneuvers` **en plus** du tracé, et c'est la
 * seule façon d'être sûr qu'elles décrivent le trait qu'on peint : deux
 * appels rendraient deux chemins, donc un bandeau qui annonce un virage que
 * le tracé ne prend pas (`docs/CONTRAT-BFF.md` §10). Le routeur de voirie
 * n'est plus qu'un repli, pour un trajet qui n'en porte aucune.
 *
 * Une manœuvre qui ne tombe pas sur le tracé n'est pas rapprochée : elle est
 * **écartée**.
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

    /**
     * **La sortie de l'anneau, pas une instruction.**
     *
     * Un giratoire rend deux manœuvres : l'entrée, qui porte le numéro de
     * sortie et le nom de la voie, puis la sortie, qui répète le même numéro.
     * Les jeter — le réflexe, puisqu'elles n'annoncent rien de neuf — retire
     * la seule borne qui dise *quand* on quitte l'anneau ; les laisser parler
     * remplace « 2e sortie » par « continuer » à l'instant précis où l'on
     * cherche la sortie. Sur le Nantes-centre → Bouguenais de
     * `route-car.json`, quatorze sorties sur quarante-quatre manœuvres.
     *
     * Elle est donc gardée et muette : [notAhead] la saute.
     */
    ROUNDABOUT_EXIT,
    FORK,
    MERGE,
    RAMP,
    ARRIVE,
    UNKNOWN,
}

/**
 * Une voie du carrefour abordé, et ce qu'elle permet.
 *
 * [valid] est le seul champ qui compte pour se placer : c'est lui qui
 * distingue la voie où l'on doit être de celle qu'on regarde. Les
 * indications gardent les mots du moteur — « slight right », « none » —,
 * comme [RoadManeuver.modifier] : les traduire ici obligerait à trancher
 * sans le type de la manœuvre sous les yeux.
 */
data class ManeuverLane(
    val indications: List<String> = emptyList(),
    val valid: Boolean = false,
)

/**
 * Une manœuvre telle que le moteur la rend : **un point du sol et un geste**.
 *
 * [instruction] et [modifier] portent le vocabulaire d'OSRM, sans retouche :
 * le serveur rend le fait de voirie, la phrase est le travail du client
 * (ADR-011). Les champs facultatifs sont **absents, jamais nuls** — le BFF
 * normalise, un client n'a donc pas à distinguer « nul » de « vide ».
 */
data class RoadManeuver(
    val instruction: String,
    val location: Coordinate,
    /**
     * Ce que le routeur de voirie annonce jusqu'à la manœuvre suivante. Le
     * bandeau ne s'en sert pas : la distance qu'il affiche se mesure entre la
     * position du moment et le point agrafé sur le tracé. Relayer celle du
     * moteur donnerait un chiffre juste au calcul et faux dès le premier
     * mètre parcouru. `/api/route` ne les rend pas, et c'est sans effet.
     */
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Le nom de la voie **où l'on s'engage**, ou son numéro à défaut. */
    val streetName: String? = null,
    val modifier: String? = null,
    /**
     * Le cap juste avant la manœuvre, puis juste après.
     *
     * ⚠️ **Ils voyagent ensemble ou pas du tout** : un angle est une
     * différence, et une différence à laquelle il manque un terme ne vaut pas
     * zéro degré — elle ne vaut rien. Quand ils sont là, c'est la mesure qui
     * arbitre le côté ; voir [maneuverKindOf].
     */
    val bearingBefore: Double? = null,
    val bearingAfter: Double? = null,
    /**
     * Le numéro de route seul — « A 811 ». Il vit **aussi** dans
     * [streetName] faute de nom de voie ; ce champ sert à le composer
     * autrement, un écusson d'autoroute n'ayant pas sa place au milieu d'une
     * phrase.
     */
    val ref: String? = null,
    /**
     * Ce qui est écrit sur le panneau, tel quel.
     *
     * ⚠️ **Une chaîne d'affichage, pas une liste** : « A 11 (péage) »,
     * « A 11: Paris, Laval, Angers, Ancenis ». Sa forme varie avec le
     * cartographe et dépasse souvent une ligne de bandeau — la découper est à
     * la vue, seule à connaître la place dont elle dispose.
     */
    val destinations: String? = null,
    /** La sortie à prendre, sur un giratoire. Portée par l'entrée **et** par la sortie. */
    val exit: Int? = null,
    /** Le nom du grand rond-point, quand il en a un. */
    val rotaryName: String? = null,
    /** Vide le plus souvent : OSM ne cartographie les voies que sur les grands carrefours. */
    val lanes: List<ManeuverLane> = emptyList(),
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

/**
 * Le geste que décrit une manœuvre, arbitré par la mesure quand elle existe.
 *
 * ## ⚠️ Le modificateur ment, et les caps disent quand
 *
 * [modifier] vient du moteur, qui choisit le côté sur la **topologie** du
 * carrefour — quelles branches en partent, laquelle on quitte — et non sur
 * l'angle réellement parcouru. Mesuré sur trois trajets nantais le
 * 25/08/2026 : **2 pas `turn` sur 13 annoncent un côté pour moins de dix
 * degrés d'angle**. Place Saint-Pierre, le moteur rend `turn` / `left` avec
 * `bearingBefore: 81` et `bearingAfter: 81` — zéro degré, et une flèche à
 * gauche là où la place se traverse tout droit.
 *
 * Sans les **deux** caps, rien n'est arbitré : le modificateur passe tel
 * quel, comme avant eux. C'est aussi ce qui protège du `depart`, où le
 * moteur met 0 en guise de cap d'avant — il n'en rend alors aucun, et la
 * famille de la manœuvre le tient de toute façon à l'écart.
 */
fun maneuverKindOf(
    type: String,
    modifier: String? = null,
    bearingBefore: Double? = null,
    bearingAfter: Double? = null,
): ManeuverKind {
    val t = type.trim().lowercase()
    val m = modifier?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val turn = turnOf(t, m, bearingBefore, bearingAfter)
    return when (t) {
        "depart" -> ManeuverKind.DEPART
        "arrive" -> ManeuverKind.ARRIVE
        "merge" -> ManeuverKind.MERGE
        "fork" -> ManeuverKind.FORK
        "on ramp", "off ramp" -> ManeuverKind.RAMP
        "roundabout", "rotary", "roundabout turn" -> ManeuverKind.ROUNDABOUT
        "exit roundabout", "exit rotary" -> ManeuverKind.ROUNDABOUT_EXIT
        "turn", "new name", "continue", "end of road", "notification" -> turn
        else -> {
            // Un type inconnu n'est pas jeté : le moteur en publie de nouveaux
            // au fil de ses versions, et un bandeau muet à un carrefour est
            // pire qu'un bandeau générique. Il ne devient UNKNOWN que
            // lorsqu'on n'a **ni** côté annoncé **ni** angle mesuré.
            if (m == null && !hasBearings(bearingBefore, bearingAfter)) ManeuverKind.UNKNOWN else turn
        }
    }
}

/**
 * L'angle réellement parcouru, ramené à un côté.
 *
 * Les seuils sont ceux d'OSRM, repris pour que la mesure et le moteur
 * parlent la même langue : les comparer sur deux échelles ferait diverger
 * les deux à chaque carrefour un peu ouvert, et l'arbitrage n'aurait plus
 * de sens.
 */
fun measuredTurnOf(bearingBefore: Double, bearingAfter: Double): ManeuverKind {
    val angle = GeoMath.shortestHeadingDelta(bearingBefore, bearingAfter)
    val magnitude = abs(angle)
    val right = angle > 0
    return when {
        magnitude < 10 -> ManeuverKind.STRAIGHT
        magnitude < 50 -> if (right) ManeuverKind.SLIGHT_RIGHT else ManeuverKind.SLIGHT_LEFT
        magnitude < 130 -> if (right) ManeuverKind.RIGHT else ManeuverKind.LEFT
        magnitude < 170 -> if (right) ManeuverKind.SHARP_RIGHT else ManeuverKind.SHARP_LEFT
        else -> ManeuverKind.U_TURN
    }
}

private fun hasBearings(before: Double?, after: Double?): Boolean =
    before != null && after != null && before.isFinite() && after.isFinite()

/** Ce que le modificateur d'une manœuvre **veut dire**, selon son type. */
private enum class ModifierMeaning {
    /** Un geste de volant : il décrit un angle, la mesure peut le contredire. */
    STEERING,

    /** Un côté de chaussée : il décrit une position, pas un angle. */
    LATERAL,

    /** Rien qu'on affiche : un giratoire porte un numéro de sortie, un départ ne tourne pas. */
    MUTE,
}

private fun meaningOf(type: String): ModifierMeaning = when (type) {
    "turn", "end of road", "continue", "new name", "notification" -> ModifierMeaning.STEERING
    "fork", "merge", "on ramp", "off ramp" -> ModifierMeaning.LATERAL
    "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary",
    "depart", "arrive",
    -> ModifierMeaning.MUTE
    // Un type inconnu est traité comme un geste de volant : pour lui, la
    // mesure est le seul moyen de savoir si le côté annoncé veut dire
    // quelque chose.
    else -> ModifierMeaning.STEERING
}

private fun turnOf(
    type: String,
    modifier: String?,
    bearingBefore: Double?,
    bearingAfter: Double?,
): ManeuverKind {
    val engine = fromModifier(modifier)
    if (!hasBearings(bearingBefore, bearingAfter)) return engine
    val measured = measuredTurnOf(bearingBefore!!, bearingAfter!!)
    return when (meaningOf(type)) {
        ModifierMeaning.STEERING -> reconciled(engine, measured, hasModifier = modifier != null)
        // ⚠️ **Une bretelle et une insertion gardent le côté du moteur, même
        // contredit.** Leur modificateur ne décrit pas un angle de volant mais
        // de quel côté de la chaussée on se tient : on « reste à droite » dans
        // une fourche dont les deux branches partent à moins de cinq degrés
        // l'une de l'autre. La mesure ne parle donc que dans le silence.
        ModifierMeaning.LATERAL -> if (modifier != null) engine else measured
        ModifierMeaning.MUTE -> engine
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
 * L'arbitrage proprement dit, sur l'axe gauche/droite.
 *
 * **Tolérance d'un cran**, et pas zéro : le moteur voit le carrefour, la
 * mesure ne voit que deux caps. Un virage que le moteur dit « franc » et que
 * la mesure classe « léger » est un désaccord de vocabulaire, pas une erreur
 * de direction — et le moteur, qui sait combien de branches partent du
 * carrefour, a de meilleures raisons que nous de trancher. Au-delà d'un cran,
 * ce n'est plus le même geste, et c'est la mesure qui gagne.
 */
private fun reconciled(
    engine: ManeuverKind,
    measured: ManeuverKind,
    hasModifier: Boolean,
): ManeuverKind {
    // Sans modificateur — ou avec un mot qu'on n'a pas su lire —, il n'y a
    // rien à arbitrer : la mesure est tout ce qu'on a.
    if (!hasModifier || engine == ManeuverKind.UNKNOWN) return measured
    // ⚠️ **Un demi-tour annoncé est toujours cru.** Le moteur le publie quand
    // la route se referme sur elle-même, une configuration où les deux caps se
    // ressemblent trop pour qu'on les départage — un demi-tour par un
    // rond-point d'échangeur mesure trente degrés. Le contredire ferait rater
    // la seule manœuvre qu'on ne peut pas rattraper.
    if (engine == ManeuverKind.U_TURN) return ManeuverKind.U_TURN
    // Réciproquement, un demi-tour **mesuré** ne se range pas parmi les
    // virages : à plus de cent soixante-dix degrés, aucun autre mot ne décrit
    // ce qu'on fait.
    if (measured == ManeuverKind.U_TURN) return ManeuverKind.U_TURN
    val engineRank = axisRank(engine) ?: return measured
    val measuredRank = axisRank(measured) ?: return engine
    return if (abs(engineRank - measuredRank) <= 1) engine else measured
}

/**
 * La place d'un côté sur l'axe gauche → droite, de −3 à +3.
 *
 * Elle n'existe **que** pour mesurer un désaccord en nombre de crans. Le
 * demi-tour n'y a pas de rang utile — il n'est pas « plus à droite » que
 * [ManeuverKind.SHARP_RIGHT], il est ailleurs —, et [reconciled] le traite
 * avant d'en arriver ici.
 */
private fun axisRank(kind: ManeuverKind): Int? = when (kind) {
    ManeuverKind.SHARP_LEFT -> -3
    ManeuverKind.LEFT -> -2
    ManeuverKind.SLIGHT_LEFT -> -1
    ManeuverKind.STRAIGHT -> 0
    ManeuverKind.SLIGHT_RIGHT -> 1
    ManeuverKind.RIGHT -> 2
    ManeuverKind.SHARP_RIGHT -> 3
    else -> null
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
            kind = maneuverKindOf(
                type = maneuver.instruction,
                modifier = maneuver.modifier,
                bearingBefore = maneuver.bearingBefore,
                bearingAfter = maneuver.bearingAfter,
            ),
            t = match.t,
            streetName = maneuver.streetName?.trim()?.takeIf { it.isNotEmpty() },
        )
        floor = match.t
    }
    return out
}

/**
 * Ce qui ne s'annonce jamais : le départ, qui est derrière dès le premier
 * mètre, et la sortie de giratoire, dont l'entrée a déjà tout dit.
 */
private val notAhead = setOf(ManeuverKind.DEPART, ManeuverKind.ROUNDABOUT_EXIT)

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
