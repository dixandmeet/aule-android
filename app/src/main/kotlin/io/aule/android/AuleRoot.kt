package io.aule.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.LocalAppearanceMode
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.map.MapController
import io.aule.android.feature.auth.AccessCheckScreen
import io.aule.android.feature.auth.AccountMenuScreen
import io.aule.android.feature.auth.AuthScreen
import io.aule.android.feature.auth.AuthViewModel
import io.aule.android.feature.auth.ProfileScreen
import io.aule.android.feature.auth.RegistrationScreen
import io.aule.android.feature.auth.RegistrationViewModel
import io.aule.android.feature.map.EndServiceHost
import io.aule.android.feature.map.HandoverScreen
import io.aule.android.feature.map.HandoverStep
import io.aule.android.feature.map.HandoverViewModel
import io.aule.android.feature.map.handoverAlertBody
import io.aule.android.feature.map.handoverAlertTitle
import io.aule.android.feature.map.handoverTakenBody
import io.aule.android.feature.map.handoverTakenTitle
import io.aule.android.feature.map.MapScreen
import io.aule.android.feature.map.MapViewModel
import io.aule.android.feature.map.PriseServiceScreen
import io.aule.android.feature.map.PriseServiceViewModel
import io.aule.android.feature.map.ServiceViewModel

/**
 * La racine Compose : session d'abord, carte ensuite.
 *
 * Tant que [AuthViewModel] n'a pas fini de restaurer, on montre un fond
 * neutre — pas la carte : monter MapLibre pour la démonter une seconde
 * plus tard coûterait un style reload pour rien.
 */
@Composable
fun AuleRoot(
    graph: AuleGraph,
    mapController: MapController,
) {
    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    auth = graph.auth,
                    profiles = graph.profiles,
                    logger = graph.logger,
                )
            }
        },
    )
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val authCallback by graph.authCallback.collectAsStateWithLifecycle()
    val appearance by graph.appearance.mode.collectAsStateWithLifecycle()
    var showingRegistration by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(authCallback, authState.isReady) {
        if (!authState.isReady) return@LaunchedEffect
        val uri = authCallback ?: return@LaunchedEffect
        val code = uri.getQueryParameter("code")
        graph.consumeAuthCallback()
        showingRegistration = false
        if (!code.isNullOrBlank()) {
            authViewModel.completeEmailConfirmation(code)
        }
    }

    CompositionLocalProvider(LocalAppearanceMode provides appearance) {
    when {
        !authState.isReady -> {
            AuleTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuleTheme.tokens.surfaceSolid.color),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = stringResource(R.string.app_name),
                        style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                            .copy(color = AuleTheme.tokens.onSurface.color),
                    )
                }
            }
        }
        authState.isCheckingAccess -> AccessCheckScreen()
        !authState.isSignedIn && showingRegistration -> {
            val registrationViewModel: RegistrationViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        RegistrationViewModel(
                            auth = graph.auth,
                            drafts = graph.registrationDrafts,
                            logger = graph.logger,
                        )
                    }
                },
            )
            RegistrationScreen(
                viewModel = registrationViewModel,
                onClose = { showingRegistration = false },
            )
        }
        !authState.isSignedIn -> AuthScreen(
            viewModel = authViewModel,
            onCreateAccount = { showingRegistration = true },
        )
        else -> {
            val mapViewModel: MapViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MapViewModel(
                            stopRepository = graph.stops,
                            vehicleRepository = graph.vehicles,
                            placeRepository = graph.places,
                            routingRepository = graph.routing,
                            roadRouter = graph.roads,
                            dispatchers = graph.dispatchers,
                            logger = graph.logger,
                        )
                    }
                },
            )
            val serviceViewModel: ServiceViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        ServiceViewModel(
                            auth = graph.auth,
                            services = graph.services,
                            logger = graph.logger,
                        )
                    }
                },
            )
            val serviceState by serviceViewModel.state.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
            LaunchedEffect(lifecycleState) {
                serviceViewModel.setInBackground(
                    !lifecycleState.isAtLeast(Lifecycle.State.STARTED),
                )
            }
            LaunchedEffect(serviceState.active?.id) {
                if (serviceState.active == null) return@LaunchedEffect
                graph.location.lastFix.collect { serviceViewModel.onLocationFix(it) }
            }
            var showingMenu by rememberSaveable { mutableStateOf(false) }
            var showingProfile by rememberSaveable { mutableStateOf(false) }
            var showingPrise by rememberSaveable { mutableStateOf(false) }
            var priseNonce by rememberSaveable { mutableIntStateOf(0) }
            var showingHandover by rememberSaveable { mutableStateOf(false) }
            var handoverNonce by rememberSaveable { mutableIntStateOf(0) }
            var showingEnd by rememberSaveable { mutableStateOf(false) }
            var handoverFix by remember { mutableStateOf<HandoverFix?>(null) }
            var handoverStop by remember { mutableStateOf<Coordinate?>(null) }
            var handoverStopArrived by remember { mutableStateOf(false) }
            var hideHandoverChrome by remember { mutableStateOf(false) }
            LaunchedEffect(serviceState.active, serviceState.isEnding, showingEnd) {
                if (showingEnd && serviceState.active == null && !serviceState.isEnding) {
                    showingEnd = false
                }
            }
            LaunchedEffect(showingHandover) {
                if (!showingHandover) {
                    handoverFix = null
                    handoverStop = null
                    handoverStopArrived = false
                    hideHandoverChrome = false
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
            MapScreen(
                viewModel = mapViewModel,
                controller = mapController,
                location = graph.location,
                config = graph.config,
                onOpenMenu = { showingMenu = true },
                onStartService = { showingPrise = true },
                serviceActive = serviceState.active != null,
                onOpenActiveService = { showingEnd = true },
                onOpenHandover = { showingHandover = true },
                serviceLiveHandover = serviceState.liveHandover,
                serviceNotice = serviceState.notice,
                onDismissServiceNotice = serviceViewModel::clearNotice,
                handoverFix = handoverFix,
                handoverStop = handoverStop,
                handoverStopArrived = handoverStopArrived,
                hideChrome = hideHandoverChrome,
                onSubmitReport = { report ->
                    val session = graph.auth.currentSession()
                        ?: throw DriverReportException(DriverReportFailureKind.NOT_SIGNED_IN)
                    graph.reports.submit(
                        session,
                        report,
                        driverServiceId = serviceState.active?.id,
                        vehicleId = serviceState.active?.vehicleId,
                    )
                },
            )
            // Le menu, le profil, la prise de service et la relève couvrent
            // la carte sans la démonter : MapLibre garde son style et sa position.
            val overlaySession = graph.auth.currentSession()
            if (showingPrise && overlaySession != null) {
                val priseViewModel: PriseServiceViewModel = viewModel(
                    key = "prise-$priseNonce",
                    factory = viewModelFactory {
                        initializer {
                            PriseServiceViewModel(
                                session = overlaySession,
                                networkId = authState.profile?.networkId,
                                services = graph.services,
                                logger = graph.logger,
                            )
                        }
                    },
                )
                PriseServiceScreen(
                    viewModel = priseViewModel,
                    location = graph.location,
                    onClose = {
                        showingPrise = false
                        priseNonce += 1
                    },
                    onStarted = { started ->
                        serviceViewModel.adopt(started)
                        showingPrise = false
                        priseNonce += 1
                    },
                )
            } else if (showingHandover && overlaySession != null) {
                val context = LocalContext.current
                val handoverViewModel: HandoverViewModel = viewModel(
                    key = "handover-$handoverNonce",
                    factory = viewModelFactory {
                        initializer {
                            HandoverViewModel(
                                session = overlaySession,
                                networkId = authState.profile?.networkId,
                                alreadyOnService = serviceState.active != null,
                                services = graph.services,
                                handovers = graph.handovers,
                                stops = graph.stops,
                                around = { graph.location.lastFix.value?.coordinate },
                                roads = graph.roads,
                                alertPrefsStore = graph.handoverAlertPrefs,
                                onAlert = { alert, stopName ->
                                    val prefs = graph.handoverAlertPrefs.read()
                                    graph.alertTone.alert(
                                        sound = prefs.sound,
                                        vibration = prefs.vibration,
                                    )
                                    graph.handoverAlerts.show(
                                        title = handoverAlertTitle(context, alert),
                                        body = handoverAlertBody(context, alert, stopName),
                                        kind = alert.kind,
                                    )
                                },
                                onServiceTaken = { lineLabel, reliefStopName ->
                                    graph.handoverAlerts.showCompleted(
                                        title = handoverTakenTitle(context),
                                        body = handoverTakenBody(
                                            context,
                                            lineLabel,
                                            reliefStopName,
                                        ),
                                    )
                                },
                                logger = graph.logger,
                            )
                        }
                    },
                )
                val handoverState by handoverViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(handoverViewModel) {
                    mapViewModel.fleet.collect(handoverViewModel::onFleetSnapshot)
                }
                val overlay = handoverState.step == HandoverStep.STOP ||
                    handoverState.step == HandoverStep.ALERTS ||
                    handoverState.step == HandoverStep.CONFIRM
                LaunchedEffect(
                    overlay,
                    handoverState.trackFix,
                    handoverState.selectedLiveStop,
                    handoverState.handover?.reliefStopCoordinate,
                    handoverState.reliefArrived,
                ) {
                    hideHandoverChrome = overlay
                    handoverFix = if (overlay) handoverState.trackFix else null
                    handoverStop = if (overlay) {
                        handoverState.selectedLiveStop?.coordinate
                            ?: handoverState.handover?.reliefStopCoordinate
                    } else {
                        null
                    }
                    handoverStopArrived = overlay && handoverState.reliefArrived
                }
                HandoverScreen(
                    viewModel = handoverViewModel,
                    onClose = {
                        handoverViewModel.dismiss()
                        showingHandover = false
                        handoverNonce += 1
                    },
                    onStarted = { started ->
                        serviceViewModel.adopt(started)
                        showingHandover = false
                        handoverNonce += 1
                    },
                    modifier = if (overlay) {
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxSize()
                    },
                )
            } else if (showingEnd) {
                EndServiceHost(
                    ending = serviceState.isEnding,
                    failure = serviceState.endFailure,
                    onConfirm = serviceViewModel::end,
                    onClose = {
                        if (!serviceState.isEnding) {
                            showingEnd = false
                            serviceViewModel.clearEndFailure()
                        }
                    },
                )
            } else if (showingProfile) {
                ProfileScreen(
                    viewModel = authViewModel,
                    appearance = appearance,
                    onAppearance = graph.appearance::setMode,
                    traces = graph.traces,
                    onClose = { showingProfile = false },
                )
            } else if (showingMenu) {
                AccountMenuScreen(
                    viewModel = authViewModel,
                    versionLabel = "${graph.config.versionName} (${graph.config.versionCode})",
                    onClose = { showingMenu = false },
                    onOpenProfile = {
                        showingMenu = false
                        showingProfile = true
                    },
                )
            }
            }
        }
    }
    }
}
