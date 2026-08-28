package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.NextActionKind
import io.aule.android.core.model.Place
import io.aule.android.core.model.RoadManeuver
import io.aule.android.core.model.RouteCandidate
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePlace
import io.aule.android.core.model.RoutePlan
import io.aule.android.core.model.RoutePreferences
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import io.aule.android.core.model.repository.GpsTraceRecorder
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRoute
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapGuidanceViewModelTest {

    private val origin = RoutePlace(Coordinate.NANTES, "Ma position")
    private val destination = RoutePlace(
        Coordinate(latitude = 47.2412, longitude = -1.5232),
        "Beaujoire",
    )

    @Test
    fun `demarrer sans trajet retenu ne fait rien`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher)
            advanceUntilIdle()
            assertFalse(viewModel.startGuidance(origin.coordinate))
            assertNull(viewModel.state.value.navigation)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `demarrer sur un plan pret ouvre le guidage sans etape de chargement`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher, routing = FakeRouting(plan = samplePlan("a")))
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            assertFalse(viewModel.startGuidance(origin.coordinate))

            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))

            val navigation = assertNotNull(viewModel.state.value.navigation)
            val route = assertNotNull(viewModel.state.value.route)
            assertEquals(RouteLoadStatus.READY, route.status)
            assertEquals("a", route.selectedId)
            assertEquals(NextActionKind.FOLLOW, navigation.action?.kind)
            assertTrue(navigation.action?.title?.isNotBlank() == true)
            assertFalse(navigation.offRoute)
            assertFalse(navigation.signalLost)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un OSRM muet laisse le libelle de la jambe`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val roads = FakeRoadRouter(result = null)
            val viewModel = viewModel(
                dispatcher,
                routing = FakeRouting(plan = samplePlan("a")),
                roads = roads,
            )
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()

            val action = assertNotNull(viewModel.state.value.navigation?.action)
            assertEquals(NextActionKind.FOLLOW, action.kind)
            assertTrue(action.title.isNotBlank())
            assertTrue(roads.calls >= 1)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une reponse OSRM lente n ecrase pas un guidage arrete`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val roads = FakeRoadRouter(
                delayMs = 5_000,
                result = RoadRoute(
                    points = listOf(origin.coordinate, destination.coordinate),
                    distanceMeters = 4_200.0,
                    durationSeconds = 800.0,
                    maneuvers = listOf(
                        RoadManeuver(
                            instruction = "Tourner à droite",
                            location = destination.coordinate,
                            distanceMeters = 80.0,
                            durationSeconds = 20.0,
                            streetName = "Rue de la Beaujoire",
                            modifier = "right",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                dispatcher,
                routing = FakeRouting(plan = samplePlan("a")),
                roads = roads,
            )
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            viewModel.stopGuidance()
            assertNull(viewModel.state.value.navigation)

            advanceTimeBy(5_000)
            advanceUntilIdle()
            assertNull(viewModel.state.value.navigation)
            assertEquals(RouteLoadStatus.READY, viewModel.state.value.route?.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans fix le bandeau signale le GPS perdu`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel(dispatcher, routing = FakeRouting(plan = samplePlan("a")))
            advanceUntilIdle()
            viewModel.routeTo(destination, origin)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance())
            viewModel.onGuidanceFix(null)
            assertTrue(viewModel.state.value.navigation?.signalLost == true)

            viewModel.onGuidanceFix(
                LocationFix(
                    coordinate = origin.coordinate,
                    accuracyMeters = 8.0,
                    courseDegrees = 0.0,
                    speedMetersPerSecond = 1.2,
                    timestampMillis = 1_000,
                    stabilizedHeading = 0.0,
                    isHeadingFrozen = false,
                ),
            )
            assertFalse(viewModel.state.value.navigation?.signalLost == true)
            advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le guidage ecrit une trace et la referme en s arretant`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val traces = RecordingTraces()
            val viewModel = viewModel(
                dispatcher,
                routing = FakeRouting(plan = samplePlan("a")),
                traces = traces,
            )
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()

            val recorder = assertNotNull(traces.recorders.singleOrNull())
            viewModel.onGuidanceFix(fixAt(origin.coordinate))
            viewModel.onGuidanceFix(fixAt(destination.coordinate))
            assertEquals(2, recorder.points.size)

            // Une position inexploitable ne rentre pas : la trace doit
            // ressembler à ce sur quoi le guidage a réellement décidé.
            viewModel.onGuidanceFix(fixAt(origin.coordinate, accuracy = 400.0))
            assertEquals(2, recorder.points.size)

            // La boucle relit la position chaque seconde là où le GPS n'en
            // publie une que toutes les huit : la même mesure ne s'écrit pas
            // deux fois.
            viewModel.onGuidanceFix(fixAt(destination.coordinate))
            assertEquals(2, recorder.points.size)

            viewModel.stopGuidance()
            advanceUntilIdle()
            assertTrue(recorder.closed)

            // Le guidage suivant ouvre sa propre trace, pas celle d'avant.
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()
            assertEquals(2, traces.recorders.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * La sortie qu'on oublie : on quitte l'application par le geste de retour,
     * sans passer par « Arrêter ». Relevé sur le S21 — la trace restait
     * ouverte et son tampon partait avec le processus.
     */
    @Test
    fun `quitter l ecran en plein guidage referme la trace`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val traces = RecordingTraces()
            val viewModel = viewModel(
                dispatcher,
                routing = FakeRouting(plan = samplePlan("a")),
                traces = traces,
            )
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            viewModel.onGuidanceFix(fixAt(origin.coordinate))
            advanceUntilIdle()

            val recorder = assertNotNull(traces.recorders.singleOrNull())
            assertFalse(recorder.closed)

            viewModel.clearForTest()
            advanceUntilIdle()
            assertTrue(recorder.closed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Le défaut de la recette : rater une sortie n'était pas rattrapé.
     *
     * La sortie de tracé était détectée depuis toujours — trois mesures au-delà
     * du seuil — mais ne posait qu'un bandeau « Rejoignez le tracé ». Sur une
     * heure de route, ne jamais s'écarter du tracé n'est pas un cas nominal.
     */
    @Test
    fun `sortir du trace demande un nouvel itineraire`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"))
            val viewModel = viewModel(dispatcher, routing = routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin, mode = RouteMode.CAR)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()
            val plansBefore = routing.calls

            // Le trajet suivant qu'on rendra : un autre identifiant, pour
            // pouvoir vérifier que c'est bien celui-là qui prend la place.
            routing.plan = samplePlan("b")
            driveOffRoute(viewModel)
            advanceUntilIdle()

            assertTrue(routing.calls > plansBefore, "un recalcul doit être parti")
            val navigation = assertNotNull(viewModel.state.value.navigation)
            assertFalse(navigation.recalculating, "le recalcul est terminé")
            assertFalse(navigation.offRoute, "on est sur le nouveau tracé, donc dessus")
            assertEquals("b", viewModel.state.value.route?.selectedId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * En transport, un écart de trente mètres n'est pas une erreur de trajet :
     * c'est une géométrie de ligne approximative, ou un bus qui prend une
     * contre-allée. Rendre un autre itinéraire — donc une autre correspondance
     * — à quelqu'un assis dans le bon véhicule serait pire que le bandeau.
     */
    @Test
    fun `un trajet en transport ne se recalcule pas tout seul`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"))
            val viewModel = viewModel(dispatcher, routing = routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin, mode = RouteMode.TRANSIT)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()
            val before = routing.calls

            routing.plan = samplePlan("b")
            driveOffRoute(viewModel)
            advanceUntilIdle()

            assertEquals(before, routing.calls, "aucun recalcul en transport")
            assertTrue(viewModel.state.value.navigation?.offRoute == true, "le bandeau, lui, reste")
            assertEquals("a", viewModel.state.value.route?.selectedId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Le trajet d'avant reste à l'écran tant qu'on n'a pas mieux : il est faux,
     * mais il est orienté, et un écran nu à quatre-vingt-dix à l'heure est pire
     * qu'un tracé périmé.
     */
    @Test
    fun `un moteur muet laisse le trajet et le bandeau de sortie`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"))
            val viewModel = viewModel(dispatcher, routing = routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin, mode = RouteMode.CAR)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()

            routing.failWith = "502"
            driveOffRoute(viewModel)
            advanceUntilIdle()

            val navigation = assertNotNull(viewModel.state.value.navigation)
            assertFalse(navigation.recalculating)
            assertTrue(navigation.offRoute, "sans nouveau trajet, on est toujours dehors")
            assertEquals("a", viewModel.state.value.route?.selectedId, "l'ancien plan tient")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * `OffRouteDetector` remet son compteur à zéro en déclenchant : sans
     * temporisation, un moteur en panne se ferait appeler toutes les trois
     * secondes pendant tout le trajet.
     */
    @Test
    fun `deux sorties rapprochees ne lancent qu un calcul`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"))
            val viewModel = viewModel(dispatcher, routing = routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin, mode = RouteMode.CAR)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()

            routing.failWith = "502"
            driveOffRoute(viewModel, fromMillis = 100_000L)
            advanceUntilIdle()
            val afterFirst = routing.calls

            // Trois secondes plus tard, le détecteur peut redéclencher. La
            // temporisation, elle, tient douze secondes.
            driveOffRoute(viewModel, fromMillis = 103_000L)
            advanceUntilIdle()
            assertEquals(afterFirst, routing.calls, "la temporisation retient le second")

            driveOffRoute(viewModel, fromMillis = 130_000L)
            advanceUntilIdle()
            assertTrue(routing.calls > afterFirst, "passé le délai, on retente")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Revenir sur le tracé de soi-même annule le calcul en cours : lui
     * substituer un autre trajet ferait sauter le tracé sous les yeux du
     * conducteur alors qu'il vient de se rattraper.
     */
    @Test
    fun `revenir sur le trace annule le recalcul`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"), delayMs = 5_000)
            val viewModel = viewModel(dispatcher, routing = routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin, mode = RouteMode.CAR)
            advanceUntilIdle()
            assertTrue(viewModel.startGuidance(origin.coordinate))
            advanceUntilIdle()

            routing.plan = samplePlan("b")
            driveOffRoute(viewModel, fromMillis = 200_000L)
            assertTrue(
                viewModel.state.value.navigation?.recalculating == true,
                "le calcul est en vol",
            )

            // Retour sur le tracé avant que la réponse n'arrive.
            viewModel.onGuidanceFix(fixAt(onRoute, atMillis = 201_000L))
            advanceUntilIdle()

            assertEquals("a", viewModel.state.value.route?.selectedId, "on garde le trajet suivi")
            assertFalse(viewModel.state.value.navigation?.offRoute == true)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Un point **sur** le tracé, un peu plus loin que l'écart : celui où l'on
     * rejoint. À 8 % du trajet, il tombe dans la fenêtre avant de
     * [io.aule.android.core.geo.PolylineProjection].
     */
    private val onRoute = Coordinate(latitude = 47.220224, longitude = -1.551168)

    /**
     * Trois mesures à cent vingt mètres du tracé, tout près de son début.
     *
     * Deux contraintes se croisent ici, et les rater ne teste plus rien :
     *
     * - l'écart doit dépasser le seuil de base de 32 m — d'où les 120 m, qui
     *   sont l'écart d'une sortie ratée, pas d'une erreur de GPS ;
     * - la progression n'avance que de [io.aule.android.core.geo.PolylineProjection.FORWARD_WINDOW]
     *   par mesure, soit 12 % du tracé. Un point choisi au milieu du trajet
     *   serait **hors fenêtre** au premier coup : la projection se collerait au
     *   bord de la fenêtre et rendrait une déviation d'un kilomètre, qui n'est
     *   pas celle qu'on croit mesurer.
     */
    private fun driveOffRoute(viewModel: MapViewModel, fromMillis: Long = 50_000L) {
        val astray = Coordinate(latitude = 47.220264, longitude = -1.553256)
        repeat(3) { index ->
            viewModel.onGuidanceFix(fixAt(astray, atMillis = fromMillis + index * 1_000L))
        }
    }

    private fun fixAt(
        at: Coordinate,
        accuracy: Double = 5.0,
        atMillis: Long = at.latitude.toRawBits(),
    ) = LocationFix(
        coordinate = at,
        accuracyMeters = accuracy,
        courseDegrees = 90.0,
        speedMetersPerSecond = 8.0,
        timestampMillis = atMillis,
        stabilizedHeading = 90.0,
        isHeadingFrozen = false,
    )

    private fun viewModel(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        routing: FakeRouting = FakeRouting(),
        roads: FakeRoadRouter = FakeRoadRouter(),
        traces: GpsTraceCatalog = NoTraces,
    ) = MapViewModel(
        stopRepository = FakeStops(),
        vehicleRepository = FakeVehicles(),
        linePaletteRepository = FakeLinePalette(),
        traces = traces,
        placeRepository = FakePlaces(),
        routingRepository = routing,
        roadRouter = roads,
        dispatchers = TestDispatchers(dispatcher),
        logger = NoopLogger,
    )

    private fun samplePlan(vararg ids: String) = RoutePlan(
        alternatives = ids.map { id ->
            RouteCandidate(
                id = id,
                coordinates = listOf(origin.coordinate, destination.coordinate),
                segments = emptyList(),
                distanceMeters = 4_200,
                durationMinutes = 18,
                steps = emptyList(),
                summary = "",
                accessible = false,
                alertCount = 0,
                profiles = emptyList(),
            )
        },
        departures = emptyList(),
        selectedId = ids.first(),
        timetable = true,
    )

    private class FakeStops : StopRepository {
        override suspend fun allStops() = emptyList<TransitStop>()
        override suspend fun departures(atStopNamed: String) = StopDepartures(
            stopName = atStopNamed,
            outcome = DeparturesOutcome.NOTHING_ANNOUNCED,
            fetchedAt = Instant.EPOCH,
        )
        override suspend fun servingLines(atStopNamed: String) = emptyList<io.aule.android.core.model.ServingLine>()
    }

    private class FakeLinePalette : LinePaletteRepository {

        override suspend fun palette(): LinePalette = LinePalette.EMPTY

    }


    private class FakeVehicles : VehicleRepository {
        override suspend fun vehicles(around: Coordinate, radiusMeters: Double, limit: Int) =
            FleetSnapshot.EMPTY
    }

    private class FakePlaces : PlaceSearchRepository {
        override suspend fun search(query: String) = emptyList<Place>()
    }

    private class FakeRouting(
        var plan: RoutePlan? = null,
        var delayMs: Long = 0,
        var failWith: String? = null,
    ) : RoutingRepository {
        /** Combien de fois le moteur a été sollicité — recalculs compris. */
        var calls = 0
        override suspend fun plan(
            mode: RouteMode,
            from: Coordinate,
            to: Coordinate,
            preferences: RoutePreferences,
            departureAt: Instant?,
            arriveBy: Boolean,
        ): RoutePlan {
            calls++
            if (delayMs > 0) delay(delayMs)
            failWith?.let { error(it) }
            return plan ?: error("aucun plan")
        }
    }

    private class FakeRoadRouter(
        var result: RoadRoute? = null,
        var delayMs: Long = 0,
    ) : RoadRouter {
        var calls = 0
        override suspend fun route(
            from: Coordinate,
            to: Coordinate,
            profile: RoadProfile,
        ): RoadRoute? {
            calls++
            if (delayMs > 0) delay(delayMs)
            return result
        }
    }

    private class TestDispatchers(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
}
