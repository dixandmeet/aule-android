package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.text.Normalizer

/**
 * Combien de suggestions par famille. Au-delà, la liste noie ce qu'on cherchait.
 */
const val SEARCH_LIMIT_PER_KIND = 5

/**
 * En deçà, on n'appelle pas le géocodeur : deux lettres rendent la moitié de
 * la Loire-Atlantique, et l'appel est payé pour rien.
 *
 * Les arrêts, eux, viennent du catalogue déjà chargé et répondent dès la
 * première lettre utile.
 */
const val MIN_PLACE_QUERY_LENGTH = 3

/**
 * Distance en deçà de laquelle deux orthographes désignent le même lieu.
 *
 * « Mairie » existe dans presque chaque commune : les fusionner ferait
 * afficher des lignes qui ne passent pas là. Deux cents mètres tiennent
 * un pôle ; ils ne tiennent pas deux communes.
 */
const val SAME_PLACE_METERS = 200.0

/**
 * Un arrêt qui a répondu à la saisie.
 *
 * Le libellé d'affichage (« Station de tram · 5 quais ») n'est **pas** ici :
 * c'est une phrase, elle vit dans les ressources (ADR-011). Le modèle porte
 * le mode et le nombre de quais, et c'est la vue qui les formule.
 */
data class StopSearchHit(
    val representative: TransitStop,
    val names: List<String>,
    val mode: TransportMode,
    val quays: Int,
) {
    val label: String get() = representative.departuresKey
    val coordinate: Coordinate get() = representative.coordinate
}

/**
 * La recherche d'arrêts, **pure**.
 *
 * Deux sources, jamais fondues : les arrêts viennent du catalogue déjà en
 * mémoire — disponibles sans réseau, dans un tunnel comme en zone blanche.
 * Les adresses viennent du géocodeur, et coûtent un aller-retour. Faire
 * attendre la recherche du réseau sur le géocodeur rendrait la barre moins
 * bonne qu'avant pour gagner les adresses.
 *
 * Port de `SAE/lib/carte_immersive/hud/map_search.dart`.
 */
object StopSearch {

    /**
     * Les arrêts qui répondent à une saisie, du plus pertinent au moins.
     *
     * Trois rangs, et l'ordre compte plus que le score : ce qui **commence**
     * par ce qu'on tape passe avant ce qui commence par le même mot plus loin
     * dans le nom, qui passe avant ce qui le contient quelque part. À rang
     * égal, le lieu qui porte le plus de quais gagne — c'est le pôle, pas
     * l'arrêt de desserte fine qui en reprend le nom.
     */
    fun search(
        catalog: List<TransitStop>,
        query: String,
        limit: Int = SEARCH_LIMIT_PER_KIND,
    ): List<StopSearchHit> {
        val needle = normalizeStopName(query)
        if (needle.isEmpty() || limit <= 0) return emptyList()

        val index = cluster(catalog)
        val matches = index.mapNotNull { entry ->
            val rank = rank(entry.normalized, needle) ?: return@mapNotNull null
            Scored(entry, rank)
        }.sortedWith(
            compareBy<Scored> { it.rank }
                .thenByDescending { it.entry.quays }
                .thenBy { it.entry.normalized.length }
                .thenBy { it.entry.normalized },
        )

        // La fusion se fait sur **toute** la liste avant de la couper :
        // s'arrêter à la limite laisserait dehors une orthographe concurrente
        // du lieu retenu, et le panneau d'arrêt n'aurait que la moitié de
        // ses lignes.
        val merged = mutableListOf<Cluster>()
        for (match in matches) {
            val twin = merged.firstOrNull { candidate ->
                candidate.normalized == match.entry.normalized &&
                    GeoMath.distance(candidate.coordinate, match.entry.coordinate) <= SAME_PLACE_METERS
            }
            if (twin != null) {
                twin.absorb(match.entry)
            } else {
                merged += match.entry.copy()
            }
        }
        return merged.take(limit).map { it.toHit() }
    }

    /**
     * Un lieu, une entrée.
     *
     * Un pôle d'échange compte jusqu'à sept quais du même nom : les énumérer
     * remplirait la liste de « Commerce, Commerce, Commerce » sans rien
     * apprendre. Deux lieux du même nom éloignés restent deux lieux.
     */
    private fun cluster(catalog: List<TransitStop>): List<Cluster> {
        val clusters = mutableListOf<Cluster>()
        for (stop in catalog) {
            val twin = clusters.firstOrNull { candidate ->
                candidate.key == stop.departuresKey &&
                    GeoMath.distance(candidate.coordinate, stop.coordinate) <= SAME_PLACE_METERS
            }
            if (twin != null) {
                twin.absorb(stop)
            } else {
                clusters += Cluster(stop)
            }
        }
        return clusters
    }

    /** `null` quand le nom ne répond pas du tout. */
    private fun rank(name: String, needle: String): Int? {
        if (name.startsWith(needle)) return 0
        if (name.split(' ').any { it.startsWith(needle) }) return 1
        if (name.contains(needle)) return 2
        return null
    }

    private data class Scored(val entry: Cluster, val rank: Int)

    private class Cluster(first: TransitStop) {
        val key: String = first.departuresKey
        val normalized: String = normalizeStopName(first.departuresKey)
        var representative: TransitStop = first
        val names: MutableList<String> = mutableListOf(first.name)
        var mode: TransportMode = first.mode
        var quays: Int = 1
        val coordinate: Coordinate get() = representative.coordinate

        constructor(other: Cluster) : this(other.representative) {
            names.clear()
            names.addAll(other.names)
            mode = other.mode
            quays = other.quays
        }

        fun copy(): Cluster = Cluster(this)

        fun absorb(stop: TransitStop) {
            if (stop.name !in names) names += stop.name
            quays += 1
            mode = mode.mergePreferringRail(stop.mode)
        }

        fun absorb(other: Cluster) {
            for (name in other.names) {
                if (name !in names) names += name
            }
            quays += other.quays
            mode = mode.mergePreferringRail(other.mode)
        }

        fun toHit(): StopSearchHit = StopSearchHit(
            representative = representative,
            names = names.toList(),
            mode = mode,
            quays = quays,
        )
    }
}

/**
 * Nom comparable entre réseaux : accents, casse, tirets et espaces ne
 * comptent pas.
 *
 * C'est la même règle que « autour de vous » côté Flutter : deux libellés
 * pour la même chose se remarquent tout de suite.
 */
fun normalizeStopName(value: String): String {
    val prepared = value.lowercase()
        .replace("œ", "oe")
        .replace("æ", "ae")
    val folded = Normalizer.normalize(prepared, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
    val buffer = StringBuilder(folded.length)
    var lastWasSpace = true
    for (ch in folded) {
        if (ch.isLetterOrDigit()) {
            buffer.append(ch)
            lastWasSpace = false
        } else if (!lastWasSpace) {
            buffer.append(' ')
            lastWasSpace = true
        }
    }
    return buffer.toString().trim()
}

/**
 * Le nom court d'une adresse : ce qui précède la première virgule.
 *
 * Le géocodeur rend « 12 Rue de Strasbourg, Nantes, Loire-Atlantique, France ».
 * Le titre garde la rue, le sous-titre garde tout : deux « Rue de Strasbourg »
 * ne se distinguent que par leur commune.
 */
fun shortPlaceName(label: String): String {
    val trimmed = label.trim()
    val cut = trimmed.indexOf(',')
    if (cut <= 0) return trimmed
    val short = trimmed.substring(0, cut).trim()
    return short.ifEmpty { trimmed }
}

fun Place.shortLabel(): String = shortPlaceName(label)

/**
 * Un lieu desservi par un tram et par un car reste une station de tram.
 * Un lieu desservi par un Navibus et par un bus reste une escale.
 */
internal fun TransportMode.mergePreferringRail(other: TransportMode): TransportMode = when {
    this == TransportMode.TRAM || other == TransportMode.TRAM -> TransportMode.TRAM
    this == TransportMode.BOAT || other == TransportMode.BOAT -> TransportMode.BOAT
    else -> TransportMode.BUS
}

private val COMBINING_MARKS = "\\p{M}+".toRegex()
