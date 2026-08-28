package io.aule.android.feature.map

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import io.aule.android.core.designsystem.AuleSheetMotion
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.resolvedNight
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleChrome
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
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlace
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.shortPlaceName
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.shortLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * L'écran carte.
 *
 * La carte est le socle : elle est montée une fois et tout vient se poser
 * par-dessus. Les panneaux de détail s'ouvrent en volet au-dessus d'elle,
 * sans jamais la remplacer.
 *
 * Le bas de la fenêtre appartient au **socle** : la recherche de destination,
 * en carte flottante tant qu'on ne s'en sert pas. Elle n'est pas une barre —
 * c'est le volet du dessous, celui qui revient dès qu'aucun autre n'est
 * présenté. Voir [MapSearchSheet].
 *
 * Tout ce qu'on peut engager depuis la carte — prendre un service, relever un
 * collègue, calculer un itinéraire, lister les lignes, signaler un événement,
 * voir les correspondances — tient dans le menu flottant ancré à droite,
 * au-dessus du socle, et ne se déplie qu'au moment où on le cherche. Six
 * actions rares n'ont pas à occuper six cibles permanentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    /**
     * L'avatar du compte, posé dans le socle de recherche. C'est la porte du
     * menu, et il vient **entier** de `:app` : la carte ne connaît pas le
     * compte. Voir `AccountAvatarButton`.
     */
    accountAvatar: (@Composable () -> Unit)? = null,
    /**
     * Le menu du compte, **en volet** : le contenu vient de `:feature:auth`,
     * l'écran ne fait que le présenter là où il présente ses fiches. C'est
     * `showingMenu` qui l'ouvre, et [onDismissMenu] qui le referme — l'état
     * vit dans `:app`, qui ouvre aussi le profil et le Guet depuis ce menu.
     */
    menuSheet: (@Composable () -> Unit)? = null,
    showingMenu: Boolean = false,
    onDismissMenu: () -> Unit = {},
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
    // La gestion des favoris et son éditeur. Hors de [MapUiState] pour la même
    // raison que les mentions légales : ce n'est pas un objet de la carte qu'on
    // sélectionne, et les y mettre aurait fait recomposer la carte à chaque
    // lettre tapée dans un champ de nom.
    var managingSavedPlaces by remember { mutableStateOf(false) }
    // La carte s'ouvre : les favoris se rattachent au compte connecté et se
    // rapprochent de lui. C'est le seul instant où l'on est sûr que la session
    // est celle de qui regarde — un téléphone de service passe de main en main,
    // et la rangée ne doit pas montrer le domicile du collègue précédent.
    LaunchedEffect(Unit) { viewModel.savedPlaces.sync() }
    var savedPlaceTarget by remember { mutableStateOf<SavedPlaceTarget?>(null) }
    var deletingSavedPlace by remember { mutableStateOf<SavedPlace?>(null) }
    // La demande de clavier faite au champ de recherche, une fois.
    //
    // Elle ne vit pas dans [MapUiState] : ce n'est pas un état de la carte,
    // c'est le fait qu'une **autre** commande que le volet vient d'ouvrir la
    // recherche — « Trouver un itinéraire », l'action d'accessibilité de la
    // carte — et que celle-là promet une saisie. Le volet tiré au pouce, lui,
    // ne l'arme pas : monter pour relire ses destinations récentes ne doit pas
    // faire surgir un clavier par-dessus.
    var focusSearchField by remember { mutableStateOf(false) }
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
    LaunchedEffect(state.route?.selected?.id, state.route?.status, state.route?.mode) {
        val route = state.route
        val candidate = route?.selected?.takeIf { route.status == RouteLoadStatus.READY }
        routeLayer.setTrace(
            candidate = candidate,
            mode = route?.mode ?: RouteMode.TRANSIT,
            origin = route?.origin,
            destination = route?.destination,
        )
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

    // Arrivé, on rend le service de premier plan — sans fermer l'écran.
    //
    // « Vous êtes arrivé » s'affichait, et c'était tout : le service, son verrou
    // de six heures et le flux en haute précision continuaient jusqu'à ce que
    // quelqu'un pense à toucher « Arrêter ». Un conducteur qui se gare et range
    // son téléphone ne le fait jamais.
    //
    // On ne coupe pas le guidage pour autant : la fiche d'arrivée et le tracé
    // restent, parce qu'on veut encore les regarder. Seul le **palier** retombe,
    // et avec lui ce qui coûte : le service, le verrou, l'arrière-plan.
    val arrived = state.navigation?.progress?.arrived == true
    LaunchedEffect(state.isNavigating, arrived, serviceActive) {
        if (location.authorization.value.allowsUpdates) {
            location.setPurpose(
                trackingPurpose(state.isNavigating && !arrived, serviceActive),
            )
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
    // La bande que le socle prend à la carte, remontée depuis la mise en page
    // du volet. Zéro tant qu'il n'est pas là — en guidage, sous un autre volet.
    var socleBandPx by remember { mutableFloatStateOf(0f) }
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
    LaunchedEffect(state.hasSheet, state.isNavigating, socleBandPx) {
        if (!state.hasSheet && !state.isNavigating) {
            // Le cadre se rend **avant** que le volet ne retombe : sinon la
            // hauteur qui repasse à zéro relancerait un cadrage sur un tracé
            // que la fermeture vient d'effacer.
            controller.releaseFrame()
            // Mais la carte ne récupère pas tout : le socle garde sa bande, et
            // la caméra doit centrer dans ce qui **reste** visible. C'est
            // `kSearchSheetBand` d'iOS, à ceci près qu'ici elle est mesurée et
            // non relevée à l'écran. Le palier du socle, et lui seul : le volet
            // monté n'entre pas dans le compte, sinon ouvrir la recherche
            // déplacerait la ville sous les doigts.
            sheetHeightPx = socleBandPx
            controller.sheetHeightPx = socleBandPx
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
    val paneSearch = stringResource(R.string.sheet_pane_search)
    val paneMenu = stringResource(R.string.sheet_pane_menu)
    val searchCollapseLabel = stringResource(R.string.search_cancel)
    val paneLine = stringResource(R.string.sheet_pane_line)
    val paneNetworkLines = stringResource(R.string.sheet_pane_network_lines)
    val paneTrip = stringResource(R.string.sheet_pane_trip)
    val originMine = stringResource(R.string.route_origin_me)
    val originMap = stringResource(R.string.route_origin_map)
    // Les deux noms d'emplacement, résolus ici : ils partent dans un rappel qui
    // n'est pas une composition, et `stringResource` n'y a pas cours. C'est
    // « Domicile » que le volet d'itinéraire doit annoncer, pas « 12 rue Paul
    // Bellamy » — on y va pour rentrer chez soi, pas à une adresse.
    val savedHomeLabel = stringResource(R.string.saved_place_home)
    val savedWorkLabel = stringResource(R.string.saved_place_work)
    val savedLabel: (SavedPlace) -> String = { place ->
        when (place.slot) {
            SavedPlaceSlot.HOME -> savedHomeLabel
            SavedPlaceSlot.WORK -> savedWorkLabel
            SavedPlaceSlot.CUSTOM -> place.name.ifEmpty { shortPlaceName(place.label) }
        }
    }
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

    // **Le socle : le volet du dessous.**
    //
    // Rien ne l'ouvre et rien ne le ferme — il est là dès que rien d'autre
    // n'occupe le bas de l'écran, et il revient de lui-même quand le volet
    // qui l'avait remplacé se referme. C'est `MapSheet.search` d'iOS, mot pour
    // mot, y compris le refus de se rejeter — voir le veto posé sur l'état du
    // volet, plus bas.
    //
    // Il se retire pour trois choses seulement : un guidage, qui prend la même
    // bande pour sa barre d'arrivée ; un autre volet, qui parle de ce qu'on
    // vient de désigner ; et la relève, qui demande la carte nue.
    val menuOpen = showingMenu && menuSheet != null
    // Ce que le volet porte, quelle qu'en soit la source : les fiches de la
    // carte, ou le menu du compte que `:app` y pose.
    val sheetPresented = state.hasSheet || menuOpen
    val showingSocle = !navigating && !sheetPresented && !hideChrome
    val searchOpen = showingSocle && state.search.isActive

    // Le retour pendant un guidage : la sortie qu'on prend sans y penser.
    //
    // Elle n'était pas gardée. Guidage engagé, volet refermé, les deux
    // conditions ci-dessus tombaient à faux, le geste n'était pas intercepté
    // et **l'activité se terminait**. Le modèle d'écran partait avec elle —
    // donc l'itinéraire — pendant que le flux de positions, le service de
    // premier plan et son verrou de six heures continuaient de tourner pour un
    // guidage qui n'existait plus. Toucher la notification rouvrait une carte
    // sans trajet.
    //
    // Le geste demande donc maintenant confirmation, comme la déconnexion et
    // pour la même raison : un doigt qui dérape au volant ne doit pas coûter
    // le trajet.
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    // Une confirmation ouverte se referme au retour suivant, sinon le geste
    // traverserait le dialogue et quitterait l'application derrière lui.
    val backConsumes = sheetPresented || searchOpen || navigating || confirmingStop

    // `BottomSheetScaffold` sert un volet **persistant** : contrairement à
    // `ModalBottomSheet`, il n'installe aucun gestionnaire de retour. Sans
    // celui-ci, le geste de retour sur un volet ouvert ne le referme pas — il
    // quitte l'application, en pleine consultation d'un arrêt.
    PredictiveBackHandler(enabled = backConsumes) { progress ->
        try {
            progress.collect { }
            when {
                confirmingStop -> confirmingStop = false
                menuOpen -> onDismissMenu()
                // Le socle ne se ferme pas : le retour le **redescend**, ce qui
                // est le seul geste qu'il connaisse. Quitter l'application
                // depuis une recherche ouverte serait perdre la carte pour
                // avoir tapé trois lettres.
                searchOpen -> viewModel.collapseSearch()
                showingTrip -> viewModel.hideTripSheet()
                // Une ligne ouverte se referme sur le tableau d'où elle vient :
                // le retour défait le dernier pas, pas toute la consultation.
                state.showingLine -> viewModel.closeLine()
                sheetPresented -> viewModel.dismissSheet()
                navigating -> confirmingStop = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    if (confirmingStop) {
        StopGuidanceDialog(
            onKeepGoing = { confirmingStop = false },
            onStop = {
                confirmingStop = false
                stopGuidance(view, viewModel, controller, location, serviceActive)
            },
        )
    }

    // Le guidage s'en va avec l'écran.
    //
    // `onPauseOrDispose` ne suffit pas : il ne distingue pas une mise en fond
    // — où le guidage doit **continuer**, c'est tout l'objet du service de
    // premier plan — d'une composition détruite pour de bon. Ce `DisposableEffect`
    // ne parle que du second cas, et il est le dernier filet : quoi qu'il
    // arrive à l'écran, le flux de positions ne survit pas à sa disparition.
    //
    // Un changement de configuration ne compte pas : le modèle d'écran, lui,
    // y survit, et couper le guidage là serait perdre un trajet pour un
    // basculement clair/sombre.
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        onDispose {
            val recreating = activity?.isChangingConfigurations == true
            if (!recreating && viewModel.state.value.isNavigating) {
                viewModel.stopGuidance()
                location.stop()
            }
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
            // La bande du socle, mesurée par le volet de recherche lui-même :
            // la poignée, le champ, et la marge sous lui. C'est le pendant
            // d'iOS `kSearchSheetHeight`, à ceci près qu'elle n'est pas écrite —
            // un réglage de texte agrandi fait grandir le champ, et le palier
            // le suit sans qu'on ait à y penser.
            var socleHeightPx by remember { mutableFloatStateOf(0f) }
            val measuredPeek = with(density) {
                (sheetHandleHeightPx + sheetContentHeightPx).toDp()
            }
            // La barre système entre dans le palier, et pas dans le contenu :
            // le volet descend jusqu'au bord de la fenêtre, donc un pic réglé
            // sur la seule hauteur du champ aurait posé celui-ci **sous** les
            // trois boutons du S21. Ce qu'on ajoute ici est de la surface de
            // volet, pas de la marge — le champ, lui, garde la sienne.
            val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
            val navigationBarHeight = with(density) { navigationBarPx.toDp() }
            // ⚠️ **Sans la poignée** : la carte flottante n'en a pas, et la
            // hauteur retenue de la dernière qui en avait une — celle du menu —
            // gonflait le palier d'autant. Le volet montait alors trop haut, la
            // carte se dessinait au-dessus de la bande visible, et le doigt
            // tombait sous le champ : le socle devenait intouchable après un
            // passage par le menu.
            val socleHeight = with(density) {
                (socleHeightPx + navigationBarPx).toDp()
            }
            // Le palier redescend jusqu'à la caméra, qui vit hors de la
            // composition : elle cadre sur la bande **restante**, et cette
            // bande est ce que le socle occupe.
            val socleBand = if (showingSocle) socleHeight else 0.dp
            val socleBandTargetPx = with(density) { socleBand.toPx() }
            LaunchedEffect(socleBandTargetPx) { socleBandPx = socleBandTargetPx }

            val peekHeight = when {
                // Le socle passe avant la mesure du contenu : celui du volet de
                // recherche vaut tout l'écran une fois déployé, et le prendre
                // pour palier ouvrirait la recherche en grand sans qu'on l'ait
                // demandée. Avant la première mesure, la bande d'un champ : une
                // image, invisible, mais jamais zéro — un pic nul pose le
                // palier à l'endroit exact du volet fermé.
                showingSocle ->
                    if (socleHeight > 0.dp) minOf(socleHeight, maxPeekHeight) else AuleChrome.bar
                sheetPresented && measuredPeek > 0.dp ->
                    minOf(measuredPeek - SHEET_PEEK_EPSILON, maxPeekHeight)
                else -> maxPeekHeight
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
            // **Le socle ne se rejette pas.**
            //
            // `enabledValues` dirait la même chose et serait plus direct — mais
            // c'est une **clé** du `rememberSaveable` qui porte l'état du volet
            // (voir `SheetDefaults.rememberSheetState`) : la changer au vol
            // reconstruit l'état, donc repose le volet à `Hidden`, et chaque
            // passage du socle à une fiche ferait descendre le volet d'un coup
            // sec avant de le remonter. Le veto, lui, est une lambda stable qui
            // lit un état : le glissement vers le bas part, puis revient au
            // palier, et le volet garde sa position d'un bout à l'autre.
            val socleShowing = rememberUpdatedState(showingSocle)
            val confirmSheetValue = remember {
                { value: SheetValue -> value != SheetValue.Hidden || !socleShowing.value }
            }
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(
                    SheetValue.Hidden,
                    SheetValue.PartiallyExpanded,
                    SheetValue.Expanded,
                ),
                confirmValueChange = confirmSheetValue,
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
                // **Le menu passe devant tout.** On l'ouvre expressément, et il
                // se referme sur ce qu'on regardait : une fiche d'arrêt ouverte
                // dessous n'est pas perdue, elle attend. C'est la règle d'iOS,
                // et sans elle un arrêt touché sur la carte pendant que le menu
                // est ouvert lui prenait le volet — le menu restait « ouvert »
                // dans l'état de `:app` et resurgissait à la fermeture de la
                // fiche.
                menuOpen -> "menu"
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
                // Le socle en dernier : c'est ce qui reste quand rien d'autre
                // n'est présenté, et il ne prend jamais la place d'un volet.
                showingSocle -> "search"
                else -> null
            }
            val paneTitle = when {
                menuOpen -> paneMenu
                showingTrip -> paneTrip
                state.showingNetworkLines -> paneNetworkLines
                state.showingNearby -> paneNearby
                state.showingLine -> paneLine
                state.selectedStop != null -> paneStop
                state.selectedVehicle != null -> paneVehicle
                state.selectedPlace != null -> panePlace
                state.route != null && !navigating -> paneRoute
                showingSocle -> paneSearch
                else -> ""
            }

            LaunchedEffect(sheetIdentity) {
                // ⚠️ **On ne commande pas un volet qui n'a pas encore été
                // posé.** `expand()` et consorts passent par `anchoredDrag` :
                // sans ancrage, la fonction ne bouge rien mais **écrit quand
                // même** l'état d'arrivée, et le volet se retrouve « au palier »
                // avec un décalage jamais calculé. `BottomSheetScaffold` lit ce
                // décalage à chaque mise en page — pour poser ses messages — et
                // se termine par `IllegalStateException: The offset was read
                // before being initialized`. Vu à l'écran, dès la première
                // sélection d'un arrêt depuis la recherche.
                //
                // Attendre le décalage, c'est attendre les ancrages : le volet
                // ne reçoit d'ordre qu'une fois mesuré.
                sheetState.awaitLayout()
                if (sheetIdentity == null) {
                    runSheetCommand {
                        if (sheetState.currentValue != SheetValue.Hidden) {
                            sheetState.hide()
                        }
                    }
                    return@LaunchedEffect
                }
                // ⚠️ **L'ordre d'ouverture ne doit pas emporter le collecteur.**
                // Il passe par le mutex du glissement, et un doigt déjà posé —
                // ou une animation du volet en cours — le fait refuser par une
                // annulation. Relancée, elle tuait la coroutine **avant** que
                // le collecteur du dessous ne soit installé : le palier et
                // l'état de la recherche cessaient alors de s'accorder pour
                // toute la vie de l'écran, et le volet redescendait en gardant
                // ses résultats et son clavier.
                if (sheetIdentity != "search") {
                    runSheetCommand {
                        // Le trajet s'ouvre en grand : c'est une lecture, pas
                        // un aperçu. En cran intermédiaire, ses étapes tenaient
                        // à peine et « Arrêter » — la seule sortie du guidage —
                        // finissait sous la barre système.
                        // `show()` prend le palier s'il existe, le grand cran
                        // sinon : `partialExpand()` levait dès que le contenu
                        // tenait sous le peek, et le geste de fermeture n'était
                        // plus écouté.
                        if (showingTrip) sheetState.expand() else sheetState.show()
                    }
                } else {
                    // ⚠️ **Le socle ne reçoit pas d'ordre ici**, et c'est tout
                    // l'intérêt : c'est l'accord état → volet, plus bas, qui le
                    // pose — lui seul. Les deux ont visé le même palier au même
                    // instant ; le mutex du glissement en annulait un, celui-là
                    // rendait la main aussitôt, et le collecteur repartait sur
                    // un palier qui n'était pas encore celui de l'état. Vu à
                    // l'écran : fermer le menu du compte rouvrait la recherche,
                    // déployée, résultats compris. Un écrivain, un seul.
                    //
                    // Et **on n'écoute le volet qu'une fois qu'il s'accorde avec
                    // l'état** : le socle prend la suite d'un autre volet, qui
                    // pouvait être déployé. Lire le palier dans cet instant-là,
                    // c'est prendre la position d'où l'on vient pour une
                    // recherche que personne n'a ouverte.
                    snapshotFlow {
                        val wanted = if (viewModel.state.value.search.isActive) {
                            SheetValue.Expanded
                        } else {
                            SheetValue.PartiallyExpanded
                        }
                        sheetState.currentValue == wanted
                    }.first { it }
                }
                // Le jet du pouce vers le bas, tel qu'on le sert au socle :
                // noté ici parce qu'il faut attendre que le geste rende la
                // main avant d'y répondre. Voir juste en dessous.
                var socleFlungDown = false
                snapshotFlow { sheetState.currentValue to sheetState.targetValue }
                    .collect { (current, target) ->
                        // **Le palier du socle est l'état de la recherche.**
                        // Les deux sens comptent : monté au pouce, le volet
                        // ouvre la recherche, sinon il montrerait un champ
                        // au-dessus de rien ; redescendu, il la referme, sinon
                        // le clavier resterait pris par un champ passé sous le
                        // pli. C'est la règle de `SearchSheet` sur iOS, où le
                        // contenu suit la taille et non l'inverse.
                        //
                        // Redescendre **n'efface pas le champ** : voir
                        // [MapViewModel.collapseSearch].
                        if (sheetIdentity == "search") {
                            // ⚠️ **Un jet vers le bas vise le rejet**, que le
                            // socle refuse — et le refus seul le renvoyait au
                            // cran d'où il partait, c'est-à-dire déployé : le
                            // geste le plus franc pour refermer était le seul
                            // qui ne refermait pas.
                            //
                            // On note l'intention, et on ne la sert qu'une fois
                            // le geste terminé — `current == target`. Y répondre
                            // pendant le vol ne servait à rien : le glissement
                            // tient le volet à une priorité supérieure, l'ordre
                            // était rejeté, et le volet restait en l'air.
                            if (target == SheetValue.Hidden) {
                                socleFlungDown = true
                                return@collect
                            }
                            // ⚠️ **Rien ne se décide tant que le volet est en
                            // route.** Le palier qu'on lit pendant un vol est
                            // celui d'où l'on part, pas celui où l'on va :
                            // répondre à celui-là refermait la recherche dans
                            // la seconde qui suivait le doigt posé sur le
                            // champ — le volet montait, l'état disait « fermé »,
                            // et le volet redescendait.
                            if (current != target) return@collect
                            if (socleFlungDown && current == SheetValue.Expanded) {
                                socleFlungDown = false
                                runSheetCommand { sheetState.partialExpand() }
                                return@collect
                            }
                            socleFlungDown = false
                            when (current) {
                                SheetValue.Expanded -> viewModel.activateSearch()
                                else -> viewModel.collapseSearch()
                            }
                            return@collect
                        }
                        // Un fling depuis le grand cran vise Hidden et saute
                        // le palier. On le ramène : le prochain geste, depuis
                        // le peek, pourra refermer.
                        if (
                            target == SheetValue.Hidden &&
                            current == SheetValue.Expanded &&
                            sheetState.hasPartiallyExpandedState
                        ) {
                            runSheetCommand { sheetState.partialExpand() }
                            return@collect
                        }
                        if (current == SheetValue.Hidden) {
                            when {
                                menuOpen -> onDismissMenu()
                                showingTrip -> viewModel.hideTripSheet()
                                else -> viewModel.dismissSheet()
                            }
                        }
                    }
            }

            // Et l'état de la recherche est le palier du socle : ce qui l'ouvre
            // d'ailleurs — le menu d'actions, l'action d'accessibilité de la
            // carte — doit monter le volet, faute de quoi la commande promise
            // n'aurait aucun effet visible.
            LaunchedEffect(showingSocle, state.search.isActive) {
                if (!showingSocle) return@LaunchedEffect
                sheetState.awaitLayout()
                runSheetCommand {
                    if (state.search.isActive) {
                        if (sheetState.currentValue != SheetValue.Expanded) sheetState.expand()
                    } else if (sheetState.currentValue != SheetValue.PartiallyExpanded &&
                        sheetState.hasPartiallyExpandedState
                    ) {
                        sheetState.partialExpand()
                    }
                }
            }
            LaunchedEffect(parentHeightPx, sheetPresented) {
                if (!sheetPresented) return@LaunchedEffect
                snapshotFlow {
                    runCatching { sheetState.requireOffset() }.getOrNull()
                }.collect { offset ->
                    if (offset != null) {
                        sheetHeightPx = (parentHeightPx - offset).coerceAtLeast(0f)
                    }
                }
            }

            // Le volet anime plus lentement que le reste de l'application :
            // Material fait redescendre un volet au régime des *effets
            // rapides*, un dixième de seconde, et le socle claquait. Voir
            // [AuleSheetMotion], et la raison pour laquelle le régime
            // n'enveloppe que ce sous-arbre.
            AuleSheetMotion {
                BottomSheetScaffold(
                    sheetContent = {
                        if (showingSocle) {
                            // Le voile du menu d'actions **passe aussi sur le
                            // socle**. Il est peint dans le corps de l'écran, et le
                            // volet se dessine par-dessus le corps : sans ce
                            // second voile, le menu déplié assombrissait la ville
                            // et laissait le champ en pleine lumière, touchable —
                            // c'est-à-dire qu'il ne se lisait plus comme un menu.
                            val dimMenu = fabMenuExpanded
                            val dismissMenuLabel = stringResource(R.string.fab_menu_close)
                            // **Le socle prend toute la hauteur permise**, alors
                            // que les autres volets se mesurent. Ce n'est pas une
                            // fantaisie : le grand cran d'un volet se pose à la
                            // hauteur de son contenu, et un contenu haut comme le
                            // socle donnerait deux crans confondus — le palier
                            // disparaîtrait alors des ancrages, et le volet ne
                            // saurait plus redescendre. La bande visible au repos
                            // reste celle du pic ; tout le reste attend sous le
                            // bord de l'écran.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(maxSheetHeight)
                                    .navigationBarsPadding()
                                    .imePadding()
                                    .semantics(mergeDescendants = false) {
                                        this.paneTitle = paneSearch
                                        isTraversalGroup = true
                                        traversalIndex = -2f
                                        if (searchOpen) {
                                            customActions = listOf(
                                                CustomAccessibilityAction(searchCollapseLabel) {
                                                    viewModel.collapseSearch()
                                                    true
                                                },
                                            )
                                        }
                                    },
                            ) {
                                MapSearchSheet(
                                    search = state.search,
                                    catalog = state.stops,
                                    positions = location.lastFix,
                                    repository = viewModel.stopRepository,
                                    dispatchers = viewModel.dispatchers,
                                    expanded = state.search.isActive,
                                    focusRequested = focusSearchField,
                                    onQueryChange = viewModel::setSearchQuery,
                                    onFieldFocused = viewModel::activateSearch,
                                    onFocusConsumed = { focusSearchField = false },
                                    onSocleHeightPx = { height ->
                                        if (height != socleHeightPx) socleHeightPx = height
                                    },
                                    onSelectStop = { hit ->
                                        selectStopFromSearch(
                                            view, viewModel, controller, hit.representative,
                                        )
                                    },
                                    onSelectPlace = { place ->
                                        startRoute(
                                            view, viewModel, controller, location,
                                            RoutePlace(place.coordinate, place.shortLabel()),
                                            originMine, originMap,
                                        )
                                    },
                                    onSelectNearbyStop = { stop ->
                                        selectStopFromSearch(view, viewModel, controller, stop)
                                    },
                                    savedPlaces = viewModel.savedPlaces.places,
                                    // Un favori touché **part**, il ne s'ouvre
                                    // pas : c'est toute la promesse — ouvrir,
                                    // toucher Domicile, rouler. L'ouvrir en
                                    // fiche aurait remis un geste entre
                                    // l'intention et l'itinéraire.
                                    onSelectSaved = { place ->
                                        startRoute(
                                            view, viewModel, controller, location,
                                            RoutePlace(place.coordinate, savedLabel(place)),
                                            originMine, originMap,
                                        )
                                    },
                                    onFillSaved = { slot ->
                                        savedPlaceTarget = SavedPlaceTarget.Fill(slot)
                                    },
                                    onManageSaved = { managingSavedPlaces = true },
                                    accountAvatar = accountAvatar,
                                )
                                if (dimMenu) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(
                                                MaterialTheme.colorScheme.scrim
                                                    .copy(alpha = AuleAlpha.SHADE),
                                            )
                                            .clickable(
                                                interactionSource = remember {
                                                    MutableInteractionSource()
                                                },
                                                indication = null,
                                                onClickLabel = dismissMenuLabel,
                                            ) { fabMenuExpanded = false }
                                            .semantics {
                                                contentDescription = dismissMenuLabel
                                            },
                                    )
                                }
                            }
                            return@BottomSheetScaffold
                        }
                        if (!sheetPresented) return@BottomSheetScaffold
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
                                            when {
                                                menuOpen -> onDismissMenu()
                                                showingTrip -> viewModel.hideTripSheet()
                                                else -> viewModel.dismissSheet()
                                            }
                                            true
                                        },
                                    )
                                },
                        ) {
                            when {
                                // Le menu passe devant : on l'a demandé
                                // expressément, et il se referme sur ce qu'on
                                // regardait.
                                menuOpen -> menuSheet()
                                showingTrip && state.navigation != null -> {
                                    TripSheet(
                                        state = state.navigation!!,
                                        onStop = {
                                            stopGuidance(
                                                view,
                                                viewModel,
                                                controller,
                                                location,
                                                serviceActive,
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
                                            onSelectDirection =
                                                viewModel.lineStops::selectDirection,
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
                                            val tiramisu = Build.VERSION.SDK_INT >=
                                                Build.VERSION_CODES.TIRAMISU
                                            if (!watchState.isArmed && tiramisu) {
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
                                            val place = state.selectedPlace
                                                ?: return@PlaceDetailSheet
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
                    // Le volet **s'efface** sous le socle fermé : c'est la carte
                    // flottante qui porte alors la surface et l'ombre, écartée des
                    // bords, et le volet ne doit rien peindre derrière elle.
                    sheetContainerColor = if (showingSocle && !searchOpen) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    sheetShape = BottomSheetDefaults.ExpandedShape,
                    sheetTonalElevation = 0.dp,
                    sheetShadowElevation = if (showingSocle && !searchOpen) {
                        0.dp
                    } else {
                        BottomSheetDefaults.Elevation
                    },
                    // ⚠️ **Pas de poignée sur la carte flottante.** Elle promet
                    // un glissement qui n'existe pas au repos — le socle ne s'ouvre
                    // qu'au doigt posé sur le champ — et un trait de préhension
                    // posé sur une carte détachée des bords ne ressemble à rien.
                    // Déployé, le volet la retrouve : c'est un volet.
                    sheetDragHandle = if (sheetPresented || searchOpen) {
                        {
                            Box(
                                // Le voile du menu couvre la poignée avec le reste
                                // du socle : dimensionnée seule, elle laissait une
                                // bande blanche en travers d'un écran assombri.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { size ->
                                        val height = size.height.toFloat()
                                        if (height != sheetHandleHeightPx) {
                                            sheetHandleHeightPx = height
                                        }
                                    }
                                    .background(
                                        if (showingSocle && fabMenuExpanded) {
                                            MaterialTheme.colorScheme.scrim
                                                .copy(alpha = AuleAlpha.SHADE)
                                        } else {
                                            Color.Transparent
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
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
                    sheetSwipeEnabled = sheetPresented || searchOpen,
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
                                            focusSearchField = true
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
                            onShowNearby = viewModel::showNearby,
                            onRetryStops = viewModel::retryLoadingStops,
                            onOpenSettings = location::openSettings,
                            onRequestPrecise = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
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
                        val showingChrome = !showingReport && !hideChrome && !sheetPresented &&
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
                                            // Le menu promet une saisie : le volet
                                            // monte **et** le clavier s'ouvre. Tiré
                                            // au pouce, il monterait sans clavier —
                                            // voir [focusSearchField].
                                            onClick = {
                                                focusSearchField = true
                                                viewModel.activateSearch()
                                            },
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
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            },
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
                                // Le chrome se relève de ce qui occupe déjà la
                                // bande du bas : la barre d'arrivée en guidage, la
                                // recherche le reste du temps. Posé au bord comme
                                // avant, le bouton d'action serait venu sur le
                                // champ — deux cibles superposées dans le seul coin
                                // que le pouce atteint sans bouger.
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(
                                        bottom = if (navigating) {
                                            TripSummaryBarHeight + AuleSpacing.md
                                        } else {
                                            // Le palier moins la barre système : le
                                            // chrome pose la sienne par-dessus, et
                                            // les compter deux fois éloignerait le
                                            // bouton d'action du bord d'autant.
                                            (peekHeight - navigationBarHeight)
                                                .coerceAtLeast(0.dp)
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
                        if (managingSavedPlaces) {
                            SavedPlacesSheet(
                                places = viewModel.savedPlaces.places,
                                onEdit = { place ->
                                    savedPlaceTarget = SavedPlaceTarget.Edit(place)
                                },
                                onFill = { slot ->
                                    savedPlaceTarget = SavedPlaceTarget.Fill(slot)
                                },
                                onDelete = { place -> deletingSavedPlace = place },
                                onClose = { managingSavedPlaces = false },
                            )
                        }
                        deletingSavedPlace?.let { doomed ->
                            SavedPlaceDeleteDialog(
                                name = savedLabel(doomed),
                                onConfirm = {
                                    viewModel.savedPlaces.remove(doomed.id)
                                    deletingSavedPlace = null
                                },
                                onDismiss = { deletingSavedPlace = null },
                            )
                        }
                        savedPlaceTarget?.let { target ->
                            SavedPlaceEditorSheet(
                                target = target,
                                catalog = state.stops,
                                repository = viewModel.placeRepository,
                                dispatchers = viewModel.dispatchers,
                                logger = viewModel.logger,
                                onSave = viewModel.savedPlaces::save,
                                onDelete = viewModel.savedPlaces::remove,
                                onClose = { savedPlaceTarget = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Donner un ordre au volet sans se faire tuer par le geste en cours.
 *
 * ⚠️ `expand()`, `partialExpand()` et `hide()` passent par le `MutatorMutex` du
 * glissement. Un doigt posé sur le volet le tient à une priorité supérieure :
 * l'ordre est alors refusé, et il l'est **en jetant une `CancellationException`
 * qui n'est pas celle de la portée**. La relancer — le réflexe correct partout
 * ailleurs — tuait le collecteur qui tient l'accord entre le palier et l'état :
 * un jet du pouce vers le bas laissait le volet déployé et vide, et plus aucun
 * geste ne le redescendait.
 *
 * [ensureActive] fait la part des choses : il ne relance que si la portée, elle,
 * a vraiment été annulée.
 */
private suspend fun runSheetCommand(block: suspend () -> Unit) {
    try {
        block()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
    } catch (_: Exception) {
    }
}

/**
 * Attendre que le volet ait une position, c'est-à-dire des ancrages.
 *
 * Le décalage naît de la mesure ; tant qu'elle n'a pas eu lieu il vaut `NaN`,
 * et toute commande donnée avant pose l'état sans poser le volet. Voir l'appel
 * dans `MapScreen`, qui porte le défaut que ça a produit.
 */
@OptIn(ExperimentalMaterial3Api::class)
private suspend fun SheetState.awaitLayout() {
    snapshotFlow { runCatching { requireOffset() }.getOrNull() }.first { it != null }
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

/**
 * « Arrêter le guidage ? »
 *
 * Le même dialogue que la déconnexion, et pour la même raison : ce sont les
 * deux seules actions de l'application qu'un doigt qui dérape au volant peut
 * déclencher et qu'on ne peut pas défaire d'un geste. Le bouton de sortie
 * prend donc le rôle d'erreur, et le bouton neutre — celui qu'on touche par
 * réflexe — est celui qui **ne fait rien**.
 */
@Composable
private fun StopGuidanceDialog(
    onKeepGoing: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onKeepGoing,
        icon = {
            Icon(
                imageVector = AuleGlyph.CLOSE.asImageVector(filled = true),
                contentDescription = null,
            )
        },
        iconContentColor = colors.error,
        title = {
            Text(
                text = stringResource(R.string.nav_stop_confirm_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = { Text(stringResource(R.string.nav_stop_confirm_body)) },
        confirmButton = {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.nav_stop))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) {
                Text(stringResource(R.string.nav_stop_confirm_keep))
            }
        },
    )
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

/**
 * Le point qui sépare le palier du grand cran, et qui évite un plantage.
 *
 * `BottomSheetScaffold` **retire le palier** dès que le pic vaut exactement la
 * hauteur du volet : les deux crans se confondent, il n'en garde qu'un. Or le
 * pic d'ici est *mesuré sur le contenu* — l'égalité est le cas courant, pas le
 * cas limite. Tant que rien ne bouge, c'est sans conséquence.
 *
 * ⚠️ Mais quand le palier disparaît **pendant qu'une animation le vise** —
 * exactement ce qui arrive en passant du socle de recherche à la fiche d'un
 * arrêt qu'on vient d'y choisir —, `AnchoredDraggable` termine son vol en
 * écrivant la position d'un ancrage qui n'existe plus, c'est-à-dire `NaN`. La
 * mise en page suivante lit ce décalage et jette : *The offset was read before
 * being initialized*. Le volet ne s'ouvrait pas, l'application se fermait.
 *
 * Un point de moins tient les deux crans distincts. Il ne se voit pas — c'est
 * un point de contenu de moins au palier — et il vaut mieux que le plantage.
 */
private val SHEET_PEEK_EPSILON = 1.dp

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
