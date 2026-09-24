package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.Place
import io.aule.android.core.model.PlaceSearchSession
import io.aule.android.core.model.PlaceSuggestion
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.rememberPlace
import io.aule.android.core.model.repository.GpsTraceCatalog
import io.aule.android.core.model.repository.GpsTraceFile
import io.aule.android.core.model.repository.GpsTraceRecorder
import io.aule.android.core.model.repository.LinePaletteRepository
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.RoutingRepository
import io.aule.android.core.model.repository.SearchHistoryStore
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.core.model.repository.VehicleRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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

    @Test
    fun `un lieu choisi entre dans l historique et s affiche a la reouverture`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val history = MemorySearchHistory()
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
                searchHistory = history,
            )
            advanceUntilIdle()

            val beaujoire = Place(
                label = "Beaujoire, 44300 Nantes",
                coordinate = Coordinate(latitude = 47.2560, longitude = -1.5250),
            )
            viewModel.select(beaujoire)
            advanceUntilIdle()

            // Choisir referme la recherche : l'historique ne se voit qu'à la
            // prochaine ouverture, et c'est là qu'il faut le lire.
            assertTrue(viewModel.state.value.search.history.isEmpty())

            viewModel.activateSearch()
            advanceUntilIdle()

            val search = viewModel.state.value.search
            assertEquals(listOf("Beaujoire, 44300 Nantes"), search.history.map { it.label })
            assertTrue(search.showsHistory)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un arret choisi est retenu comme arret et non comme adresse`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val history = MemorySearchHistory()
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
                searchHistory = history,
            )
            advanceUntilIdle()

            viewModel.setSearchQuery("commerce")
            advanceTimeBy(400)
            advanceUntilIdle()
            viewModel.select(viewModel.state.value.search.stops.first())
            advanceUntilIdle()

            // C'est le mode, jamais le libellé, qui dira qu'on peut demander
            // les passages de ce lieu.
            val kept = history.read().single()
            assertEquals("Commerce", kept.label)
            assertEquals(TransportMode.TRAM, kept.stopMode)
            // L'écran, lui, reçoit bien l'arrêt du catalogue, avec ses quais.
            assertEquals(commerce, viewModel.state.value.selectedStop)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `repousser le volet garde la frappe et rend les resultats`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.setSearchQuery("commerce")
            advanceTimeBy(400)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.search.stops.isNotEmpty())

            viewModel.collapseSearch()
            advanceUntilIdle()

            // Le champ garde ce qu'on a tapé — repousser n'est pas annuler —
            // mais les réponses partent avec le volet.
            val collapsed = viewModel.state.value.search
            assertEquals("commerce", collapsed.query)
            assertTrue(!collapsed.isActive)
            assertTrue(collapsed.stops.isEmpty())

            viewModel.activateSearch()
            advanceTimeBy(400)
            advanceUntilIdle()

            // Rouvrir repose la question : sans cela, « Commerce » se serait
            // rouvert sur « Aucun résultat pour commerce ».
            val reopened = viewModel.state.value.search
            assertEquals("commerce", reopened.query)
            assertTrue(reopened.isActive)
            assertEquals(listOf("Commerce"), reopened.stops.map { it.label })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans historique branche la recherche marche a l identique`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = MapViewModel(
                stopRepository = FakeStops(listOf(commerce)),
                vehicleRepository = FakeVehicles(),
                linePaletteRepository = FakeLinePalette(),
                traces = NoTraces,
                placeRepository = FakePlaces(),
                routingRepository = FakeRouting(),
                roadRouter = FakeRoadRouter(),
                dispatchers = TestDispatchers(dispatcher),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.select(
                Place(label = "Beaujoire", coordinate = Coordinate.NANTES),
            )
            viewModel.activateSearch()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.search.history.isEmpty())
            assertTrue(!viewModel.state.value.search.showsHistory)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------ situer au choix, et seulement au choix

    /** « gare de nantes » et « commerce », relevés sur `www.aule.fr` le 24/09/2026. */
    private val gare = PlaceSuggestion.Prediction(
        placeId = "ChIJkXDHRLnuBUgRlpH_NZEcZ1s",
        label = "Gare de Nantes, Boulevard de Stalingrad, Nantes, France",
        details = "Boulevard de Stalingrad, Nantes, France",
        isTransitStop = true,
    )
    private val gareSud = PlaceSuggestion.Prediction(
        placeId = "ChIJpTwOOrjuBUgRdu0h4YjeGsk",
        label = "Gare de Nantes Sud, Rue de Lourmel, Nantes, France",
        details = "Rue de Lourmel, Nantes, France",
    )
    private val commerceArret = PlaceSuggestion.Prediction(
        placeId = "ChIJ-dFxE6buBUgRFa9LIcUym8w",
        label = "Commerce, Nantes, France",
        details = "Nantes, France",
        isTransitStop = true,
    )
    private val placeDuCommerce = PlaceSuggestion.Prediction(
        placeId = "EiFQbGFjZSBkdSBDb21tZXJjZSwgTmFudGVzLCBGcmFuY2UiLiosChQKEgkDmbp5qO4FSBF4wNGXtV3yPBIUChIJra6o8IHuBUgRMO0NHlI3DQQ",
        label = "Place du Commerce, Nantes, France",
        details = "Nantes, France",
    )

    /** Ce que `?placeId=` rend pour la gare : la fiche s'appelle « Nantes », l'adresse est à part. */
    private val gareAddress = mapOf(gare.placeId to "27 Bd de Stalingrad, 44041 Nantes, France")

    private fun searchTest(block: suspend TestScope.(TestDispatcher) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            block(dispatcher)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun searchModel(
        dispatcher: TestDispatcher,
        places: FakePlaces,
        stops: StopRepository = FakeStops(listOf(commerce)),
    ) = MapViewModel(
        stopRepository = stops,
        vehicleRepository = FakeVehicles(),
        linePaletteRepository = FakeLinePalette(),
        traces = NoTraces,
        placeRepository = places,
        routingRepository = FakeRouting(),
        roadRouter = FakeRoadRouter(),
        dispatchers = TestDispatchers(dispatcher),
        logger = NoopLogger,
    )

    /** Taper, puis laisser la frappe reposer : le géocodeur a répondu. */
    private fun TestScope.type(viewModel: MapViewModel, query: String) {
        viewModel.setSearchQuery(query)
        advanceTimeBy(400)
        advanceUntilIdle()
    }

    /**
     * Le défaut de `686d1d3`, dans sa forme exacte : chaque frappe reposée situait les cinq
     * premières adresses, et la liste sortait juste. C'est le compte des résolutions qui le dit.
     */
    @Test
    fun `une frappe ne situe aucune adresse`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gareSud, gare))
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de")
        type(viewModel, "gare de nantes")

        assertEquals(listOf(gareSud, gare), viewModel.state.value.search.places)
        assertEquals(2, places.calls)
        assertTrue(places.resolutions.isEmpty(), "rien ne se situe tant qu'on n'a rien touché")
    }

    /**
     * Toucher situe **une** adresse, dans la session des frappes qui l'ont proposée : Google
     * facture alors l'ensemble comme une seule recherche. Et le nom touché titre le lieu — la
     * fiche Google de la gare s'appelle « Nantes ».
     */
    @Test
    fun `toucher une adresse la situe une fois dans la session de ses frappes`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gareSud, gare), addresses = gareAddress)
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de")
        type(viewModel, "gare de nantes")
        var concluded: Place? = null
        viewModel.choose(gare) { concluded = it }
        assertEquals(gare, viewModel.state.value.search.locating, "le rang dit qu'on le situe")
        advanceUntilIdle()

        val session = places.sessions.first()
        assertNotNull(session)
        assertEquals(listOf<PlaceSearchSession?>(session, session), places.sessions, "une recherche, un jeton")
        assertEquals(listOf<Pair<PlaceSuggestion.Prediction, PlaceSearchSession?>>(gare to session), places.resolutions)
        assertEquals("Gare de Nantes, 27 Bd de Stalingrad, 44041 Nantes, France", concluded?.label)
        assertNull(viewModel.state.value.search.locating)
    }

    /** Le jeton meurt au choix : réutilisé, il ferait facturer la recherche suivante à l'unité. */
    @Test
    fun `la recherche qui suit un choix a son propre jeton`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), addresses = gareAddress)
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        viewModel.choose(gare) {}
        advanceUntilIdle()
        type(viewModel, "gare de nantes sud")

        assertEquals(2, places.sessions.size)
        assertNotEquals(places.sessions[0], places.sessions[1])
    }

    @Test
    fun `vider le champ recommence la recherche avec un jeton neuf`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare))
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        type(viewModel, "")
        type(viewModel, "commerce")

        assertNotEquals(places.sessions[0], places.sessions[1])
    }

    /**
     * ⚠️ **Une résolution manquée n'est pas une recherche manquée.** Les autres adresses restent
     * bonnes : les effacer ferait perdre quatre lieux à cause d'un cinquième.
     */
    @Test
    fun `une resolution manquee se dit sans effacer la liste`() = searchTest { dispatcher ->
        val places = FakePlaces(
            results = listOf(gareSud, gare),
            resolveFailure = IllegalStateException("502"),
        )
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        var concluded: Place? = null
        viewModel.choose(gare) { concluded = it }
        advanceUntilIdle()

        val search = viewModel.state.value.search
        assertNull(concluded)
        assertEquals(gare, search.unlocated)
        assertNull(search.locating)
        assertEquals(listOf(gareSud, gare), search.places)
    }

    /**
     * Taper, c'est changer d'avis : sans l'abandon, l'itinéraire de la gare s'ouvrait un
     * aller-retour plus tard, pendant qu'on tapait déjà autre chose.
     */
    @Test
    fun `une frappe abandonne la resolution en vol`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), addresses = gareAddress, resolveDelayMs = 1_000)
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        var concluded: Place? = null
        viewModel.choose(gare) { concluded = it }
        advanceTimeBy(100)
        type(viewModel, "gare de nantes sud")
        advanceUntilIdle()

        assertNull(concluded, "un choix dépassé ne conclut rien")
        assertNull(viewModel.state.value.search.locating)
        assertNull(viewModel.state.value.search.unlocated, "un abandon n'est pas un échec")
    }

    @Test
    fun `replier le volet abandonne la resolution en vol`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), addresses = gareAddress, resolveDelayMs = 1_000)
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        var concluded: Place? = null
        viewModel.choose(gare) { concluded = it }
        advanceTimeBy(100)
        viewModel.collapseSearch()
        advanceUntilIdle()

        assertNull(concluded)
        assertNull(viewModel.state.value.search.locating)
    }

    /** Retoucher le rang qu'on situe ne relance pas une requête que le fournisseur facturerait. */
    @Test
    fun `retoucher l adresse qu on situe ne relance rien`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), addresses = gareAddress, resolveDelayMs = 1_000)
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "gare de nantes")
        var conclusions = 0
        viewModel.choose(gare) { conclusions++ }
        advanceTimeBy(100)
        viewModel.choose(gare) { conclusions++ }
        advanceUntilIdle()

        assertEquals(1, places.resolutions.size)
        assertEquals(1, conclusions)
    }

    /** Un lieu déjà situé — le repli IGN rend le point d'emblée — part sans aller-retour. */
    @Test
    fun `un lieu deja situe part sans aller retour`() = searchTest { dispatcher ->
        val rue = PlaceSuggestion.Located(
            Place(label = "Rue de Strasbourg, 44000 Nantes", coordinate = Coordinate(47.216979, -1.552075)),
        )
        val places = FakePlaces(results = listOf(rue))
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "rue de strasbourg")
        var concluded: Place? = null
        viewModel.choose(rue) { concluded = it }

        assertEquals(rue.place, concluded)
        assertTrue(places.resolutions.isEmpty())
    }

    /**
     * « commerce » : Google annonce l'arrêt de tram, que la liste montre déjà depuis le
     * catalogue. Les deux rangs mèneraient au même endroit, et un seul ouvre les passages.
     */
    @Test
    fun `le jumeau d un arret montre ne revient pas en adresse`() = searchTest { dispatcher ->
        val places = FakePlaces(results = listOf(commerceArret, placeDuCommerce))
        val viewModel = searchModel(dispatcher, places)
        advanceUntilIdle()

        type(viewModel, "commerce")

        val search = viewModel.state.value.search
        assertEquals(listOf("Commerce"), search.stops.map { it.label })
        assertEquals(listOf(placeDuCommerce), search.places)
    }

    /** Les adresses ont pu répondre avant le catalogue : le jumeau s'écarte à son arrivée. */
    @Test
    fun `le jumeau s ecarte aussi quand le catalogue arrive apres les adresses`() = searchTest { dispatcher ->
        val stops = LateStops(listOf(commerce))
        val places = FakePlaces(results = listOf(commerceArret, placeDuCommerce))
        val viewModel = searchModel(dispatcher, places, stops = stops)

        type(viewModel, "commerce")
        assertEquals(listOf(commerceArret, placeDuCommerce), viewModel.state.value.search.places)

        stops.ready.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(placeDuCommerce), viewModel.state.value.search.places)
    }

    /** Le dépôt de `:app`, sans disque — la règle, elle, est déjà testée à part. */
    private class MemorySearchHistory : SearchHistoryStore {
        private var places = emptyList<Place>()
        override fun read(): List<Place> = places
        override fun remember(place: Place): List<Place> {
            places = rememberPlace(place, places)
            return places
        }
        override fun clear() {
            places = emptyList()
        }
    }

    private class FakeStops(private val catalog: List<TransitStop>) : StopRepository {

        /** La grille théorique ne sert à aucun de ces tests : on ne sait rien de cette desserte. */

        override suspend fun daySchedule(

            atStopNamed: String,

            alsoNamed: List<String>,

            line: String,

            direction: String,

            on: java.time.LocalDate,

        ) = io.aule.android.core.model.StopDaySchedule.unknown(line, direction, on)

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

    /**
     * Le géocodeur en deux temps, sans réseau.
     *
     * Il retient **ce qu'on lui demande** — les jetons de chaque frappe, chaque résolution —
     * parce que c'est là que se jouent les défauts de ce contrat : `686d1d3` rendait des lieux
     * justes en situant cinq adresses par frappe, et rien dans l'état de l'écran ne le montrait.
     */
    private class FakePlaces(
        private val results: List<PlaceSuggestion> = emptyList(),
        private val fail: Boolean = false,
        /** Ce que `?placeId=` rend pour chaque clé : l'adresse postale, comme le BFF la donne. */
        private val addresses: Map<String, String> = emptyMap(),
        private val resolveFailure: Throwable? = null,
        /** Le temps d'un aller-retour de résolution — ce qui laisse à une frappe le temps de l'abandonner. */
        private val resolveDelayMs: Long = 0,
    ) : PlaceSearchRepository {
        var calls = 0
        val sessions = mutableListOf<PlaceSearchSession?>()
        val resolutions = mutableListOf<Pair<PlaceSuggestion.Prediction, PlaceSearchSession?>>()

        override suspend fun search(query: String, session: PlaceSearchSession?): List<PlaceSuggestion> {
            calls++
            sessions += session
            if (fail) error("502")
            return results
        }

        override suspend fun resolve(prediction: PlaceSuggestion.Prediction, session: PlaceSearchSession?): Place {
            resolutions += prediction to session
            delay(resolveDelayMs)
            resolveFailure?.let { throw it }
            return prediction.locatedAt(Coordinate.NANTES, addresses[prediction.placeId])
        }
    }

    /** Un catalogue qui ne répond qu'au signal : c'est ainsi qu'il arrive après les adresses. */
    private class LateStops(private val catalog: List<TransitStop>) : StopRepository {
        val ready = CompletableDeferred<Unit>()

        override suspend fun allStops(): List<TransitStop> {
            ready.await()
            return catalog
        }

        override suspend fun daySchedule(
            atStopNamed: String,
            alsoNamed: List<String>,
            line: String,
            direction: String,
            on: java.time.LocalDate,
        ) = io.aule.android.core.model.StopDaySchedule.unknown(line, direction, on)

        override suspend fun departures(atStopNamed: String) = StopDepartures(
            stopName = atStopNamed,
            outcome = DeparturesOutcome.NOTHING_ANNOUNCED,
            fetchedAt = Instant.EPOCH,
        )

        override suspend fun servingLines(atStopNamed: String) = emptyList<io.aule.android.core.model.ServingLine>()
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
