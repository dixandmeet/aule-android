package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.Place
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
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
class MapRouteViewModelTest {

    private val origin = RoutePlace(Coordinate.NANTES, "Ma position")
    private val destination = RoutePlace(
        Coordinate(latitude = 47.2412, longitude = -1.5232),
        "Beaujoire",
    )

    @Test
    fun `un plan pret selectionne la premiere variante`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a", "b"))
            val viewModel = viewModel(dispatcher, routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            assertEquals(RouteLoadStatus.LOADING, viewModel.state.value.route?.status)

            advanceUntilIdle()
            val route = viewModel.state.value.route
            assertEquals(RouteLoadStatus.READY, route?.status)
            assertEquals("a", route?.selectedId)
            assertEquals("a", route?.selected?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une reponse lente n ecrase pas un calcul plus recent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(
                plan = samplePlan("slow"),
                delayMs = 5_000,
            )
            val viewModel = viewModel(dispatcher, routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            routing.plan = samplePlan("fast")
            routing.delayMs = 0
            viewModel.routeTo(destination, origin)
            advanceUntilIdle()

            assertEquals("fast", viewModel.state.value.route?.selectedId)

            advanceTimeBy(5_000)
            advanceUntilIdle()
            assertEquals("fast", viewModel.state.value.route?.selectedId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un 404 porte le message du serveur`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(failWith = "Aucun arrêt de transport en commun à proximité")
            val viewModel = viewModel(dispatcher, routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            advanceUntilIdle()

            val route = viewModel.state.value.route
            assertEquals(RouteLoadStatus.ERROR, route?.status)
            assertEquals("Aucun arrêt de transport en commun à proximité", route?.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ouvrir la recherche abandonne l itineraire en cours`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val routing = FakeRouting(plan = samplePlan("a"), delayMs = 5_000)
            val viewModel = viewModel(dispatcher, routing)
            advanceUntilIdle()

            viewModel.routeTo(destination, origin)
            viewModel.activateSearch()
            advanceTimeBy(5_000)
            advanceUntilIdle()

            assertNull(viewModel.state.value.route)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(dispatcher: kotlinx.coroutines.CoroutineDispatcher, routing: FakeRouting) =
        MapViewModel(
            stopRepository = FakeStops(),
            vehicleRepository = FakeVehicles(),
            linePaletteRepository = FakeLinePalette(),
            traces = NoTraces,
            placeRepository = FakePlaces(),
            routingRepository = routing,
            roadRouter = FakeRoadRouter(),
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
        override suspend fun plan(
            mode: RouteMode,
            from: Coordinate,
            to: Coordinate,
            preferences: RoutePreferences,
            departureAt: Instant?,
            arriveBy: Boolean,
        ): RoutePlan {
            if (delayMs > 0) delay(delayMs)
            failWith?.let { error(it) }
            return plan ?: error("aucun plan")
        }
    }

    private class FakeRoadRouter : RoadRouter {
        override suspend fun route(
            from: Coordinate,
            to: Coordinate,
            profile: io.aule.android.core.model.repository.RoadProfile,
        ) = null
    }

    private class TestDispatchers(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
}
