package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HeartbeatVerdict
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.readHeartbeat
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.shouldPublishHeartbeat
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServiceNoticeKind { HANDED_OVER }

data class ServiceNotice(
    val kind: ServiceNoticeKind,
    val handover: HandoverSummary,
)

data class ServiceUiState(
    val active: ActiveDriverService? = null,
    val isRestoring: Boolean = true,
    val isEnding: Boolean = false,
    val endFailure: DriverServiceFailureKind? = null,
    val liveHandover: HandoverSummary? = null,
    val notice: ServiceNotice? = null,
)

/**
 * Le service ouvert, restauré depuis le serveur.
 *
 * Un service vit côté `driver_services`, pas seulement dans la mémoire du
 * téléphone : au redémarrage on relit, et on refuse d'en ouvrir un second.
 *
 * La publication de position est un consommateur de plus du flux GPS unique.
 * C'est par sa réponse que le sortant apprend qu'une relève a été engagée,
 * puis qu'elle a abouti — et surtout qu'il ne faut pas appeler `endService`
 * après une relève réussie.
 */
class ServiceViewModel(
    private val auth: AuthRepository,
    private val services: DriverServiceRepository,
    private val logger: AuleLogger,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(ServiceUiState())
    val state: StateFlow<ServiceUiState> = _state.asStateFlow()

    private var knownHandoverId: String? = null
    private var lastPublishAt: Instant? = null
    private var publishInFlight = false
    private var serviceClosed = false
    private var inBackground = false

    init {
        restore()
    }

    fun adopt(service: ActiveDriverService) {
        resetHeartbeat()
        _state.value = ServiceUiState(active = service, isRestoring = false)
    }

    fun setInBackground(inBackground: Boolean) {
        this.inBackground = inBackground
    }

    fun onLocationFix(fix: LocationFix?) {
        val active = _state.value.active ?: return
        if (fix == null) return
        val at = now()
        if (!shouldPublishHeartbeat(
                now = at,
                fixAt = Instant.ofEpochMilli(fix.timestampMillis),
                mocked = fix.isMocked,
                publishInFlight = publishInFlight,
                serviceClosed = serviceClosed,
                inBackground = inBackground,
                lastPublishAt = lastPublishAt,
            )
        ) {
            return
        }
        val session = auth.currentSession() ?: return
        lastPublishAt = at
        publishInFlight = true
        val serviceId = active.id
        viewModelScope.launch {
            try {
                val heartbeat = services.publishPosition(
                    session,
                    PositionPublishRequest(
                        driverServiceId = serviceId,
                        latitude = fix.coordinate.latitude,
                        longitude = fix.coordinate.longitude,
                        vehicleId = active.vehicleId,
                        speed = fix.speedMetersPerSecond.takeIf { it >= 0.0 },
                        heading = (fix.courseDegrees ?: fix.stabilizedHeading)
                            ?.takeIf { it >= 0.0 },
                        accuracy = fix.accuracyMeters.takeIf {
                            it.isFinite() && it >= 0.0
                        },
                    ),
                )
                if (_state.value.active?.id != serviceId) return@launch
                applyVerdict(readHeartbeat(heartbeat, knownHandoverId = knownHandoverId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Publication de position impossible.", failure)
            } finally {
                publishInFlight = false
            }
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    fun end() {
        val active = _state.value.active ?: return
        if (_state.value.isEnding) return
        val session = auth.currentSession()
        if (session == null) {
            _state.value = _state.value.copy(
                endFailure = DriverServiceFailureKind.NOT_SIGNED_IN,
            )
            return
        }
        _state.value = _state.value.copy(isEnding = true, endFailure = null)
        viewModelScope.launch {
            try {
                services.endService(session, active.id)
                serviceClosed = true
                _state.value = ServiceUiState(isRestoring = false)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isEnding = false)
                throw cancelled
            } catch (failure: DriverServiceException) {
                logger.warn(LogDomain.NET, "Clôture de service refusée.", failure)
                _state.value = _state.value.copy(isEnding = false, endFailure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Clôture de service impossible.", failure)
                _state.value = _state.value.copy(
                    isEnding = false,
                    endFailure = DriverServiceFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun clearEndFailure() {
        _state.value = _state.value.copy(endFailure = null)
    }

    private fun applyVerdict(verdict: HeartbeatVerdict) {
        if (verdict.liveHandover?.id != knownHandoverId) {
            _state.value = _state.value.copy(liveHandover = verdict.liveHandover)
        }
        knownHandoverId = verdict.liveHandover?.id
        if (!verdict.stopPublishing) return
        serviceClosed = true
        val handedOverTo = verdict.handedOverTo
        _state.value = if (handedOverTo != null) {
            ServiceUiState(
                isRestoring = false,
                notice = ServiceNotice(ServiceNoticeKind.HANDED_OVER, handedOverTo),
            )
        } else {
            ServiceUiState(isRestoring = false)
        }
    }

    private fun resetHeartbeat() {
        knownHandoverId = null
        lastPublishAt = null
        publishInFlight = false
        serviceClosed = false
    }

    private fun restore() {
        val session = auth.currentSession()
        if (session == null) {
            _state.value = ServiceUiState(isRestoring = false)
            return
        }
        viewModelScope.launch {
            try {
                val active = services.fetchActiveService(session)
                val labelled = if (active == null || active.lineLabel.isNotBlank()) {
                    active
                } else {
                    val lines = runCatching { services.fetchLines(session) }.getOrDefault(emptyList())
                    val line = lines.find { it.id == active.lineId }
                    val terminus = line?.directions
                        ?.find { it.id == active.directionId }
                        ?.terminus
                        ?.ifBlank { active.terminus }
                        ?: active.terminus
                    active.copy(
                        lineLabel = line?.label ?: active.lineId,
                        terminus = terminus,
                    )
                }
                resetHeartbeat()
                _state.value = ServiceUiState(active = labelled, isRestoring = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Reprise du service impossible.", failure)
                _state.value = ServiceUiState(isRestoring = false)
            }
        }
    }
}
