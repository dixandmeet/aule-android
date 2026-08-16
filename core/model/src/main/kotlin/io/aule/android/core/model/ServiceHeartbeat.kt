package io.aule.android.core.model

import java.time.Duration
import java.time.Instant

/**
 * Réponse de `publish_position_with_state` — le heartbeat du conducteur
 * en service.
 *
 * Quand une relève aboutit, le serveur clôt le service du sortant, et la
 * policy RLS refuse dès lors ses insertions de position. Sans ce retour,
 * l'app continuerait de tourner en n'émettant plus rien. En V1 le sortant
 * ne confirme rien : c'est par ici qu'il l'apprend.
 *
 * Les phrases d'écran (« Camille vous relève… ») restent dans
 * `strings.xml` (ADR-011).
 */
data class ServiceHeartbeat(
    val serviceStatus: String,
    val published: Boolean,
    val serverTime: Instant,
    val handover: HandoverSummary? = null,
) {
    val serviceActive: Boolean get() = serviceStatus == "active"

    /** Le service a été soldé côté serveur par une relève aboutie. */
    val handedOver: Boolean
        get() = !serviceActive && handover != null && handover.status.isDone
}

/**
 * Ce que la réponse du serveur implique pour l'écran.
 *
 * Un objet plutôt que quatre booléens rendus séparément : ils se lisent
 * ensemble, et c'est ensemble qu'ils décrivent une situation.
 */
data class HeartbeatVerdict(
    val serviceStillOpen: Boolean,
    val liveHandover: HandoverSummary? = null,
    val announceHandover: Boolean = false,
    val handedOverTo: HandoverSummary? = null,
) {
    /** Le service s'arrête ici, quelle qu'en soit la raison. */
    val stopPublishing: Boolean get() = !serviceStillOpen
}

data class PositionPublishRequest(
    val driverServiceId: String,
    val latitude: Double,
    val longitude: Double,
    val vehicleId: String? = null,
    val speed: Double? = null,
    val heading: Double? = null,
    val accuracy: Double? = null,
)

/** Intervalle de publication, écran allumé. */
val HEARTBEAT_INTERVAL: Duration = Duration.ofSeconds(5)

/**
 * Intervalle de publication en arrière-plan.
 *
 * Trois fois plus espacé : l'information voyageurs reste utile, et la
 * batterie d'un téléphone posé sur un support tient la journée de service.
 */
val HEARTBEAT_BACKGROUND_INTERVAL: Duration = Duration.ofSeconds(15)

/**
 * Au-delà de cet âge, un fix ne se publie plus.
 *
 * Publier une position d'il y a deux minutes, c'est afficher aux voyageurs
 * un bus là où il n'est plus. Mieux vaut un trou dans la trace qu'un
 * mensonge dedans.
 */
val HEARTBEAT_FIX_MAX_AGE: Duration = Duration.ofSeconds(60)

/**
 * Faut-il publier ce fix, maintenant ?
 *
 * L'absence de service n'en fait pas partie : l'appelant écarte ce cas
 * avant d'appeler — il lui faut l'identifiant pour publier.
 */
fun shouldPublishHeartbeat(
    now: Instant,
    fixAt: Instant,
    mocked: Boolean,
    publishInFlight: Boolean,
    serviceClosed: Boolean,
    inBackground: Boolean,
    lastPublishAt: Instant?,
): Boolean {
    if (mocked || publishInFlight || serviceClosed) return false
    if (Duration.between(fixAt, now) > HEARTBEAT_FIX_MAX_AGE) return false
    val interval = if (inBackground) HEARTBEAT_BACKGROUND_INTERVAL else HEARTBEAT_INTERVAL
    return lastPublishAt == null || Duration.between(lastPublishAt, now) >= interval
}

/**
 * Ce que dit un heartbeat, sachant la relève déjà connue.
 *
 * [knownHandoverId] est la relève dont l'écran a déjà parlé. C'est elle
 * qui fait la différence entre « une relève est en cours » et « une
 * relève vient d'être engagée ».
 */
fun readHeartbeat(
    heartbeat: ServiceHeartbeat,
    knownHandoverId: String? = null,
): HeartbeatVerdict {
    val handover = heartbeat.handover
    if (heartbeat.serviceActive) {
        val live = handover?.takeIf { it.status.isLive }
        return HeartbeatVerdict(
            serviceStillOpen = true,
            liveHandover = live,
            announceHandover = live != null && live.id != knownHandoverId,
        )
    }
    return HeartbeatVerdict(
        serviceStillOpen = false,
        liveHandover = handover,
        handedOverTo = if (heartbeat.handedOver) handover else null,
    )
}
