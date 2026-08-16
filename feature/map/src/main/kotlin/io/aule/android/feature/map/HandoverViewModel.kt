package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.HandoverAlert
import io.aule.android.core.model.HandoverAlertEngine
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.HandoverEngagement
import io.aule.android.core.model.HandoverException
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.HandoverTrack
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.distinctStops
import io.aule.android.core.model.fallbackStopsByProximity
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.HandoverProgress
import io.aule.android.core.model.HandoverProgressEngine
import io.aule.android.core.model.ScheduledTrip
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.measureHandoverProgress
import io.aule.android.core.model.normalizeStopName
import io.aule.android.core.model.plannedReliefPassage
import io.aule.android.core.model.remainingReliefStops
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.repository.HandoverAlertPrefsStore
import io.aule.android.core.model.repository.HandoverRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.selectFallbackPassages
import io.aule.android.core.model.toIncomingService
import java.time.Duration
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class HandoverStep {
    RESUME,
    LINE,
    VEHICLE,
    CANDIDATES,
    DIRECTION,
    FALLBACK_STOP,
    FALLBACK_TIME,
    STOP,
    ALERTS,
    CONFIRM,
}

data class HandoverUiState(
    val step: HandoverStep = HandoverStep.RESUME,
    val lines: List<ServiceLine> = emptyList(),
    val isLoadingLines: Boolean = true,
    val loadFailure: HandoverFailureKind? = null,
    val search: String = "",
    /** Lignes avec un véhicule certifié en ce moment (`route_id`). */
    val activeLineIds: Set<String> = emptySet(),
    /** Lignes relevées récemment, la plus récente en tête. */
    val recentLineIds: List<String> = emptyList(),
    val selectedLineId: String? = null,
    val query: String = "",
    val candidates: List<HandoverTarget> = emptyList(),
    val lookupEmpty: Boolean = false,
    val pending: HandoverEngagement? = null,
    val target: HandoverTarget? = null,
    val handover: HandoverSummary? = null,
    val isBusy: Boolean = false,
    val failure: HandoverFailureKind? = null,
    val alreadyOnService: Boolean = false,
    val started: ActiveDriverService? = null,
    val trackFix: HandoverFix? = null,
    val abortedReason: String? = null,
    val fallbackDirectionKey: String? = null,
    val fallbackStops: List<LineJourneyStop> = emptyList(),
    val fallbackAround: Coordinate? = null,
    val fallbackStopSearch: String = "",
    val fallbackStop: LineJourneyStop? = null,
    val fallbackPassages: List<StopDeparture> = emptyList(),
    val fallbackShowsAllDirections: Boolean = false,
    val isLoadingPassages: Boolean = false,
    val skippedDirection: Boolean = false,
    val fallbackTime: Instant? = null,
    val liveStops: List<LineJourneyStop> = emptyList(),
    val selectedLiveStop: LineJourneyStop? = null,
    val alertPrefs: HandoverAlertPrefs = HandoverAlertPrefs.DEFAULTS,
    val reliefArrived: Boolean = false,
    val progress: HandoverProgress? = null,
    val travelToRelief: Duration? = null,
    val neighbourPassages: Map<String, List<Instant>> = emptyMap(),
    /** Course GTFS du jour, quand le calage a réussi. */
    val activeTrip: ScheduledTrip? = null,
) {
    val leaveBy: Instant? get() = progress?.leaveBy(travelToRelief)
    val selectedLine: ServiceLine?
        get() = lines.find { it.id == selectedLineId }

    val fallbackDirection: ServiceDirection?
        get() = selectedLine?.directions?.find { it.key == fallbackDirectionKey }

    val visibleFallbackStops: List<LineJourneyStop>
        get() {
            val sorted = fallbackStopsByProximity(fallbackStops, fallbackAround)
            val query = fallbackStopSearch.trim()
            if (query.isEmpty()) return sorted
            return sorted.filter { it.name.contains(query, ignoreCase = true) }
        }

    val visibleLiveStops: List<LineJourneyStop>
        get() = fallbackStopsByProximity(liveStops, fallbackAround)

    val isSearchingLines: Boolean
        get() = search.trim().isNotEmpty()

    /**
     * Lignes du réseau qui roulent en ce moment, dans l'ordre du catalogue.
     */
    val activeLines: List<ServiceLine>
        get() = lines.filter { it.id in activeLineIds }

    /**
     * Lignes récentes encore proposables, hors de celles déjà dans [activeLines].
     */
    val recentLines: List<ServiceLine>
        get() = recentLineIds.mapNotNull { id -> lines.find { it.id == id } }
            .filter { it.id !in activeLineIds }

    /**
     * Résultats de recherche. Vide tant que le champ l'est : sans requête on
     * ne liste pas les quatre-vingts lignes du réseau (Flutter `_LineStep`).
     *
     * « T1 » désigne le tram 1 sur les feuilles de route, alors que la ligne
     * s'appelle « 1 ». On accepte la lettre de mode en préfixe.
     */
    val filteredLines: List<ServiceLine>
        get() {
            val query = search.trim()
            if (query.isEmpty()) return emptyList()
            val lower = query.lowercase()
            val stripped = if (lower.length > 1 && lower.startsWith('t')) {
                lower.substring(1)
            } else {
                null
            }
            return lines.filter { line ->
                line.label.contains(query, ignoreCase = true) ||
                    (
                        stripped != null &&
                            line.mode == TransportMode.TRAM &&
                            line.label.equals(stripped, ignoreCase = true)
                        )
            }
        }

    val canSearch: Boolean
        get() = query.trim().isNotEmpty() && !isBusy
}

/**
 * L'assistant de relève : ligne, véhicule, candidat ou repli horaire.
 *
 * Un lookup vide n'est pas un échec : la plupart des relèves se font face à
 * un collègue qui n'utilise pas encore l'application. On bascule alors sur
 * le sens, l'arrêt et le passage. Un conducteur connecté ouvre le choix
 * d'arrêt, les seuils d'alerte, puis le suivi.
 */
class HandoverViewModel(
    private val session: AuthSession,
    private val networkId: String?,
    alreadyOnService: Boolean,
    private val services: DriverServiceRepository,
    private val handovers: HandoverRepository,
    private val stops: StopRepository,
    private val roads: RoadRouter,
    private val around: () -> Coordinate? = { null },
    private val alertPrefsStore: HandoverAlertPrefsStore,
    private val onAlert: (HandoverAlert, String) -> Unit = { _, _ -> },
    private val logger: AuleLogger,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HandoverUiState(
            alreadyOnService = alreadyOnService,
            alertPrefs = alertPrefsStore.read(),
            recentLineIds = alertPrefsStore.readRecentLines(),
        ),
    )
    val state: StateFlow<HandoverUiState> = _state.asStateFlow()

    private var trackingJob: Job? = null
    private var travelJob: Job? = null
    private var neighbourJob: Job? = null
    private var alertEngine: HandoverAlertEngine? = null
    private var progressEngine: HandoverProgressEngine? = null
    private var lastTravelFetchAt: Instant = Instant.EPOCH

    init {
        loadLines()
        checkResume()
    }

    /**
     * Alimenté par le poller de flotte de la carte : les positions certifiées
     * portent le `route_id` de leur service. Aucun appel supplémentaire.
     */
    fun onFleetSnapshot(snapshot: FleetSnapshot) {
        val next = snapshot.vehicles
            .asSequence()
            .filter { it.isLive && it.lineId.isNotBlank() }
            .map { it.lineId }
            .toSet()
        val current = _state.value
        if (next == current.activeLineIds) return
        _state.value = current.copy(activeLineIds = next)
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
                    loadFailure = if (visible.isEmpty()) HandoverFailureKind.LINES_EMPTY else null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: DriverServiceException) {
                _state.value = _state.value.copy(
                    isLoadingLines = false,
                    loadFailure = when (failure.kind) {
                        DriverServiceFailureKind.NOT_CONFIGURED -> HandoverFailureKind.NOT_CONFIGURED
                        DriverServiceFailureKind.NETWORK -> HandoverFailureKind.NETWORK
                        DriverServiceFailureKind.LINES_EMPTY -> HandoverFailureKind.LINES_EMPTY
                        else -> HandoverFailureKind.UNKNOWN
                    },
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Catalogue de lignes illisible.", failure)
                _state.value = _state.value.copy(
                    isLoadingLines = false,
                    loadFailure = HandoverFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun setSearch(query: String) {
        _state.value = _state.value.copy(search = query)
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value, lookupEmpty = false, failure = null)
    }

    fun pickLine(id: String) {
        val recent = alertPrefsStore.pushRecentLine(id)
        _state.value = _state.value.copy(
            selectedLineId = id,
            query = "",
            candidates = emptyList(),
            lookupEmpty = false,
            failure = null,
            recentLineIds = recent,
            step = HandoverStep.VEHICLE,
        )
    }

    fun searchVehicle() {
        val current = _state.value
        val lineId = current.selectedLineId ?: return
        val query = current.query.trim()
        if (query.isEmpty() || current.isBusy) return
        _state.value = current.copy(isBusy = true, failure = null, lookupEmpty = false)
        viewModelScope.launch {
            try {
                val found = handovers.lookup(session, lineId, query)
                if (found.isEmpty()) {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        candidates = emptyList(),
                        lookupEmpty = true,
                    )
                    startFallback()
                } else {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        candidates = found,
                        lookupEmpty = false,
                        step = HandoverStep.CANDIDATES,
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: HandoverException) {
                _state.value = _state.value.copy(isBusy = false, failure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Recherche de relève impossible.", failure)
                _state.value = _state.value.copy(isBusy = false, failure = HandoverFailureKind.UNKNOWN)
            }
        }
    }

    fun setFallbackStopSearch(query: String) {
        _state.value = _state.value.copy(fallbackStopSearch = query)
    }

    fun pickDirection(direction: ServiceDirection) {
        val line = _state.value.selectedLine ?: return
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(
            isBusy = true,
            failure = null,
            fallbackDirectionKey = direction.key,
        )
        viewModelScope.launch {
            try {
                val journey = services.fetchJourney(session, line.id, direction.id)
                _state.value = _state.value.copy(
                    isBusy = false,
                    fallbackStops = journey.distinctStops(),
                    fallbackAround = around(),
                    fallbackStopSearch = "",
                    fallbackStop = null,
                    fallbackPassages = emptyList(),
                    step = HandoverStep.FALLBACK_STOP,
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: DriverServiceException) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    failure = when (failure.kind) {
                        DriverServiceFailureKind.NOT_CONFIGURED -> HandoverFailureKind.NOT_CONFIGURED
                        DriverServiceFailureKind.NETWORK -> HandoverFailureKind.NETWORK
                        DriverServiceFailureKind.LINES_EMPTY -> HandoverFailureKind.JOURNEY_UNAVAILABLE
                        else -> HandoverFailureKind.JOURNEY_UNAVAILABLE
                    },
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Desserte de relève indisponible.", failure)
                _state.value = _state.value.copy(
                    isBusy = false,
                    failure = HandoverFailureKind.JOURNEY_UNAVAILABLE,
                )
            }
        }
    }

    fun pickFallbackStop(stop: LineJourneyStop) {
        val current = _state.value
        val line = current.selectedLine ?: return
        val terminus = current.fallbackDirection?.terminus.orEmpty()
        if (current.isBusy) return
        _state.value = current.copy(
            fallbackStop = stop,
            fallbackPassages = emptyList(),
            fallbackShowsAllDirections = false,
            isLoadingPassages = true,
            failure = null,
            step = HandoverStep.FALLBACK_TIME,
        )
        viewModelScope.launch {
            try {
                val serving = stops.servingLines(stop.name)
                val announced = stops.departures(stop.name)
                val selected = selectFallbackPassages(
                    lineLabel = line.label,
                    terminus = terminus,
                    serving = serving,
                    departures = announced.departures,
                )
                _state.value = _state.value.copy(
                    isLoadingPassages = false,
                    fallbackPassages = selected.passages,
                    fallbackShowsAllDirections = selected.showsAllDirections,
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isLoadingPassages = false)
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Passages de relève indisponibles.", failure)
                _state.value = _state.value.copy(
                    isLoadingPassages = false,
                    failure = HandoverFailureKind.NETWORK,
                )
            }
        }
    }

    fun startFallback(passage: StopDeparture) {
        val current = _state.value
        val line = current.selectedLine ?: return
        val direction = current.fallbackDirection ?: return
        if (current.isBusy) return
        if (current.alreadyOnService) {
            _state.value = current.copy(failure = HandoverFailureKind.ALREADY_ON_SERVICE)
            return
        }
        _state.value = current.copy(isBusy = true, failure = null, fallbackTime = passage.expectedAt)
        viewModelScope.launch {
            try {
                val started = services.startService(
                    session,
                    ServiceStartRequest(
                        lineId = line.id,
                        lineLabel = line.label,
                        directionId = direction.id,
                        terminus = direction.terminus,
                        trainNumber = current.query.trim().ifEmpty { null },
                    ),
                )
                _state.value = _state.value.copy(isBusy = false, started = started)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: DriverServiceException) {
                _state.value = _state.value.copy(
                    isBusy = false,
                    failure = when (failure.kind) {
                        DriverServiceFailureKind.ALREADY_ON_SERVICE -> HandoverFailureKind.ALREADY_ON_SERVICE
                        DriverServiceFailureKind.NOT_SIGNED_IN -> HandoverFailureKind.NOT_SIGNED_IN
                        DriverServiceFailureKind.NO_DRIVER -> HandoverFailureKind.NO_DRIVER
                        DriverServiceFailureKind.NOT_CONFIGURED -> HandoverFailureKind.NOT_CONFIGURED
                        DriverServiceFailureKind.NETWORK -> HandoverFailureKind.NETWORK
                        else -> HandoverFailureKind.UNKNOWN
                    },
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Prise de service en relève impossible.", failure)
                _state.value = _state.value.copy(isBusy = false, failure = HandoverFailureKind.UNKNOWN)
            }
        }
    }

    private fun startFallback() {
        val line = _state.value.selectedLine ?: return
        val directions = line.directions
        if (directions.size == 1) {
            _state.value = _state.value.copy(skippedDirection = true)
            pickDirection(directions.first())
            return
        }
        _state.value = _state.value.copy(
            step = HandoverStep.DIRECTION,
            skippedDirection = false,
            fallbackDirectionKey = null,
            fallbackStops = emptyList(),
            fallbackStop = null,
            fallbackPassages = emptyList(),
            failure = null,
        )
    }

    fun engage(target: HandoverTarget) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, failure = null, target = target)
        viewModelScope.launch {
            try {
                val summary = handovers.request(session, target.serviceId)
                _state.value = _state.value.copy(handover = summary, target = target)
                openLiveStops(summary, target, releaseOnFailure = true)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false, target = null)
                throw cancelled
            } catch (failure: HandoverException) {
                _state.value = _state.value.copy(isBusy = false, target = null, failure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Engagement de relève impossible.", failure)
                _state.value = _state.value.copy(
                    isBusy = false,
                    target = null,
                    failure = HandoverFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun pickLiveStop(stop: LineJourneyStop) {
        val current = _state.value
        val handover = current.handover ?: return
        if (current.isBusy) return
        _state.value = current.copy(isBusy = true, selectedLiveStop = stop, failure = null)
        viewModelScope.launch {
            try {
                val coordinate = stop.coordinate
                val plannedAt = plannedPassage(stop)
                val updated = handovers.setStop(
                    session = session,
                    handoverId = handover.id,
                    stopId = stop.id,
                    stopName = stop.name,
                    latitude = coordinate?.latitude ?: 0.0,
                    longitude = coordinate?.longitude ?: 0.0,
                    plannedAt = plannedAt,
                )
                _state.value = _state.value.copy(
                    isBusy = false,
                    handover = updated,
                    selectedLiveStop = stop,
                    step = HandoverStep.ALERTS,
                    reliefArrived = false,
                    progress = null,
                    travelToRelief = null,
                )
                lastTravelFetchAt = Instant.EPOCH
                armProgressEngine(stop)
                armAlerts()
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: HandoverException) {
                _state.value = _state.value.copy(isBusy = false, failure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Enregistrement de l'arrêt de relève impossible.", failure)
                _state.value = _state.value.copy(isBusy = false, failure = HandoverFailureKind.UNKNOWN)
            }
        }
    }

    fun resumePending() {
        val pending = _state.value.pending ?: return
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, failure = null)
        viewModelScope.launch {
            val summary = pending.handover
            val target = pending.target
            val hasStop = summary.reliefStopId != null ||
                summary.reliefStopName != null ||
                summary.reliefStopCoordinate != null
            try {
                _state.value = _state.value.copy(handover = summary, target = target)
                val remaining = resolveLiveStops(target, summary.id)
                if (remaining == null) {
                    _state.value = _state.value.copy(isBusy = false, pending = null)
                    return@launch
                }
                if (!hasStop && remaining.isEmpty()) {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        failure = HandoverFailureKind.JOURNEY_UNAVAILABLE,
                    )
                    return@launch
                }
                val selected = matchReliefStop(remaining, summary)
                _state.value = _state.value.copy(
                    isBusy = false,
                    pending = null,
                    target = target,
                    handover = summary,
                    selectedLineId = target.lineId,
                    query = target.trainNumber ?: target.vehicleId.orEmpty(),
                    liveStops = remaining,
                    selectedLiveStop = selected,
                    fallbackAround = around(),
                    step = if (hasStop) HandoverStep.CONFIRM else HandoverStep.STOP,
                    failure = null,
                    trackFix = _state.value.trackFix,
                    abortedReason = null,
                    reliefArrived = false,
                    progress = null,
                    travelToRelief = null,
                )
                if (hasStop) {
                    lastTravelFetchAt = Instant.EPOCH
                    armProgressEngine(selected)
                    armAlerts()
                }
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: Throwable) {
                if (hasStop) {
                    _state.value = _state.value.copy(
                        isBusy = false,
                        pending = null,
                        target = target,
                        handover = summary,
                        selectedLineId = target.lineId,
                        query = target.trainNumber ?: target.vehicleId.orEmpty(),
                        selectedLiveStop = matchReliefStop(emptyList(), summary),
                        step = HandoverStep.CONFIRM,
                        failure = null,
                        abortedReason = null,
                    )
                    lastTravelFetchAt = Instant.EPOCH
                    armProgressEngine(_state.value.selectedLiveStop)
                    armAlerts()
                } else {
                    logger.warn(LogDomain.NET, "Reprise de relève : desserte indisponible.", failure)
                    _state.value = _state.value.copy(
                        isBusy = false,
                        failure = kindOf(failure).takeUnless { it == HandoverFailureKind.UNKNOWN }
                            ?: HandoverFailureKind.JOURNEY_UNAVAILABLE,
                    )
                }
            }
        }
    }

    fun beginTracking() {
        val current = _state.value
        if (current.handover == null || current.selectedLiveStop == null) return
        if (alertEngine == null) armAlerts()
        if (progressEngine == null) armProgressEngine(current.selectedLiveStop)
        lastTravelFetchAt = Instant.EPOCH
        _state.value = current.copy(step = HandoverStep.CONFIRM, failure = null)
    }

    fun updateAlertPrefs(next: HandoverAlertPrefs) {
        _state.value = _state.value.copy(alertPrefs = next)
        alertEngine?.prefs = next
        alertPrefsStore.write(next)
    }

    /**
     * Sonde la position du collègue tant que la relève est vivante.
     *
     * Cadence cinq secondes, backoff jusqu'à trente. Une position manquante
     * n'est pas une erreur : le calque garde le dernier point.
     */
    fun startTracking() {
        val handoverId = _state.value.handover?.id ?: return
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            var errors = 0
            while (isActive) {
                try {
                    val track = handovers.track(session, handoverId)
                    errors = 0
                    applyTrack(track)
                    if (!track.handover.status.isLive) return@launch
                    delay(TRACK_INTERVAL_MS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    logger.warn(LogDomain.NET, "Suivi de relève indisponible.", failure)
                    errors = min(errors + 1, 3)
                    delay(min(TRACK_INTERVAL_MS * (1L shl errors), TRACK_MAX_BACKOFF_MS))
                }
            }
        }
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        travelJob?.cancel()
        travelJob = null
        neighbourJob?.cancel()
        neighbourJob = null
    }

    fun discardPending() {
        val pending = _state.value.pending ?: return
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, failure = null)
        viewModelScope.launch {
            try {
                handovers.cancel(session, pending.handover.id, reason = "cancelled_by_driver")
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Annulation de relève impossible.", failure)
                _state.value = _state.value.copy(isBusy = false, failure = kindOf(failure))
                return@launch
            }
            _state.value = _state.value.copy(
                isBusy = false,
                pending = null,
                step = HandoverStep.LINE,
                failure = null,
            )
        }
    }

    fun confirm() {
        val current = _state.value
        val handover = current.handover ?: return
        val target = current.target ?: return
        if (current.isBusy) return
        _state.value = current.copy(isBusy = true, failure = null)
        viewModelScope.launch {
            try {
                val result = handovers.confirm(session, handover.id)
                stopTracking()
                val started = result.toIncomingService(
                    target = target,
                    lineLabel = current.selectedLine?.label ?: target.lineId,
                    startedAt = now(),
                ) ?: throw HandoverException(HandoverFailureKind.UNKNOWN)
                _state.value = _state.value.copy(isBusy = false, handover = result, started = started)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(isBusy = false)
                throw cancelled
            } catch (failure: HandoverException) {
                _state.value = _state.value.copy(isBusy = false, failure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Confirmation de relève impossible.", failure)
                _state.value = _state.value.copy(isBusy = false, failure = HandoverFailureKind.UNKNOWN)
            }
        }
    }

    /**
     * Étape précédente. `true` : fermer l'écran. Une relève engagée est
     * relâchée en revenant des candidats, pour ne pas bloquer la suivante.
     */
    fun back(): Boolean {
        val current = _state.value
        return when (current.step) {
            HandoverStep.RESUME, HandoverStep.LINE -> true
            HandoverStep.VEHICLE -> {
                _state.value = current.copy(
                    step = HandoverStep.LINE,
                    failure = null,
                    lookupEmpty = false,
                )
                false
            }
            HandoverStep.CANDIDATES -> {
                _state.value = current.copy(step = HandoverStep.VEHICLE, failure = null)
                false
            }
            HandoverStep.DIRECTION -> {
                _state.value = current.copy(
                    step = HandoverStep.VEHICLE,
                    failure = null,
                    fallbackDirectionKey = null,
                )
                false
            }
            HandoverStep.FALLBACK_STOP -> {
                _state.value = current.copy(
                    step = if (current.skippedDirection) HandoverStep.VEHICLE else HandoverStep.DIRECTION,
                    failure = null,
                    fallbackStops = emptyList(),
                    fallbackStop = null,
                    fallbackStopSearch = "",
                )
                false
            }
            HandoverStep.FALLBACK_TIME -> {
                _state.value = current.copy(
                    step = HandoverStep.FALLBACK_STOP,
                    failure = null,
                    fallbackPassages = emptyList(),
                    fallbackStop = null,
                    isLoadingPassages = false,
                )
                false
            }
            HandoverStep.STOP -> {
                stopTracking()
                alertEngine = null
                releaseEngaged(reason = "changed_target")
                _state.value = _state.value.copy(
                    step = if (current.candidates.isEmpty()) HandoverStep.VEHICLE else HandoverStep.CANDIDATES,
                    failure = null,
                    trackFix = null,
                    abortedReason = null,
                    liveStops = emptyList(),
                    selectedLiveStop = null,
                    reliefArrived = false,
                    progress = null,
                    travelToRelief = null,
                    neighbourPassages = emptyMap(),
                )
                false
            }
            HandoverStep.ALERTS -> {
                _state.value = current.copy(step = HandoverStep.STOP, failure = null)
                false
            }
            HandoverStep.CONFIRM -> {
                if (current.selectedLiveStop != null) {
                    _state.value = current.copy(
                        step = HandoverStep.ALERTS,
                        failure = null,
                    )
                } else if (current.liveStops.isNotEmpty()) {
                    _state.value = current.copy(
                        step = HandoverStep.STOP,
                        failure = null,
                    )
                } else {
                    stopTracking()
                    alertEngine = null
                    releaseEngaged(reason = "changed_target")
                    _state.value = _state.value.copy(
                        step = if (current.candidates.isEmpty()) HandoverStep.VEHICLE else HandoverStep.CANDIDATES,
                        failure = null,
                        trackFix = null,
                        abortedReason = null,
                        selectedLiveStop = null,
                        reliefArrived = false,
                        progress = null,
                        travelToRelief = null,
                    )
                }
                false
            }
        }
    }

    /**
     * Fermeture du panneau. Une relève seulement *proposée* à la reprise
     * n'est pas annulée : le conducteur la retrouvera à la prochaine ouverture.
     */
    fun dismiss() {
        stopTracking()
        alertEngine = null
        progressEngine = null
        val engaged = _state.value.handover
        if (engaged != null && engaged.status.isLive) {
            releaseEngaged(reason = "cancelled_by_driver")
        }
    }

    private fun applyTrack(track: HandoverTrack) {
        val aborted = track.handover.status.isAborted
        val current = _state.value
        val fix = if (track.handover.status.isLive) track.fix else null
        val stop = current.selectedLiveStop
        val engine = alertEngine
        var arrived = current.reliefArrived
        var progress = if (aborted) null else current.progress
        if (!aborted &&
            current.step == HandoverStep.CONFIRM &&
            fix != null &&
            stop != null
        ) {
            val at = now()
            val measured = progressEngine?.update(
                position = fix.coordinate,
                recordedAt = at.minusSeconds(fix.ageSeconds.toLong().coerceAtLeast(0L)),
                now = at,
                speedMps = fix.speed,
                fixAgeSeconds = fix.ageSeconds,
            ) ?: measureHandoverProgress(
                fix,
                stop,
                current.liveStops,
                at,
                timetableAt = current.handover?.reliefPlannedAt
                    ?: track.handover.reliefPlannedAt
                    ?: current.activeTrip?.stops
                        ?.find { normalizeStopName(it.name) == normalizeStopName(stop.name) }
                        ?.passageAt,
            )
            progress = measured
            arrived = measured.arrived
            engine?.evaluate(measured, at)?.forEach { alert ->
                onAlert(alert, stop.name)
            }
            refreshTravel(stop)
        }
        _state.value = current.copy(
            handover = track.handover,
            trackFix = fix,
            abortedReason = if (aborted) track.handover.cancelReason else null,
            failure = if (aborted) HandoverFailureKind.CLOSED else current.failure,
            reliefArrived = if (aborted) false else arrived,
            progress = progress,
            travelToRelief = if (aborted) null else current.travelToRelief,
        )
    }

    /**
     * Temps de trajet du releveur jusqu'au quai. Rafraîchi avec parcimonie :
     * la cible ne bouge pas. Un échec laisse le dernier temps connu.
     */
    private fun refreshTravel(relief: LineJourneyStop) {
        val from = around() ?: return
        val to = relief.coordinate ?: return
        val at = now()
        if (Duration.between(lastTravelFetchAt, at).seconds < TRAVEL_REFRESH_SECONDS) return
        lastTravelFetchAt = at
        travelJob?.cancel()
        travelJob = viewModelScope.launch {
            val route = try {
                roads.route(from, to, RoadProfile.CAR)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Temps de trajet vers la relève indisponible.", failure)
                null
            } ?: return@launch
            val seconds = route.durationSeconds.roundToLong().coerceAtLeast(0L)
            _state.value = _state.value.copy(travelToRelief = Duration.ofSeconds(seconds))
        }
    }

    private fun armAlerts() {
        alertEngine = HandoverAlertEngine(prefs = _state.value.alertPrefs)
    }

    private fun armProgressEngine(stop: LineJourneyStop?) {
        val trip = _state.value.activeTrip
        if (stop == null || trip == null) {
            progressEngine = null
            return
        }
        val index = trip.stops.indexOfFirst { it.stopId == stop.id }.takeIf { it >= 0 }
            ?: trip.stops.indexOfFirst {
                normalizeStopName(it.name) == normalizeStopName(stop.name)
            }.takeIf { it >= 0 }
        if (index == null) {
            progressEngine = null
            return
        }
        progressEngine = HandoverProgressEngine(trip, index)
    }

    /**
     * Passages de la ligne à chaque arrêt proposé, chargés en fond.
     *
     * Une ligne de trente arrêts ferait autant d'allers-retours : on se
     * limite aux six plus proches. Sans grille, l'arrêt reste
     * sélectionnable.
     */
    private fun loadNeighbourPassages(stops: List<LineJourneyStop>) {
        neighbourJob?.cancel()
        neighbourJob = viewModelScope.launch {
            val wanted = stops.take(NEIGHBOUR_STOPS)
            for (stop in wanted) {
                if (!isActive) return@launch
                if (_state.value.neighbourPassages.containsKey(stop.name)) continue
                val times = fetchNeighbourTimes(stop) ?: continue
                _state.value = _state.value.copy(
                    neighbourPassages = _state.value.neighbourPassages + (stop.name to times),
                )
            }
        }
    }

    private suspend fun plannedPassage(stop: LineJourneyStop): Instant? {
        _state.value.activeTrip?.stops
            ?.firstOrNull {
                it.stopId == stop.id ||
                    normalizeStopName(it.name) == normalizeStopName(stop.name)
            }
            ?.passageAt
            ?.let { return it }
        val cached = _state.value.neighbourPassages[stop.name]
        if (cached != null) return plannedReliefPassage(cached, now())
        val times = fetchNeighbourTimes(stop) ?: return null
        _state.value = _state.value.copy(
            neighbourPassages = _state.value.neighbourPassages + (stop.name to times),
        )
        return plannedReliefPassage(times, now())
    }

    private suspend fun fetchNeighbourTimes(stop: LineJourneyStop): List<Instant>? {
        val lineLabel = _state.value.selectedLine?.label ?: _state.value.target?.lineId ?: return null
        val terminus = _state.value.target?.terminus.orEmpty()
        return try {
            val serving = stops.servingLines(stop.name)
            val announced = stops.departures(stop.name)
            val times = selectFallbackPassages(
                lineLabel = lineLabel,
                terminus = terminus,
                serving = serving,
                departures = announced.departures,
                limit = NEIGHBOUR_PER_STOP,
            ).passages.map { it.expectedAt }
            times.takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Charge les arrêts restants après un engagement. Sans desserte, on
     * relâche la relève : laisser un `engaged` orphelin bloquerait la
     * suivante jusqu'à l'expiration.
     */
    private suspend fun openLiveStops(
        summary: HandoverSummary,
        target: HandoverTarget,
        releaseOnFailure: Boolean,
    ) {
        val remaining = try {
            resolveLiveStops(target, summary.id)
        } catch (cancelled: CancellationException) {
            if (releaseOnFailure) releaseEngaged(reason = "changed_target")
            throw cancelled
        } catch (failure: Throwable) {
            if (releaseOnFailure) releaseEngaged(reason = "changed_target")
            _state.value = _state.value.copy(
                isBusy = false,
                handover = if (releaseOnFailure) null else summary,
                failure = journeyFailureOf(failure),
            )
            return
        }
        if (remaining == null) {
            return
        }
        if (remaining.isEmpty()) {
            if (releaseOnFailure) releaseEngaged(reason = "changed_target")
            _state.value = _state.value.copy(
                isBusy = false,
                handover = if (releaseOnFailure) null else summary,
                failure = HandoverFailureKind.JOURNEY_UNAVAILABLE,
            )
            return
        }
        _state.value = _state.value.copy(
            isBusy = false,
            handover = summary,
            target = target,
            liveStops = remaining,
            selectedLiveStop = remaining.lastOrNull(),
            fallbackAround = around(),
            step = HandoverStep.STOP,
            failure = null,
            abortedReason = null,
            neighbourPassages = emptyMap(),
        )
        loadNeighbourPassages(remaining)
    }

    /**
     * `null` : le service sortant s'est déjà arrêté, l'écran de suivi
     * affiche l'annulation. Liste vide : desserte inconnue.
     */
    private suspend fun resolveLiveStops(
        target: HandoverTarget,
        handoverId: String,
    ): List<LineJourneyStop>? {
        val track = try {
            handovers.track(session, handoverId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (track != null) {
            if (track.handover.status.isAborted) {
                applyTrack(track)
                _state.value = _state.value.copy(
                    isBusy = false,
                    step = HandoverStep.CONFIRM,
                )
                return null
            }
            _state.value = _state.value.copy(
                trackFix = track.fix,
                handover = track.handover,
            )
        }
        val near = track?.fix?.coordinate ?: around()
        val trip = if (near != null) {
            try {
                services.nearestActiveTrip(
                    session = session,
                    lineId = target.lineId,
                    directionId = target.directionId ?: 0,
                    destinationHint = target.terminus,
                    near = near,
                    at = now(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Course GTFS de relève introuvable.", failure)
                null
            }
        } else {
            null
        }
        if (trip != null && trip.stops.size >= 2) {
            _state.value = _state.value.copy(activeTrip = trip)
            val journeyStops = trip.stops.map { it.toJourneyStop() }
            return remainingReliefStops(journeyStops, track?.fix?.coordinate)
        }
        _state.value = _state.value.copy(activeTrip = null)
        val journey = services.fetchJourney(
            session,
            target.lineId,
            target.directionId ?: 0,
        )
        return remainingReliefStops(journey.distinctStops(), track?.fix?.coordinate)
    }

    private fun matchReliefStop(
        stops: List<LineJourneyStop>,
        summary: HandoverSummary,
    ): LineJourneyStop? {
        summary.reliefStopId?.let { id ->
            stops.find { it.id == id }?.let { return it }
        }
        val wanted = summary.reliefStopName?.let { normalizeStopName(it) }.orEmpty()
        if (wanted.isNotEmpty()) {
            stops.find { normalizeStopName(it.name) == wanted }?.let { return it }
        }
        val name = summary.reliefStopName ?: return null
        return LineJourneyStop(
            id = summary.reliefStopId ?: name,
            name = name,
            coordinate = summary.reliefStopCoordinate,
        )
    }

    private fun journeyFailureOf(failure: Throwable): HandoverFailureKind {
        if (failure is DriverServiceException) {
            return when (failure.kind) {
                DriverServiceFailureKind.NOT_CONFIGURED -> HandoverFailureKind.NOT_CONFIGURED
                DriverServiceFailureKind.NETWORK -> HandoverFailureKind.NETWORK
                else -> HandoverFailureKind.JOURNEY_UNAVAILABLE
            }
        }
        return (failure as? HandoverException)?.kind ?: HandoverFailureKind.JOURNEY_UNAVAILABLE
    }

    private fun releaseEngaged(reason: String) {
        val engaged = _state.value.handover ?: return
        if (!engaged.status.isLive) {
            _state.value = _state.value.copy(handover = null)
            return
        }
        val id = engaged.id
        _state.value = _state.value.copy(handover = null)
        viewModelScope.launch {
            try {
                handovers.cancel(session, id, reason = reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Relâchement de relève impossible.", failure)
            }
        }
    }

    private fun checkResume() {
        viewModelScope.launch {
            try {
                val found = handovers.activeForMe(session)
                val current = _state.value
                if (current.step != HandoverStep.RESUME) return@launch
                _state.value = current.copy(
                    pending = found,
                    selectedLineId = found?.target?.lineId ?: current.selectedLineId,
                    step = if (found == null) HandoverStep.LINE else HandoverStep.RESUME,
                    isBusy = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Reprise de relève impossible.", failure)
                val current = _state.value
                if (current.step == HandoverStep.RESUME) {
                    _state.value = current.copy(step = HandoverStep.LINE, isBusy = false)
                }
            }
        }
    }

    private fun kindOf(failure: Throwable): HandoverFailureKind =
        (failure as? HandoverException)?.kind ?: HandoverFailureKind.UNKNOWN

    private companion object {
        const val TRACK_INTERVAL_MS = 5_000L
        const val TRACK_MAX_BACKOFF_MS = 30_000L
        const val TRAVEL_REFRESH_SECONDS = 120L
        const val NEIGHBOUR_STOPS = 6
        const val NEIGHBOUR_PER_STOP = 3
    }
}
