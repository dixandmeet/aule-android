package io.aule.android.core.model

import java.time.Instant

/**
 * Un service ouvert — la ligne `driver_services` au statut `active` / `paused`.
 */
data class ActiveDriverService(
    val id: String,
    val lineId: String,
    val lineLabel: String,
    val directionId: Int,
    val terminus: String,
    val startedAt: Instant,
    val vehicleId: String? = null,
    val trainNumber: String? = null,
)

data class ServiceStartRequest(
    val lineId: String,
    val lineLabel: String,
    val directionId: Int,
    val terminus: String,
    val vehicleId: String? = null,
    val trainNumber: String? = null,
)

enum class DriverServiceFailureKind {
    NOT_SIGNED_IN,
    NO_DRIVER,
    ALREADY_ON_SERVICE,
    NOT_CONFIGURED,
    NETWORK,
    LINES_EMPTY,
    REJECTED,
    UNKNOWN,
}

class DriverServiceException(
    val kind: DriverServiceFailureKind,
) : Exception(kind.name)
