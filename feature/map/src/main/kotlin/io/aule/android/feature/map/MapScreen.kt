package io.aule.android.feature.map

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.resolvedNight
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.location.LocationPurpose
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapController
import io.aule.android.core.map.MapZoom
import io.aule.android.core.map.camera.CameraMode
import io.aule.android.core.map.layer.DestinationLayer
import io.aule.android.core.map.layer.HandoverLayer
import io.aule.android.core.map.layer.LineStopLayer
import io.aule.android.core.map.layer.RouteLayer
import io.aule.android.core.map.layer.TransitLinesLayer
import io.aule.android.core.map.layer.StopsLayer
import io.aule.android.core.map.layer.UserPuckLayer
import io.aule.android.core.map.layer.VehiclesLayer
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.RoutePlace
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.shortLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * L'écran carte.
 *
 * La carte est le socle : elle est montée une fois et tout vient se poser
 * par-dessus. Les panneaux de détail s'ouvrent en volet au-dessus d'elle,
 * sans jamais la remplacer.
 *
 * Rien ne barre le bas de la fenêtre. Tout ce qu'on peut engager depuis la
 * carte — prendre un service, relever un collègue, calculer un itinéraire,
 * lister les lignes, signaler un événement, voir les correspondances — tient
 * dans le menu flottant ancré à droite, et ne se déplie qu'au moment où on le
 * cherche. Six actions rares n'ont pas à occuper six cibles permanentes, et la
 * carte garde son bord bas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    onOpenMenu: (() -> Unit)? = null,
    onSubmitReport: (suspend (DriverReport) -> Unit)? = null,
    onStartService: (() -> Unit)? = null,
    serviceActive: Boolean = false,
    onOpenActiveService: (() -> Unit)? = null,
    onOpenHandover: (() -> Unit)? = null,
    serviceLiveHandover: HandoverSummary? = null,
    serviceNotice: ServiceNotice? = null,
    onDismissServiceNotice: () -> Unit = {},
    handoverFix: HandoverFix? = null,
    handoverStop: Coordinate? = null,
    handoverStopArrived: Boolean = false,
    hideChrome: Boolean = false,
    /**
     * L'archive des tracés, une fois recopiée là où le lecteur PMTiles sait la
     * lire. `null` tant que la copie n'a pas abouti — au premier lancement, le
     * temps de sortir 3,4 Mo des assets — et le calque n'est alors pas monté.
     *
     * Cela ne dérange pas l'empilement : les tracés s'insèrent **sous** les
     * étiquettes du fond, quand tous les autres calques s'ajoutent par-dessus.
     * Arriver en retard ne les fait donc pas passer devant.
     */
    transitArchiveUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // La veille se collecte à part de l'état de la carte : elle bat toutes les
    // trente secondes pour un volet et un marqueur, quand `state` ne change
    // qu'aux gestes. Les fondre ferait recomposer l'écran entier au rythme du
    // fournisseur temps réel.
    val watchState by viewModel.departureWatch.state.collectAsStateWithLifecycle()
    // La fiche horaire vit à part du temps réel : elle se charge une fois par
    // journée demandée, là où la veille bat toutes les trente secondes.
    val timetableState by viewModel.timetable.state.collectAsStateWithLifecycle()
    // La desserte du véhicule suivi, elle aussi à part : elle se charge une fois
    // par véhicule suivi, quand la carte, elle, bat toutes les quinze secondes.
    val tripState by viewModel.vehicleTrip.state.collectAsStateWithLifecycle()

    // Collecté ici et non dans le volet : c'est la **carte** qui doit savoir
    // quel arrêt de la desserte est visé, et elle n'a pas de volet sous la main.
    val lineStopsState by viewModel.lineStops.state.collectAsStateWithLifecycle()
    val night = resolvedNight()
    val ambiance = MapAmbiance.of(night)
    val view = LocalView.current
    var showingReport by rememberSaveable { mutableStateOf(false) }
    // Les mentions légales de la carte, ouvertes par la pastille ⓘ. Elles ne
    // passent pas par [MapUiState] : ce n'est pas un objet de la carte qu'on
    // sélectionne, c'est une obligation de licence qu'on consulte.
    var showingLegal by rememberSaveable { mutableStateOf(false) }
    // Le menu d'actions, déplié ou non. `rememberSaveable` parce qu'une
    // rotation ne doit pas le refermer sous le doigt ; refermé à la main dès
    // que le chrome s'en va, sinon il attendrait rouvert le retour du volet.
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    // Un guidage s'utilise posé sur un support : l'extinction de l'écran
    // couperait aussi le flux. Le verrou ne vit que pendant NAVIGATING.
    DisposableEffect(state.isNavigating) {
        view.keepScreenOn = state.isNavigating
        onDispose { view.keepScreenOn = false }
    }

    val transitLinesLayer = remember(controller, transitArchiveUrl) {
        transitArchiveUrl?.let { url ->
            TransitLinesLayer(archiveUrl = url, logger = viewModel.logger)
                .also { controller.registry.register(it) }
        }
    }

    val stopsLayer = remember(controller) {
        StopsLayer(onSelect = { stop ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            viewModel.select(stop)
            if (controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE) {
                controller.setCameraMode(CameraMode.FREE_EXPLORE)
            }
        }, logger = viewModel.logger)
            .also { controller.registry.register(it) }
    }

    // Au-dessus du catalogue, sous les véhicules : l'arrêt qu'on est allé voir
    // doit primer sur les pastilles ordinaires, et céder devant ce qui roule.
    val lineStopLayer = remember(controller) {
        LineStopLayer().also { controller.registry.register(it) }
    }

    // L'ordre d'enregistrement **est** l'ordre de superposition : arrêts,
    // arrêt visé, véhicules, relève, destination, tracé, puck. Le puck doit
    // rester au-dessus de tout — y compris du ruban d'itinéraire.
    val vehiclesLayer = remember(controller) {
        VehiclesLayer(onSelect = { vehicle ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            viewModel.select(vehicle)
        }).also { controller.registry.register(it) }
    }
    val handoverLayer = remember(controller) {
        HandoverLayer().also { controller.registry.register(it) }
    }
    val destinationLayer = remember(controller) {
        DestinationLayer().also { controller.registry.register(it) }
    }
    val routeLayer = remember(controller) {
        RouteLayer().also { controller.registry.register(it) }
    }
    val puckLayer = remember(controller) {
        UserPuckLayer().also { controller.registry.register(it) }
    }
    val followState = remember { CameraFollowState() }

    LaunchedEffect(transitLinesLayer, state.showingNetworkLines) {
        transitLinesLayer?.setVisible(state.showingNetworkLines)
    }
    LaunchedEffect(transitLinesLayer, state.focusedNetworkLine) {
        transitLinesLayer?.setFocus(state.focusedNetworkLine)
    }
    // Emmener la carte sur la ligne désignée. Le cadre vient de l'index — les
    // 2 715 tronçons vivent dans les tuiles, pas ici —, et c'est ce qui rend le
    // cadrage instantané et hors ligne.
    LaunchedEffect(state.focusedNetworkLine) {
        val bounds = viewModel.focusedLine()?.bounds
        if (bounds == null) {
            // Plus de ligne désignée : la caméra n'a plus de tracé à tenir, et
            // le volet qui bouge ne doit pas la ramener sur celui d'avant.
            controller.releaseFrame()
            return@LaunchedEffect
        }
        controller.frame(listOf(bounds.southWest, bounds.northEast))
    }

    LaunchedEffect(state.stops) {
        stopsLayer.setStops(state.stops)
    }
    LaunchedEffect(state.selectedStop) {
        stopsLayer.setSelected(state.selectedStop)
    }
    // Le véhicule que la carte met en avant : celui qu'on vient de toucher
    // d'abord — c'est un geste, il prime — puis celui qu'une veille a reconnu.
    // Un seul marqueur désigné à la fois, sans quoi « lequel est le mien ? »
    // redevient une question.
    val highlightedVehicleId = state.selectedVehicle?.id ?: watchState.vehicleId
    LaunchedEffect(highlightedVehicleId, state.selectedVehicle) {
        vehiclesLayer.setSelected(highlightedVehicleId)
        if (highlightedVehicleId == null) {
            // Plus personne à suivre : on efface aussi la dernière pose tenue,
            // sinon la caméra retournerait la chercher au prochain suivi.
            followState.forgetVehicle()
        } else {
            if (highlightedVehicleId != followState.selectedVehicleId) {
                followState.lastVehiclePose = null
            }
            followState.selectedVehicleId = highlightedVehicleId
            followState.selectedVehicleSpeed = state.selectedVehicle?.speedMps ?: 0.0
        }
    }
    LaunchedEffect(state.selectedPlace) {
        destinationLayer.setCoordinate(state.selectedPlace?.coordinate)
    }
    LaunchedEffect(state.route?.selected?.id, state.route?.status) {
        val route = state.route
        val candidate = route?.selected?.takeIf { route.status == RouteLoadStatus.READY }
        routeLayer.setTrace(candidate, route?.origin, route?.destination)
    }

    LaunchedEffect(vehiclesLayer) {
        viewModel.fleet.collect { snapshot ->
            vehiclesLayer.apply(snapshot)
            val watched = viewModel.departureWatch.state.value.vehicleId
            if (watched != null && viewModel.state.value.selectedVehicle == null) {
                followState.selectedVehicleSpeed =
                    snapshot.vehicles.find { it.id == watched }?.speedMps ?: 0.0
            }
        }
    }
    var framedHandover by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        controller.onRegionSettled = { center, _, radius ->
            viewModel.onRegionSettled(center, radius)
        }
        controller.onTapMap = { viewModel.clearSelection() }
        controller.onMapLoadFailure = viewModel::reportMapError
        onDispose {
            controller.onRegionSettled = null
            controller.onTapMap = null
            controller.onMapLoadFailure = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        location.markPermissionRequested()
        location.refreshAuthorization()
        if (location.authorization.value.allowsUpdates) {
            location.start(trackingPurpose(viewModel, serviceActive))
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Un refus n'empêche pas le guidage : la notification peut rester
        // silencieuse, le service de premier plan tourne quand même.
    }

    LifecycleResumeEffect(viewModel, location, serviceActive) {
        viewModel.startFleetPolling()
        // Plus de dialogue système à froid : c'est [WelcomeScreen] qui demande,
        // après avoir dit à quoi la position sert. Ici on ne fait que démarrer
        // le flux quand l'autorisation est déjà là.
        when (location.authorization.value) {
            LocationAuthorization.GRANTED, LocationAuthorization.REDUCED_ACCURACY ->
                location.start(trackingPurpose(viewModel, serviceActive))
            else -> Unit
        }
        onPauseOrDispose {
            viewModel.stopFleetPolling()
            if (!viewModel.state.value.isNavigating && !serviceActive) {
                location.stop()
            }
        }
    }

    LaunchedEffect(state.isNavigating, serviceActive) {
        if (location.authorization.value.allowsUpdates) {
            location.setPurpose(trackingPurpose(state.isNavigating, serviceActive))
        }
    }
    LaunchedEffect(serviceActive) {
        if (serviceActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(controller, puckLayer, vehiclesLayer, location) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                tickCamera(controller, puckLayer, vehiclesLayer, location, followState)
                delay(CAMERA_TICK_MS)
            }
        }
    }

    // Le soleil, à part et bien plus lentement : il avance d'un quart de degré
    // par minute, quand la caméra se réécrit quinze fois par seconde. Les
    // fondre ferait recalculer une éphéméride à chaque image pour un résultat
    // qu'aucun écran ne montrerait.
    //
    // Suspendu hors de RESUMED, comme la caméra : une carte qu'on ne regarde
    // pas n'a pas besoin d'être éclairée, et le premier passage de la boucle
    // rattrape d'un coup le temps passé en arrière-plan.
    LaunchedEffect(controller) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                controller.refreshSunlight()
                delay(SUN_TICK_MS)
            }
        }
    }

    val isStyleLoaded by controller.isStyleLoaded.collectAsStateWithLifecycle()
    LaunchedEffect(isStyleLoaded) {
        if (isStyleLoaded) {
            viewModel.clearMapError()
            // ⚠️ **Seulement si l'on ne sait pas encore où est l'utilisateur.**
            // Le style et le premier point GPS arrivent chacun à leur rythme, et
            // dans l'ordre inverse une fois sur deux : une position en cache
            // revient avant que le fond ne soit peint. Poser quand même le
            // centre d'ouverture faisait alors un aller-retour visible —
            // Nantes centre, puis retour animé sur le conducteur, sur une carte
            // qui venait tout juste d'apparaître.
            //
            // En volume dès la première image : la carte d'Aule se regarde
            // inclinée, et une ouverture à plat qui se relèverait au premier
            // point GPS se lit comme un défaut de chargement.
            if (!followState.hasCenteredOnUser) {
                controller.moveTo(
                    center = viewModel.openingCenter,
                    zoom = MapZoom.OPENING,
                    pitch = MapZoom.PITCH_3D,
                )
            }
        }
    }
    LaunchedEffect(handoverFix, isStyleLoaded) {
        handoverLayer.show(handoverFix)
        if (handoverFix == null) {
            framedHandover = false
        } else if (!framedHandover && isStyleLoaded) {
            controller.flyTo(handoverFix.coordinate)
            framedHandover = true
        }
    }
    LaunchedEffect(handoverStop, handoverStopArrived) {
        handoverLayer.showStop(handoverStop, arrived = handoverStopArrived)
    }

    val cameraMode by controller.cameraMode.collectAsStateWithLifecycle()
    val authorization by location.authorization.collectAsStateWithLifecycle()
    val lastError by location.lastError.collectAsStateWithLifecycle()

    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sheetHeightPx) {
        controller.sheetHeightPx = sheetHeightPx
    }
    val routeReadyId = state.route?.selected?.id?.takeIf { state.route?.status == RouteLoadStatus.READY }
    val sheetHasHeight = sheetHeightPx > 0f
    LaunchedEffect(routeReadyId, sheetHasHeight, state.isNavigating) {
        if (state.isNavigating) return@LaunchedEffect
        val candidate = state.route?.selected?.takeIf { state.route?.status == RouteLoadStatus.READY }
        if (candidate != null && sheetHasHeight) {
            controller.frame(candidate.paintedCoordinates)
        }
    }
    LaunchedEffect(state.hasSheet, state.isNavigating) {
        if (!state.hasSheet && !state.isNavigating) {
            // Le cadre se rend **avant** que le volet ne retombe : sinon la
            // hauteur qui repasse à zéro relancerait un cadrage sur un tracé
            // que la fermeture vient d'effacer.
            controller.releaseFrame()
            sheetHeightPx = 0f
            controller.sheetHeightPx = 0f
            if (controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE) {
                controller.setCameraMode(CameraMode.FOLLOW)
            }
        }
    }

    // Le focus, et lui seul, prend la caméra : cadrage de navigation — vue de
    // derrière, inclinée, tournée dans le sens de marche — sur le véhicule
    // reconnu. L'alerte n'y touche pas ; elle sert précisément à ne plus
    // regarder l'écran.
    //
    // L'effet ne rejoue qu'au changement de focus ou de véhicule : reprendre la
    // caméra à chaque sondage la volerait à quelqu'un en train d'explorer.
    LaunchedEffect(watchState.isFocused, watchState.vehicleId) {
        val focused = watchState.isFocused && watchState.vehicleId != null
        when {
            focused && controller.cameraMode.value != CameraMode.FOLLOW_VEHICLE -> {
                controller.setCameraMode(CameraMode.FOLLOW_VEHICLE)
            }
            !focused && state.selectedVehicle == null &&
                controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE -> {
                controller.setCameraMode(CameraMode.FOLLOW)
            }
        }
    }

    // Le plan de ligne se charge quand la caméra s'accroche au véhicule, et
    // s'oublie dès qu'elle le lâche. C'est le seul endroit qui voie à la fois
    // le mode de caméra — qui vit dans le contrôleur — et la sélection, qui vit
    // dans le modèle d'écran.
    LaunchedEffect(cameraMode, state.selectedVehicle) {
        viewModel.followVehicle(
            state.selectedVehicle?.takeIf { cameraMode == CameraMode.FOLLOW_VEHICLE },
        )
    }

    // Ce que la caméra doit savoir du guidage, et qu'elle ne peut lire nulle
    // part ailleurs : le cap du segment sous les pieds, le mode de la jambe
    // en cours — on ne cadre pas un trottoir comme une quatre-voies — et la
    // distance à la prochaine manœuvre, qui commande le rapprochement sur le
    // carrefour.
    //
    // Le tout est **poussé** dans l'état de suivi plutôt que lu par le ticker :
    // celui-ci bat quatre fois plus vite et n'a pas à traverser le modèle
    // d'écran quinze fois par seconde pour trois nombres qui changent une fois
    // par seconde.
    LaunchedEffect(state.isNavigating) {
        if (!state.isNavigating) {
            followState.forgetGuidance()
            return@LaunchedEffect
        }
        while (isActive) {
            viewModel.onGuidanceFix(location.lastFix.value)
            val navigation = viewModel.state.value.navigation
            followState.routeBearingDegrees = navigation?.routeBearing
            followState.travel = navigation?.let { travelStyleOf(it.currentLegMode) }
            followState.maneuverMeters = navigation?.maneuverMeters
            delay(GUIDANCE_TICK_MS)
        }
    }

    val nearbyLabel = stringResource(R.string.nearby_title)
    val routeLabel = stringResource(R.string.fab_route)
    val mapDescription = stringResource(R.string.map_content_description)
    val mapHint = stringResource(R.string.map_explore_hint)
    val handleDescription = stringResource(R.string.stop_sheet_handle)
    val dismissLabel = stringResource(R.string.sheet_dismiss)
    val paneNearby = stringResource(R.string.sheet_pane_nearby)
    val paneStop = stringResource(R.string.sheet_pane_stop)
    val paneVehicle = stringResource(R.string.sheet_pane_vehicle)
    val panePlace = stringResource(R.string.sheet_pane_place)
    val paneRoute = stringResource(R.string.sheet_pane_route)
    val paneLine = stringResource(R.string.sheet_pane_line)
    val paneNetworkLines = stringResource(R.string.sheet_pane_network_lines)
    val paneTrip = stringResource(R.string.sheet_pane_trip)
    val originMine = stringResource(R.string.route_origin_me)
    val originMap = stringResource(R.string.route_origin_map)
    val navigating = state.isNavigating
    val showingTrip = state.navigation?.showingTrip == true
    val handedOver = serviceNotice?.takeIf { it.kind == ServiceNoticeKind.HANDED_OVER }
    val serviceBanner = when {
        handedOver != null -> handoverCompletedMessage(handedOver.handover)
        serviceLiveHandover != null -> handoverAnnouncementMessage(serviceLiveHandover)
        else -> null
    }
    val serviceBannerAction = if (handedOver != null) {
        stringResource(R.string.heartbeat_dismiss)
    } else {
        null
    }

    // `BottomSheetScaffold` sert un volet **persistant** : contrairement à
    // `ModalBottomSheet`, il n'installe aucun gestionnaire de retour. Sans
    // celui-ci, le geste de retour sur un volet ouvert ne le referme pas — il
    // quitte l'application, en pleine consultation d'un arrêt.
    PredictiveBackHandler(enabled = state.hasSheet) { progress ->
        try {
            progress.collect { }
            when {
                showingTrip -> viewModel.hideTripSheet()
                // Une ligne ouverte se referme sur le tableau d'où elle vient :
                // le retour défait le dernier pas, pas toute la consultation.
                state.showingLine -> viewModel.closeLine()
                else -> viewModel.dismissSheet()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme(night = night) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val parentHeightPx = constraints.maxHeight
            val density = LocalDensity.current
            val maxPeekHeight = with(density) {
                (parentHeightPx * SHEET_PEEK_FRACTION).toDp()
            }
            var sheetHandleHeightPx by remember { mutableFloatStateOf(0f) }
            var sheetContentHeightPx by remember { mutableFloatStateOf(0f) }
            val measuredPeek = with(density) {
                (sheetHandleHeightPx + sheetContentHeightPx).toDp()
            }
            val peekHeight = if (state.hasSheet && measuredPeek > 0.dp) {
                minOf(measuredPeek, maxPeekHeight)
            } else {
                maxPeekHeight
            }

            // `BottomSheetScaffold` n'a pas d'encoche pour les insets : déployé,
            // il monte jusqu'au pixel zéro et sa poignée finit dans l'heure et
            // les icônes de la barre d'état. On borne donc le contenu — la
            // liste défile à l'intérieur, et le volet s'arrête sous la barre.
            // La poignée compte : le volet, c'est elle **plus** le contenu.
            // L'oublier laisse remonter l'ensemble de sa hauteur dans la barre.
            val statusBarPx = WindowInsets.statusBars.getTop(density)
            val maxSheetHeight = with(density) {
                val available = parentHeightPx - statusBarPx - sheetHandleHeightPx -
                    SHEET_TOP_INSET.toPx()
                available.coerceAtLeast(0f).toDp()
            }
            // `rememberStandardBottomSheetState` retire tout seul le cran
            // intermédiaire dès que le volet fait moins de la moitié de
            // l'écran. À 45 % de peek, la plupart des fiches y passent :
            // déployer puis ramener n'a plus d'ancrage, le geste saute à
            // Hidden et le volet se ferme. L'API unifiée garde le palier
            // tant qu'on le demande.
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(
                    SheetValue.Hidden,
                    SheetValue.PartiallyExpanded,
                    SheetValue.Expanded,
                ),
            )
            val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

            // Le marqueur **et** la caméra suivent l'arrêt visé, depuis l'état et non
            // depuis le geste. C'est ce qui fait que tous les chemins de sortie se
            // valent : le bouton « revoir la ligne », le changement de sens, le retour
            // à l'inventaire. Câblés sur les rappels du volet, le premier rendait la
            // caméra et les deux autres laissaient l'écran collé à un arrêt qu'ils
            // venaient d'effacer.
            LaunchedEffect(lineStopsState.focusedStopId, state.openedNetworkLine) {
                val stop = lineStopsState.focusedStop?.takeIf { state.openedNetworkLine != null }
                lineStopLayer.setStop(
                    coordinate = stop?.coordinate,
                    mode = viewModel.openedLine()?.mode,
                )
                val target = stop?.coordinate
                if (target != null) {
                    // Le volet se retire d'abord : déployé, il couvre les deux tiers de
                    // l'écran, et le vol se jouerait derrière lui. La caméra part
                    // ensuite, parce qu'elle centre l'arrêt dans la bande **restante**,
                    // et que cette bande n'est connue qu'une fois le volet posé.
                    runCatching {
                        if (sheetState.hasPartiallyExpandedState) sheetState.partialExpand()
                    }
                    // Un rapprochement, pas un plongeon : le cadre courant est
                    // gardé tant qu'il tient dans les bornes de sélection, et
                    // l'arrêt garde son quartier autour de lui. Se poser à
                    // l'échelle du trottoir répond « ici » sans répondre « où ».
                    controller.focusOn(target)
                } else if (state.openedNetworkLine != null) {
                    // Le tracé se reprend depuis l'index : le rapprochement a rendu
                    // le cadre, il faut le réécrire.
                    val bounds = viewModel.focusedLine()?.bounds
                    if (bounds != null) {
                        controller.frame(listOf(bounds.southWest, bounds.northEast))
                    }
                }
            }
            val sheetIdentity: Any? = when {
                showingTrip -> "trip"
                // L'inventaire du réseau compte pour `hasSheet` : sans identité
                // ici, il faisait disparaître le chrome sans jamais déplier le
                // volet, et la carte restait nue. La ligne ouverte par-dessus ne
                // change pas l'identité — le volet ne se rejoue pas, il change
                // seulement de contenu.
                state.showingNetworkLines -> "network-lines"
                state.showingNearby -> "nearby"
                state.selectedStop != null -> state.selectedStop
                state.lineFocus != null -> state.lineFocus
                state.selectedVehicle != null -> state.selectedVehicle
                state.selectedPlace != null -> state.selectedPlace
                state.route != null && !navigating -> state.route
                else -> null
            }
            val paneTitle = when {
                showingTrip -> paneTrip
                state.showingNetworkLines -> paneNetworkLines
                state.showingNearby -> paneNearby
                state.showingLine -> paneLine
                state.selectedStop != null -> paneStop
                state.selectedVehicle != null -> paneVehicle
                state.selectedPlace != null -> panePlace
                state.route != null && !navigating -> paneRoute
                else -> ""
            }

            LaunchedEffect(sheetIdentity) {
                if (sheetIdentity == null) {
                    try {
                        if (sheetState.currentValue != SheetValue.Hidden) {
                            sheetState.hide()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                    }
                    return@LaunchedEffect
                }
                try {
                    // Le trajet s'ouvre en grand : c'est une lecture, pas un
                    // aperçu. En cran intermédiaire, ses étapes tenaient à
                    // peine et « Arrêter » — la seule sortie du guidage —
                    // finissait sous la barre système.
                    // `show()` prend le palier s'il existe, le grand cran
                    // sinon : `partialExpand()` levait dès que le contenu
                    // tenait sous le peek, et le geste de fermeture n'était
                    // plus écouté.
                    if (showingTrip) sheetState.expand() else sheetState.show()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@LaunchedEffect
                }
                snapshotFlow { sheetState.currentValue to sheetState.targetValue }
                    .collect { (current, target) ->
                        // Un fling depuis le grand cran vise Hidden et saute
                        // le palier. On le ramène : le prochain geste, depuis
                        // le peek, pourra refermer.
                        if (
                            target == SheetValue.Hidden &&
                            current == SheetValue.Expanded &&
                            sheetState.hasPartiallyExpandedState
                        ) {
                            try {
                                sheetState.partialExpand()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                            }
                            return@collect
                        }
                        if (current == SheetValue.Hidden) {
                            if (showingTrip) {
                                viewModel.hideTripSheet()
                            } else {
                                viewModel.dismissSheet()
                            }
                        }
                    }
            }
            LaunchedEffect(parentHeightPx, state.hasSheet) {
                if (!state.hasSheet) return@LaunchedEffect
                snapshotFlow {
                    runCatching { sheetState.requireOffset() }.getOrNull()
                }.collect { offset ->
                    if (offset != null) {
                        sheetHeightPx = (parentHeightPx - offset).coerceAtLeast(0f)
                    }
                }
            }

            BottomSheetScaffold(
                sheetContent = {
                    if (!state.hasSheet) return@BottomSheetScaffold
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxSheetHeight)
                            .onSizeChanged { size ->
                                val height = size.height.toFloat()
                                if (height != sheetContentHeightPx) {
                                    sheetContentHeightPx = height
                                }
                            }
                            .navigationBarsPadding()
                            .semantics(mergeDescendants = false) {
                                this.paneTitle = paneTitle
                                isTraversalGroup = true
                                traversalIndex = -2f
                                customActions = listOf(
                                    CustomAccessibilityAction(dismissLabel) {
                                        if (showingTrip) {
                                            viewModel.hideTripSheet()
                                        } else {
                                            viewModel.dismissSheet()
                                        }
                                        true
                                    },
                                )
                            },
                    ) {
                        when {
                            showingTrip && state.navigation != null -> {
                                TripSheet(
                                    state = state.navigation!!,
                                    onStop = {
                                        stopGuidance(
                                            view, viewModel, controller, location, serviceActive,
                                        )
                                    },
                                )
                            }
                            state.showingNetworkLines -> {
                                val opened = viewModel.openedLine()
                                if (opened != null) {
                                    LineStopsSheet(
                                        line = opened,
                                        state = lineStopsState,
                                        onBack = viewModel::closeNetworkLine,
                                        onSelectDirection = viewModel.lineStops::selectDirection,
                                        onRetry = viewModel.lineStops::retry,
                                        // Poser l'état suffit : le vol et le
                                        // retour au tracé se jouent plus haut,
                                        // sur l'arrêt visé.
                                        onFocusStop = { stop ->
                                            if (stop.coordinate != null) {
                                                viewModel.lineStops.focusStop(stop.id)
                                            }
                                        },
                                        onReleaseStop = { viewModel.lineStops.focusStop(null) },
                                    )
                                } else {
                                    val digest by viewModel.networkDigest
                                        .collectAsStateWithLifecycle()
                                    NetworkLinesSheet(
                                        digest = digest,
                                        query = state.networkLineQuery,
                                        focused = state.focusedNetworkLine,
                                        onQuery = viewModel::setNetworkLineQuery,
                                        onFocus = viewModel::focusNetworkLine,
                                        onOpen = viewModel::openNetworkLine,
                                    )
                                }
                            }
                            state.showingNearby -> {
                                val around = location.lastFix.value?.coordinate
                                    ?: controller.cameraCenter
                                    ?: viewModel.openingCenter
                                NearbySheet(
                                    digest = viewModel.nearbyDigest(around),
                                    linePalette = state.linePalette,
                                    repository = viewModel.stopRepository,
                                    dispatchers = viewModel.dispatchers,
                                    onSelectStop = { stop ->
                                        selectStopFromSheet(view, viewModel, controller, stop)
                                    },
                                    onSelectVehicle = { vehicle ->
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK,
                                        )
                                        viewModel.select(vehicle)
                                    },
                                )
                            }
                            state.showingLine -> {
                                LineDepartureSheet(
                                    watch = state.lineFocus!!,
                                    state = watchState,
                                    onBack = {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK,
                                        )
                                        viewModel.closeLine()
                                    },
                                    timetable = timetableState,
                                    onPickDate = viewModel::showTimetableDate,
                                    onRetryTimetable = viewModel::retryTimetable,
                                    onToggleFocus = {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CONTEXT_CLICK,
                                        )
                                        viewModel.toggleFocus()
                                    },
                                    onToggleWatch = {
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CONTEXT_CLICK,
                                        )
                                        // La permission se demande au moment où
                                        // elle sert, et pas au lancement : c'est
                                        // ici seulement que l'application a
                                        // quelque chose à annoncer, et un refus
                                        // ne coûte que la bannière — la carte
                                        // suit le véhicule quand même.
                                        if (!watchState.isArmed &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                        ) {
                                            notificationPermissionLauncher.launch(
                                                Manifest.permission.POST_NOTIFICATIONS,
                                            )
                                        }
                                        viewModel.toggleWatch()
                                    },
                                )
                            }
                            state.selectedStop != null -> {
                                StopDetailSheet(
                                    stop = state.selectedStop!!,
                                    repository = viewModel.stopRepository,
                                    dispatchers = viewModel.dispatchers,
                                    onRoute = {
                                        val stop = state.selectedStop ?: return@StopDetailSheet
                                        startRoute(
                                            view, viewModel, controller, location,
                                            RoutePlace(stop.coordinate, stop.departuresKey),
                                            originMine, originMap,
                                        )
                                    },
                                    onSelectLine = { row ->
                                        val stop = state.selectedStop ?: return@StopDetailSheet
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK,
                                        )
                                        viewModel.openLine(stop, row)
                                    },
                                )
                            }
                            state.selectedVehicle != null -> {
                                VehicleDetailSheet(
                                    vehicle = state.selectedVehicle!!,
                                    lineColor = state.linePalette.colorOf(
                                        state.selectedVehicle!!.lineId,
                                    ),
                                    isFollowing = cameraMode == CameraMode.FOLLOW_VEHICLE,
                                    onFollow = {
                                        toggleFollowSelectedVehicle(
                                            view, controller, state.selectedVehicle,
                                        )
                                    },
                                    trip = tripState,
                                )
                            }
                            state.selectedPlace != null -> {
                                PlaceDetailSheet(
                                    place = state.selectedPlace!!,
                                    onRoute = {
                                        val place = state.selectedPlace ?: return@PlaceDetailSheet
                                        startRoute(
                                            view, viewModel, controller, location,
                                            RoutePlace(place.coordinate, place.shortLabel()),
                                            originMine, originMap,
                                        )
                                    },
                                )
                            }
                            state.route != null && !navigating -> {
                                RouteSheet(
                                    state = state.route!!,
                                    onSelect = viewModel::selectRoute,
                                    onMode = viewModel::setRouteMode,
                                    onSwap = {
                                        // Le même retour au doigt que le choix
                                        // d'un arrêt : le calcul repart, et
                                        // l'écran met une seconde à le montrer.
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.CLOCK_TICK,
                                        )
                                        viewModel.swapRouteEnds()
                                    },
                                    onStart = {
                                        startGuidance(
                                            view, viewModel, controller, location, followState,
                                        ) {
                                            if (Build.VERSION.SDK_INT >=
                                                Build.VERSION_CODES.TIRAMISU
                                            ) {
                                                notificationPermissionLauncher.launch(
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                // `surface` et non le `surfaceContainerLow` que Material propose
                // par défaut. Le volet est le **support** des cartes qu'il
                // contient, et un support doit être plus clair que ce qu'on pose
                // dessus, sans quoi la hiérarchie s'inverse : au défaut Material,
                // le volet et ses cartes ne se séparaient que d'un point de
                // clarté, et la liste se lisait comme un aplat continu.
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetShape = BottomSheetDefaults.ExpandedShape,
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = BottomSheetDefaults.Elevation,
                sheetDragHandle = if (state.hasSheet) {
                    {
                        Box(
                            modifier = Modifier.onSizeChanged { size ->
                                val height = size.height.toFloat()
                                if (height != sheetHandleHeightPx) {
                                    sheetHandleHeightPx = height
                                }
                            },
                        ) {
                            BottomSheetDefaults.DragHandle(
                                modifier = Modifier.semantics {
                                    contentDescription = handleDescription
                                },
                            )
                        }
                    }
                } else {
                    null
                },
                sheetSwipeEnabled = state.hasSheet,
                containerColor = Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize()) {
                    AuleMap(
                        controller = controller,
                        ambiance = ambiance,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                traversalIndex = 0f
                                contentDescription = "$mapDescription. $mapHint"
                                customActions = listOf(
                                    CustomAccessibilityAction(routeLabel) {
                                        viewModel.activateSearch()
                                        true
                                    },
                                    CustomAccessibilityAction(nearbyLabel) {
                                        viewModel.showNearby()
                                        true
                                    },
                                )
                            },
                    )

                    MapHud(
                        state = state,
                        authorization = authorization,
                        lastLocationError = lastError,
                        positions = location.lastFix,
                        stopRepository = viewModel.stopRepository,
                        dispatchers = viewModel.dispatchers,
                        onShowNearby = viewModel::showNearby,
                        onRetryStops = viewModel::retryLoadingStops,
                        onOpenSettings = location::openSettings,
                        onRequestPrecise = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                        onSearchQuery = viewModel::setSearchQuery,
                        onSearchActivate = viewModel::activateSearch,
                        onSearchCancel = viewModel::cancelSearch,
                        onSelectSearchStop = { hit ->
                            selectStopFromSearch(view, viewModel, controller, hit.representative)
                        },
                        onSelectNearbyStop = { stop ->
                            selectStopFromSearch(view, viewModel, controller, stop)
                        },
                        onSelectSearchPlace = { place ->
                            startRoute(
                                view, viewModel, controller, location,
                                RoutePlace(place.coordinate, place.shortLabel()),
                                originMine, originMap,
                            )
                        },
                        onOpenTrip = viewModel::showTripSheet,
                        // Seulement quand la veille est armée : la même cible
                        // sert au volet ouvert sans alerte, et une pastille
                        // annoncerait alors une surveillance qui n'existe pas.
                        watch = watchState.armed,
                        onOpenWatch = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            viewModel.reopenWatch()
                        },
                        onSummaryHeightPx = { height ->
                            if (navigating && !showingTrip) sheetHeightPx = height
                        },
                        onOpenMenu = onOpenMenu,
                        serviceBanner = serviceBanner,
                        serviceBannerAction = serviceBannerAction,
                        onServiceBannerAction = if (handedOver != null) {
                            onDismissServiceNotice
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .semantics { traversalIndex = -1f },
                    )

                    // Une seule cible en bas stabilise le geste et évite de faire
                    // parcourir les deux bords de l'écran au pouce.
                    val showingChrome = !showingReport && !hideChrome && !state.hasSheet &&
                        !state.search.isActive
                    // Un volet ouvert par ailleurs — une veille qui se rouvre, un
                    // arrêt sélectionné sur la carte — emporte le chrome avec lui.
                    // Sans cela le menu attendrait, déplié, le retour de la carte.
                    LaunchedEffect(showingChrome) {
                        if (!showingChrome) fabMenuExpanded = false
                    }
                    BackHandler(enabled = fabMenuExpanded) { fabMenuExpanded = false }
                    if (showingChrome) {
                        // Le service garde son entrée unique : selon qu'il tourne
                        // ou non, la même place ouvre la prise ou la fin. Deux
                        // entrées côte à côte dont une seule répond seraient pires
                        // que le changement de libellé.
                        val actions = buildList {
                            if (!navigating) {
                                if (serviceActive && onOpenActiveService != null) {
                                    add(
                                        MapFabAction(
                                            glyph = AuleGlyph.BUS,
                                            label = stringResource(R.string.fab_service_end),
                                            onClick = onOpenActiveService,
                                        ),
                                    )
                                } else if (onStartService != null) {
                                    add(
                                        MapFabAction(
                                            glyph = AuleGlyph.PLAY,
                                            label = stringResource(R.string.fab_service_start),
                                            onClick = onStartService,
                                        ),
                                    )
                                }
                                if (onOpenHandover != null) {
                                    add(
                                        MapFabAction(
                                            glyph = AuleGlyph.SWAP,
                                            label = stringResource(R.string.fab_handover),
                                            onClick = onOpenHandover,
                                        ),
                                    )
                                }
                                add(
                                    MapFabAction(
                                        glyph = AuleGlyph.ROUTE,
                                        label = stringResource(R.string.fab_route),
                                        onClick = viewModel::activateSearch,
                                    ),
                                )
                                add(
                                    MapFabAction(
                                        glyph = AuleGlyph.TRAM,
                                        label = stringResource(R.string.fab_network_lines),
                                        onClick = viewModel::openNetworkLines,
                                    ),
                                )
                                // Les deux rescapées de la barre du bas, placées
                                // en dernier : le menu se déplie vers le haut,
                                // donc la fin de la liste est ce que le pouce
                                // atteint sans bouger. Ce sont aussi les deux
                                // qu'on ouvre le plus souvent.
                                if (onSubmitReport != null) {
                                    add(
                                        MapFabAction(
                                            glyph = AuleGlyph.FLAG,
                                            label = stringResource(R.string.fab_report),
                                            onClick = { showingReport = true },
                                        ),
                                    )
                                }
                                add(
                                    MapFabAction(
                                        glyph = AuleGlyph.PIN,
                                        label = stringResource(R.string.fab_nearby),
                                        onClick = viewModel::showNearby,
                                    ),
                                )
                            }
                        }
                        if (fabMenuExpanded) {
                            val dismissMenu = stringResource(R.string.fab_menu_close)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.scrim
                                            .copy(alpha = AuleAlpha.SHADE),
                                    )
                                    // Sans ondulation : un voile plein écran qui
                                    // s'illumine au doigt ferait croire qu'on a
                                    // touché quelque chose, alors qu'on referme.
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClickLabel = dismissMenu,
                                    ) { fabMenuExpanded = false }
                                    .semantics { contentDescription = dismissMenu },
                            )
                        }
                        MapActionChrome(
                            cameraMode = cameraMode,
                            onRecenter = {
                                onRecenter(controller, state.selectedVehicle, navigating)
                            },
                            onOpenLegal = { showingLegal = true },
                            actions = actions,
                            fabExpanded = fabMenuExpanded,
                            onFabExpandedChange = { fabMenuExpanded = it },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    bottom = if (navigating) {
                                        TripSummaryBarHeight + AuleSpacing.md
                                    } else {
                                        0.dp
                                    },
                                ),
                        )
                    }
                    val submitReport = onSubmitReport
                    if (showingReport && submitReport != null) {
                        ReportSheetHost(
                            onClose = { showingReport = false },
                            onSubmit = { report ->
                                val fix = location.lastFix.value?.coordinate
                                val positioned = if (report.latitude == null && fix != null) {
                                    report.copy(
                                        latitude = fix.latitude,
                                        longitude = fix.longitude,
                                    )
                                } else {
                                    report
                                }
                                submitReport(positioned)
                            },
                        )
                    }
                    if (showingLegal) {
                        LegalNoticeSheet(onClose = { showingLegal = false })
                    }
                }
            }
        }
    }
}

private fun selectStopFromSheet(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    stop: TransitStop,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    viewModel.select(stop)
    if (controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE) {
        controller.setCameraMode(CameraMode.FREE_EXPLORE)
    }
}

/**
 * Un arrêt choisi dans la recherche — qu'on l'ait tapé ou qu'elle l'ait
 * proposé.
 *
 * La caméra suit, contrairement à une sélection faite depuis un volet : on
 * vient de nommer un lieu qu'on ne voit pas forcément à l'écran, et ouvrir sa
 * fiche sans l'y amener laisserait le volet parler d'un point hors cadre.
 */
private fun selectStopFromSearch(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    stop: TransitStop,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    viewModel.select(stop)
    controller.focusOn(stop.coordinate)
}

private fun startRoute(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    destination: RoutePlace,
    myPositionLabel: String,
    mapLabel: String,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    val fix = location.lastFix.value?.coordinate
    val origin = if (fix != null) {
        RoutePlace(fix, myPositionLabel)
    } else {
        RoutePlace(controller.cameraCenter ?: viewModel.openingCenter, mapLabel)
    }
    viewModel.routeTo(destination, origin)
}

private fun toggleFollowSelectedVehicle(
    view: android.view.View,
    controller: MapController,
    vehicle: TransportVehicle?,
) {
    if (vehicle == null) return
    if (controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE) {
        controller.setCameraMode(CameraMode.FOLLOW)
    } else {
        controller.setCameraMode(CameraMode.FOLLOW_VEHICLE)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

/**
 * Le bouton unique : recentrer si l'on s'est éloigné, sinon basculer
 * l'orientation. Si un véhicule est sélectionné, reprendre le suivi ramène
 * sur lui — c'est lui qu'on suivait avant le geste.
 */
private fun onRecenter(
    controller: MapController,
    selectedVehicle: TransportVehicle?,
    navigating: Boolean,
) {
    if (navigating) {
        // Après un geste, recentrer reprend le cadrage de navigation — pas le
        // suivi au nord, dont le puck n'a pas assez de route devant.
        controller.setCameraMode(
            if (controller.cameraMode.value == CameraMode.NAVIGATION) {
                CameraMode.FOLLOW
            } else {
                CameraMode.NAVIGATION
            },
        )
        return
    }
    when (controller.cameraMode.value) {
        CameraMode.FREE_EXPLORE, CameraMode.OVERVIEW ->
            controller.setCameraMode(
                if (selectedVehicle != null) CameraMode.FOLLOW_VEHICLE else CameraMode.FOLLOW,
            )
        CameraMode.FOLLOW -> controller.setCameraMode(CameraMode.NAVIGATION)
        CameraMode.NAVIGATION, CameraMode.FOLLOW_VEHICLE ->
            controller.setCameraMode(CameraMode.FOLLOW)
    }
}

private fun startGuidance(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    followState: CameraFollowState,
    requestNotifications: () -> Unit,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    if (!viewModel.startGuidance(location.lastFix.value?.coordinate)) return
    // Le contexte de cadrage est posé **tout de suite**, et pas au premier
    // battement du guidage une seconde plus tard : sans lui, la première
    // seconde d'un trajet à pied se joue avec le cadre d'une voiture — plus
    // basse, plus loin, tournée dans le sens de la route — juste au moment où
    // l'on regarde l'écran pour savoir où partir.
    val engaged = viewModel.state.value.navigation
    followState.routeBearingDegrees = engaged?.routeBearing
    followState.travel = engaged?.let { travelStyleOf(it.currentLegMode) }
    followState.maneuverMeters = engaged?.maneuverMeters
    requestNotifications()
    location.setPurpose(LocationPurpose.NAVIGATING)
    controller.setCameraMode(CameraMode.NAVIGATION)
}

private fun stopGuidance(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    serviceActive: Boolean,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    viewModel.stopGuidance()
    location.setPurpose(trackingPurpose(navigating = false, serviceActive = serviceActive))
    val candidate = viewModel.state.value.route?.selected
        ?.takeIf { viewModel.state.value.route?.status == RouteLoadStatus.READY }
    if (candidate != null) {
        controller.frame(candidate.paintedCoordinates)
    }
}

private fun trackingPurpose(viewModel: MapViewModel, serviceActive: Boolean): LocationPurpose =
    trackingPurpose(viewModel.state.value.isNavigating, serviceActive)

private fun trackingPurpose(navigating: Boolean, serviceActive: Boolean): LocationPurpose = when {
    navigating -> LocationPurpose.NAVIGATING
    serviceActive -> LocationPurpose.ON_DUTY
    else -> LocationPurpose.READY
}

private const val GUIDANCE_TICK_MS = 1_000L

/**
 * Le battement du soleil.
 *
 * Une minute est le bon grain : le soleil parcourt un quart de degré dans ce
 * temps-là, soit nettement moins que ce qu'une façade peut montrer, et le
 * calcul tient en quelques microsecondes. Plus lent, on verrait la lumière
 * avancer par crans au crépuscule, quand tout change vite ; plus rapide, on
 * repeindrait les volumes pour rien.
 */
private const val SUN_TICK_MS = 60_000L

/**
 * Ce qu'un volet montre sans qu'on le tire.
 *
 * 30 % ne suffisaient pas : le volet d'un véhicule tient en un titre, un statut,
 * un prochain arrêt et un bouton — et c'est le bouton qui passait sous la barre
 * système, à moitié lisible et à moitié cliquable. Le palier ne borne que les
 * volets **longs** (arrêt, autour de vous), qui se tirent de toute façon ; les
 * courts doivent tenir entiers.
 */
private const val SHEET_PEEK_FRACTION = 0.45f

/** Ce qui reste de carte au-dessus d'un volet déployé : assez pour se situer. */
private val SHEET_TOP_INSET = 12.dp

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
