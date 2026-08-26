package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.time.Duration
import java.time.Instant

/**
 * Une veille : « préviens-moi quand ce bus-là approche ».
 *
 * C'est le pendant usager de [HandoverAlertEngine], et la ressemblance s'arrête
 * à l'intention. La relève surveille une **position** relue chaque seconde ; une
 * veille d'usager surveille un **temps annoncé**, rafraîchi toutes les trente
 * secondes par le fournisseur temps réel. Les deux mécaniques ne peuvent donc
 * pas partager le même moteur : ce qui protège l'une du bruit ferait arriver
 * l'autre en retard (voir [DepartureWatchEngine]).
 *
 * Une veille désigne une **ligne à un arrêt**, jamais un véhicule : c'est ce
 * qu'on touche dans le tableau des passages, et c'est ce qui reste vrai quand le
 * bus attendu est remplacé par le suivant. Le véhicule, lui, se retrouve après
 * coup et par ressemblance ([matchWatchedVehicle]) — il peut manquer sans que la
 * veille cesse de fonctionner.
 */
data class DepartureWatch(
    /** Le lieu, tel que l'API des départs le connaît — [TransitStop.departuresKey]. */
    val stopName: String,
    val line: String,
    val destination: String,
    val lineColor: String? = null,
    val mode: TransportMode? = null,
    /** Où se trouve l'arrêt, pour reconnaître le véhicule qui s'en approche. */
    val stopCoordinate: Coordinate? = null,
) {
    val id: String get() = "$stopName|$line|$destination"
}

/** Ce qu'une veille annonce. */
enum class DepartureWatchAlertKind {
    /** Le seuil d'anticipation est franchi : il reste de quoi rejoindre l'arrêt. */
    MINUTES_BEFORE,

    /** Il est là. */
    APPROACHING,
}

/**
 * Une alerte à émettre : le genre et les chiffres, jamais la phrase (ADR-011).
 */
data class DepartureWatchAlert(
    val kind: DepartureWatchAlertKind,
    val minutes: Int? = null,
)

/**
 * Décide quand une veille parle.
 *
 * ## Un seul relevé suffit, contrairement à la relève
 *
 * [HandoverAlertEngine] exige deux mesures consécutives sous le seuil : sa
 * source est une position GPS, qui saute. Ici la source est un temps annoncé par
 * le réseau, relu toutes les trente secondes — exiger une confirmation ferait
 * arriver « il approche » une demi-minute après l'approche, c'est-à-dire au
 * moment où le bus repart. On alerte donc au premier relevé qui franchit le
 * seuil, et c'est le **loquet** seul qui empêche la répétition.
 *
 * ## Le loquet se relâche quand le passage change
 *
 * Une veille suit le prochain passage, pas un véhicule identifié : quand le bus
 * attendu est parti, le suivant devient le prochain et l'attente **remonte**.
 * Sans réarmement, une veille ouverte à 18 h se serait tue pour la soirée entière
 * après sa première alerte. Le réarmement ne se déclenche pas au moindre
 * sursaut — un temps annoncé oscille d'une minute sans que rien ne soit passé —
 * mais au-delà d'une marge qu'aucune oscillation ne franchit.
 *
 * ## Rien ne part d'une donnée périmée
 *
 * Même règle que pour la relève, pour la même raison : annoncer une approche à
 * partir d'un tableau vieux de plusieurs minutes fait manquer le bus, ce qui est
 * pire que se taire.
 */
class DepartureWatchEngine(
    /** Combien de minutes avant le passage on veut être prévenu. */
    val minutesBefore: Int = DEFAULT_MINUTES_BEFORE,
) {
    init {
        require(minutesBefore >= 1)
    }

    private val fired = mutableSetOf<DepartureWatchAlertKind>()

    fun reset() = fired.clear()

    fun hasFired(kind: DepartureWatchAlertKind): Boolean = kind in fired

    /**
     * Les alertes que ce relevé justifie — au plus une par genre, et une seule
     * fois tant que le même passage reste le prochain.
     *
     * @param wait l'attente du prochain passage de la ligne veillée, ou `null`
     *   quand plus rien n'est annoncé. Le silence n'alerte pas : il ne dit pas
     *   que le bus arrive, il dit qu'on ne sait plus.
     * @param fresh vrai si le tableau des passages est assez récent pour qu'on
     *   engage l'usager sur ce qu'il annonce.
     */
    fun evaluate(wait: Wait?, fresh: Boolean): List<DepartureWatchAlert> {
        if (wait == null) return emptyList()
        val minutes = when (wait) {
            Wait.Approaching -> 0
            is Wait.Minutes -> wait.minutes
        }

        // Le passage veillé a changé : ce qui a déjà été annoncé concernait le
        // bus précédent, et le loquet doit repartir pour celui-ci.
        if (minutes > minutesBefore + REARM_MARGIN_MINUTES) reset()

        if (!fresh) return emptyList()

        val alerts = mutableListOf<DepartureWatchAlert>()
        if (minutes <= minutesBefore) {
            arm(DepartureWatchAlertKind.MINUTES_BEFORE, minutes)?.let(alerts::add)
        }
        if (minutes <= 0) {
            arm(DepartureWatchAlertKind.APPROACHING, null)?.let(alerts::add)
        }

        // Une veille armée sur un bus déjà là franchit les deux seuils du même
        // coup. Les annoncer tous les deux, c'est faire vibrer le téléphone
        // deux fois pour un seul fait, et faire lire « dans 0 min » juste avant
        // « à l'approche ». Le loquet du premier reste posé — il a bien été
        // franchi — mais c'est le second qui parle, parce qu'il est plus vrai.
        if (alerts.any { it.kind == DepartureWatchAlertKind.APPROACHING }) {
            alerts.removeAll { it.kind == DepartureWatchAlertKind.MINUTES_BEFORE }
        }
        return alerts
    }

    private fun arm(kind: DepartureWatchAlertKind, minutes: Int?): DepartureWatchAlert? {
        if (!fired.add(kind)) return null
        return DepartureWatchAlert(kind = kind, minutes = minutes)
    }

    companion object {
        /**
         * Le temps qu'il faut pour descendre et traverser. Cinq minutes — le
         * seuil de la relève — sont faites pour rejoindre un quai en véhicule ;
         * un usager qui attend chez lui n'a pas ce trajet à faire, et une alerte
         * trop précoce se range dans la même case mentale qu'une publicité.
         */
        const val DEFAULT_MINUTES_BEFORE = 3

        /**
         * Au-delà de cette remontée, l'attente ne décrit plus le même passage.
         *
         * Assez large pour qu'aucune oscillation du temps réel ne la franchisse,
         * assez étroite pour rattraper le passage suivant sur une ligne fréquente.
         */
        const val REARM_MARGIN_MINUTES = 4
    }
}

/**
 * Au-delà de cet âge, un tableau de passages ne dit plus où en est le bus.
 *
 * Le volet resonde toutes les trente secondes ; trois minutes laissent passer
 * plusieurs échecs — le fournisseur temps réel en a — sans qu'on cesse
 * d'alerter, et coupent net quand la panne s'installe.
 */
const val DEPARTURE_WATCH_STALE_AFTER_SECONDS = 180L

/** Le tableau est-il assez frais pour qu'on engage l'usager sur ce qu'il annonce. */
fun StopDepartures.isFresh(now: Instant): Boolean =
    Duration.between(fetchedAt, now).seconds.coerceAtLeast(0L) <=
        DEPARTURE_WATCH_STALE_AFTER_SECONDS

/**
 * Les passages d'une seule ligne vers une seule destination, dans l'ordre.
 *
 * Le pendant détaillé de [StopDepartures.grouped] : le tableau d'un arrêt résume
 * chaque ligne en trois attentes, cette liste-ci rend la colonne entière parce
 * qu'on l'a demandée. Aucune borne d'horizon ne s'y applique — celle de
 * [DepartureRow.followingWaits] existe pour qu'une rangée ne déborde pas, et
 * l'usager qui ouvre la ligne veut justement voir jusqu'où va la journée.
 */
fun StopDepartures.forLine(line: String, destination: String): List<StopDeparture> {
    val wantedDestination = normalizeStopName(destination)
    return departures
        .filter {
            it.line.equals(line, ignoreCase = true) &&
                normalizeStopName(it.destination) == wantedDestination
        }
        .sortedBy { it.expectedAt }
}

/**
 * Le véhicule qui porte le prochain passage veillé, si on le voit.
 *
 * Le flux de flotte n'annonce pas quel véhicule assure quel passage : il faut
 * donc le **reconnaître**, et une reconnaissance qui se trompe est pire que
 * l'absence — la carte suivrait alors un bus qui va ailleurs. Trois filtres
 * successifs, du plus sûr au plus faible :
 *
 * 1. la ligne, qui ne se discute pas ;
 * 2. la destination, quand le flux la publie — un bus qui remonte la ligne dans
 *    l'autre sens porte le même numéro et n'a rien à voir avec l'attente ;
 * 3. le prochain arrêt, qui départage les deux ou trois bus d'une même ligne
 *    présents dans le rayon sondé.
 *
 * À défaut de prochain arrêt, on prend le plus proche de l'arrêt veillé — et
 * seulement s'il est dans un rayon où « il arrive » a encore un sens. Un bus de
 * la bonne ligne à six kilomètres n'est pas celui qu'on attend.
 *
 * Rend `null` plutôt qu'un à-peu-près : la veille fonctionne sans véhicule, elle
 * ne survit pas à un mauvais.
 */
fun matchWatchedVehicle(
    vehicles: List<TransportVehicle>,
    watch: DepartureWatch,
): TransportVehicle? {
    val wantedDestination = normalizeStopName(watch.destination)
    val wantedStop = normalizeStopName(watch.stopName)

    val onTheLine = vehicles.filter { vehicle ->
        vehicle.lineId.equals(watch.line, ignoreCase = true) ||
            vehicle.lineName.equals(watch.line, ignoreCase = true)
    }
    if (onTheLine.isEmpty()) return null

    val heading = onTheLine.filter { vehicle ->
        val destination = vehicle.destination?.let { normalizeStopName(it) }
        destination == null || destination.isEmpty() || destination == wantedDestination
    }
    val candidates = heading.ifEmpty { return null }

    candidates
        .filter { normalizeStopName(it.nextStop.orEmpty()) == wantedStop }
        // Deux bus qui annoncent le même prochain arrêt : celui qui y sera le
        // premier est celui dont l'attente parle.
        .minByOrNull { it.etaSeconds ?: Double.POSITIVE_INFINITY }
        ?.let { return it }

    val stop = watch.stopCoordinate ?: return null
    return candidates
        .map { it to GeoMath.distance(stop, it.coordinate) }
        .filter { (_, meters) -> meters <= WATCH_MATCH_RADIUS_METERS }
        .minByOrNull { (_, meters) -> meters }
        ?.first
}

/**
 * Le rayon dans lequel un véhicule de la bonne ligne peut être celui qu'on attend.
 *
 * Deux kilomètres : à peu près ce qu'un bus urbain parcourt en cinq minutes,
 * donc l'horizon de l'alerte d'anticipation. Au-delà, le rapprochement
 * désignerait un service qui n'a pas encore commencé à venir.
 */
const val WATCH_MATCH_RADIUS_METERS = 2_000.0
