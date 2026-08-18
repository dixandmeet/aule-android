package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Duration
import java.time.Instant

/**
 * D'où vient la position d'un véhicule.
 *
 * La distinction est le cœur du produit : un véhicule *mesuré* et un véhicule
 * *calculé depuis l'horaire* ne valent pas la même chose, et l'interface ne doit
 * jamais laisser croire l'un pour l'autre. Un théorique s'affiche en retrait,
 * jamais avec la pastille temps réel.
 */
enum class VehicleFeed {
    /** Position remontée par un véhicule réel. */
    LIVE,

    /** Position déduite de l'horaire théorique et de la forme de la ligne. */
    SCHEDULED;

    companion object {
        fun fromApiValue(value: String?): VehicleFeed =
            if (value?.trim()?.lowercase() == "live") LIVE else SCHEDULED
    }
}

/**
 * Un véhicule de transport en commun.
 *
 * Les champs après [updatedAt] ne sont pas du décor : ce sont eux qui rendent
 * l'interpolation possible. Un véhicule qui ne connaîtrait que sa position
 * sauterait d'un point au suivant toutes les quinze secondes.
 */
data class TransportVehicle(
    val id: String,
    val mode: TransportMode,
    val feed: VehicleFeed,

    /** L'identifiant de ligne côté GTFS (`route_id`) — « 1 », « C3 »… */
    val lineId: String,
    /** Ce qui s'affiche à l'usager. Vaut [lineId] tant que l'API n'envoie rien d'autre. */
    val lineName: String,
    val destination: String? = null,

    val coordinate: Coordinate,
    val headingDegrees: Double = 0.0,
    val updatedAt: Instant? = null,

    /** Taux de remplissage, de 0 à 1, quand le réseau le publie. */
    val occupancy: Double? = null,

    // --- Ce qui permet de faire glisser le véhicule ---

    /** Où il sera à la fin de l'horizon annoncé par le serveur. */
    val ahead: Coordinate? = null,

    /**
     * Une courte polyligne de la position actuelle jusqu'à [ahead], épousant la voie.
     *
     * Suivre le tracé plutôt que la corde est ce qui empêche un tram de couper à
     * travers les immeubles dans les virages.
     */
    val trajectory: List<Coordinate> = emptyList(),

    val speedMps: Double? = null,

    /** Où, le long de [trajectory], se trouve le prochain arrêt — de 0 à 1. */
    val stopProgress: Double? = null,

    /** Combien de temps il reste à quai. Pendant ce temps, il ne glisse pas. */
    val dwellSeconds: Double = 0.0,

    val nextStop: String? = null,
    val etaSeconds: Double? = null,

    /**
     * Le véhicule théorique que celui-ci remplace. Sert à ne pas faire clignoter
     * la carte quand une position mesurée arrive enfin pour un tram jusque-là
     * calculé.
     */
    val twinId: String? = null,
) {
    val isLive: Boolean get() = feed == VehicleFeed.LIVE

    /**
     * Il est immobile — **mesuré** immobile.
     *
     * Surtout pas [dwellSeconds] : le serveur y met la même valeur pour tout le
     * monde (cinq secondes, relevé fait sur la flotte entière), parce que c'est
     * un paramètre de la glisse — le temps pendant lequel la carte retient le
     * marqueur au début de chaque horizon — et non l'observation d'un véhicule
     * à quai. S'en servir affichait « À l'arrêt » sur chaque fiche, y compris
     * pour un bus que la carte montrait en train de rouler.
     *
     * La vitesse d'un véhicule théorique ne dit rien non plus : elle vient de
     * l'horaire, pas de la route. Seule une position mesurée peut affirmer
     * qu'il ne bouge pas.
     */
    val isStopped: Boolean
        get() = isLive && speedMps?.takeIf { it.isFinite() }?.let { it < MOVING_SPEED_MPS } == true

    /**
     * Le palier de remplissage, ou rien si le réseau ne publie pas la charge.
     *
     * Une valeur hors de [0, 1] est un capteur qui déraille, pas un véhicule
     * plein à 300 % : on préfère ne rien dire.
     */
    val load: VehicleLoad?
        get() {
            val ratio = occupancy?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: return null
            return when {
                ratio >= LOAD_FULL -> VehicleLoad.FULL
                ratio >= LOAD_BUSY -> VehicleLoad.BUSY
                ratio >= LOAD_STEADY -> VehicleLoad.STEADY
                else -> VehicleLoad.QUIET
            }
        }

    /**
     * La vitesse en km/h, quand elle est **mesurée** et qu'il avance vraiment.
     *
     * Réservée aux positions mesurées pour la même raison qu'[isStopped] : la
     * vitesse d'un véhicule théorique est celle de l'horaire, et l'écrire en
     * chiffres la ferait passer pour un relevé.
     *
     * Un bus arrêté au feu remonte rarement zéro pile : afficher « 2 km/h »
     * donnerait du mouvement là où il n'y en a pas, et ferait clignoter le
     * chiffre à chaque sondage.
     */
    val speedKmh: Int?
        get() = if (!isLive) {
            null
        } else {
            speedMps
                ?.takeIf { it.isFinite() && it >= MOVING_SPEED_MPS }
                ?.let { Math.round(it * MPS_TO_KMH).toInt() }
        }

    /**
     * Depuis combien de secondes cette position a été relevée.
     *
     * L'âge est ce qui distingue « il est là » de « il y était ». Une horloge
     * de téléphone en avance sur celle du serveur donnerait un âge négatif,
     * donc un futur : on le ramène à l'instant présent.
     */
    fun positionAgeSeconds(now: Instant): Long? {
        val recorded = updatedAt ?: return null
        return Duration.between(recorded, now).seconds.coerceAtLeast(0L)
    }
}

/**
 * Le monde à bord, en quatre paliers.
 *
 * Le réseau publie un taux continu ; l'afficher tel quel — « 62 % » — donnerait
 * une précision que la mesure n'a pas, puisqu'elle vient d'un comptage de
 * portes ou d'une charge à l'essieu. Quatre paliers disent la seule chose qui
 * se décide dessus : monter dans celui-là, ou attendre le suivant.
 */
enum class VehicleLoad {
    /** Des places assises. */
    QUIET,

    /** Il se remplit, on trouve encore où se mettre. */
    STEADY,

    /** Debout, serré. */
    BUSY,

    /** Il ne prend plus personne. */
    FULL,
}

/** Les seuils des paliers de remplissage, en part de la charge maximale. */
private const val LOAD_STEADY = 0.35
private const val LOAD_BUSY = 0.70

/**
 * En deçà de la charge d'écrasement, un véhicule prend encore du monde : c'est
 * le refus au dernier arrêt qu'on annonce, pas le dernier siège occupé.
 */
private const val LOAD_FULL = 0.92

/** Sous cette vitesse, un véhicule est à l'arrêt, pas lent. */
private const val MOVING_SPEED_MPS = 1.0

private const val MPS_TO_KMH = 3.6

/**
 * L'état de la flotte, sous une forme que l'interface traduit.
 *
 * Structuré plutôt que rédigé, contrairement au proto iOS qui rend directement
 * une phrase : les textes de l'application vivent dans les ressources, et un
 * modèle qui porte du français est un modèle qu'on ne peut pas traduire.
 * L'invariant testable, lui, est le même — l'**ordre** dans lequel les cas se
 * décident.
 */
sealed interface FleetStatus {
    /** Le dernier sondage a échoué. Prime sur tout le reste. */
    data object Stale : FleetStatus

    /** Le sondage a réussi et n'a rien rapporté. */
    data object Empty : FleetStatus

    data class LiveOnly(val count: Int) : FleetStatus

    data class ScheduledOnly(val count: Int) : FleetStatus

    data class Mixed(val live: Int, val scheduled: Int) : FleetStatus
}

/** Ce qu'un sondage de la flotte a rapporté. */
data class FleetSnapshot(
    val vehicles: List<TransportVehicle> = emptyList(),
    val generatedAt: Instant = Instant.EPOCH,
    /**
     * Horizon, en secondes, couvert par [TransportVehicle.ahead] et
     * [TransportVehicle.trajectory]. C'est la durée sur laquelle étaler la glisse.
     */
    val horizonSeconds: Double = 10.0,
    /**
     * Vrai quand le dernier sondage a échoué et qu'on montre encore
     * l'avant-dernier.
     *
     * On garde l'affichage plutôt que de vider la carte, mais on ne prétend
     * **jamais** que la donnée est fraîche.
     */
    val isStale: Boolean = false,
    /** Le serveur signale qu'une de ses deux sources est tombée. */
    val degraded: String? = null,
) {
    val liveCount: Int get() = vehicles.count { it.isLive }
    val scheduledCount: Int get() = vehicles.count { !it.isLive }

    /**
     * L'ordre compte : **« périmé » se décide avant « aucun »**. L'inverse a déjà
     * fait annoncer « Aucun véhicule en vue » pendant une coupure réseau — ce qui
     * est faux, et décourage précisément de réessayer.
     */
    val status: FleetStatus
        get() = when {
            isStale -> FleetStatus.Stale
            vehicles.isEmpty() -> FleetStatus.Empty
            scheduledCount == 0 -> FleetStatus.LiveOnly(liveCount)
            liveCount == 0 -> FleetStatus.ScheduledOnly(scheduledCount)
            else -> FleetStatus.Mixed(liveCount, scheduledCount)
        }

    companion object {
        val EMPTY = FleetSnapshot()
    }
}
