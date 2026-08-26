package io.aule.android.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.DepartureWatchAlert
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
import io.aule.android.core.model.NetworkLinesDigest
import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.canonicalLineName
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.geo.RouteProgress
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.JourneyPlan
import io.aule.android.core.model.JourneyProgress
import io.aule.android.core.model.LegMode
import io.aule.android.core.model.NextAction
import io.aule.android.core.model.NextActionKind
import io.aule.android.core.model.OffRouteDetector
import io.aule.android.core.model.PinnedManeuver
import io.aule.android.core.model.TripSummary
import io.aule.android.core.model.journeyFromCandidate
import io.aule.android.core.model.journeyProgressAt
import io.aule.android.core.model.nextAction
import io.aule.android.core.model.pinManeuvers
import io.aule.android.core.model.tripSummary
import io.aule.android.core.model.Timetable
import io.aule.android.core.model.TimetableException
import io.aule.android.core.model.TimetableFailureKind
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceRecorder
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.NetworkLineRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.SearchHistoryStore
import io.aule.android.core.model.repository.SavedPlaceRepository
import io.aule.android.core.model.repository.SavedPlacesStore
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.TimetableRepository
import io.aule.android.core.model.repository.VehicleRepository
import java.time.LocalDate
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
 * les ferme aussi, et pour une autre raison : **elle est elle-même le volet**,
 * celui du dessous, et rien ne se pose par-dessus lui sans le remplacer.
 */
data class MapSearchState(
    val query: String = "",
    val isActive: Boolean = false,
    val stops: List<StopSearchHit> = emptyList(),
    val places: List<Place> = emptyList(),
    val isGeocoding: Boolean = false,
    /**
     * Les destinations déjà demandées, la plus récente en tête.
     *
     * Elles ne se mêlent pas aux résultats : ce sont deux listes qui ne
     * répondent pas à la même question — « ce que vous cherchez » et « où vous
     * alliez ». C'est [showsHistory] qui décide laquelle s'affiche.
     */
    val history: List<Place> = emptyList(),
) {
    /**
     * L'historique remplace les résultats tant qu'aucune lettre n'est tapée.
     *
     * C'est le seul moment où il sert : une fois la frappe commencée, ce qu'on
     * cherche prime sur où l'on allait, et deux listes empilées feraient défiler
     * pour atteindre la première réponse.
     */
    val showsHistory: Boolean get() = isActive && query.trim().isEmpty() && history.isNotEmpty()

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
    /**
     * La ligne ouverte depuis le tableau d'un arrêt.
     *
     * Elle se pose **par-dessus** le volet de l'arrêt plutôt qu'à sa place :
     * l'arrêt reste sélectionné, sa pastille reste allumée sur la carte, et le
     * retour ramène au tableau d'où l'on vient. C'est le même geste que le
     * détail d'un trajet pendant le guidage — un cran de plus dans la même
     * chose, pas une autre chose.
     */
    val lineFocus: DepartureWatch? = null,
    val showingNearby: Boolean = false,
    /**
     * Le volet « Lignes du réseau », et la ligne qu'il met en avant.
     *
     * Les deux sont séparés, et ce n'est pas un raffinement : ils répondent à
     * deux questions différentes — « où passent les lignes ici ? » et « où passe
     * celle-ci ? » —, et la seconde doit pouvoir se poser sur une carte nue.
     */
    val showingNetworkLines: Boolean = false,
    val networkLineQuery: String = "",
    val focusedNetworkLine: String? = null,
    /**
     * La fiche de ligne, ouverte **par-dessus** l'inventaire.
     *
     * Elle ne remplace pas le volet : la ligne reste mise en avant sur la carte,
     * et le retour ramène à la liste d'où l'on vient.
     */
    val openedNetworkLine: String? = null,
    val search: MapSearchState = MapSearchState(),
    val route: RouteUiState? = null,
    val navigation: NavigationUiState? = null,
) {
    val hasSheet: Boolean
        get() = showingNetworkLines ||
            showingNearby ||
            lineFocus != null ||
            selectedStop != null ||
            selectedVehicle != null ||
            selectedPlace != null ||
            (route != null && navigation == null) ||
            navigation?.showingTrip == true
    val isNavigating: Boolean get() = navigation != null

    /** Vrai quand le geste de retour doit refermer la ligne, et non le volet. */
    val showingLine: Boolean get() = lineFocus != null
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
    /**
     * Combien de minutes par mode, pour la même destination.
     *
     * ## Pourquoi elles sont ici, et pas dans le plan
     *
     * Le moteur ne répond que pour **un** mode à la fois. Sans ces valeurs, le
     * sélecteur annonçait trois choix dont on ne pouvait comparer aucun : il
     * fallait toucher « À pied » — donc relancer un calcul, donc attendre, donc
     * perdre le trajet affiché — pour apprendre qu'on en avait pour vingt-cinq
     * minutes. Le choix se faisait à l'aveugle, ou pas du tout.
     *
     * Une entrée absente veut dire « pas encore » ; une entrée à `null` veut
     * dire « ce mode n'a rien rendu ». Les distinguer évite d'afficher un
     * indicateur d'attente sur un mode qui a déjà répondu qu'il ne pouvait pas.
     *
     * Elles survivent au changement de mode ([setRouteMode]) : ce sont les
     * durées d'**une destination**, pas d'un calcul, et les recalculer à chaque
     * bascule aurait triplé le réseau pour réafficher les mêmes chiffres.
     */
    val durations: Map<RouteMode, Int?> = emptyMap(),
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
) {

    /**
     * Le mode de la jambe qu'on est en train de parcourir.
     *
     * La caméra ne cadre pas un trottoir comme une quatre-voies : c'est
     * cette valeur, et non le mode du trajet entier, qui choisit le profil.
     * Un même itinéraire passe de la marche au tram puis à la marche, et le
     * cadrage doit suivre à chaque jambe.
     *
     * Le repli sur la marche n'est pas arbitraire : un index de jambe hors
     * bornes veut dire qu'on est au bout du trajet, donc à pied.
     */
    val currentLegMode: LegMode
        get() = plan.legs.getOrNull(progress.legIndex)?.mode ?: LegMode.WALK

    /**
     * La distance au prochain point qui demande de l'attention, en mètres.
     *
     * `null` quand il n'y en a pas en vue — on suit une ligne droite, ou on
     * est assis dans un tram. C'est ce que lit la caméra pour se rapprocher
     * d'un carrefour, et c'est pour cela que la montée en véhicule y figure
     * au même titre qu'un virage : trouver le bon quai à trente mètres
     * demande exactement le même cadre que prendre la bonne branche.
     */
    val maneuverMeters: Double?
        get() = action
            ?.takeIf { it.kind == NextActionKind.MANEUVER || it.kind == NextActionKind.BOARD }
            ?.leadMeters
}

class MapViewModel(
    internal val stopRepository: StopRepository,
    private val vehicleRepository: VehicleRepository,
    private val linePaletteRepository: LinePaletteRepository,
    private val traces: GpsTraceCatalog,
    /**
     * Le géocodeur. `internal` et non privé : l'éditeur de favori s'en sert
     * aussi, avec son propre champ et son propre débrayage — voir
     * [PlacePickerModel]. Le lui passer par l'écran évite de dupliquer un
     * paramètre de construction pour la même dépendance.
     */
    internal val placeRepository: PlaceSearchRepository,
    private val routingRepository: RoutingRepository,
    private val roadRouter: RoadRouter,
    internal val dispatchers: AuleDispatchers,
    val logger: AuleLogger,
    /**
     * Ce qu'on fait d'une alerte de veille : un son et une bannière système.
     * Muet par défaut — la composition les branche (`AuleRoot`), un test non.
     */
    private val onDepartureAlert: (DepartureWatchAlert, DepartureWatch) -> Unit = { _, _ -> },
    /**
     * La grille horaire théorique, et la session qui donne le droit de la lire.
     *
     * La session est une **lecture**, pas une valeur : un jeton capturé à la
     * création du `ViewModel` aurait vieilli avec l'écran, et la grille aurait
     * fini par se voir refuser sans que rien n'ait changé pour l'usager.
     * Absente, le volet le dit et n'affiche pas de grille — les tables GTFS ne
     * s'ouvrent pas aux anonymes.
     */
    private val timetableRepository: TimetableRepository? = null,
    /**
     * La desserte horodatée d'une course, pour le plan de ligne du véhicule
     * suivi. Absente, la fiche reste ce qu'elle était — un véhicule se suit
     * très bien sans son plan.
     */
    private val serviceRepository: DriverServiceRepository? = null,
    private val session: () -> AuthSession? = { null },
    private val today: () -> LocalDate = { LocalDate.now() },
    /**
     * Les destinations déjà demandées. Absent, la recherche fonctionne à
     * l'identique et n'affiche simplement rien à l'ouverture — c'est ce que
     * voient les tests qui ne parlent pas d'historique.
     */
    private val searchHistory: SearchHistoryStore? = null,
    /**
     * Les adresses favorites, sur l'appareil. Absent, la recherche n'affiche
     * aucun raccourci et n'en propose pas — ce que voient les tests qui n'en
     * parlent pas.
     */
    private val savedPlacesStore: SavedPlacesStore? = null,
    /**
     * Les mêmes, sur le compte. Absent, les favoris restent locaux : c'est
     * exactement ce qui se passe hors ligne, et rien d'autre ne change.
     */
    private val savedPlaceRepository: SavedPlaceRepository? = null,
    /**
     * L'inventaire des lignes, lu dans les assets. Absent, le volet « Lignes du
     * réseau » s'ouvre vide — ce que voient les tests qui n'en parlent pas.
     */
    private val networkLineRepository: NetworkLineRepository? = null,
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

    /**
     * La veille d'un passage, portée par l'écran et non par le volet.
     *
     * C'est tout l'intérêt : une veille sert à ne plus regarder. Elle survit
     * donc au volet qui l'a armée, et son état se collecte à part de
     * [state] — il change toutes les trente secondes, la carte non.
     */
    /**
     * La grille du jour, à côté du temps réel et non dedans.
     *
     * Deux modèles pour un seul volet, parce que ce sont deux mondes : l'un se
     * rafraîchit toutes les trente secondes et se périme, l'autre se charge une
     * fois par journée demandée et reste vrai. Les fondre aurait fait sonder un
     * catalogue au rythme d'un flux temps réel.
     */
    internal val timetable = TimetableModel(
        repository = timetableRepository ?: NoTimetables,
        session = session,
        dispatchers = dispatchers,
        scope = viewModelScope,
        logger = logger,
        today = today,
    )

    /**
     * Le plan de ligne du véhicule suivi.
     *
     * Encore un modèle à côté du temps réel, et pour la même raison que la
     * grille : une desserte se charge une fois, quand le temps réel se sonde
     * en boucle. Ce qui bouge dans le plan — la coupure entre desservi et à
     * desservir — se recalcule à l'affichage, à partir de la position qui
     * arrive déjà.
     */
    /**
     * La desserte de la ligne dont la fiche est ouverte.
     *
     * Encore un modèle à côté de l'état de la carte, et pour la même raison que
     * les autres : il ne se charge qu'à l'ouverture d'une fiche, quand `state`
     * change à chaque geste. Les fondre ferait recomposer l'écran entier au
     * rythme d'un volet qui, la plupart du temps, n'est pas ouvert.
     */
    internal val lineStops = LineStopsModel(
        repository = serviceRepository,
        session = session,
        dispatchers = dispatchers,
        scope = viewModelScope,
        logger = logger,
        stops = stopRepository,
    )

    internal val vehicleTrip = VehicleTripModel(
        repository = serviceRepository,
        session = session,
        dispatchers = dispatchers,
        scope = viewModelScope,
        logger = logger,
    )

    /**
     * Les adresses favorites — Domicile, Travail, et le reste.
     *
     * Encore un modèle à côté de [state], et pour la raison inverse des autres :
     * ceux-là se chargent depuis le réseau, celui-ci est **déjà là**. Il se lit
     * du disque à la construction, sans attendre, parce que la recherche montre
     * ses raccourcis à l'instant où elle s'ouvre.
     */
    internal val savedPlaces = SavedPlacesModel(
        store = savedPlacesStore,
        repository = savedPlaceRepository,
        session = session,
        dispatchers = dispatchers,
        scope = viewModelScope,
        logger = logger,
    )

    internal val departureWatch = DepartureWatchModel(
        repository = stopRepository,
        dispatchers = dispatchers,
        scope = viewModelScope,
        logger = logger,
        onAlert = { alert, watch -> onDepartureAlert(alert, watch) },
    )

    private var pollJob: Job? = null
    private var geocodeJob: Job? = null
    private var routeJob: Job? = null
    private var previewJob: Job? = null
    private var routeToken = 0
    private var maneuverGeneration = 0
    private val maneuversByLeg = mutableMapOf<Int, List<PinnedManeuver>>()
    private val routeProgress = RouteProgress()

    /** La trace du guidage en cours. `null` hors guidage, et en production. */
    private var trace: GpsTraceRecorder? = null

    /**
     * L'horodatage du dernier point consigné.
     *
     * La boucle de guidage relit la position chaque seconde, mais le
     * fournisseur n'en publie une que toutes les huit environ : sans ce
     * garde-fou, sept lignes sur huit répétaient la précédente. Une trace doit
     * dire ce que le GPS a **mesuré**, pas à quelle cadence on l'a relu — et
     * l'écart entre deux horodatages consignés rend justement visible le
     * rythme du fournisseur, que la répétition masquait.
     */
    private var lastTracedMillis: Long? = null
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
                    departureWatch.onFleetSnapshot(snapshot)
                    val current = _state.value.selectedVehicle
                    val refreshed = current?.let { selected ->
                        snapshot.vehicles.find { it.id == selected.id }
                            // Le théorique qu'on regardait a pu être remplacé
                            // par sa mesure : même bus, autre identifiant. Le
                            // perdre ici figerait la fiche sur une position qui
                            // ne bougera plus, et laisserait la caméra suivre un
                            // identifiant que plus personne ne publie.
                            ?: snapshot.vehicles.find { it.twinId == selected.id }
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
                    departureWatch.onFleetSnapshot(stale)
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

    // ------------------------------------------------- lignes du réseau

    /**
     * Les lignes du réseau, rangées et filtrées.
     *
     * Lues **une fois** au premier besoin puis gardées : l'index vit dans les
     * assets, sa lecture ne dépend d'aucun réseau, et 138 lignes ne changent pas
     * pendant qu'un volet est ouvert.
     */
    private var networkLines: List<TransitLine> = emptyList()

    private val _networkDigest = MutableStateFlow(NetworkLinesDigest(emptyList()))
    val networkDigest: StateFlow<NetworkLinesDigest> = _networkDigest.asStateFlow()

    fun openNetworkLines() {
        if (_state.value.showingNetworkLines) return
        releaseLine()
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            showingNetworkLines = true,
            networkLineQuery = "",
            openedNetworkLine = null,
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            lineFocus = null,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
        viewModelScope.launch { loadNetworkLines() }
    }

    /**
     * Referme le volet **et éteint les tracés avec lui**.
     *
     * Les deux vont ensemble : le réseau est quelque chose qu'on demande, et le
     * laisser peint après la fermeture recouvrirait la carte d'un lacis que plus
     * rien n'explique.
     */
    fun closeNetworkLines() {
        if (!_state.value.showingNetworkLines) return
        lineStops.close()
        _state.value = _state.value.copy(
            showingNetworkLines = false,
            networkLineQuery = "",
            focusedNetworkLine = null,
            openedNetworkLine = null,
        )
    }

    /**
     * Ouvre la fiche d'une ligne : ses arrêts, dans l'ordre, par sens.
     *
     * Elle **met aussi la ligne en avant** : ouvrir sa desserte sans la montrer
     * sur la carte demanderait de faire deux gestes pour une seule intention.
     */
    fun openNetworkLine(name: String) {
        val canonical = canonicalLineName(name)
        if (_state.value.openedNetworkLine == canonical) return
        _state.value = _state.value.copy(
            openedNetworkLine = canonical,
            focusedNetworkLine = canonical,
        )
        lineStops.open(canonical)
    }

    /** Referme la fiche et **garde l'inventaire ouvert** : c'est d'où l'on vient. */
    fun closeNetworkLine() {
        if (_state.value.openedNetworkLine == null) return
        _state.value = _state.value.copy(openedNetworkLine = null)
        lineStops.close()
    }

    /** La ligne dont la fiche est ouverte, avec tout ce que l'index en sait. */
    fun openedLine(): TransitLine? {
        val name = _state.value.openedNetworkLine ?: return null
        return networkLines.firstOrNull { it.match == name }
    }

    fun setNetworkLineQuery(query: String) {
        if (_state.value.networkLineQuery == query) return
        _state.value = _state.value.copy(networkLineQuery = query)
        _networkDigest.value = NetworkLinesDigest.build(networkLines, query)
    }

    /**
     * Met une ligne en avant, ou retire la mise en avant si c'est la même.
     *
     * Retoucher le rang déjà désigné **éteint** la mise en avant : c'est le geste
     * qu'on fait sans réfléchir pour revenir en arrière, et il n'a nulle part
     * ailleurs où aller.
     */
    fun focusNetworkLine(name: String?) {
        val canonical = name?.let(::canonicalLineName)
        val next = if (canonical != null && canonical == _state.value.focusedNetworkLine) {
            null
        } else {
            canonical
        }
        if (next == _state.value.focusedNetworkLine) return
        _state.value = _state.value.copy(focusedNetworkLine = next)
    }

    /** La ligne désignée, avec son cadre — ce qu'il faut pour l'emmener à l'écran. */
    fun focusedLine(): TransitLine? {
        val name = _state.value.focusedNetworkLine ?: return null
        return networkLines.firstOrNull { it.match == name }
    }

    private suspend fun loadNetworkLines() {
        if (networkLines.isEmpty()) {
            networkLines = runCatching {
                withContext(dispatchers.io) { networkLineRepository?.allLines().orEmpty() }
            }.getOrElse { failure ->
                if (failure is CancellationException) throw failure
                logger.warn(LogDomain.MAP, "Inventaire des lignes illisible.", failure)
                emptyList()
            }
            logger.info(LogDomain.MAP, "Inventaire du réseau : ${networkLines.size} ligne(s).")
        }
        _networkDigest.value = NetworkLinesDigest.build(networkLines, _state.value.networkLineQuery)
    }

    // ---------------------------------------------------------------- sélection

    fun select(stop: TransitStop) {
        releaseLine()
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = stop,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            lineFocus = null,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun select(vehicle: TransportVehicle) {
        releaseLine()
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = vehicle,
            selectedPlace = null,
            showingNearby = false,
            lineFocus = null,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    fun select(place: Place) {
        releaseLine()
        geocodeJob?.cancel()
        abandonRoute()
        searchHistory?.remember(place)
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = place,
            showingNearby = false,
            lineFocus = null,
            search = MapSearchState(),
            route = null,
            navigation = null,
        )
    }

    /**
     * Un arrêt choisi dans la recherche.
     *
     * Il entre dans l'historique **comme lieu d'arrêt** — mode renseigné — et
     * non comme adresse : c'est le mode, jamais le libellé, qui dira plus tard
     * qu'on peut demander ses passages. Une adresse peut porter le nom d'un
     * arrêt — « rue de la Beaujoire » — et l'interroger rendrait les lignes d'un
     * lieu où l'on ne va pas.
     *
     * L'écran, lui, reçoit l'arrêt du catalogue et non ce lieu : la fiche
     * d'arrêt a besoin de ses quais.
     */
    fun select(hit: StopSearchHit) {
        searchHistory?.remember(
            Place(
                label = hit.label,
                coordinate = hit.coordinate,
                stopMode = hit.mode,
            ),
        )
        select(hit.representative)
    }

    /**
     * On suit ce véhicule, ou plus aucun : le plan de ligne suit la caméra.
     *
     * L'état du suivi vit dans le contrôleur de carte, pas ici — c'est un
     * cadrage, pas une donnée d'écran. L'écran, qui voit les deux, dit au
     * modèle quel véhicule mérite sa desserte.
     */
    fun followVehicle(vehicle: TransportVehicle?) = vehicleTrip.follow(vehicle)

    // ------------------------------------------------------------------- veille

    /**
     * Ouvre une ligne du tableau d'un arrêt.
     *
     * On y va pour deux raisons, et une seule suffit à justifier le volet : voir
     * **tous** les horaires — le tableau n'en montre que trois — ou demander à
     * être prévenu. La rangée touchée porte déjà tout ce qu'il faut pour les
     * deux, et le mode vient de l'arrêt quand le passage ne le publie pas.
     */
    fun openLine(stop: TransitStop, row: DepartureRow) {
        val watch = DepartureWatch(
            stopName = stop.departuresKey,
            line = row.line,
            destination = row.destination,
            lineColor = row.lineColor,
            mode = row.mode ?: stop.mode,
            stopCoordinate = stop.coordinate,
        )
        departureWatch.open(watch)
        timetable.open(watch)
        _state.value = _state.value.copy(lineFocus = watch)
    }

    /**
     * Revient sur la ligne veillée, depuis la pastille de la carte.
     *
     * L'arrêt est resélectionné quand le catalogue le connaît : c'est ce qui
     * rallume sa pastille sur la carte et donne au geste de retour un endroit
     * où revenir. S'il ne le connaît pas — catalogue encore en chargement — la
     * ligne s'ouvre quand même : elle porte le nom de l'arrêt, et c'est tout ce
     * qu'il faut pour lire des horaires.
     */
    fun reopenWatch() {
        val target = departureWatch.state.value.armed ?: return
        val stop = _state.value.stops.firstOrNull { it.departuresKey == target.stopName }
        if (stop != null) {
            select(stop)
        }
        departureWatch.open(target)
        timetable.open(target)
        _state.value = _state.value.copy(lineFocus = target)
    }

    /** Referme la ligne et revient au tableau de l'arrêt. La veille, elle, reste. */
    fun closeLine() {
        if (_state.value.lineFocus == null) return
        departureWatch.close()
        timetable.close()
        _state.value = _state.value.copy(lineFocus = null)
    }

    /** La journée qu'on regarde dans la grille. */
    fun showTimetableDate(date: LocalDate) = timetable.setDate(date)

    /** Après un échec réseau : la reprise se demande, elle ne s'improvise pas. */
    fun retryTimetable() = timetable.retry()

    /**
     * Le bouton unique du volet de ligne : on veille, ou on ne veille plus.
     *
     * Une seule veille à la fois, et c'est un choix : deux alertes concurrentes
     * demanderaient de dire laquelle a parlé, donc une liste, donc un écran de
     * gestion — pour un besoin qui, sur le terrain, s'exprime au singulier. On
     * attend **un** bus.
     */
    /**
     * Le second bouton : la carte accompagne le véhicule, ou le laisse partir.
     *
     * Séparé de l'alerte, et pas par symétrie : on arme une alerte **pour
     * ranger son téléphone**, on demande un focus **pour le regarder**. Les
     * joindre obligeait à accepter l'un pour obtenir l'autre.
     */
    fun toggleFocus() {
        departureWatch.setFocused(!departureWatch.state.value.isFocused)
    }

    fun toggleWatch() {
        if (departureWatch.state.value.isArmed) {
            departureWatch.disarm()
        } else {
            departureWatch.arm()
        }
    }

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
        releaseLine()
        geocodeJob?.cancel()
        abandonRoute()
        _state.value = _state.value.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = true,
            lineFocus = null,
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
        releaseLine()
        _state.value = current.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            lineFocus = null,
        )
    }

    /**
     * Le volet de ligne disparaît avec celui qui le portait.
     *
     * La **veille**, elle, ne disparaît pas : c'est [DepartureWatchModel.close]
     * qui arbitre — il arrête le sondage si personne ne regarde et que rien
     * n'est armé, et le laisse tourner sinon.
     */
    private fun releaseLine() {
        if (_state.value.lineFocus == null) return
        departureWatch.close()
        timetable.close()
    }

    /** Ferme le volet, y compris « autour de vous » et l'itinéraire. */
    fun dismissSheet() {
        val current = _state.value
        // Le volet du réseau se ferme par son propre chemin : il emporte avec lui
        // les tracés et la ligne désignée, ce que le reste de cette fonction ne
        // sait pas faire.
        if (current.showingNetworkLines) {
            closeNetworkLines()
            return
        }
        if (current.selectedStop == null &&
            current.selectedVehicle == null &&
            current.selectedPlace == null &&
            !current.showingNearby &&
            current.route == null &&
            current.navigation == null
        ) {
            return
        }
        releaseLine()
        abandonRoute()
        _state.value = current.copy(
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            lineFocus = null,
            route = null,
            navigation = null,
        )
    }

    // ---------------------------------------------------------------- recherche

    /**
     * La recherche s'ouvre : le socle monte, et ce qui était présenté cède.
     *
     * Ce n'est pas une préférence d'écran, c'est la même règle qu'ailleurs — un
     * seul volet à la fois. La carte, elle, reste montée derrière.
     */
    fun activateSearch() {
        val current = _state.value
        if (current.search.isActive) return
        abandonRoute()
        // Relu à chaque ouverture, et non gardé en mémoire depuis le démarrage :
        // c'est le seul instant où il sert, et le lire ici garantit qu'il est à
        // jour même si un autre écran l'a fait grandir entre-temps.
        _state.value = current.copy(
            search = current.search.copy(
                isActive = true,
                history = searchHistory?.read().orEmpty(),
            ),
            selectedStop = null,
            selectedVehicle = null,
            selectedPlace = null,
            showingNearby = false,
            route = null,
            navigation = null,
        )
        // La frappe gardée par [collapseSearch] reprend son travail. Sans cela,
        // rouvrir sur « ranz » aurait montré « Aucun résultat pour ranz » —
        // c'est-à-dire une réponse à une question que personne n'avait reposée.
        val kept = current.search.query
        if (kept.isNotBlank()) setSearchQuery(kept)
        // Le compte a pu enregistrer une adresse depuis un autre appareil. La
        // liste locale est déjà à l'écran ; ceci ne fait que la rattraper, et
        // se débraye tout seul si elle vient d'être rattrapée.
        savedPlaces.sync()
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

    /**
     * Le volet redescend au socle : la recherche se referme, **le champ garde
     * sa frappe**.
     *
     * Repousser n'est pas annuler. Un volet à paliers invite l'aller-retour —
     * on redescend pour revoir la carte, on remonte pour affiner — et effacer
     * ici ferait payer ce geste au prix d'une saisie entière. C'est
     * `DestinationSearchModel.collapse()` sur iOS, et l'argument y est le même.
     *
     * Ce qui part, en revanche, ce sont les **réponses** : l'appel au géocodeur
     * est coupé, et les listes sont vidées plutôt que gardées au chaud sous un
     * volet fermé. Elles reviennent à la réouverture — voir [activateSearch].
     *
     * Pour effacer, il y a la croix du champ, qui repasse par [setSearchQuery].
     */
    fun collapseSearch() {
        geocodeJob?.cancel()
        geocodeJob = null
        val current = _state.value
        if (!current.search.isActive) return
        _state.value = current.copy(
            search = current.search.copy(
                isActive = false,
                stops = emptyList(),
                places = emptyList(),
                isGeocoding = false,
            ),
        )
    }

    // ------------------------------------------------------------- itinéraire

    /**
     * Calcule un itinéraire. L'origine est la position, ou le centre de la
     * carte si on ne l'a pas — le départ ne se demande plus, il se trouve.
     *
     * Un jeton empêche une réponse lente d'écraser un calcul plus récent.
     */
    fun routeTo(
        destination: RoutePlace,
        origin: RoutePlace,
        mode: RouteMode = RouteMode.TRANSIT,
        /**
         * Vrai quand seule la variante change : les durées déjà connues restent,
         * et l'aperçu ne repart pas. Voir [RouteUiState.durations].
         */
        keepDurations: Boolean = false,
    ) {
        geocodeJob?.cancel()
        routeJob?.cancel()
        if (!keepDurations) previewJob?.cancel()
        val known = if (keepDurations) _state.value.route?.durations.orEmpty() else emptyMap()
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
                durations = known,
            ),
        )
        if (!keepDurations) previewDurations(origin, destination, issued)
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
                        // Le mode demandé n'est pas redemandé par l'aperçu : sa
                        // durée est déjà là, dans le plan qu'on vient de lire.
                        durations = latest.durations +
                            (mode to plan.selected()?.durationMinutes),
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

    /**
     * Les durées des **autres** modes, pour la destination qu'on vient de poser.
     *
     * ## Ce que ça coûte, et ce que ça évite
     *
     * Deux appels de plus — le mode demandé, lui, répond déjà. C'est le prix
     * d'un sélecteur où l'on compare avant de choisir, au lieu d'un sélecteur
     * qu'il faut essayer pour savoir. Ils partent **en parallèle** du calcul
     * principal et ne le retardent pas : le trajet s'affiche à son rythme, et
     * les chiffres se posent sur les onglets quand ils arrivent.
     *
     * Et deux appels seulement par **destination** : changer de mode n'en
     * relance aucun, la réponse est déjà là.
     *
     * ## Un aperçu muet ne fait rien échouer
     *
     * Un mode sans réponse s'affiche sans chiffre, et c'est tout. Le trajet
     * demandé, lui, est ailleurs et n'en dépend pas — faire remonter cet échec
     * poserait un bandeau d'erreur sur un itinéraire parfaitement calculé.
     */
    private fun previewDurations(
        origin: RoutePlace,
        destination: RoutePlace,
        issued: Int,
    ) {
        val repository = routingRepository
        previewJob = viewModelScope.launch {
            RouteMode.entries
                .filter { it != _state.value.route?.mode }
                .forEach { mode ->
                    val minutes = runCatching {
                        withContext(dispatchers.io) {
                            repository.plan(
                                mode = mode,
                                from = origin.coordinate,
                                to = destination.coordinate,
                            )
                        }
                    }.getOrElse { failure ->
                        if (failure is CancellationException) throw failure
                        logger.warn(LogDomain.NET, "Aperçu ${mode.apiValue} muet.", failure)
                        null
                    }?.selected()?.durationMinutes

                    if (issued != routeToken) return@launch
                    val latest = _state.value.route ?: return@launch
                    _state.value = _state.value.copy(
                        route = latest.copy(durations = latest.durations + (mode to minutes)),
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
        routeTo(current.destination, current.origin, mode, keepDurations = true)
    }

    /**
     * Retourne le trajet : ce qui était l'arrivée devient le départ.
     *
     * Le calcul repart entièrement, et c'est voulu — un itinéraire n'est pas
     * symétrique. Les sens uniques, les arrêts desservis dans une seule
     * direction et l'heure elle-même changent la réponse : à 17 h, l'aller
     * existe et le retour peut n'avoir plus de correspondance.
     *
     * « Ma position » part avec sa coordonnée, pas avec son nom : une fois en
     * arrivée, elle désigne l'endroit d'où l'on vient — celui où l'on était au
     * moment du premier calcul — et non l'endroit où l'on sera tout à l'heure.
     */
    fun swapRouteEnds() {
        val current = _state.value.route ?: return
        routeTo(
            destination = current.origin,
            origin = current.destination,
            mode = current.mode,
        )
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
        val plan = journeyFromCandidate(candidate, route.mode, route.destination.label) ?: return false
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
        if (fix.timestampMillis != lastTracedMillis) {
            lastTracedMillis = fix.timestampMillis
            trace?.record(fix.toTracePoint())
        }
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
        // L'aperçu meurt avec le trajet : deux calculs de comparaison pour un
        // itinéraire qu'on vient d'abandonner sont deux requêtes payées pour un
        // sélecteur que plus personne ne regarde.
        previewJob?.cancel()
        previewJob = null
        routeToken++
        stopGuidanceInternal()
    }

    /**
     * Referme la trace. Le champ est vidé d'abord, pour qu'un guidage relancé
     * dans la foulée n'écrive pas dans la trace du précédent.
     */
    private fun stopGuidanceInternal() {
        maneuverGeneration++
        maneuversByLeg.clear()
        focusedLeg = -1
        routeProgress.reset()
        offRoute.reset()
        lastTracedMillis = null
        val closing = trace
        trace = null
        closing?.close()
    }

    /**
     * Ce que fait le système quand l'écran s'en va.
     *
     * `onCleared` est protégé : sans cette porte, la seule sortie qui a
     * réellement posé problème serait aussi la seule qu'aucun test ne
     * couvrirait.
     */
    internal fun clearForTest() = onCleared()

    /** Le centre d'ouverture, tant qu'aucune position n'est connue. */
    val openingCenter: Coordinate get() = Coordinate.NANTES

    /**
     * L'écran s'en va — et un guidage peut être en cours.
     *
     * C'est la sortie qu'on oublie : on quitte l'application par le geste de
     * retour, sans passer par « Arrêter ». Sans cette ligne, la trace du
     * trajet restait ouverte et son tampon partait avec le processus.
     */
    override fun onCleared() {
        stopFleetPolling()
        departureWatch.clear()
        timetable.close()
        vehicleTrip.close()
        geocodeJob?.cancel()
        routeJob?.cancel()
        trace?.close()
        trace = null
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

/**
 * Le repli quand aucune source d'horaires n'est branchée — les tests, et toute
 * variante de l'application qui n'aurait pas de catalogue.
 *
 * Il **lève**, il ne rend pas une grille vide : une journée sans passage et une
 * source absente n'appellent pas le même écran, et c'est précisément la
 * confusion que le contrat existe pour éviter.
 */
private object NoTimetables : TimetableRepository {
    override suspend fun timetable(
        session: AuthSession,
        stopName: String,
        line: String,
        destination: String,
        date: LocalDate,
    ): Timetable = throw TimetableException(TimetableFailureKind.NOT_CONFIGURED)
}
