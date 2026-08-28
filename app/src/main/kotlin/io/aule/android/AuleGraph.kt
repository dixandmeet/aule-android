package io.aule.android

import android.content.Context
import android.net.Uri
import io.aule.android.appearance.AppearanceSettings
import io.aule.android.appearance.PreferencesAppearanceStore
import io.aule.android.auth.PreferencesAuthPkceStore
import io.aule.android.auth.PreferencesAuthSessionStore
import io.aule.android.auth.PreferencesRegistrationDraftStore
import io.aule.android.search.PreferencesSavedPlacesStore
import io.aule.android.search.PreferencesSearchHistoryStore
import io.aule.android.assets.AndroidAssetBytes
import io.aule.android.assets.FileCacheStore
import io.aule.android.assets.TransitArchive
import io.aule.android.data.caching.CachedStopRepository
import io.aule.android.guet.PreferencesGuetStore
import io.aule.android.data.tiles.AssetNetworkLineRepository
import io.aule.android.welcome.PreferencesWelcomeStore
import io.aule.android.handover.HandoverAlertNotifier
import io.aule.android.watch.DepartureWatchNotifier
import io.aule.android.handover.PreferencesHandoverAlertStore
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.DefaultDispatchers
import io.aule.android.core.common.config.AppConfig
import io.aule.android.core.common.config.DataSource
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.location.AlertTone
import io.aule.android.core.location.FusedLocationProvider
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.DriverProfileRepository
import io.aule.android.core.model.repository.DriverReportRepository
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.HandoverAlertPrefsStore
import io.aule.android.core.model.repository.HandoverRepository
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RegistrationDraftStore
import io.aule.android.core.model.repository.SavedPlaceRepository
import io.aule.android.core.model.repository.SavedPlacesStore
import io.aule.android.core.model.repository.SearchHistoryStore
import io.aule.android.core.model.repository.GuetPreferencesStore
import io.aule.android.core.model.repository.NetworkLineRepository
import io.aule.android.core.model.repository.WelcomeStore
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.TimetableRepository
import io.aule.android.core.model.repository.VehicleRepository
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.AulePlaceSearchRepository
import io.aule.android.data.aule.AuleRoutingRepository
import io.aule.android.data.aule.AuleStopRepository
import io.aule.android.data.aule.AuleVehicleRepository
import io.aule.android.data.aule.OsrmRoadRouter
import io.aule.android.data.aule.SupabaseAuthRepository
import io.aule.android.data.aule.SupabaseDriverProfileRepository
import io.aule.android.data.aule.SupabaseDriverReportRepository
import io.aule.android.data.aule.SupabaseDriverServiceRepository
import io.aule.android.data.aule.SupabaseHandoverRepository
import io.aule.android.data.aule.SupabaseLinePaletteRepository
import io.aule.android.data.aule.SupabaseSavedPlaceRepository
import io.aule.android.data.aule.SupabaseTimetableRepository
import io.aule.android.log.AndroidLogger
import io.aule.android.traces.FileGpsTraceCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * La racine de composition.
 *
 * C'est le **seul** endroit qui décide quelle implémentation répond à quelle
 * interface. Les écrans reçoivent des interfaces ; aucun ne sait si la donnée
 * vient du BFF, de Supabase ou d'une fixture, et c'est ce qui permet de basculer
 * sans toucher un Composable.
 *
 * Assemblé à la main plutôt que par Hilt (ADR-003) : le graphe tient en quelques
 * dizaines de lignes, et comme tout passe déjà par constructeur, basculer vers
 * Hilt plus tard ne toucherait aucun appelant.
 */
class AuleGraph private constructor(
    val config: AppConfig,
    val logger: AuleLogger,
    val dispatchers: AuleDispatchers,
    val okHttp: OkHttpClient,
    val vehicles: VehicleRepository,
    val stops: StopRepository,
    val timetables: TimetableRepository,
    val linePalette: LinePaletteRepository,
    val places: PlaceSearchRepository,
    val routing: RoutingRepository,
    val roads: RoadRouter,
    val auth: AuthRepository,
    val profiles: DriverProfileRepository,
    val registrationDrafts: RegistrationDraftStore,
    val searchHistory: SearchHistoryStore,
    val savedPlaces: SavedPlacesStore,
    val savedPlaceSync: SavedPlaceRepository,
    val welcome: WelcomeStore,
    val networkLines: NetworkLineRepository,
    val transitArchive: TransitArchive,
    val guetPreferences: GuetPreferencesStore,
    val appearance: AppearanceSettings,
    val traces: GpsTraceCatalog,
    val reports: DriverReportRepository,
    val services: DriverServiceRepository,
    val handovers: HandoverRepository,
    val location: LocationProvider,
    val alertTone: AlertTone,
    val handoverAlertPrefs: HandoverAlertPrefsStore,
    val handoverAlerts: HandoverAlertNotifier,
    val departureAlerts: DepartureWatchNotifier,
) {
    private val _authCallback = MutableStateFlow<Uri?>(null)
    val authCallback: StateFlow<Uri?> = _authCallback.asStateFlow()

    /**
     * Le deep link `io.aule.pro://login-callback/?code=…` arrive ici, que
     * l'activité soit déjà ouverte (`onNewIntent`) ou qu'elle démarre à froid.
     */
    fun offerAuthCallback(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == "io.aule.pro" && uri.host == "login-callback") {
            _authCallback.value = uri
        }
    }

    fun consumeAuthCallback() {
        _authCallback.value = null
    }

    companion object {

        fun create(context: Context): AuleGraph {
            val config = resolveConfig()
            val logger = AndroidLogger(verbose = BuildConfig.VERBOSE_LOGGING)

            logger.info(LogDomain.APP, "Démarrage — ${config.buildLabel}")
            if (!config.supabaseConfigured) {
                // Dit une fois, fort. Une configuration absente qui se tait
                // produit un écran vide dont personne ne connaît la cause.
                logger.warn(
                    LogDomain.APP,
                    "Supabase non configuré : aule.supabaseUrl et " +
                        "aule.supabasePublishableKey manquent dans local.properties.",
                )
            }
            if (config.usesPublicDemoRouter) {
                // Le guidage tient à ce serveur pour chacune de ses consignes,
                // et il n'a ni garantie ni droit d'usage en production. Le jour
                // où il limite le débit, le bandeau retombe **en silence** sur
                // le libellé de la jambe : rien ne plante, l'application cesse
                // simplement de guider. C'est le genre de panne qu'on ne
                // comprend qu'en ayant lu cette ligne au démarrage.
                logger.warn(
                    LogDomain.APP,
                    "Manœuvres servies par le serveur de démonstration public " +
                        "d'OSRM (${config.roadRouterOrigin}) : sans garantie de " +
                        "service. Poser aule.osrmOrigin dans local.properties.",
                )
            }

            // Un seul client OkHttp pour toute l'application. MapLibre recevra
            // celui-ci pour ses tuiles et ses glyphes : un seul pool de
            // connexions, un seul délai d'attente, un seul point de journal.
            val okHttp = AuleHttpClient.defaultOkHttp()
            val http = AuleHttpClient(okHttp, logger)
            val endpoints = AuleEndpoints(config.apiBase)
            val location = FusedLocationProvider(context, logger)
            val auth = SupabaseAuthRepository(
                client = http,
                store = PreferencesAuthSessionStore(context),
                supabaseUrl = config.supabaseUrl,
                publishableKey = config.supabasePublishableKey,
                logger = logger,
                pkce = PreferencesAuthPkceStore(context),
            )

            val profiles = SupabaseDriverProfileRepository(
                client = http,
                supabaseUrl = config.supabaseUrl,
                publishableKey = config.supabasePublishableKey,
            )

            return when (config.dataSource) {
                DataSource.PRODUCTION -> AuleGraph(
                    config = config,
                    logger = logger,
                    dispatchers = DefaultDispatchers,
                    okHttp = okHttp,
                    vehicles = AuleVehicleRepository(endpoints, http),
                    // Le catalogue passe par le disque : un lancement dans un
                    // tunnel doit montrer des arrêts. Le décorateur enveloppe le
                    // dépôt réseau et rien d'autre ne change — c'est la couture
                    // qui existait déjà.
                    stops = CachedStopRepository(
                        upstream = AuleStopRepository(endpoints, http),
                        cache = FileCacheStore(context, logger),
                        // Un scope à part de tout écran : la revalidation est un
                        // travail de fond, et la lier au volet qui a demandé les
                        // arrêts l'annulerait au premier changement de volet.
                        scope = CoroutineScope(SupervisorJob() + DefaultDispatchers.io),
                        logger = logger,
                    ),
                    timetables = SupabaseTimetableRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    linePalette = SupabaseLinePaletteRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    places = AulePlaceSearchRepository(endpoints, http),
                    routing = AuleRoutingRepository(endpoints, http),
                    roads = OsrmRoadRouter(http, config.roadRouterOrigin),
                    auth = auth,
                    profiles = profiles,
                    registrationDrafts = PreferencesRegistrationDraftStore(context),
                    searchHistory = PreferencesSearchHistoryStore(context),
                    savedPlaces = PreferencesSavedPlacesStore(context),
                    savedPlaceSync = SupabaseSavedPlaceRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    welcome = PreferencesWelcomeStore(context),
                    networkLines = AssetNetworkLineRepository(AndroidAssetBytes(context)),
                    transitArchive = TransitArchive(context, logger),
                    guetPreferences = PreferencesGuetStore(context),
                    appearance = AppearanceSettings(PreferencesAppearanceStore(context)),
                    traces = FileGpsTraceCatalog(context),
                    reports = SupabaseDriverReportRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    services = SupabaseDriverServiceRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    handovers = SupabaseHandoverRepository(
                        client = http,
                        supabaseUrl = config.supabaseUrl,
                        publishableKey = config.supabasePublishableKey,
                    ),
                    location = location,
                    alertTone = AlertTone(context),
                    handoverAlertPrefs = PreferencesHandoverAlertStore(context),
                    handoverAlerts = HandoverAlertNotifier(context),
                    departureAlerts = DepartureWatchNotifier(context),
                )

                // Ces deux chemins arrivent avec leurs lots respectifs. Ils
                // échouent bruyamment plutôt que de retomber en silence sur la
                // production : se tromper de source de données est le genre de
                // défaut qui ne se voit qu'une fois quelqu'un devant un véhicule
                // qui n'existe pas.
                DataSource.DEVELOPMENT -> error(
                    "La source « development » (Supabase en direct) n'est pas encore branchée.",
                )

                DataSource.MOCK -> error(
                    "La source « mock » n'est pas encore branchée. Les fixtures ne seront " +
                        "compilées que dans le flavor development.",
                )
            }
        }

        private fun resolveConfig(): AppConfig {
            val requested = DataSource.of(BuildConfig.DEFAULT_DATA_SOURCE)

            // La fixture n'est pas seulement interdite en production : elle n'y
            // sera pas compilée (ADR-005). Cette garde attrape le cas où
            // quelqu'un changerait DEFAULT_DATA_SOURCE dans le flavor production,
            // ce que le compilateur ne peut pas voir.
            check(!(requested == DataSource.MOCK && !BuildConfig.ALLOW_MOCK_SOURCE)) {
                "Le flavor ${BuildConfig.ENVIRONMENT_LABEL} ne peut pas servir de fixtures."
            }

            return AppConfig(
                dataSource = requested,
                apiBase = BuildConfig.AULE_API_BASE,
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                roadRouterOrigin = BuildConfig.OSRM_ORIGIN,
                environmentLabel = BuildConfig.ENVIRONMENT_LABEL,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
            )
        }
    }
}
