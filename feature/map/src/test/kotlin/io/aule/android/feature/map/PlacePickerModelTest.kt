package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.Place
import io.aule.android.core.model.PlaceSearchSession
import io.aule.android.core.model.PlaceSuggestion
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.PlaceSearchRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * La recherche de l'éditeur de favori : un favori sans point ne mène nulle part, et le point
 * ne se demande que pour l'adresse qu'on touche.
 *
 * Les mêmes règles que la recherche de la carte, éprouvées à part : c'est un autre modèle, qui
 * vit dans un autre volet, et le volet qui se referme est ici la seule façon d'abandonner.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerModelTest {

    /** Relevées sur `www.aule.fr` le 24/09/2026 (`?q=gare de nantes`, `?q=commerce`). */
    private val gare = PlaceSuggestion.Prediction(
        placeId = "ChIJkXDHRLnuBUgRlpH_NZEcZ1s",
        label = "Gare de Nantes, Boulevard de Stalingrad, Nantes, France",
        details = "Boulevard de Stalingrad, Nantes, France",
        isTransitStop = true,
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

    private val commerce = TransitStop(
        id = "COMM",
        name = "Commerce",
        coordinate = Coordinate.NANTES,
        mode = TransportMode.TRAM,
        stationName = "Commerce",
    )

    private fun pickerTest(block: suspend TestScope.(TestDispatcher) -> Unit) = runTest {
        block(StandardTestDispatcher(testScheduler))
    }

    private fun picker(dispatcher: TestDispatcher, places: FakePlaces) =
        PlacePickerModel(places, TestDispatchers(dispatcher), NoopLogger)

    private fun TestScope.type(picker: PlacePickerModel, query: String) {
        picker.search(listOf(commerce), query)
        advanceTimeBy(400)
        advanceUntilIdle()
    }

    @Test
    fun `l editeur ne situe que l adresse touchee`() = pickerTest { dispatcher ->
        val places = FakePlaces(
            results = listOf(gare),
            addresses = mapOf(gare.placeId to "27 Bd de Stalingrad, 44041 Nantes, France"),
        )
        val picker = picker(dispatcher, places)

        type(picker, "gare de")
        type(picker, "gare de nantes")
        assertTrue(places.resolutions.isEmpty(), "rien ne se situe tant qu'on n'a rien touché")

        var chosen: Place? = null
        picker.choose(gare) { chosen = it }
        advanceUntilIdle()

        val session = places.sessions.first()
        assertNotNull(session)
        assertEquals(listOf<Pair<PlaceSuggestion.Prediction, PlaceSearchSession?>>(gare to session), places.resolutions)
        // Le favori prendra son nom de là : « Gare de Nantes », et non « Nantes ».
        assertEquals("Gare de Nantes, 27 Bd de Stalingrad, 44041 Nantes, France", chosen?.label)
    }

    @Test
    fun `fermer l editeur abandonne la resolution en vol`() = pickerTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), resolveDelayMs = 1_000)
        val picker = picker(dispatcher, places)

        type(picker, "gare de nantes")
        var chosen: Place? = null
        picker.choose(gare) { chosen = it }
        advanceTimeBy(100)
        picker.close()
        advanceUntilIdle()

        assertNull(chosen, "un volet refermé n'enregistre rien")
    }

    @Test
    fun `une frappe abandonne la resolution en vol`() = pickerTest { dispatcher ->
        val places = FakePlaces(results = listOf(gare), resolveDelayMs = 1_000)
        val picker = picker(dispatcher, places)

        type(picker, "gare de nantes")
        var chosen: Place? = null
        picker.choose(gare) { chosen = it }
        advanceTimeBy(100)
        type(picker, "gare de nantes sud")

        assertNull(chosen)
        assertNull(picker.locating)
        assertNull(picker.unlocated)
    }

    @Test
    fun `une resolution manquee se dit sans effacer la liste`() = pickerTest { dispatcher ->
        val places = FakePlaces(
            results = listOf(placeDuCommerce, gare),
            resolveFailure = IllegalStateException("502"),
        )
        val picker = picker(dispatcher, places)

        type(picker, "gare de nantes")
        var chosen: Place? = null
        picker.choose(gare) { chosen = it }
        advanceUntilIdle()

        assertNull(chosen)
        assertEquals(gare, picker.unlocated)
        assertNull(picker.locating)
        assertEquals(listOf(placeDuCommerce, gare), picker.places)
    }

    @Test
    fun `le jumeau d un arret montre ne revient pas en adresse`() = pickerTest { dispatcher ->
        val picker = picker(dispatcher, FakePlaces(results = listOf(commerceArret, placeDuCommerce)))

        type(picker, "commerce")

        assertEquals(listOf("Commerce"), picker.stops.map { it.label })
        assertEquals(listOf(placeDuCommerce), picker.places)
    }

    private class FakePlaces(
        private val results: List<PlaceSuggestion> = emptyList(),
        private val addresses: Map<String, String> = emptyMap(),
        private val resolveFailure: Throwable? = null,
        private val resolveDelayMs: Long = 0,
    ) : PlaceSearchRepository {
        val sessions = mutableListOf<PlaceSearchSession?>()
        val resolutions = mutableListOf<Pair<PlaceSuggestion.Prediction, PlaceSearchSession?>>()

        override suspend fun search(query: String, session: PlaceSearchSession?): List<PlaceSuggestion> {
            sessions += session
            return results
        }

        override suspend fun resolve(prediction: PlaceSuggestion.Prediction, session: PlaceSearchSession?): Place {
            resolutions += prediction to session
            delay(resolveDelayMs)
            resolveFailure?.let { throw it }
            return prediction.locatedAt(Coordinate.NANTES, addresses[prediction.placeId])
        }
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }
}
