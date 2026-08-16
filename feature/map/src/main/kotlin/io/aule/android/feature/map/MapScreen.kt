package io.aule.android.feature.map

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.aule.android.core.common.config.AppConfig
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.resolvedNight
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
import io.aule.android.core.map.layer.RouteLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * L'écran carte.
 *
 * La carte est le socle : elle est montée une fois et tout vient se poser
 * par-dessus. Les panneaux de détail s'ouvrent en volet au-dessus d'elle,
 * sans jamais la remplacer.
 *
 * Les rails flottent **au-dessus** des volets, pas en dessous : c'est le
 * port de `MapActionRail`. Démarrer, Relève et Signaler y sont branchés.
 */
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    controller: MapController,
    location: LocationProvider,
    config: AppConfig,
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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val night = resolvedNight()
    val ambiance = MapAmbiance.of(night)
    val view = LocalView.current
    var showingReport by rememberSaveable { mutableStateOf(false) }

    // Un guidage s'utilise posé sur un support : l'extinction de l'écran
    // couperait aussi le flux. Le verrou ne vit que pendant NAVIGATING.
    DisposableEffect(state.isNavigating) {
        view.keepScreenOn = state.isNavigating
        onDispose { view.keepScreenOn = false }
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

    // L'ordre d'enregistrement **est** l'ordre de superposition : arrêts,
    // véhicules, relève, destination, tracé, puck. Le puck doit rester
    // au-dessus de tout — y compris du ruban d'itinéraire.
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

    LaunchedEffect(state.stops) {
        stopsLayer.setStops(state.stops)
    }
    LaunchedEffect(state.selectedStop) {
        stopsLayer.setSelected(state.selectedStop)
    }
    LaunchedEffect(state.selectedVehicle) {
        vehiclesLayer.setSelected(state.selectedVehicle?.id)
        followState.selectedVehicleId = state.selectedVehicle?.id
        followState.selectedVehicleSpeed = state.selectedVehicle?.speedMps ?: 0.0
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
        viewModel.fleet.collect { snapshot -> vehiclesLayer.apply(snapshot) }
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
        when (location.authorization.value) {
            LocationAuthorization.UNKNOWN -> permissionLauncher.launch(LOCATION_PERMISSIONS)
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

    val isStyleLoaded by controller.isStyleLoaded.collectAsStateWithLifecycle()
    LaunchedEffect(isStyleLoaded) {
        if (isStyleLoaded) {
            viewModel.clearMapError()
            controller.moveTo(
                center = viewModel.openingCenter,
                zoom = MapZoom.OPENING,
            )
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
            sheetHeightPx = 0f
            controller.sheetHeightPx = 0f
            if (controller.cameraMode.value == CameraMode.FOLLOW_VEHICLE) {
                controller.setCameraMode(CameraMode.FOLLOW)
            }
        }
    }

    LaunchedEffect(state.isNavigating) {
        if (!state.isNavigating) {
            followState.routeBearingDegrees = null
            return@LaunchedEffect
        }
        while (isActive) {
            viewModel.onGuidanceFix(location.lastFix.value)
            followState.routeBearingDegrees = viewModel.state.value.navigation?.routeBearing
            delay(GUIDANCE_TICK_MS)
        }
    }

    val nearbyLabel = stringResource(R.string.nearby_title)
    val mapDescription = stringResource(R.string.map_content_description)
    val mapHint = stringResource(R.string.map_explore_hint)
    val handleDescription = stringResource(R.string.stop_sheet_handle)
    val dismissLabel = stringResource(R.string.sheet_dismiss)
    val paneNearby = stringResource(R.string.sheet_pane_nearby)
    val paneStop = stringResource(R.string.sheet_pane_stop)
    val paneVehicle = stringResource(R.string.sheet_pane_vehicle)
    val panePlace = stringResource(R.string.sheet_pane_place)
    val paneRoute = stringResource(R.string.sheet_pane_route)
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

    AuleTheme(night = night) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val parentHeightPx = constraints.maxHeight

            AuleMap(
                controller = controller,
                ambiance = ambiance,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        traversalIndex = 0f
                        contentDescription = "$mapDescription. $mapHint"
                        customActions = listOf(
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
                showsDiagnostics = config.showsDiagnostics,
                diagnosticsLabel = stringResource(
                    R.string.map_diagnostics,
                    config.environmentLabel,
                    config.dataSource.id,
                ),
                onShowNearby = viewModel::showNearby,
                onRetryStops = viewModel::retryLoadingStops,
                onOpenSettings = location::openSettings,
                onRequestPrecise = { permissionLauncher.launch(LOCATION_PERMISSIONS) },
                onSearchQuery = viewModel::setSearchQuery,
                onSearchActivate = viewModel::activateSearch,
                onSearchCancel = viewModel::cancelSearch,
                onSelectSearchStop = { hit ->
                    selectStopFromSearch(view, viewModel, controller, hit)
                },
                onSelectSearchPlace = { place ->
                    startRoute(
                        view, viewModel, controller, location,
                        RoutePlace(place.coordinate, place.shortLabel()),
                        originMine, originMap,
                    )
                },
                onOpenTrip = viewModel::showTripSheet,
                onSummaryHeightPx = { height ->
                    if (navigating && !showingTrip) sheetHeightPx = height
                },
                onOpenMenu = onOpenMenu,
                serviceBanner = serviceBanner,
                serviceBannerAction = serviceBannerAction,
                onServiceBannerAction = if (handedOver != null) onDismissServiceNotice else null,
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .semantics { traversalIndex = -1f },
            )

            when {
                showingTrip && state.navigation != null -> {
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = paneTrip,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::hideTripSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
                        TripSheet(
                            state = state.navigation!!,
                            onStop = {
                                stopGuidance(view, viewModel, controller, location, serviceActive)
                            },
                        )
                    }
                }
                state.showingNearby -> {
                    val around = location.lastFix.value?.coordinate
                        ?: controller.cameraCenter
                        ?: viewModel.openingCenter
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = paneNearby,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::dismissSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
                        NearbySheet(
                            digest = viewModel.nearbyDigest(around),
                            onSelectStop = { stop -> selectStopFromSheet(view, viewModel, controller, stop) },
                            onSelectVehicle = { vehicle ->
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                viewModel.select(vehicle)
                            },
                        )
                    }
                }
                state.selectedStop != null -> {
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = paneStop,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::dismissSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
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
                        )
                    }
                }
                state.selectedVehicle != null -> {
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = paneVehicle,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::dismissSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
                        VehicleDetailSheet(
                            vehicle = state.selectedVehicle!!,
                            isFollowing = cameraMode == CameraMode.FOLLOW_VEHICLE,
                            onFollow = {
                                toggleFollowSelectedVehicle(view, controller, state.selectedVehicle)
                            },
                        )
                    }
                }
                state.selectedPlace != null -> {
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = panePlace,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::dismissSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
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
                }
                state.route != null && !navigating -> {
                    AuleSheet(
                        parentHeightPx = parentHeightPx,
                        handleDescription = handleDescription,
                        paneTitle = paneRoute,
                        dismissLabel = dismissLabel,
                        onDismiss = viewModel::dismissSheet,
                        onHeightPx = { sheetHeightPx = it },
                    ) {
                        RouteSheet(
                            state = state.route!!,
                            onSelect = viewModel::selectRoute,
                            onMode = viewModel::setRouteMode,
                            onStart = {
                                startGuidance(
                                    view, viewModel, controller, location, followState,
                                ) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

            // Les rails vivent sur la carte, pas sur les volets. Les garder
            // compacts au-dessus d'une liste masquait noms et distances sur le
            // S21 ; le volet fournit déjà son propre retour et ses actions.
            if (!showingReport && !hideChrome && !state.hasSheet && !state.search.isActive) {
                val searching = state.search.isActive
                val leftItems = buildList {
                    if (!navigating && !searching) {
                        if (serviceActive && onOpenActiveService != null) {
                            add(
                                MapActionItem(
                                    glyph = AuleGlyph.BUS,
                                    label = stringResource(R.string.rail_in_service),
                                    semanticsLabel = stringResource(R.string.rail_in_service_a11y),
                                    onClick = onOpenActiveService,
                                    active = true,
                                    primary = true,
                                ),
                            )
                        } else if (onStartService != null) {
                            add(
                                MapActionItem(
                                    glyph = AuleGlyph.PLAY,
                                    label = stringResource(R.string.rail_start),
                                    semanticsLabel = stringResource(R.string.rail_start_a11y),
                                    onClick = onStartService,
                                    primary = true,
                                ),
                            )
                        }
                        if (onOpenHandover != null) {
                            add(
                                MapActionItem(
                                    glyph = AuleGlyph.SWAP,
                                    label = stringResource(R.string.rail_handover),
                                    semanticsLabel = stringResource(R.string.rail_handover_a11y),
                                    onClick = onOpenHandover,
                                ),
                            )
                        }
                        if (onSubmitReport != null) {
                            add(
                                MapActionItem(
                                    glyph = AuleGlyph.FLAG,
                                    label = stringResource(R.string.rail_report),
                                    semanticsLabel = stringResource(R.string.rail_report_a11y),
                                    onClick = { showingReport = true },
                                ),
                            )
                        }
                    }
                }
                val rightItems = buildList {
                    if (!searching) {
                        if (!navigating) {
                            add(
                                MapActionItem(
                                    glyph = AuleGlyph.ROUTE,
                                    label = stringResource(R.string.rail_route),
                                    semanticsLabel = stringResource(R.string.rail_route_a11y),
                                    onClick = viewModel::activateSearch,
                                ),
                            )
                        }
                        add(
                            MapActionItem(
                                glyph = AuleGlyph.PIN,
                                label = stringResource(R.string.rail_nearby),
                                semanticsLabel = stringResource(R.string.rail_nearby_a11y),
                                onClick = viewModel::showNearby,
                            ),
                        )
                    }
                }
                MapActionChrome(
                    cameraMode = cameraMode,
                    compact = navigating,
                    leftItems = leftItems,
                    rightItems = rightItems,
                    onRecenter = { onRecenter(controller, state.selectedVehicle, navigating) },
                    showAttribution = !state.hasSheet && !navigating,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .safeDrawingPadding()
                        .padding(horizontal = AuleSpacing.md)
                        .padding(
                            bottom = if (navigating) {
                                TripSummaryBarHeight + AuleSpacing.md
                            } else {
                                AuleSpacing.sm
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

private fun selectStopFromSearch(
    view: android.view.View,
    viewModel: MapViewModel,
    controller: MapController,
    hit: StopSearchHit,
) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    viewModel.select(hit)
    controller.flyTo(hit.coordinate)
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
    followState.routeBearingDegrees = viewModel.state.value.navigation?.routeBearing
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

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
