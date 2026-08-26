package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.Place
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import io.aule.android.core.model.RouteMode
import io.aule.android.core.model.RoutePreferences
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

/**
 * Le volet d'une ligne, du point de vue de la carte.
 *
 * Ce qui se teste ici est l'empilement : une ligne s'ouvre **par-dessus** un
 * arrêt, le retour défait ce pas-là et pas la consultation entière, et une
 * veille armée survit à tout ce qu'on fait ensuite du volet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapLineSheetViewModelTest {

    private val ranzay = TransitStop(
        id = "RANZ1",
        name = "Ranzay",
        coordinate = Coordinate(47.24, -1.53),
        mode = TransportMode.BUS,
        stationName = "Ranzay",
    )

    private val row = DepartureRow(
        id = "C6|Hermeland",
        line = "C6",
        lineColor = "#8d6cbf",
        destination = "Hermeland",
        mode = TransportMode.BUS,
        isRealtime = true,
        waits = listOf(5, 17),
    )

    @Test
    fun `la ligne s ouvre sur l arret et le retour y revient`() = runTest {
        withViewModel { viewModel ->
            viewModel.select(ranzay)
            viewModel.openLine(ranzay, row)

            assertTrue(viewModel.state.value.showingLine)
            // L'arrêt reste sélectionné : sa pastille reste allumée sur la
            // carte, et c'est là qu'on revient.
            assertEquals(ranzay, viewModel.state.value.selectedStop)
            assertEquals("C6", viewModel.departureWatch.state.value.viewed?.line)

            viewModel.closeLine()

            assertNull(viewModel.state.value.lineFocus)
            assertEquals(ranzay, viewModel.state.value.selectedStop)
        }
    }

    /** Le mode manque parfois au passage ; l'arrêt, lui, le connaît toujours. */
    @Test
    fun `le mode de l arret comble celui du passage`() = runTest {
        withViewModel { viewModel ->
            viewModel.openLine(ranzay, row.copy(mode = null))

            assertEquals(TransportMode.BUS, viewModel.state.value.lineFocus?.mode)
        }
    }

    /** Toucher un véhicule referme le volet — jamais la veille. */
    @Test
    fun `la veille survit au changement de volet`() = runTest {
        withViewModel { viewModel ->
            viewModel.select(ranzay)
            viewModel.openLine(ranzay, row)
            runCurrent()
            viewModel.toggleWatch()

            viewModel.select(vehicle())

            assertNull(viewModel.state.value.lineFocus)
            assertEquals("C6", viewModel.departureWatch.state.value.armed?.line)
        }
    }

    /** La pastille ramène à la ligne, et rallume l'arrêt d'où elle venait. */
    @Test
    fun `la pastille rouvre la ligne veillee`() = runTest {
        withViewModel { viewModel ->
            runCurrent()
            viewModel.select(ranzay)
            viewModel.openLine(ranzay, row)
            runCurrent()
            viewModel.toggleWatch()
            viewModel.dismissSheet()
            assertNull(viewModel.state.value.lineFocus)

            viewModel.reopenWatch()

            assertTrue(viewModel.state.value.showingLine)
            assertEquals(ranzay, viewModel.state.value.selectedStop)
            assertNotNull(viewModel.state.value.lineFocus)
        }
    }

    /** Sans veille, refermer le volet ne laisse rien derrière. */
    @Test
    fun `sans veille, refermer le volet oublie la ligne`() = runTest {
        withViewModel { viewModel ->
            viewModel.select(ranzay)
            viewModel.openLine(ranzay, row)
            runCurrent()
            viewModel.dismissSheet()
            runCurrent()

            assertNull(viewModel.departureWatch.state.value.viewed)
        }
    }

    // ------------------------------------------------------------- fabriques

    private fun kotlinx.coroutines.test.TestScope.withViewModel(
        block: (MapViewModel) -> Unit,
    ) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(ranzay)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            runCurrent()
            block(viewModel)
            viewModel.clearForTest()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun vehicle() = TransportVehicle(
        id = "bus-1",
        mode = TransportMode.BUS,
        feed = VehicleFeed.LIVE,
        lineId = "C6",
        lineName = "C6",
        coordinate = Coordinate(47.24, -1.53),
    )

    private class FakeStops(private val catalog: List<TransitStop>) : StopRepository {
        override suspend fun allStops() = catalog
        override suspend fun departures(atStopNamed: String) = StopDepartures(
            stopName = atStopNamed,
            outcome = DeparturesOutcome.NOTHING_ANNOUNCED,
            fetchedAt = Instant.EPOCH,
        )
        override suspend fun servingLines(atStopNamed: String) = emptyList<ServingLine>()
    }

    private class FakeLinePalette : LinePaletteRepository {
        override suspend fun palette(): LinePalette = LinePalette.EMPTY
    }

    private class FakeVehicles : VehicleRepository {
        override suspend fun vehicles(around: Coordinate, radiusMeters: Double, limit: Int) =
            FleetSnapshot.EMPTY
    }

    private class FakePlaces : PlaceSearchRepository {
        override suspend fun search(query: String): List<Place> = emptyList()
    }

    private class FakeRouting : RoutingRepository {
        override suspend fun plan(
            mode: RouteMode,
            from: Coordinate,
            to: Coordinate,
            preferences: RoutePreferences,
            departureAt: Instant?,
            arriveBy: Boolean,
        ) = error("itinéraire non sollicité")
    }

    private class FakeRoadRouter : RoadRouter {
        override suspend fun route(from: Coordinate, to: Coordinate, profile: RoadProfile) = null
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
}
