package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransitLineBounds
import io.aule.android.core.model.TransitLineFamily
import io.aule.android.core.model.TransitNetwork
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.NetworkLineRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

/**
 * Le volet « Lignes du réseau », côté état.
 *
 * Ce qui se vérifie ici tient en une phrase : **les tracés vivent le temps du
 * volet**. Un réseau qui reste peint après la fermeture recouvre la carte d'un
 * lacis que plus rien n'explique, et c'est le genre de fuite qu'aucun écran ne
 * signale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapNetworkLinesViewModelTest {

    private val lines = listOf(
        TransitLine(
            name = "1",
            colorHex = "#E30613",
            mode = TransportMode.TRAM,
            network = TransitNetwork.NAOLIB,
            headsigns = listOf("Beaujoire > François Mitterrand"),
            bounds = TransitLineBounds(
                southWest = Coordinate(latitude = 47.19, longitude = -1.62),
                northEast = Coordinate(latitude = 47.27, longitude = -1.50),
            ),
        ),
        TransitLine(
            name = "C6",
            colorHex = "#00A754",
            mode = TransportMode.BUS,
            network = TransitNetwork.NAOLIB,
            headsigns = listOf("Hôtel Dieu > Pirmil"),
        ),
        TransitLine(
            name = "E311",
            mode = TransportMode.BUS,
            network = TransitNetwork.ALEOP,
            headsigns = listOf("Blain > Nantes"),
        ),
    )

    private fun withMain(block: suspend TestScope.(MapViewModel, FakeLines) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeLines(lines)
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = MapViewModel(
                stopRepository = FakeStops(),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRoutingStub(),
                roadRouter = FakeRoads(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
                networkLineRepository = repository,
            )
            advanceUntilIdle()
            block(viewModel, repository)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ouvrir le volet lit l inventaire et le range`() = withMain { viewModel, repository ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showingNetworkLines)
        assertTrue(viewModel.state.value.hasSheet)
        val digest = viewModel.networkDigest.value
        assertEquals(
            listOf(
                TransitLineFamily.TRAM,
                TransitLineFamily.CHRONOBUS,
                TransitLineFamily.INTERURBAN,
            ),
            digest.sections.map { it.family },
        )
        assertEquals(3, digest.count)
        assertEquals(1, repository.reads)
    }

    @Test
    fun `l inventaire n est lu qu une fois`() = withMain { viewModel, repository ->
        viewModel.openNetworkLines()
        advanceUntilIdle()
        viewModel.closeNetworkLines()
        viewModel.openNetworkLines()
        advanceUntilIdle()

        // 138 lignes ne changent pas pendant qu'un volet s'ouvre et se referme.
        assertEquals(1, repository.reads)
    }

    @Test
    fun `fermer le volet eteint les traces et la ligne designee`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()
        viewModel.focusNetworkLine("C6")
        assertEquals("C6", viewModel.state.value.focusedNetworkLine)

        viewModel.closeNetworkLines()

        // Les deux partent ensemble : un réseau peint que plus aucun volet
        // n'explique recouvre la carte pour rien.
        assertFalse(viewModel.state.value.showingNetworkLines)
        assertNull(viewModel.state.value.focusedNetworkLine)
        assertFalse(viewModel.state.value.hasSheet)
    }

    @Test
    fun `le geste de retour ferme le volet du reseau`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()
        viewModel.focusNetworkLine("1")

        viewModel.dismissSheet()

        assertFalse(viewModel.state.value.showingNetworkLines)
        assertNull(viewModel.state.value.focusedNetworkLine)
    }

    @Test
    fun `retoucher le meme rang eteint la mise en avant`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        viewModel.focusNetworkLine("C6")
        assertEquals("C6", viewModel.state.value.focusedNetworkLine)
        // Le geste qu'on fait sans réfléchir pour revenir en arrière.
        viewModel.focusNetworkLine("C6")
        assertNull(viewModel.state.value.focusedNetworkLine)
    }

    @Test
    fun `la mise en avant se canonise`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        viewModel.focusNetworkLine("c6")

        // Les tuiles connaissent « C6 » : minuscule, le filtre ne retiendrait rien.
        assertEquals("C6", viewModel.state.value.focusedNetworkLine)
    }

    @Test
    fun `la ligne designee rend son cadre pour emmener la carte`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        viewModel.focusNetworkLine("1")

        val bounds = viewModel.focusedLine()?.bounds
        assertEquals(Coordinate(latitude = 47.19, longitude = -1.62), bounds?.southWest)
        // Une ligne sans cadre ne se cadre pas, et ne lève pas pour autant.
        viewModel.focusNetworkLine("C6")
        assertNull(viewModel.focusedLine()?.bounds)
    }

    @Test
    fun `la recherche filtre par indice et par terminus`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        viewModel.setNetworkLineQuery("blain")
        assertEquals(listOf("E311"), viewModel.networkDigest.value.sections.flatMap { it.lines }.map { it.name })

        viewModel.setNetworkLineQuery("C6")
        assertEquals(listOf("C6"), viewModel.networkDigest.value.sections.flatMap { it.lines }.map { it.name })

        viewModel.setNetworkLineQuery("")
        assertEquals(3, viewModel.networkDigest.value.count)
    }

    @Test
    fun `une seule lettre balaie aussi les terminus`() = withMain { viewModel, _ ->
        viewModel.openNetworkLines()
        advanceUntilIdle()

        viewModel.setNetworkLineQuery("C")

        // Ce test **documente le comportement**, il ne le célèbre pas. L'indice
        // se cherche par le début, les terminus par le **contenu** : « C »
        // remonte donc le tram 1, dont le terminus « François Mitterrand »
        // contient un c. C'est le prix de la règle qui fait qu'on trouve
        // « beaujoire » sans taper « la beaujoire », et c'est le comportement de
        // l'iOS — en diverger silencieusement ferait deux recherches
        // différentes pour la même frappe.
        val found = viewModel.networkDigest.value.sections.flatMap { it.lines }.map { it.name }
        assertEquals(listOf("1", "C6"), found)
    }

    @Test
    fun `sans inventaire branche le volet s ouvre vide sans lever`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = MapViewModel(
                stopRepository = FakeStops(),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRoutingStub(),
                roadRouter = FakeRoads(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.openNetworkLines()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.showingNetworkLines)
            assertTrue(viewModel.networkDigest.value.isEmpty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }

    private class FakeLines(private val lines: List<TransitLine>) : NetworkLineRepository {
        var reads = 0
        override suspend fun allLines(): List<TransitLine> {
            reads++
            return lines
        }
        override suspend fun line(named: String): TransitLine? =
            lines.firstOrNull { it.name.equals(named, ignoreCase = true) }
    }

    private class FakeStops : StopRepository {
        override suspend fun allStops(): List<TransitStop> = emptyList()
        override suspend fun departures(atStopNamed: String) = StopDepartures(
            stopName = atStopNamed,
            outcome = DeparturesOutcome.NOTHING_ANNOUNCED,
            fetchedAt = Instant.EPOCH,
        )
        override suspend fun servingLines(atStopNamed: String) =
            emptyList<io.aule.android.core.model.ServingLine>()
    }

    private class FakePalette : LinePaletteRepository {
        override suspend fun palette(): LinePalette = LinePalette.EMPTY
    }

    private class FakeVehicles : VehicleRepository {
        override suspend fun vehicles(around: Coordinate, radiusMeters: Double, limit: Int) =
            FleetSnapshot.EMPTY
    }

    private class FakePlaces : PlaceSearchRepository {
        override suspend fun search(query: String): List<Place> = emptyList()
    }

    private class FakeRoutingStub : io.aule.android.core.model.repository.RoutingRepository {
        override suspend fun plan(
            mode: io.aule.android.core.model.RouteMode,
            from: Coordinate,
            to: Coordinate,
            preferences: io.aule.android.core.model.RoutePreferences,
            departureAt: Instant?,
            arriveBy: Boolean,
        ) = error("non sollicité")
    }

    private class FakeRoads : io.aule.android.core.model.repository.RoadRouter {
        override suspend fun route(
            from: Coordinate,
            to: Coordinate,
            profile: io.aule.android.core.model.repository.RoadProfile,
        ) = null
    }
}
