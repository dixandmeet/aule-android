package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
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
}

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
