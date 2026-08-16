package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant

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

    val followingWaits: List<Int> get() = waits.drop(1)
}

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
