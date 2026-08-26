package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Un arrêt ou un quai.
 *
 * Le réseau distingue le **lieu** (« Commerce », qu'on donne en rendez-vous) du
 * **quai** (« Commerce, quai B », où l'on monte). La carte les traite
 * différemment selon le zoom : un pôle d'échange resterait illisible si ses huit
 * quais s'affichaient de loin.
 */
data class TransitStop(
    val id: String,
    val name: String,
    /** Le code exploitant (« OTAG2 »). Absent sur certains arrêts saisis à la main. */
    val code: String? = null,
    val coordinate: Coordinate,
    val mode: TransportMode,
    /** Le lieu auquel ce quai appartient. Vaut [name] quand l'arrêt est déjà un lieu. */
    val stationName: String? = null,
    val isWheelchairAccessible: Boolean = false,
) {
    /**
     * Le nom sous lequel on interroge les passages. L'API des départs travaille
     * par nom de lieu, pas par identifiant de quai.
     */
    val departuresKey: String get() = stationName ?: name
}

/** Un passage annoncé à un arrêt. */
data class StopDeparture(
    val id: String,
    val line: String,
    val lineColor: String? = null,
    val destination: String,
    val expectedAt: Instant,
    /** Vrai quand l'heure vient d'une mesure, faux quand elle vient de l'horaire. */
    val isRealtime: Boolean,
    val mode: TransportMode? = null,
) {
    /**
     * Minutes d'attente à partir de maintenant, **jamais négatives** : un passage
     * en retard d'une seconde afficherait « −0 min ».
     */
    fun waitMinutes(from: Instant): Int =
        Duration.between(from, expectedAt).toMinutes().coerceAtLeast(0).toInt()
}

/**
 * Pourquoi une liste de passages est vide.
 *
 * **404 et 502 ne veulent pas dire la même chose.** Le premier annonce que rien
 * ne circule — la nuit, un dimanche. Le second annonce que le fournisseur temps
 * réel est muet. Les confondre fait dire à l'app qu'il n'y a pas de bus alors
 * qu'elle ne sait pas.
 */
enum class DeparturesOutcome {
    /** Des passages ont été annoncés. */
    ANNOUNCED,

    /** Le réseau a répondu : rien ne passe ici dans les prochaines heures. */
    NOTHING_ANNOUNCED,

    /** Le fournisseur temps réel ne répond pas. L'horaire théorique reste vrai. */
    PROVIDER_SILENT,
}

data class StopDepartures(
    val stopName: String,
    val departures: List<StopDeparture> = emptyList(),
    val outcome: DeparturesOutcome,
    val fetchedAt: Instant,
) {
    /**
     * Regroupe par ligne et destination, en gardant les prochaines attentes.
     *
     * Sans regroupement, un arrêt de tram affiche huit fois « ligne 1 » et
     * l'usager doit lire huit lignes pour apprendre une chose : la 1 passe dans
     * 2, 6 et 11 minutes.
     *
     * L'ordre des rangées suit le prochain passage de chacune : la ligne qui
     * arrive en premier se lit en premier.
     */
    fun grouped(from: Instant, maxPerRow: Int = 3): List<DepartureRow> {
        val order = mutableListOf<String>()
        val rows = mutableMapOf<String, DepartureRow>()

        for (departure in departures.sortedBy { it.expectedAt }) {
            val key = "${departure.line}|${departure.destination}"
            val existing = rows[key]
            if (existing == null) {
                order += key
                rows[key] = DepartureRow(
                    id = key,
                    line = departure.line,
                    lineColor = departure.lineColor,
                    destination = departure.destination,
                    mode = departure.mode,
                    isRealtime = departure.isRealtime,
                    waits = listOf(departure.waitMinutes(from)),
                )
            } else if (existing.waits.size < maxPerRow) {
                rows[key] = existing.copy(waits = existing.waits + departure.waitMinutes(from))
            }
        }
        return order.mapNotNull { rows[it] }
    }
}

/** Une ligne, une destination, et les prochaines attentes — une rangée du tableau. */
data class DepartureRow(
    val id: String,
    val line: String,
    val lineColor: String?,
    val destination: String,
    val mode: TransportMode?,
    val isRealtime: Boolean,
    val waits: List<Int>,
) {
    /**
     * La prochaine attente, et les suivantes. Le modèle rend des **valeurs** ;
     * « à l'approche · 6 · 11 » est le travail de la vue.
     */
    val nextWait: Wait?
        get() = waits.firstOrNull()?.let { minutes ->
            if (minutes == 0) Wait.Approaching else Wait.Minutes(minutes)
        }

    /**
     * Les passages d'après, tant qu'ils aident encore à décider.
     *
     * Au-delà d'une heure, on ne choisit plus entre deux passages : on
     * reviendra, ou on prendra autre chose. Le chiffre ne dit alors plus rien
     * qu'on puisse faire — et il nuit, parce qu'il se lit collé à l'attente
     * utile. Sur la 80, « 20 min » suivi de « 80 · 140 » met sous les yeux un
     * « 80 » qui est aussi le numéro de la ligne.
     *
     * La borne est donc éditoriale et non technique : elle vit ici, avec
     * [NEARBY_LIMIT], parce que « ce qui mérite d'être montré » est une
     * décision du domaine que trois vues ne doivent pas reprendre chacune à sa
     * façon.
     */
    val followingWaits: List<Int>
        get() = waits.drop(1).filter { it < FOLLOWING_WAIT_HORIZON_MINUTES }
}

/** Passé cette attente, un passage n'est plus une option : c'est une autre journée. */
private const val FOLLOWING_WAIT_HORIZON_MINUTES = 60

/**
 * Combien de temps avant le prochain passage.
 *
 * Zéro minute n'est pas « 0 min » : ça se lit comme une panne d'affichage.
 * C'est une approche, et le modèle le dit.
 */
sealed interface Wait {
    data object Approaching : Wait
    data class Minutes(val minutes: Int) : Wait
}

/**
 * Une ligne qui dessert un arrêt, indépendamment de l'heure.
 *
 * Complète les passages : la nuit, un arrêt n'annonce rien mais dessert toujours
 * les mêmes lignes, et c'est cette information-là qui reste utile.
 */
data class ServingLine(
    val line: String,
    val direction: String,
    val lineColor: String? = null,
    val mode: TransportMode? = null,
) {
    val id: String get() = "$line|$direction"
}

/** Un lieu rendu par le géocodeur. */
data class Place(
    val label: String,
    val coordinate: Coordinate,
    val stopMode: TransportMode? = null,
)

/**
 * Le catalogue d'arrêts, tel qu'il se pose sur le disque.
 *
 * Écrit à la main plutôt que par `@Serializable` : le modèle reste sans
 * annotation — c'est ce qui lui permet de vivre dans un module que la
 * sérialisation ne traverse pas — et le format du fichier devient une décision
 * visible, pas un effet de bord d'un nom de champ.
 *
 * Les clés sont **courtes**. 2 635 arrêts avec des clés en toutes lettres pèsent
 * un tiers de plus, pour un fichier que personne ne lit à l'œil.
 */
fun List<TransitStop>.encodeCatalog(): String = buildJsonArray {
    forEach { stop ->
        addJsonObject {
            put("i", stop.id)
            put("n", stop.name)
            stop.code?.let { put("c", it) }
            put("y", stop.coordinate.latitude)
            put("x", stop.coordinate.longitude)
            put("m", stop.mode.name)
            stop.stationName?.let { put("s", it) }
            if (stop.isWheelchairAccessible) put("a", true)
        }
    }
}.toString()

/**
 * Relit le catalogue. Une entrée illisible est **sautée**, pas fatale.
 *
 * ⚠️ Un catalogue vide et un catalogue illisible se répondent de la même façon —
 * une liste vide — et c'est l'appelant qui décide ce qu'elle vaut. Le décorateur
 * de cache, lui, refuse de **servir** une liste vide : au lancement suivant elle
 * passerait pour un cache valide, et l'application n'aurait plus un seul arrêt
 * sans qu'aucune erreur ne le dise.
 */
fun decodeStopCatalog(raw: String?): List<TransitStop> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            val id = obj["i"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val name = obj["n"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val lat = obj["y"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
            val lng = obj["x"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
            val mode = obj["m"]?.jsonPrimitive?.contentOrNull
                ?.let { value -> TransportMode.entries.firstOrNull { it.name == value } }
                ?: return@runCatching null
            TransitStop(
                id = id,
                name = name,
                code = obj["c"]?.jsonPrimitive?.contentOrNull,
                coordinate = Coordinate(latitude = lat, longitude = lng),
                mode = mode,
                stationName = obj["s"]?.jsonPrimitive?.contentOrNull,
                isWheelchairAccessible = obj["a"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }.getOrNull()
    }
}
