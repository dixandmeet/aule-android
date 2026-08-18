package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapSearchViewModelTest {

    private val commerce = TransitStop(
        id = "COMM",
        name = "Commerce",
        coordinate = Coordinate.NANTES,
        mode = TransportMode.TRAM,
        stationName = "Commerce",
    )

    @Test
    fun `un geocodeur muet laisse les arrets`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val places = FakePlaces(fail = true)
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
            traces = NoTraces,
                placeRepository = places,
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.setSearchQuery("commerce")
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals("Commerce", viewModel.state.value.search.stops.first().label)
            assertTrue(viewModel.state.value.search.places.isEmpty())
            assertEquals(false, viewModel.state.value.search.isGeocoding)
            assertTrue(places.calls >= 1)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `deux lettres n appellent pas le geocodeur`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val places = FakePlaces()
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
            traces = NoTraces,
                placeRepository = places,
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.setSearchQuery("co")
            advanceTimeBy(400)
            advanceUntilIdle()

            assertEquals(0, places.calls)
            assertEquals("Commerce", viewModel.state.value.search.stops.first().label)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeStops(private val catalog: List<TransitStop>) : StopRepository {
        override suspend fun allStops() = catalog
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

    private class FakePlaces(
        private val results: List<Place> = emptyList(),
        private val fail: Boolean = false,
    ) : PlaceSearchRepository {
        var calls = 0
        override suspend fun search(query: String): List<Place> {
            calls++
            if (fail) error("502")
            return results
        }
    }

    private class FakeRouting : RoutingRepository {
        override suspend fun plan(
            mode: io.aule.android.core.model.RouteMode,
            from: Coordinate,
            to: Coordinate,
            preferences: io.aule.android.core.model.RoutePreferences,
            departureAt: Instant?,
            arriveBy: Boolean,
        ) = error("itinéraire non sollicité")
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
