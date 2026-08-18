package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.FleetStatus
import io.aule.android.core.model.GpsTracePoint
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.NearbyDigest
import io.aule.android.core.model.NearbyDigestBuilder
import io.aule.android.core.model.Place
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlace
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.StopSearch
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.geo.RouteProgress
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.JourneyPlan
import io.aule.android.core.model.JourneyProgress
import io.aule.android.core.model.LegMode
import io.aule.android.core.model.NextAction
import io.aule.android.core.model.OffRouteDetector
import io.aule.android.core.model.PinnedManeuver
import io.aule.android.core.model.TripSummary
import io.aule.android.core.model.journeyFromCandidate
import io.aule.android.core.model.journeyProgressAt
import io.aule.android.core.model.nextAction
import io.aule.android.core.model.pinManeuvers
import io.aule.android.core.model.tripSummary
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceRecorder
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * L'état de l'écran carte.
 *
 * Il ne porte **que** ce qui change quelques fois par minute. Les positions de
 * véhicules n'y figurent pas et n'y figureront jamais : elles vont directement
 * dans les sources MapLibre, sans réveiller Compose.
 *
 * Un seul volet à la fois, par construction : sélectionner un véhicule libère
 * l'arrêt, et réciproquement. « Autour de vous » ferme les deux. La recherche
 * ferme le volet : le clavier prend la moitié basse, et deux surfaces
 * empilées ne se lisent pas.
 */
data class MapSearchState(
    val query: String = "",
    val isActive: Boolean = false,
    val stops: List<StopSearchHit> = emptyList(),
    val places: List<Place> = emptyList(),
    val isGeocoding: Boolean = false,
) {
    val showsResults: Boolean get() = isActive && query.trim().isNotEmpty()
    val isEmpty: Boolean
        get() = stops.isEmpty() && places.isEmpty() && !isGeocoding
}

data class MapUiState(
    val stops: List<TransitStop> = emptyList(),
    val isLoadingStops: Boolean = false,
    /**
     * Les couleurs de lignes. Vide tant que le catalogue n'a pas répondu — et
     * vide pour toujours s'il ne répond jamais : un badge gris est une fiche
     * moins jolie, pas une fiche fausse.
     */
    val linePalette: LinePalette = LinePalette.EMPTY,
    /**
     * La panne, telle qu'on peut la dire à l'usager.
     *
     * Le message vient de l'exception — le backend écrit les siens dans
     * `{"error": …}` et ils sont plus utiles que tout ce qu'on écrirait à leur
     * place. Un écran vide sans explication est le défaut qu'on cherche à rendre
     * impossible.
     */
    val stopsFailure: String? = null,
    val mapError: String? = null,
    val fleetStatus: FleetStatus = FleetStatus.Empty,
    val showsFleetStatus: Boolean = false,
    val selectedStop: TransitStop? = null,
    val selectedVehicle: TransportVehicle? = null,
    val selectedPlace: Place? = null,
    val showingNearby: Boolean = false,
    val search: MapSearchState = MapSearchState(),
    val route: RouteUiState? = null,
    val navigation: NavigationUiState? = null,
) {
    val hasSheet: Boolean
        get() = showingNearby ||
            selectedStop != null ||
            selectedVehicle != null ||
            selectedPlace != null ||
            (route != null && navigation == null) ||
            navigation?.showingTrip == true
    val isNavigating: Boolean get() = navigation != null
}

enum class RouteLoadStatus { LOADING, READY, ERROR }

data class RouteUiState(
    val origin: RoutePlace,
    val destination: RoutePlace,
    val mode: RouteMode = RouteMode.TRANSIT,
    val status: RouteLoadStatus = RouteLoadStatus.LOADING,
    val plan: RoutePlan? = null,
    val selectedId: String? = null,
    val error: String? = null,
) {
    val selected: RouteCandidate?
        get() = plan?.selected(selectedId)
}

data class NavigationUiState(
    val plan: JourneyPlan,
    val progress: JourneyProgress,
    val action: NextAction?,
    val summary: TripSummary,
    val offRoute: Boolean = false,
    val signalLost: Boolean = false,
    val routeBearing: Double = 0.0,
    val showingTrip: Boolean = false,
)

class MapViewModel(
    internal val stopRepository: StopRepository,
    private val vehicleRepository: VehicleRepository,
    private val linePaletteRepository: LinePaletteRepository,
    private val traces: GpsTraceCatalog,
    private val placeRepository: PlaceSearchRepository,
    private val routingRepository: RoutingRepository,
    private val roadRouter: RoadRouter,
    internal val dispatchers: AuleDispatchers,
    val logger: AuleLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    /**
     * Les instantanés de flotte, **hors de l'état recomposable**.
     *
     * Un `SharedFlow` et non un `StateFlow` exposé à Compose : l'écran le collecte
     * dans un effet et le pousse dans la couche MapLibre. Faire transiter 250
     * véhicules par un `State` recomposerait tout l'arbre à chaque sondage, pour
     * un contenu que Compose ne dessine pas.
     */
    private val _fleet = MutableSharedFlow<FleetSnapshot>(replay = 1)
    val fleet: SharedFlow<FleetSnapshot> = _fleet.asSharedFlow()

    private var pollJob: Job? = null
    private var geocodeJob: Job? = null
    private var routeJob: Job? = null
    private var routeToken = 0
    private var maneuverGeneration = 0
    private val maneuversByLeg = mutableMapOf<Int, List<PinnedManeuver>>()
    private val routeProgress = RouteProgress()

    /** La trace du guidage en cours. `null` hors guidage, et en production. */
    private var trace: GpsTraceRecorder? = null
    private val offRoute = OffRouteDetector()
    private var focusedLeg = -1

    /** La région observée. Le sondage suit la caméra, pas l'inverse. */
    private var center: Coordinate = Coordinate.NANTES
    private var radiusMeters: Double = DEFAULT_RADIUS_M

    /** Le dernier instantané réussi, gardé pour ne jamais vider la carte. */
    private var lastSnapshot: FleetSnapshot = FleetSnapshot.EMPTY

    init {
        loadStops()
        loadLinePalette()
    }

    // ------------------------------------------------------------------- arrêts

    /**
     * Le catalogue complet, chargé une fois.
     *
     * ~2 600 arrêts pour ~600 Ko : assez peu pour un seul appel, et c'est ce qui
     * permet à la carte de les afficher sans redemander à chaque déplacement.
     */
    fun loadStops() {
        if (_state.value.isLoadingStops) return
        _state.value = _state.value.copy(isLoadingStops = true, stopsFailure = null)

        viewModelScope.launch {
            runCatching {
                // Le décodage de ~600 Ko ne se fait pas sur le thread principal :
                // ce serait un à-coup visible à chaque lancement.
                withContext(dispatchers.io) { stopRepository.allStops() }
            }.onSuccess { stops ->
                logger.info(LogDomain.MAP, "${stops.size} arrêts chargés.")
                val current = _state.value
                _state.value = current.copy(
                    stops = stops,
                    isLoadingStops = false,
                    stopsFailure = null,
                    search = current.search.copy(
                        stops = if (current.search.query.isBlank()) {
                            emptyList()
                        } else {
                            StopSearch.search(stops, current.search.query)
                        },
                    ),
                )
            }.onFailure { failure ->
                logger.warn(LogDomain.MAP, "Chargement des arrêts en échec.", failure)
                _state.value = _state.value.copy(
                    isLoadingStops = false,
                    stopsFailure = failure.message,
                )
            }
        }
    }

    fun retryLoadingStops() = loadStops()

    /**
     * Le nuancier des lignes, demandé une fois.
     *
     * En échec, on ne réessaie pas et on ne dit rien à l'usager : la couleur
     * d'un badge n'est pas une information dont l'absence se signale. Elle se
     * remplace par le gris de repli, et la fiche reste lisible.
     */
    private fun loadLinePalette() {
        viewModelScope.launch {
            runCatching {
                withContext(dispatchers.io) { linePaletteRepository.palette() }
            }.onSuccess { palette ->
                if (palette.isEmpty) return@onSuccess
                _state.value = _state.value.copy(linePalette = palette)
            }.onFailure { failure ->
                logger.warn(LogDomain.MAP, "Nuancier des lignes indisponible.", failure)
            }
        }
    }

    fun reportMapError(reason: String) {
        _state.value = _state.value.copy(mapError = reason)
    }

    fun clearMapError() {
        if (_state.value.mapError != null) {
            _state.value = _state.value.copy(mapError = null)
        }
    }

    // ------------------------------------------------------------------- flotte

    /**
     * Le sondage de la flotte.
     *
     * Toutes les 15 secondes tant que tout va bien, puis **recul exponentiel**
     * jusqu'à deux minutes. En échec, on **garde l'affichage précédent** et on
     * marque l'instantané périmé : vider la carte pendant une coupure ferait
     * croire qu'il n'y a plus de bus, ce qui est faux et décourage de réessayer.
     */
    fun startFleetPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var backoffMs = POLL_INTERVAL_MS
            while (isActive) {
                val outcome = runCatching {
                    withContext(dispatchers.io) {
                        vehicleRepository.vehicles(center, radiusMeters, FLEET_LIMIT)
                    }
                }

                outcome.onSuccess { snapshot ->
                    lastSnapshot = snapshot
                    _fleet.emit(snapshot)
                    val current = _state.value.selectedVehicle
                    val refreshed = current?.let { selected ->
                        snapshot.vehicles.find { it.id == selected.id }
                    }
                    _state.value = _state.value.copy(
                        fleetStatus = snapshot.status,
                        showsFleetStatus = snapshot.isStale || snapshot.vehicles.isNotEmpty(),
                        selectedVehicle = refreshed ?: current,
                    )
                    logger.info(
                        LogDomain.NET,
                        "Flotte : ${snapshot.vehicles.size} véhicule(s) " +
                            "(${snapshot.liveCount} mesuré(s), ${snapshot.scheduledCount} théorique(s)) " +
                            "sur ${radiusMeters.toInt()} m" +
                            (snapshot.degraded?.let { ", source dégradée : $it" } ?: ""),
                    )
                    backoffMs = POLL_INTERVAL_MS
                }.onFailure { failure ->
                    if (!isActive) return@launch
                    if (failure is CancellationException) throw failure
                    logger.warn(LogDomain.NET, "Sondage de flotte en échec.", failure)
                    val stale = lastSnapshot.copy(isStale = true)
                    lastSnapshot = stale
                    _fleet.emit(stale)
                    _state.value = _state.value.copy(
                        fleetStatus = stale.status,
                        showsFleetStatus = stale.isStale || stale.vehicles.isNotEmpty(),
                    )
                    backoffMs = min(backoffMs * 2, MAX_BACKOFF_MS)
                }

                delay(backoffMs)
            }
        }
    }

    fun stopFleetPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * La caméra s'est posée. On interroge **plus large que l'écran** pour voir
     * arriver les véhicules avant qu'ils n'entrent dans le cadre.
     */
    fun onRegionSettled(newCenter: Coordinate, visibleRadiusMeters: Double) {
        center = newCenter
        radiusMeters = (visibleRadiusMeters * RADIUS_MARGIN)
            .coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
    }

    // ---------------------------------------------------------------- sélection

    fun select(stop: TransitStop) {
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = stop,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun select(vehicle: TransportVehicle) {
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = vehicle,
            selectedPlace = null,
            showingNearby = false,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun select(place: Place) {
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = place,
            showingNearby = false,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun select(hit: StopSearchHit) = select(hit.representative)

    /**
     * Ce qu'il y a autour, calculé à l'ouverture de la liste et pas avant.
     *
     * Le point de référence est la position de l'utilisateur si on l'a — c'est
     * la question qu'il pose — et le centre de la carte sinon, ce qui reste
     * vrai quand il explore.
     */
    fun nearbyDigest(around: Coordinate): NearbyDigest =
        NearbyDigestBuilder.build(
            stops = _state.value.stops,
            vehicles = lastSnapshot.vehicles,
            around = around,
        )

    fun showNearby() {
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = true,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun clearSelection() {
        val current = _state.value
        if (current.selectedStop == null &&
            current.selectedVehicle == null &&
            current.selectedPlace == null
        ) {
            return
        }
        _state.value = current.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
        )
    }

    /** Ferme le volet, y compris « autour de vous » et l'itinéraire. */
    fun dismissSheet() {
        val current = _state.value
        if (current.selectedStop == null &&
            current.selectedVehicle == null &&
            current.selectedPlace == null &&
            !current.showingNearby &&
            current.route == null &&
            current.navigation == null
        ) {
            return
        }
        abandonRoute()
        _state.value = current.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            route = null,
            navigation = null,
        )
    }

    // ---------------------------------------------------------------- recherche

    /**
     * La saisie a pris le champ.
     *
     * Le volet cède : le clavier occupe la moitié basse, et deux surfaces
     * empilées ne se lisent pas. La carte, elle, reste montée.
     */
    fun activateSearch() {
        val current = _state.value
        if (current.search.isActive) return
        abandonRoute()
        _state.value = current.copy(
            search = current.search.copy(isActive = true),
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            route = null,
            navigation = null,
        )
    }

    /**
     * Chaque frappe.
     *
     * Les arrêts répondent tout de suite : le catalogue est déjà en mémoire.
     * Les adresses attendent [PLACE_DEBOUNCE_MS] — elles coûtent un
     * aller-retour là où l'index répond de mémoire.
     */
    fun setSearchQuery(query: String) {
        val current = _state.value
        abandonRoute()
        val stops = StopSearch.search(current.stops, query)
        val trimmed = query.trim()
        val willGeocode = trimmed.length >= MIN_PLACE_QUERY_LENGTH
        _state.value = current.copy(
            search = current.search.copy(
                query = query,
                isActive = true,
                stops = stops,
                places = if (willGeocode) current.search.places else emptyList(),
                isGeocoding = willGeocode,
            ),
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            route = null,
            navigation = null,
        )
        geocodeJob?.cancel()
        if (!willGeocode) return
        val issued = query
        geocodeJob = viewModelScope.launch {
            delay(PLACE_DEBOUNCE_MS)
            val found = runCatching {
                withContext(dispatchers.io) { placeRepository.search(trimmed) }
            }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                // Un géocodeur muet n'est pas une panne de la recherche : elle
                // a déjà répondu sur le réseau, et c'est le résultat le plus
                // utile des deux.
                logger.warn(LogDomain.NET, "Géocodeur muet.", failure)
                emptyList()
            }
            val latest = _state.value
            if (latest.search.query != issued) return@launch
            _state.value = latest.copy(
                search = latest.search.copy(places = found, isGeocoding = false),
            )
        }
    }

    fun cancelSearch() {
        geocodeJob?.cancel()
        geocodeJob = null
        val current = _state.value
        if (!current.search.isActive && current.search.query.isEmpty()) return
        _state.value = current.copy(search = MapSearchState())
    }

    // ------------------------------------------------------------- itinéraire

    /**
     * Calcule un itinéraire. L'origine est la position, ou le centre de la
     * carte si on ne l'a pas — le départ ne se demande plus, il se trouve.
     *
     * Un jeton empêche une réponse lente d'écraser un calcul plus récent.
     */
    fun routeTo(destination: RoutePlace, origin: RoutePlace, mode: RouteMode = RouteMode.TRANSIT) {
        geocodeJob?.cancel()
        routeJob?.cancel()
        val issued = ++routeToken
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            search = MapSearchState(),
            navigation = null,
            route = RouteUiState(
                origin = origin,
                destination = destination,
                mode = mode,
                status = RouteLoadStatus.LOADING,
            ),
        )
        logger.info(LogDomain.NET, "Itinéraire ${mode.name.lowercase()} vers ${destination.label}.")
        routeJob = viewModelScope.launch {
            val outcome = runCatching {
                withContext(dispatchers.io) {
                    routingRepository.plan(
                        mode = mode,
                        from = origin.coordinate,
                        to = destination.coordinate,
                    )
                }
            }
            if (issued != routeToken) return@launch
            outcome.onSuccess { plan ->
                val latest = _state.value.route ?: return@onSuccess
                _state.value = _state.value.copy(
                    route = latest.copy(
                        status = RouteLoadStatus.READY,
                        plan = plan,
                        selectedId = plan.selectedId,
                        error = null,
                    ),
                )
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                logger.warn(LogDomain.NET, "Itinéraire en échec.", failure)
                val latest = _state.value.route ?: return@onFailure
                _state.value = _state.value.copy(
                    route = latest.copy(
                        status = RouteLoadStatus.ERROR,
                        error = failure.message,
                    ),
                )
            }
        }
    }

    fun selectRoute(id: String) {
        val current = _state.value.route ?: return
        if (current.selectedId == id) return
        _state.value = _state.value.copy(route = current.copy(selectedId = id))
    }

    fun setRouteMode(mode: RouteMode) {
        val current = _state.value.route ?: return
        if (current.mode == mode) return
        routeTo(current.destination, current.origin, mode)
    }

    /**
     * Engage le guidage sur le trajet retenu.
     *
     * Sans géométrie exploitable, on ne démarre pas : un bandeau sur un
     * trajet qui n'existe pas mentirait.
     */
    fun startGuidance(around: Coordinate? = null): Boolean {
        val route = _state.value.route ?: return false
        val candidate = route.selected ?: return false
        val plan = journeyFromCandidate(candidate, route.destination.label) ?: return false
        stopGuidanceInternal()
        // Ouvert ici et refermé avec le guidage : la trace couvre exactement
        // le trajet, sans les minutes passées à choisir la destination.
        trace = traces.startRecording()
        routeProgress.reset()
        if (around != null) routeProgress.advance(plan.points, around)
        val progress = journeyProgressAt(plan, routeProgress.t) ?: return false
        logger.info(LogDomain.MAP, "Guidage vers ${route.destination.label}.")
        publishNavigation(plan, progress, showingTrip = false, offRoute = false, signalLost = false)
        loadManeuversAround(0)
        return true
    }

    fun stopGuidance() {
        val current = _state.value
        if (current.navigation == null) return
        stopGuidanceInternal()
        _state.value = current.copy(navigation = null)
        logger.info(LogDomain.MAP, "Guidage arrêté.")
    }

    fun showTripSheet() {
        val current = _state.value.navigation ?: return
        if (current.showingTrip) return
        _state.value = _state.value.copy(navigation = current.copy(showingTrip = true))
    }

    fun hideTripSheet() {
        val current = _state.value.navigation ?: return
        if (!current.showingTrip) return
        _state.value = _state.value.copy(navigation = current.copy(showingTrip = false))
    }

    /**
     * Un point GPS pendant le guidage.
     *
     * La coordonnée **ne remonte pas** dans l'état recomposable. Seuls le
     * `t`, l'action et le résumé changent — quelques fois par seconde, pas
     * 120 fois.
     */
    fun onGuidanceFix(fix: LocationFix?) {
        val current = _state.value.navigation ?: return
        if (fix == null || !fix.isUsable) {
            if (!current.signalLost) {
                _state.value = _state.value.copy(navigation = current.copy(signalLost = true))
            }
            return
        }
        trace?.record(fix.toTracePoint())
        val match = routeProgress.advance(current.plan.points, fix.coordinate)
        val progress = journeyProgressAt(current.plan, routeProgress.t) ?: return
        var off = current.offRoute
        if (match != null) {
            if (offRoute.update(match.deviationMeters, fix.accuracyMeters)) off = true
            if (off && offRoute.rejoined(match.deviationMeters)) off = false
        }
        if (progress.legIndex != focusedLeg) loadManeuversAround(progress.legIndex)
        publishNavigation(current.plan, progress, showingTrip = current.showingTrip, offRoute = off, signalLost = false)
    }

    private fun publishNavigation(
        plan: JourneyPlan,
        progress: JourneyProgress,
        showingTrip: Boolean,
        offRoute: Boolean = this.offRoute.warning && _state.value.navigation?.offRoute == true,
        signalLost: Boolean = false,
    ) {
        val pinned = maneuversByLeg.entries.sortedBy { it.key }.flatMap { it.value }
        val action = nextAction(plan, progress, pinned)
        val summary = tripSummary(plan, progress, java.time.Instant.now())
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            search = MapSearchState(),
            navigation = NavigationUiState(
                plan = plan,
                progress = progress,
                action = action,
                summary = summary,
                offRoute = offRoute,
                signalLost = signalLost,
                routeBearing = routeProgress.bearing,
                showingTrip = showingTrip,
            ),
        )
    }

    private fun loadManeuversAround(legIndex: Int) {
        val plan = _state.value.navigation?.plan ?: return
        focusedLeg = legIndex
        for (index in legIndex until legIndex + MANEUVER_LOOKAHEAD) {
            if (index !in plan.legs.indices) continue
            if (maneuversByLeg.containsKey(index)) continue
            val generation = maneuverGeneration
            val leg = plan.legs[index]
            if (!leg.isRoad) {
                maneuversByLeg[index] = emptyList()
                continue
            }
            if (leg.maneuvers.isNotEmpty()) {
                maneuversByLeg[index] = pinManeuvers(
                    painted = plan.points,
                    raw = leg.maneuvers,
                    minT = leg.startT,
                    maxT = leg.endT,
                )
                continue
            }
            maneuversByLeg[index] = emptyList()
            viewModelScope.launch {
                val profile = if (leg.mode == LegMode.WALK) RoadProfile.PEDESTRIAN else RoadProfile.CAR
                val from = plan.points.getOrNull(leg.startIndex) ?: return@launch
                val to = plan.points.getOrNull(leg.endIndex) ?: return@launch
                val road = runCatching {
                    withContext(dispatchers.io) { roadRouter.route(from, to, profile) }
                }.getOrNull()
                if (generation != maneuverGeneration) return@launch
                maneuversByLeg[index] = if (road == null) {
                    emptyList()
                } else {
                    pinManeuvers(
                        painted = plan.points,
                        raw = road.maneuvers,
                        minT = leg.startT,
                        maxT = leg.endT,
                    )
                }
                val latest = _state.value.navigation ?: return@launch
                publishNavigation(latest.plan, latest.progress, latest.showingTrip, latest.offRoute, latest.signalLost)
            }
        }
    }

    private fun abandonRoute() {
        routeJob?.cancel()
        routeJob = null
        routeToken++
        stopGuidanceInternal()
    }

    /**
     * Referme la trace, sans faire attendre l'appelant.
     *
     * `close` vide la file et purge le dossier : deux entrées-sorties qu'on ne
     * met pas sur le chemin du bouton « Arrêter ». Le champ est vidé tout de
     * suite pour qu'un guidage relancé dans la foulée n'écrive pas dans la
     * trace du précédent.
     */
    private fun stopGuidanceInternal() {
        maneuverGeneration++
        maneuversByLeg.clear()
        focusedLeg = -1
        routeProgress.reset()
        offRoute.reset()
        val closing = trace ?: return
        trace = null
        viewModelScope.launch { closing.close() }
    }

    /** Le centre d'ouverture, tant qu'aucune position n'est connue. */
    val openingCenter: Coordinate get() = Coordinate.NANTES

    override fun onCleared() {
        stopFleetPolling()
        geocodeJob?.cancel()
        routeJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 15_000L
        const val MAX_BACKOFF_MS = 120_000L
        const val FLEET_LIMIT = 250
        const val RADIUS_MARGIN = 1.5
        const val MIN_RADIUS_M = 2_500.0
        const val MAX_RADIUS_M = 8_000.0
        const val DEFAULT_RADIUS_M = 2_500.0
        const val PLACE_DEBOUNCE_MS = 320L
        const val MANEUVER_LOOKAHEAD = 2
    }
}

/**
 * La mesure telle qu'on la consigne.
 *
 * [LocationFix.coordinate] est déjà lissée par l'ancre de mouvement : c'est
 * elle qu'on écrit, et non une mesure brute qu'on n'a pas — c'est aussi celle
 * sur laquelle le guidage a décidé, donc celle qui explique ses décisions.
 */
private fun LocationFix.toTracePoint() = GpsTracePoint(
    timestampMillis = timestampMillis,
    latitude = coordinate.latitude,
    longitude = coordinate.longitude,
    accuracyMeters = accuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    courseDegrees = courseDegrees,
    isMocked = isMocked,
)
