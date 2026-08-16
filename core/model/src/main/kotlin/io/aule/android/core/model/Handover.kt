package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant

/**
 * Une relève, telle que la table `service_handovers` la connaît.
 *
 * `engaged` tant que la passation n'est pas soldée. Les libellés d'écran
 * restent dans `strings.xml` (ADR-011) : le modèle ne porte que les
 * identifiants et ce que le serveur a écrit.
 */
enum class HandoverStatus {
    ENGAGED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    ;

    val isLive: Boolean get() = this == ENGAGED
    val isDone: Boolean get() = this == COMPLETED
    val isAborted: Boolean get() = this == CANCELLED || this == EXPIRED

    companion object {
        fun fromWire(raw: String?): HandoverStatus = when (raw) {
            "completed" -> COMPLETED
            "cancelled" -> CANCELLED
            "expired" -> EXPIRED
            else -> ENGAGED
        }
    }
}

/**
 * Un service candidat, sans aucune coordonnée.
 *
 * Tant que la relève n'est pas engagée, un n° de train ne doit pas suffire
 * à géolocaliser un collègue. [terminus] est le headsign, déjà débarrassé
 * d'une éventuelle flèche côté DTO.
 */
data class HandoverTarget(
    val serviceId: String,
    val lineId: String,
    val driverDisplay: String? = null,
    val directionId: Int? = null,
    val terminus: String? = null,
    val vehicleId: String? = null,
    val trainNumber: String? = null,
    val startedAt: Instant? = null,
    val lastPositionAt: Instant? = null,
    val positionAgeSeconds: Int? = null,
)

data class HandoverSummary(
    val id: String,
    val status: HandoverStatus,
    val lineId: String,
    val outgoingServiceId: String,
    val incomingServiceId: String? = null,
    val vehicleId: String? = null,
    val outgoingDisplay: String? = null,
    val incomingDisplay: String? = null,
    val reliefStopId: String? = null,
    val reliefStopName: String? = null,
    val reliefStopCoordinate: Coordinate? = null,
    val reliefPlannedAt: Instant? = null,
    val cancelReason: String? = null,
)

/**
 * Relève déjà engagée par l'arrivant, rendue par `handover_active_for_me`.
 *
 * La cible a la même forme qu'une ligne de `handover_lookup` : la reprise
 * repart de l'état qu'aurait produit la recherche.
 */
data class HandoverEngagement(
    val handover: HandoverSummary,
    val target: HandoverTarget,
)

/**
 * Dernier point connu du véhicule relevé.
 *
 * Le serveur ne renvoie jamais l'historique : le releveur a besoin d'une
 * flèche qui bouge, pas du parcours de son collègue depuis le début du
 * service. [isReliable] est faux dès que le point a plus de trente
 * secondes : ce n'est plus une position mesurée à l'instant.
 */
data class HandoverFix(
    val coordinate: Coordinate,
    val recordedAt: Instant,
    val ageSeconds: Int,
    val speed: Double? = null,
    val heading: Double? = null,
    val accuracy: Double? = null,
) {
    val isReliable: Boolean get() = ageSeconds <= RELIABLE_AGE_SECONDS

    companion object {
        const val RELIABLE_AGE_SECONDS = 30
    }
}

/**
 * Réponse de `handover_track` : l'état de la relève et le dernier point.
 */
data class HandoverTrack(
    val handover: HandoverSummary,
    val serverTime: Instant,
    val serviceStatus: String? = null,
    val fix: HandoverFix? = null,
)

enum class HandoverFailureKind {
    NOT_SIGNED_IN,
    NO_DRIVER,
    NOT_CONFIGURED,
    NETWORK,
    LINES_EMPTY,
    TARGET_NOT_ACTIVE,
    CANNOT_RELIEVE_SELF,
    OTHER_NETWORK,
    ALREADY_RELIEVING,
    ALREADY_BEING_RELIEVED,
    ALREADY_ON_SERVICE,
    CLOSED,
    NOT_FOUND,
    ALREADY_COMPLETED,
    NOT_A_PARTY,
    REJECTED,
    JOURNEY_UNAVAILABLE,
    UNKNOWN,
}

class HandoverException(
    val kind: HandoverFailureKind,
) : Exception(kind.name)

/**
 * Le service ouvert par `handover_confirm` pour l'arrivant.
 *
 * `incoming_service_id` manque : la confirmation n'a pas basculé (sortant
 * en V1, ou réponse illisible). L'appelant ne doit alors rien adopter.
 */
fun HandoverSummary.toIncomingService(
    target: HandoverTarget,
    lineLabel: String,
    startedAt: Instant,
): ActiveDriverService? {
    val serviceId = incomingServiceId ?: return null
    return ActiveDriverService(
        id = serviceId,
        lineId = target.lineId.ifEmpty { lineId },
        lineLabel = lineLabel,
        directionId = target.directionId ?: 0,
        terminus = target.terminus.orEmpty(),
        startedAt = startedAt,
        vehicleId = target.vehicleId ?: vehicleId,
        trainNumber = target.trainNumber,
    )
}
