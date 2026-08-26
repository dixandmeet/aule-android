package io.aule.android

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.LocalAppearanceMode
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.resolvedNight
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.map.MapController
import io.aule.android.core.map.TransitTiles
import io.aule.android.feature.auth.AccessCheckScreen
import io.aule.android.feature.auth.AccountAvatarButton
import io.aule.android.feature.auth.AccountMenuSheet
import io.aule.android.feature.auth.AuthScreen
import io.aule.android.feature.auth.AuthViewModel
import io.aule.android.feature.auth.ForgotPasswordScreen
import io.aule.android.feature.auth.ProfileScreen
import io.aule.android.feature.auth.RegistrationScreen
import io.aule.android.feature.auth.RegistrationViewModel
import io.aule.android.feature.auth.UpdatePasswordScreen
import io.aule.android.feature.map.departureAlertBody
import io.aule.android.feature.map.departureAlertTitle
import io.aule.android.feature.map.EndServiceHost
import io.aule.android.feature.map.GuetSettingsHost
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
import io.aule.android.feature.map.WelcomeHost

/**
 * La racine Compose : session d'abord, carte ensuite.
 *
 * Tant que [AuthViewModel] n'a pas fini de restaurer, on montre le fond de
 * marque — pas la carte : monter MapLibre pour la démonter une seconde
 * plus tard coûterait un style reload pour rien.
 *
 * ## Ce que la racine ne fait pas
 *
 * Elle n'anime pas les passages d'un écran à l'autre, et c'est un choix. Chaque
 * écran d'Aule entre par lui-même — la cascade du kit sur ses rangées, le
 * ressort du volet Material sur son conteneur — donc une transition posée ici
 * viendrait s'ajouter à celle d'en dessous, pas la remplacer : deux mouvements
 * pour une arrivée. Et surtout, faire fondre un écran dans l'autre demande de
 * composer les deux pendant la durée du fondu ; au-dessus d'une `MapView`
 * native et d'un `ViewModel` par volet, ce sont deux choses vivantes à la fois
 * pour trois cents millisecondes d'effet. Les volets couvrent la carte, ils ne
 * la remplacent pas : le mouvement appartient à ce qui couvre.
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
    var showingRecovery by rememberSaveable { mutableStateOf(false) }
    // L'accueil se lit une fois, au montage, et se retient en mémoire ensuite :
    // relire les préférences à chaque recomposition ferait une lecture disque
    // par image pour un booléen qui ne change qu'une fois dans la vie de l'app.
    var welcomeDone by rememberSaveable { mutableStateOf(graph.welcome.hasSeenWelcome()) }
    var recoveryEmail by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(authCallback, authState.isReady) {
        if (!authState.isReady) return@LaunchedEffect
        val uri = authCallback ?: return@LaunchedEffect
        val code = uri.getQueryParameter("code")
        graph.consumeAuthCallback()
        showingRegistration = false
        showingRecovery = false
        if (!code.isNullOrBlank()) {
            // Le genre du lien — confirmation ou récupération — n'est pas lu
            // ici : il a été écrit avec le vérifieur PKCE au moment de la
            // demande, et c'est le seul endroit qui le sache à coup sûr.
            authViewModel.completeAuthCallback(code)
        }
    }

    // La porte d'entrée est sombre, toujours.
    //
    // C'est la charte du web : `aule.fr` et les écrans d'accueil de l'espace de
    // travail ne suivent pas le thème du visiteur — la maison est sombre, et
    // une façade qui change de couleur selon l'heure de celui qui sonne n'est
    // plus une identité. Le choix d'apparence reprend ses droits dès qu'une
    // session est ouverte : là, c'est l'outil de travail, et c'est
    // l'utilisateur qui décide de sa lumière.
    //
    // Le mode est posé **ici**, sur le local d'apparence, et non écran par
    // écran : les barres système le lisent au même endroit, et une nuit forcée
    // dans un thème local aurait laissé des icônes noires sur un fond noir.
    val doorway = !authState.isReady ||
        authState.isCheckingAccess ||
        authState.isResettingPassword ||
        !authState.isSignedIn
    val resolved = if (doorway) AppearanceMode.DARK else appearance

    CompositionLocalProvider(LocalAppearanceMode provides resolved) {
    SystemBarsFollowAppearance()
    when {
        // La restauration de session dure d'ordinaire deux cents millisecondes,
        // et c'est pourtant la première image de chaque lancement.
        //
        // D'où ce qu'on n'y met pas : ni surface de marque, ni cascade d'entrée
        // du kit. `isReady` peut basculer avant la fin d'une animation, et une
        // cascade coupée à mi-course se remarque bien plus que le libellé
        // qu'elle prétendait remplacer — c'est exactement pour cette raison que
        // [BootScreen], qui porte cette cascade, reste débranché.
        //
        // Ce qu'on y met, en revanche, ne peut pas être interrompu, puisque
        // c'est immobile : le fond ambiant, celui-là même que porte l'écran qui
        // suit dans tous les cas — l'habilitation quand une session se restaure,
        // la connexion ou l'inscription sinon. Un aplat de surface ici et un
        // lavis teal une image plus tard, et le démarrage clignote — non parce
        // que le fond serait laid, mais parce qu'il change.
        !authState.isReady -> {
            AuleTheme {
                AuleAmbientBackground {
                    Text(
                        text = stringResource(R.string.app_name),
                        // Même corps, même interligne, plus de graisse : sur un
                        // fond qui n'est plus vide, le nom doit peser autant que
                        // lui — et la ligne ne bouge pas d'un pixel pour autant.
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        authState.isCheckingAccess -> AccessCheckScreen()
        // Avant tout le reste, y compris avant la carte : une session ouverte
        // par un lien de récupération **n'ouvre que** le choix d'un nouveau mot
        // de passe. Placer ce cas plus bas ferait de la boîte e-mail une porte
        // d'entrée — un vieux lien suffirait à entrer sans rien retaper.
        authState.isResettingPassword -> UpdatePasswordScreen(
            viewModel = authViewModel,
            onCancel = { authViewModel.signOut() },
        )
        !authState.isSignedIn && showingRecovery -> ForgotPasswordScreen(
            viewModel = authViewModel,
            initialEmail = recoveryEmail,
            onBack = { showingRecovery = false },
        )
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
        // L'accueil vient **après** la session : il demande la localisation, et
        // la demander à quelqu'un qui n'a pas encore prouvé qu'il entre dans
        // l'application serait demander pour rien. Il vient avant la carte, en
        // revanche — c'est tout son propos, expliquer avant de demander.
        authState.isSignedIn && !welcomeDone -> WelcomeHost(
            location = graph.location,
            onDone = {
                graph.welcome.markWelcomeSeen()
                welcomeDone = true
            },
        )
        !authState.isSignedIn -> AuthScreen(
            viewModel = authViewModel,
            onCreateAccount = { showingRegistration = true },
            onForgotPassword = { typed ->
                recoveryEmail = typed
                authViewModel.clearRecovery()
                showingRecovery = true
            },
        )
        else -> {
            val mapContext = LocalContext.current
            val mapViewModel: MapViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        MapViewModel(
                            stopRepository = graph.stops,
                            vehicleRepository = graph.vehicles,
                            linePaletteRepository = graph.linePalette,
                            traces = graph.traces,
                            placeRepository = graph.places,
                            routingRepository = graph.routing,
                            roadRouter = graph.roads,
                            dispatchers = graph.dispatchers,
                            logger = graph.logger,
                            timetableRepository = graph.timetables,
                            // La desserte d'une course sert au plan de ligne du
                            // véhicule suivi. C'est le même dépôt que la relève :
                            // une course GTFS est une course GTFS, qu'on la
                            // regarde pour relever un collègue ou pour savoir où
                            // s'arrête le bus qu'on suit.
                            serviceRepository = graph.services,
                            // La session est **relue** à chaque grille demandée
                            // plutôt que capturée ici : un jeton figé à la
                            // création de l'écran se serait fait refuser au bout
                            // d'une heure, sur un écran qui, lui, n'a pas changé.
                            session = { graph.auth.currentSession() },
                            searchHistory = graph.searchHistory,
                            // Les favoris viennent du disque et sont à l'écran
                            // avant la première image ; le compte ne fait que
                            // les rattraper.
                            savedPlacesStore = graph.savedPlaces,
                            savedPlaceRepository = graph.savedPlaceSync,
                            networkLineRepository = graph.networkLines,
                            // La veille d'un passage sonne et s'affiche comme
                            // une alerte de relève — le son d'abord, parce
                            // qu'une bannière qu'on ne regarde pas ne prévient
                            // personne. Le ton suit les réglages du téléphone
                            // (`AlertTonePolicy`) : en mode silencieux, il se
                            // tait et la bannière reste.
                            onDepartureAlert = { alert, watch ->
                                graph.alertTone.alert()
                                graph.departureAlerts.show(
                                    title = departureAlertTitle(mapContext, alert, watch),
                                    body = departureAlertBody(mapContext, alert, watch),
                                    kind = alert.kind,
                                )
                            },
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
            var showingGuet by rememberSaveable { mutableStateOf(false) }
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
            // L'archive des tracés se recopie hors du fil principal, une fois :
            // 3,4 Mo à sortir des assets au premier lancement, un `stat` ensuite.
            // La carte se monte sans l'attendre — les tracés sont un calque qu'on
            // demande, pas le fond.
            val transitArchiveUrl by produceState<String?>(null, graph) {
                value = withContext(Dispatchers.IO) {
                    graph.transitArchive.ensureExtracted()?.let(TransitTiles::pmtilesUrl)
                }
            }
            MapScreen(
                viewModel = mapViewModel,
                controller = mapController,
                transitArchiveUrl = transitArchiveUrl,
                location = graph.location,
                // L'avatar et le menu descendent d'ici : `:feature:map` ne voit
                // pas `:feature:auth`, et c'est bien ainsi — la carte ne sait
                // rien du compte. Elle reçoit deux morceaux d'écran déjà
                // câblés, et leur donne une place.
                accountAvatar = {
                    AccountAvatarButton(
                        viewModel = authViewModel,
                        onClick = { showingMenu = true },
                    )
                },
                menuSheet = {
                    AccountMenuSheet(
                        viewModel = authViewModel,
                        versionLabel = "${graph.config.versionName} (${graph.config.versionCode})",
                        onOpenProfile = {
                            showingMenu = false
                            showingProfile = true
                        },
                        onOpenGuet = {
                            showingMenu = false
                            showingGuet = true
                        },
                    )
                },
                showingMenu = showingMenu,
                onDismissMenu = { showingMenu = false },
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
            } else if (showingGuet) {
                GuetSettingsHost(
                    store = graph.guetPreferences,
                    networkLines = graph.networkLines,
                    onClose = { showingGuet = false },
                )
            } else if (showingProfile) {
                ProfileScreen(
                    viewModel = authViewModel,
                    appearance = appearance,
                    onAppearance = graph.appearance::setMode,
                    traces = graph.traces,
                    onClose = { showingProfile = false },
                )
            }
            }
        }
    }
    }
}

/**
 * Les barres système suivent l'apparence **de l'application**.
 *
 * `enableEdgeToEdge()` sans argument lit le mode sombre du *téléphone*. Or Aule
 * laisse choisir clair, sombre ou automatique dans les préférences : le système
 * reste en clair, l'application passe en sombre, et la barre d'état garde ses
 * icônes noires — sur un fond devenu noir. La barre de navigation, elle, garde
 * son voile blanc en bas d'un écran sombre.
 *
 * On repose donc le style à chaque bascule, en donnant à Android le vrai état
 * de l'application plutôt que celui de l'appareil. Les voiles sont
 * transparents : le bord-à-bord veut que ce soit l'écran qui peigne dessous.
 *
 * Et il faut le redire **après** [enableEdgeToEdge]. Le thème demande déjà
 * `enforceNavigationBarContrast=false`, mais `SystemBarStyle.auto` remet le
 * contraste système à `true` en repassant : androidx ne le désarme que pour les
 * styles figés clair ou sombre. Android peint alors son propre voile derrière
 * les trois boutons — un bandeau pâle sur toute la largeur, invisible tant
 * qu'une barre d'application opaque occupait ce bord, et bien visible depuis
 * que la carte y descend.
 */
@Composable
private fun SystemBarsFollowAppearance() {
    val night = resolvedNight()
    val view = LocalView.current
    LaunchedEffect(night, view) {
        if (view.isInEditMode) return@LaunchedEffect
        val activity = view.context.findComponentActivity() ?: return@LaunchedEffect
        val transparent = SystemBarStyle.auto(
            lightScrim = android.graphics.Color.TRANSPARENT,
            darkScrim = android.graphics.Color.TRANSPARENT,
        ) { night }
        activity.enableEdgeToEdge(
            statusBarStyle = transparent,
            navigationBarStyle = transparent,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
