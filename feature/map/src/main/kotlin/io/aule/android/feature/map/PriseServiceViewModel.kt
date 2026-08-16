package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.repository.DriverServiceRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PriseServiceStep {
    LINE,
    DIRECTION,
    TIME,
    TRAIN,
    VEHICLE,
    GPS,
    ;

    val index: Int get() = ordinal
    val isLast: Boolean get() = this == GPS

    fun previous(): PriseServiceStep = entries[ordinal.coerceAtLeast(1) - 1]
    fun next(): PriseServiceStep = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
}

data class PriseServiceUiState(
    val step: PriseServiceStep = PriseServiceStep.LINE,
    val lines: List<ServiceLine> = emptyList(),
    val isLoadingLines: Boolean = true,
    val loadFailure: DriverServiceFailureKind? = null,
    val search: String = "",
    val selectedLineId: String? = null,
    val selectedDirectionKey: String? = null,
    val scheduledDeparture: Instant? = null,
    val trainNumber: String = "",
    val vehicleId: String = "",
    val gpsReady: Boolean = false,
    val isStarting: Boolean = false,
    val startFailure: DriverServiceFailureKind? = null,
    val started: ActiveDriverService? = null,
) {
    val selectedLine: ServiceLine?
        get() = lines.find { it.id == selectedLineId }

    val selectedDirection
        get() = selectedLine?.directions?.find { it.key == selectedDirectionKey }

    val filteredLines: List<ServiceLine>
        get() {
            val query = search.trim()
            if (query.isEmpty()) return lines
            return lines.filter { it.label.contains(query, ignoreCase = true) }
        }

    val canContinue: Boolean
        get() = when (step) {
            PriseServiceStep.LINE -> selectedLineId != null
            PriseServiceStep.DIRECTION -> selectedDirectionKey != null
            PriseServiceStep.GPS -> gpsReady
            else -> true
        }
}

/**
 * L'assistant de prise de service, six étapes.
 *
 * L'heure, le train et le véhicule sont facultatifs : une grille muette ne
 * doit pas retenir un conducteur au dépôt. Le GPS, lui, est exigé — sans
 * position le régulateur ne verrait rien.
 */
class PriseServiceViewModel(
    private val session: AuthSession,
    private val networkId: String?,
    private val services: DriverServiceRepository,
    private val logger: AuleLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(PriseServiceUiState())
    val state: StateFlow<PriseServiceUiState> = _state.asStateFlow()

    init {
        loadLines()
    }

    fun loadLines() {
        _state.value = _state.value.copy(isLoadingLines = true, loadFailure = null)
        viewModelScope.launch {
            try {
                val all = services.fetchLines(session)
                val visible = if (networkId == null) all else all.filter { it.networkId == networkId }
                _state.value = _state.value.copy(
                    lines = visible,
                    isLoadingLines = false,
                    loadFailure = if (visible.isEmpty()) DriverServiceFailureKind.LINES_EMPTY else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DriverServiceException) {
                _state.value = _state.value.copy(isLoadingLines = false, loadFailure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Catalogue de lignes illisible.", failure)
                _state.value = _state.value.copy(
                    isLoadingLines = false,
                    loadFailure = DriverServiceFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun setSearch(query: String) {
        _state.value = _state.value.copy(search = query)
    }

    fun pickLine(id: String) {
        _state.value = _state.value.copy(
            selectedLineId = id,
            selectedDirectionKey = null,
            scheduledDeparture = null,
            step = PriseServiceStep.DIRECTION,
        )
    }

    fun pickDirection(key: String) {
        _state.value = _state.value.copy(
            selectedDirectionKey = key,
            scheduledDeparture = null,
            step = PriseServiceStep.TIME,
        )
    }

    fun setTimeOfDay(hour: Int, minute: Int, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val today = LocalDate.now(zone)
        var at = today.atTime(hour, minute).atZone(zone).toInstant()
        if (at.isBefore(now.minus(2, ChronoUnit.HOURS))) {
            at = at.plus(1, ChronoUnit.DAYS)
        }
        _state.value = _state.value.copy(scheduledDeparture = at)
    }

    fun setTrainNumber(value: String) {
        _state.value = _state.value.copy(trainNumber = value)
    }

    fun setVehicleId(value: String) {
        _state.value = _state.value.copy(vehicleId = value)
    }

    fun setGpsReady(ready: Boolean) {
        _state.value = _state.value.copy(gpsReady = ready)
    }

    fun back(): Boolean {
        val current = _state.value.step
        if (current == PriseServiceStep.LINE) return true
        _state.value = _state.value.copy(step = current.previous(), startFailure = null)
        return false
    }

    fun continueOrStart() {
        val current = _state.value
        if (!current.canContinue || current.isStarting) return
        if (current.step.isLast) {
            start()
        } else {
            _state.value = current.copy(step = current.step.next(), startFailure = null)
        }
    }

    private fun start() {
        val current = _state.value
        val line = current.selectedLine ?: return
        val direction = current.selectedDirection ?: return
        _state.value = current.copy(isStarting = true, startFailure = null)
        viewModelScope.launch {
            try {
                val started = services.startService(
                    session,
                    ServiceStartRequest(
                        lineId = line.id,
                        lineLabel = line.label,
                        directionId = direction.id,
                        terminus = direction.terminus,
                        vehicleId = current.vehicleId.trim().takeIf { it.isNotEmpty() },
                        trainNumber = current.trainNumber.trim().takeIf { it.isNotEmpty() },
                    ),
                )
                _state.value = _state.value.copy(isStarting = false, started = started)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isStarting = false)
                throw cancelled
            } catch (failure: DriverServiceException) {
                _state.value = _state.value.copy(isStarting = false, startFailure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Prise de service impossible.", failure)
                _state.value = _state.value.copy(
                    isStarting = false,
                    startFailure = DriverServiceFailureKind.UNKNOWN,
                )
            }
        }
    }
}
