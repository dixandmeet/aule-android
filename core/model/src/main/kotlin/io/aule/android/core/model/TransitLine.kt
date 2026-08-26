package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Une ligne du réseau, telle que l'index embarqué la décrit.
 *
 * ## Pourquoi ce type existe
 *
 * `transit-lines-index.json` porte de quoi répondre à « quelles lignes existent,
 * et où passent-elles » : mode, réseau, terminus, cadre géographique. C'est
 * l'inventaire du réseau, **hors ligne et sans requête**, dans 23 Ko déjà
 * présents dans les assets.
 *
 * C'est aussi le seul inventaire dont l'application dispose sans réseau : le
 * catalogue d'arrêts dit ce qui est desservi, la flotte dit ce qui roule, et ni
 * l'un ni l'autre ne dit ce qui **existe**.
 *
 * Type **pur** : ni MapLibre ni Compose, donc entièrement vérifiable.
 * Port de `Native/Aule/Models/TransitLine.swift`.
 */
data class TransitLine(
    /**
     * L'indice public — « C6 », « 1 », « E311 ». C'est l'identité : le réseau ne
     * publie pas deux lignes du même nom, et c'est aussi ce que porte un badge.
     */
    val name: String,

    /**
     * La couleur GTFS, dans la forme qu'attend `LineBadge` — « #RRGGBB ».
     *
     * `null` **est une réponse** : le badge garde alors son gris, qui dit « on ne
     * sait pas » plutôt que d'inventer une teinte.
     */
    val colorHex: String? = null,

    /**
     * `null` quand l'index annonce un mode que l'application ne sait pas peindre
     * — le rang garde alors son badge sans glyphe, ce qui vaut mieux qu'un mode
     * choisi au hasard.
     */
    val mode: TransportMode? = null,

    /** Sous quelle marque la ligne circule. `null` sur une valeur inconnue. */
    val network: TransitNetwork? = null,

    /** Les terminus annoncés — un par sens, davantage sur une ligne à branches. */
    val headsigns: List<String> = emptyList(),

    /**
     * Le cadre du tracé, mesuré au build des tuiles.
     *
     * **C'est lui qui permet d'emmener la carte sur une ligne sans charger sa
     * géométrie** : les 2 715 tronçons vivent dans les tuiles, pas ici. `null`
     * quand l'index n'en porte pas — la ligne se peint quand même, elle ne se
     * cadre pas.
     */
    val bounds: TransitLineBounds? = null,
) {
    /**
     * Le nom sous lequel les **tuiles** connaissent cette ligne.
     *
     * `build-transit.mjs` pose sur chaque tronçon une propriété `match`, qui est
     * l'indice en majuscules. C'est la clé de jointure entre cet index et la
     * géométrie : sans elle, une ligne désignée ici ne désignerait rien là-bas.
     */
    val match: String get() = canonicalLineName(name)

    /**
     * La famille de cette ligne.
     *
     * ⚠️ **Le busway n'en a pas.** Les lignes 4 et 5 sont du bus pour le GTFS, et
     * seule la géométrie OSM les distingue (`osmType`, lu au build des tuiles) —
     * l'index, lui, ne porte pas ce champ. Elles se rangent donc avec les bus, ce
     * qui est faux sur les plans du réseau et honnête vis-à-vis de la donnée
     * qu'on a.
     */
    val family: TransitLineFamily
        get() {
            // Le réseau décide avant l'indice : « E311 » est un car Aléop, « E1 »
            // une ligne express urbaine, et les deux commencent par la même lettre.
            if (network == TransitNetwork.ALEOP) return TransitLineFamily.INTERURBAN
            return when (mode) {
                TransportMode.TRAM -> TransitLineFamily.TRAM
                TransportMode.BOAT -> TransitLineFamily.NAVIBUS
                TransportMode.BUS, null -> when {
                    isLettered('C') -> TransitLineFamily.CHRONOBUS
                    isLettered('E') -> TransitLineFamily.EXPRESS
                    else -> TransitLineFamily.BUS
                }
            }
        }

    /**
     * Vrai quand l'indice est une lettre suivie de chiffres, et **rien d'autre** :
     * « C6 » oui, « C » ni « NGG » non. C'est la règle de `rankFor`, écrite sans
     * expression régulière parce qu'elle tient en une ligne et se lit mieux ainsi.
     */
    private fun isLettered(letter: Char): Boolean {
        val canonical = match
        if (canonical.firstOrNull() != letter) return false
        val digits = canonical.drop(1)
        return digits.isNotEmpty() && digits.all { it.isDigit() }
    }

    /**
     * Vrai quand la requête désigne cette ligne.
     *
     * **L'indice se cherche par le début, les terminus par le contenu**, et la
     * nuance décide de ce que rend une frappe : « 3 » cherché dans le contenu
     * remonterait les trente-neuf lignes qui portent un 3 quelque part, dont le
     * tram 3 noyé au milieu. Un terminus, lui, se retient rarement par son
     * premier mot — on tape « beaujoire », pas « la beaujoire ».
     *
     * Une requête vide retient tout : c'est l'état au repos du volet, pas un
     * filtre.
     */
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        if (match.startsWith(canonicalLineName(needle))) return true
        // Le repli d'accents est celui de la recherche d'arrêts, et il vient du
        // même endroit : « Gétigné » se trouve en tapant « getigne », et deux
        // règles de repli différentes dans la même application finiraient par
        // rendre des résultats différents pour la même frappe.
        val folded = normalizeStopName(needle)
        return headsigns.any { normalizeStopName(it).contains(folded) }
    }
}

/**
 * La forme canonique d'un indice de ligne, et **la même que celle du web**
 * (`normalizeLineId`, `transit-selection.ts`) : sans elle, « c6 » et « C6 »
 * désigneraient deux lignes différentes.
 */
fun canonicalLineName(raw: String): String = raw.trim().uppercase()

/** Le cadre d'un tracé, en coordonnées. */
data class TransitLineBounds(
    val southWest: Coordinate,
    val northEast: Coordinate,
)

/**
 * Lit un `bbox` GeoJSON — **ouest, sud, est, nord**, dans cet ordre.
 *
 * L'ordre est celui de la spécification, et c'est le même piège que partout
 * ailleurs dans l'app : une inversion ne lève pas, elle cadre la carte au large
 * de l'Afrique. D'où le contrôle de validité plutôt qu'une confiance faite au
 * fichier.
 *
 * ## Ce que le contrôle attrape, et ce qu'il n'attrape pas
 *
 * Il attrape une valeur hors bornes et **des coins donnés à l'envers** — nord
 * avant sud, est avant ouest. Il n'attrape **pas** une transposition
 * latitude/longitude dont les deux valeurs restent dans les bornes : sur Nantes,
 * une longitude de -1,5 devient une latitude parfaitement légale. Cette
 * erreur-là ne se voit qu'en regardant *où* tombent les cadres, et c'est ce que
 * fait le test qui lit le vrai fichier.
 */
fun transitLineBoundsFromGeoJson(box: List<Double>): TransitLineBounds? {
    if (box.size != 4) return null
    val southWest = Coordinate(latitude = box[1], longitude = box[0])
    val northEast = Coordinate(latitude = box[3], longitude = box[2])
    if (!southWest.isValidCoordinate() || !northEast.isValidCoordinate()) return null
    if (southWest.latitude > northEast.latitude) return null
    if (southWest.longitude > northEast.longitude) return null
    return TransitLineBounds(southWest = southWest, northEast = northEast)
}

private fun Coordinate.isValidCoordinate(): Boolean =
    latitude in -90.0..90.0 && longitude in -180.0..180.0

/**
 * Sous quelle marque une ligne circule.
 *
 * Deux réseaux se superposent sur le même territoire, et ils ne répondent pas à
 * la même question : l'un dessert l'agglomération toutes les dix minutes,
 * l'autre relie des communes à cent kilomètres quelques fois par jour. Les mêler
 * dans une seule liste ferait chercher un bus urbain au milieu de vingt-neuf
 * cars départementaux.
 */
enum class TransitNetwork(val apiValue: String) {
    /** Le réseau urbain — celui de la carte, des véhicules suivis et des passages. */
    NAOLIB("naolib"),

    /**
     * L'interurbain régional : les cars Aléop, dont l'app ne suit ni la flotte ni
     * les horaires. Leurs tracés, eux, sont dans les tuiles.
     */
    ALEOP("aleop"),
    ;

    companion object {
        fun fromApiValue(value: String?): TransitNetwork? =
            entries.firstOrNull { it.apiValue == value?.trim()?.lowercase() }
    }
}

/**
 * Ce sous quoi le réseau range une ligne — et donc ce sous quoi on la cherche.
 *
 * **Dérivée, jamais stockée.** L'index ne porte pas de famille : il porte un
 * mode, un réseau et un indice, dont `build-transit.mjs` tire déjà son rang de
 * densité (`rankFor`). La règle est donc reprise ici plutôt qu'ajoutée à la
 * donnée, où elle aurait pu diverger.
 *
 * L'ordre des cas est celui de la lecture, et il n'est pas décoratif : c'est
 * l'ordre des sections du volet. Le structurant d'abord — ce qu'on prend sans y
 * penser —, l'interurbain en dernier — ce qu'on cherche en le sachant.
 */
enum class TransitLineFamily {
    TRAM,
    NAVIBUS,
    CHRONOBUS,
    EXPRESS,
    BUS,
    INTERURBAN,
}

/**
 * Le réseau rangé par familles, prêt à être lu rang par rang.
 *
 * Cent trente-huit lignes d'un bloc ne se lisent pas — c'est le même constat qui
 * range les tracés en trois paliers de densité sur la carte. Ici, ce n'est pas la
 * densité qui trie mais la façon dont on cherche : on cherche « le tram 1 » ou
 * « un Chronobus », jamais « la quarante-septième ligne du réseau ».
 *
 * Pur, comme [NearbyDigest] : la vue n'a rien à trier.
 */
data class NetworkLinesDigest(
    val sections: List<Section>,
) {
    data class Section(
        val family: TransitLineFamily,
        val lines: List<TransitLine>,
    )

    val isEmpty: Boolean get() = sections.isEmpty()

    /** Combien de lignes en tout — ce que le volet annonce sous son titre. */
    val count: Int get() = sections.sumOf { it.lines.size }

    companion object {
        /**
         * @param query ce qui a été tapé, vide au repos. Une famille dont plus
         *   aucune ligne ne répond **disparaît** : une section vide sous un
         *   en-tête se lit comme une panne.
         */
        fun build(lines: List<TransitLine>, query: String = ""): NetworkLinesDigest {
            val kept = lines.filter { it.matches(query) }
            val sections = TransitLineFamily.entries.mapNotNull { family ->
                val members = kept
                    .filter { it.family == family }
                    .sortedWith(TRANSIT_LINE_ORDER)
                if (members.isEmpty()) null else Section(family, members)
            }
            return NetworkLinesDigest(sections)
        }
    }
}

/**
 * Le tri « comme le Finder » : sans lui, « 10 » se rangerait avant « 2 », et une
 * liste de lignes numérotées qu'on ne peut pas parcourir de l'œil n'est plus une
 * liste, c'est une fouille.
 *
 * Kotlin n'a pas l'équivalent de `localizedStandardCompare` : la comparaison se
 * fait donc par morceaux, les suites de chiffres comparées comme des nombres et
 * le reste comme du texte. « C1 » < « C6 » < « C20 », « E1 » avant « E311 ».
 */
private val TRANSIT_LINE_ORDER = Comparator<TransitLine> { left, right ->
    compareNatural(left.name, right.name)
}

internal fun compareNatural(left: String, right: String): Int {
    var i = 0
    var j = 0
    while (i < left.length && j < right.length) {
        val a = left[i]
        val b = right[j]
        if (a.isDigit() && b.isDigit()) {
            val startI = i
            val startJ = j
            while (i < left.length && left[i].isDigit()) i++
            while (j < right.length && right[j].isDigit()) j++
            // Comparés comme des nombres, pas comme des chaînes : on compare
            // d'abord la longueur une fois les zéros de tête retirés, puis les
            // chiffres. Passer par `toInt` lèverait sur un indice absurdement long.
            val numI = left.substring(startI, i).trimStart('0')
            val numJ = right.substring(startJ, j).trimStart('0')
            if (numI.length != numJ.length) return@compareNatural numI.length - numJ.length
            val digits = numI.compareTo(numJ)
            if (digits != 0) return@compareNatural digits
        } else {
            val letters = a.uppercaseChar().compareTo(b.uppercaseChar())
            if (letters != 0) return@compareNatural letters
            i++
            j++
        }
    }
    return@compareNatural (left.length - i) - (right.length - j)
}

/**
 * Lit l'index embarqué.
 *
 * ⚠️ **Tout est facultatif sauf l'indice**, et c'est une décision de robustesse :
 * cet index est décodé d'un bloc, donc une seule entrée qui lèverait emporterait
 * les 138 autres — et avec elles la couleur de tous les badges de l'application,
 * sans que rien à l'écran ne dise pourquoi. Un champ qu'on ne sait pas lire vaut
 * `null` ; une ligne sans nom, elle, n'est pas une ligne et se saute.
 *
 * Un fichier entièrement illisible rend une liste vide plutôt que de lever :
 * sans index les badges restent gris et lisibles, là où une exception au premier
 * véhicule peint viderait la carte.
 */
fun decodeTransitLineIndex(raw: String?): List<TransitLine> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val name = obj.text("line")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        // Chaque champ se rattrape **séparément**. Les envelopper tous dans un
        // seul `runCatching` faisait perdre la ligne entière pour un `bbox` qui
        // n'était pas un tableau — soit exactement ce que ce décodage cherche à
        // éviter, une entrée abîmée qui en emporte d'autres.
        TransitLine(
            name = name,
            colorHex = obj.text("color")?.takeIf { it.isNotBlank() },
            mode = TransportMode.fromApiValue(obj.text("mode")),
            network = TransitNetwork.fromApiValue(obj.text("network")),
            headsigns = runCatching {
                obj["headsigns"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
                    .orEmpty()
            }.getOrDefault(emptyList()),
            bounds = runCatching {
                obj["bbox"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }
            }.getOrNull()?.let(::transitLineBoundsFromGeoJson),
        )
    }
}

/** Un champ texte, ou `null` s'il manque ou n'est pas un texte. */
private fun JsonObject.text(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull?.trim() }.getOrNull()
