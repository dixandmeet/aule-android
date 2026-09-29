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
     * L'indice public — « C6 », « 1 », « E311 ». C'est ce que porte un badge.
     *
     * ## ⚠️ Ce n'est plus l'identité
     *
     * Deux réseaux publient le même indice : C2, C4, C6 et C7 sont des Chronobus
     * Naolib **et** des lignes TER, et P2 comme P5 désignent chacun deux lignes
     * TER distinctes. L'identité est [key] — `réseau:MATCH` —, comme sur le web
     * (`lineIndexKey`, `transit-selection.ts`) et dans les tuiles.
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

    /**
     * Les identifiants GTFS que le réseau donne à cette ligne — « ALEOP:309 ».
     *
     * ## ⚠️ Pourquoi l'indice public ne suffit pas
     *
     * Une position **théorique** porte le `route_id` brut du GTFS, et non l'indice affiché sur
     * la caisse. Sur le réseau nantais les deux coïncident (`C1` = `C1`), sur l'interurbain non :
     * `ALEOP:309` s'annonce **E309**, `ALEOP:305AT` s'annonce **305**, et `ALEOP:T5 ESAT`
     * s'annonce **ESAT**. Couper à partir du deux-points ne rattrape donc que le cas facile.
     *
     * Sans cette table, une pastille portait « ALEOP:309 », l'en-tête se coupait en « BUS ·
     * LIGNE AL… » et la ligne restait grise, faute de se reconnaître dans l'index (recette du
     * 18/09/2026, BUG-AND-009).
     *
     * Vide sur les lignes dont l'identifiant **est** l'indice : il n'y a alors rien à traduire.
     *
     * Gardés **dans leur casse d'origine** : c'est sous cette forme que le BFF les cherche en
     * base. La comparaison, elle, se fait sans casse — l'UUID d'une ligne TER arrive tantôt en
     * majuscules, tantôt en minuscules.
     */
    val routeIds: List<String> = emptyList(),

    /**
     * Le champ `match` de l'index, quand il diffère de l'indice — « P2 RENNES - VANNES ».
     *
     * `null` sur presque toutes les lignes : leur clé de jointure **est** l'indice. Il n'existe
     * que pour les homonymes d'un même réseau (deux P2 et deux P5 TER), qui ne se
     * distingueraient autrement ni dans les tuiles ni dans une sélection.
     */
    val tileMatch: String? = null,
) {
    /**
     * Le nom sous lequel les **tuiles** connaissent cette ligne.
     *
     * `build-transit.mjs` pose sur chaque tronçon une propriété `match` : l'indice
     * en majuscules, sauf pour les homonymes qui ont la leur ([tileMatch]). C'est
     * la clé de jointure entre cet index et la géométrie : sans elle, une ligne
     * désignée ici ne désignerait rien là-bas.
     */
    val match: String get() = canonicalLineName(tileMatch?.takeIf { it.isNotBlank() } ?: name)

    /**
     * **L'identité de la ligne** : `réseau:MATCH` — « naolib:C6 », « aleop:C6 »,
     * « aleop:P2 RENNES - VANNES ». Sans réseau connu, MATCH seul.
     *
     * C'est la forme du web (`lineIndexKey`) et celle que la carte compare à
     * `network` + `match` des tuiles : l'indice seul ne suffit pas, C6 est à la
     * fois un Chronobus et un tram-train. À employer partout où une ligne se
     * désigne — clé de liste, sélection, mise en avant, nuancier.
     */
    val key: String get() = transitLineKey(network, match)

    /**
     * Vrai pour les tram-trains de l'étoile nantaise — C6 (Nantes – Clisson) et C7
     * (Nantes – Châteaubriant) **côté ferré** : la source les range parmi les TER,
     * le voyageur les connaît sous ce nom (`formatTransitLineTitle`, web).
     */
    val isTramTrain: Boolean
        get() = network == TransitNetwork.ALEOP && isTramTrainLine(name, mode)

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
            // Le train d'abord : les TER sont rangés dans le réseau Aléop, mais un
            // voyageur ne cherche pas un Nantes – Rennes parmi les cars.
            if (mode == TransportMode.TER) return TransitLineFamily.TER
            // Le réseau décide avant l'indice : « E311 » est un car Aléop, « E1 »
            // une ligne express urbaine, et les deux commencent par la même lettre.
            if (network == TransitNetwork.ALEOP) return TransitLineFamily.INTERURBAN
            return when (mode) {
                TransportMode.TRAM -> TransitLineFamily.TRAM
                TransportMode.BOAT -> TransitLineFamily.NAVIBUS
                TransportMode.TER -> TransitLineFamily.TER
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
        // L'indice public, pas `match` : « P2 » doit trouver les deux P2 TER, y
        // compris celle dont la clé de tuile est « P2 RENNES - VANNES ».
        if (canonicalLineName(name).startsWith(canonicalLineName(needle))) return true
        // Le repli d'accents est celui de la recherche d'arrêts, et il vient du
        // même endroit : « Gétigné » se trouve en tapant « getigne », et deux
        // règles de repli différentes dans la même application finiraient par
        // rendre des résultats différents pour la même frappe.
        val folded = normalizeStopName(needle)
        // Les mots du voyageur pour le train : aucun indice ne s'appelle « TER »,
        // et c'est pourtant ce qu'on tape pour les trouver.
        if (answersModeWord(folded)) return true
        return headsigns.any { normalizeStopName(it).contains(folded) }
    }

    /**
     * Vrai quand ce mot, déjà replié ([normalizeStopName]), désigne le mode de
     * cette ligne : « train », « ter » ou « sncf » pour un TER, « tram-train »
     * pour C6 et C7 côté ferré.
     */
    fun answersModeWord(folded: String): Boolean = when {
        mode == TransportMode.TER && folded in TER_WORDS -> true
        isTramTrain && folded in TRAM_TRAIN_WORDS -> true
        else -> false
    }
}

/** Ce qu'on tape pour trouver un train — déjà repliés par [normalizeStopName]. */
val TER_WORDS: Set<String> = setOf("train", "trains", "ter", "sncf")

/** Et pour un tram-train : le trait d'union devient une espace au repli. */
val TRAM_TRAIN_WORDS: Set<String> = setOf("tram train", "tram trains", "tramtrain")

/** Les indices des tram-trains de l'étoile nantaise, côté ferré. */
private val TRAM_TRAIN_LINES = setOf("C6", "C7")

/**
 * Vrai quand cet indice, porté par un train, est un tram-train — C6 ou C7.
 *
 * Sans le mode, la réponse est non : C6 et C7 sont **aussi** des Chronobus, et
 * un bus ne devient pas un tram-train parce qu'il en partage le numéro.
 */
fun isTramTrainLine(name: String?, mode: TransportMode?): Boolean =
    mode == TransportMode.TER && name != null && canonicalLineName(name) in TRAM_TRAIN_LINES

/**
 * La forme canonique d'un indice de ligne, et **la même que celle du web**
 * (`normalizeLineId`, `transit-selection.ts`) : sans elle, « c6 » et « C6 »
 * désigneraient deux lignes différentes.
 */
fun canonicalLineName(raw: String): String = raw.trim().uppercase()

/**
 * La clé d'une ligne : `réseau:MATCH`, ou MATCH seul sans réseau connu.
 *
 * Port de `lineIndexKey` (`transit-selection.ts`) : le réseau en minuscules, la
 * clé de tuile en majuscules. C'est la forme que les tuiles donnent par
 * `network` + `match`.
 */
fun transitLineKey(network: TransitNetwork?, match: String): String {
    val canonical = canonicalLineName(match)
    return if (network == null) canonical else "${network.apiValue}:$canonical"
}

/**
 * La forme canonique d'une référence de ligne — qualifiée ou nue.
 *
 * Port de `normalizeLineId` : « NAOLIB:c6 » devient « naolib:C6 », « c6 » devient
 * « C6 ». Tout ce qui suit le premier deux-points est la clé de tuile — elle peut
 * en contenir d'autres, comme un `route_id` Aléop. `null` sur une chaîne vide.
 */
fun normalizeTransitLineKey(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val separator = value.indexOf(':')
    if (separator < 0) return value.uppercase()
    val network = value.substring(0, separator).trim().lowercase()
    val match = value.substring(separator + 1).trim().uppercase()
    return "$network:$match"
}

/**
 * Les deux moitiés d'une clé : le réseau (`null` sur une clé nue) et la clé de
 * tuile. C'est ce que le filtre de la carte compare à `network` et `match`.
 *
 * `null` sur une référence vide.
 */
fun splitTransitLineKey(raw: String?): Pair<String?, String>? {
    val key = normalizeTransitLineKey(raw) ?: return null
    val separator = key.indexOf(':')
    if (separator < 0) return null to key
    return key.substring(0, separator) to key.substring(separator + 1)
}

/**
 * La clé d'une ligne connue par son seul numéro, qualifiée par ce qu'on sait
 * d'elle.
 *
 * Port de `qualifyLineId` (`transit-selection.ts`). Le numéro nu désigne toutes
 * les lignes qui le portent, tous réseaux confondus : c'est sans danger partout
 * sauf pour C2, C4, C6 et C7, Chronobus Naolib **et** TER. Un train y est donc
 * qualifié `aleop:`, un bus `naolib:` — les cars Aléop ne partagent aucun numéro.
 */
fun qualifyTransitLineKey(
    line: String?,
    network: TransitNetwork? = null,
    mode: TransportMode? = null,
): String? {
    val value = line?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (':' in value) return normalizeTransitLineKey(value)
    if (network != null) return transitLineKey(network, value)
    if (mode == TransportMode.TER) return transitLineKey(TransitNetwork.ALEOP, value)
    if (canonicalLineName(value) in SHARED_WITH_TER) return transitLineKey(TransitNetwork.NAOLIB, value)
    return canonicalLineName(value)
}

/** Numéros portés à la fois par un Chronobus Naolib et une ligne TER. */
private val SHARED_WITH_TER = setOf("C2", "C4", "C6", "C7")

/**
 * L'inventaire des lignes, indexé pour répondre à « de quelle ligne parle-t-on ? ».
 *
 * ## Pourquoi une règle unique, et pourquoi ici
 *
 * Une référence de ligne arrive sous trois formes : un `route_id` GTFS
 * (« ALEOP:309 », « ALEOP:TER:FR:Line::…: »), une clé qualifiée (« aleop:C6 ») ou
 * un numéro nu (« C6 »). Le dépôt embarqué, son décorateur et les écrans
 * répondaient chacun à leur façon — et le TER C6 ouvrait la fiche du Chronobus.
 * La règle vit donc une fois, ici, pure et éprouvée.
 *
 * ## L'ordre de résolution (le même que l'iPhone)
 *
 * 1. le `route_id` exact, **sans casse** — l'UUID d'un TER arrive dans les deux ;
 * 2. la clé qualifiée `réseau:MATCH` ;
 * 3. le numéro nu : `aleop:` d'abord si l'on sait que c'est un train, `naolib:`
 *    d'abord sinon, puis l'autre réseau, puis la **première** entrée du fichier
 *    qui porte ce nom.
 *
 * Aucun repli d'une clé qualifiée vers le numéro nu : « aleop:C6 » inconnu ne
 * doit jamais rendre le Chronobus.
 *
 * Une clé en double garde **la première** occurrence : une couleur ou une fiche
 * ne doit pas changer d'un build à l'autre au gré de l'ordre du fichier.
 */
class TransitLineLookup(val lines: List<TransitLine>) {

    private val byKey: Map<String, TransitLine> =
        buildMap { lines.forEach { putIfAbsent(it.key, it) } }

    private val byRouteId: Map<String, TransitLine> = buildMap {
        lines.forEach { line -> line.routeIds.forEach { putIfAbsent(canonicalLineName(it), line) } }
    }

    private val byName: Map<String, TransitLine> =
        buildMap { lines.forEach { putIfAbsent(canonicalLineName(it.name), it) } }

    /**
     * La ligne que cette référence désigne, ou `null`.
     *
     * @param mode le mode connu du demandeur, quand il en connaît un : c'est lui
     *   qui départage un numéro nu porté par un bus et par un train.
     */
    fun resolve(reference: String?, mode: TransportMode? = null): TransitLine? {
        val raw = reference?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        byRouteId[canonicalLineName(raw)]?.let { return it }
        val key = normalizeTransitLineKey(raw) ?: return null
        if (':' in key) return byKey[key]
        val networks = if (mode == TransportMode.TER) {
            listOf(TransitNetwork.ALEOP, TransitNetwork.NAOLIB)
        } else {
            listOf(TransitNetwork.NAOLIB, TransitNetwork.ALEOP)
        }
        networks.forEach { network -> byKey[transitLineKey(network, key)]?.let { return it } }
        return byName[key] ?: byKey[key]
    }

    companion object {
        val EMPTY = TransitLineLookup(emptyList())
    }
}

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

    /**
     * Les trains régionaux — TER et tram-trains. Rangés par la source dans le
     * réseau Aléop, mais à part des cars : « Aléop — interurbain » ne garde que
     * les cars, comme sur l'iPhone.
     */
    TER,
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
    // Deux homonymes (les deux P2 TER) se départagent par leur clé : sans elle,
    // leur ordre dépendrait de celui du fichier.
    compareNatural(left.name, right.name).takeIf { it != 0 } ?: compareNatural(left.key, right.key)
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
            routeIds = runCatching {
                obj["routes"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
                    .orEmpty()
            }.getOrDefault(emptyList()),
            // Absent sur presque toutes les lignes : la clé de tuile est alors l'indice.
            tileMatch = obj.text("match")?.takeIf { it.isNotBlank() },
        )
    }
}

/** Un champ texte, ou `null` s'il manque ou n'est pas un texte. */
private fun JsonObject.text(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.contentOrNull?.trim() }.getOrNull()
