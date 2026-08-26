package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.decodeStopCatalog
import io.aule.android.core.model.encodeCatalog
import io.aule.android.core.model.repository.CacheStore
import io.aule.android.core.model.repository.StopRepository
import io.aule.android.data.caching.CachedStopRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Le catalogue servi depuis le disque.
 *
 * Le test qui compte est `un catalogue vide ne s ecrit jamais` : au lancement
 * suivant, un fichier vide passerait pour un cache valide et l'application
 * n'aurait plus un seul arrêt **sans qu'aucune erreur ne le dise**. C'est le
 * genre de défaut qui survit aux relances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CachedStopRepositoryTest {

    private val commerce = TransitStop(
        id = "COMM",
        name = "Commerce",
        code = "OTAG2",
        coordinate = Coordinate(latitude = 47.2136, longitude = -1.5601),
        mode = TransportMode.TRAM,
        stationName = "Commerce",
        isWheelchairAccessible = true,
    )
    private val beaujoire = TransitStop(
        id = "BEAU",
        name = "Beaujoire",
        coordinate = Coordinate(latitude = 47.2560, longitude = -1.5250),
        mode = TransportMode.BUS,
    )

    @Test
    fun `le premier lancement descend du reseau et ecrit le disque`() = runTest {
        val cache = MemoryCache()
        val upstream = FakeStops(listOf(commerce, beaujoire))
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        val stops = repository.allStops()
        advanceUntilIdle()

        assertEquals(2, stops.size)
        assertEquals(1, upstream.calls)
        assertEquals(listOf(commerce, beaujoire), decodeStopCatalog(cache.single()))
    }

    @Test
    fun `le lancement suivant sert le disque sans attendre le reseau`() = runTest {
        val cache = MemoryCache().apply {
            write(CATALOG, listOf(commerce, beaujoire).encodeCatalog())
        }
        val upstream = FakeStops(listOf(commerce))
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        val stops = repository.allStops()

        // Le disque a répondu, et le réseau n'a pas encore été touché : c'est
        // toute la promesse d'un lancement dans un tunnel.
        assertEquals(listOf(commerce, beaujoire), stops)
        assertEquals(0, upstream.calls)
    }

    @Test
    fun `la revalidation reecrit le fichier pour le lancement d apres`() = runTest {
        val cache = MemoryCache().apply { write(CATALOG, listOf(commerce).encodeCatalog()) }
        val upstream = FakeStops(listOf(commerce, beaujoire))
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        val served = repository.allStops()
        advanceUntilIdle()

        // Servi ancien, réécrit neuf : le catalogue est au pire un lancement en
        // retard, pour une donnée qui change deux fois par an.
        assertEquals(listOf(commerce), served)
        assertEquals(1, upstream.calls)
        assertEquals(listOf(commerce, beaujoire), decodeStopCatalog(cache.single()))
    }

    @Test
    fun `une revalidation en echec ne remonte pas a l ecran`() = runTest {
        val cache = MemoryCache().apply { write(CATALOG, listOf(commerce).encodeCatalog()) }
        val upstream = FakeStops(emptyList(), failWith = "502")
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        val served = repository.allStops()
        advanceUntilIdle()

        // L'utilisateur a déjà ses arrêts : l'échec de fond n'est pas un incident
        // pour lui, et le fichier garde ce qu'il avait.
        assertEquals(listOf(commerce), served)
        assertEquals(listOf(commerce), decodeStopCatalog(cache.single()))
    }

    @Test
    fun `un catalogue vide ne s ecrit jamais`() = runTest {
        val cache = MemoryCache()
        val upstream = FakeStops(emptyList())
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        val stops = repository.allStops()
        advanceUntilIdle()

        assertTrue(stops.isEmpty())
        // Écrit, il passerait au lancement suivant pour un cache valide, et la
        // carte serait vide sans qu'aucune erreur ne le dise.
        assertNull(cache.read(CATALOG))
    }

    @Test
    fun `un cache vide ou abime retombe sur le reseau`() = runTest {
        val cache = MemoryCache().apply { write(CATALOG, "[]") }
        val upstream = FakeStops(listOf(commerce))
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        assertEquals(listOf(commerce), repository.allStops())
        assertEquals(1, upstream.calls)

        val broken = MemoryCache().apply { write(CATALOG, "ceci n'est pas du JSON") }
        val second = FakeStops(listOf(commerce))
        assertEquals(
            listOf(commerce),
            CachedStopRepository(second, broken, TestScope(testScheduler), NoopLogger).allStops(),
        )
    }

    @Test
    fun `une panne sans cache leve, comme le contrat le promet`() = runTest {
        val repository = CachedStopRepository(
            FakeStops(emptyList(), failWith = "502"),
            MemoryCache(),
            TestScope(testScheduler),
            NoopLogger,
        )

        // Le cache ne masque pas une panne qu'il ne peut pas couvrir : un écran
        // vide sans explication est exactement ce que ce dépôt évite.
        assertThrows<IllegalStateException> { repository.allStops() }
    }

    @Test
    fun `les passages et les dessertes ne passent pas par le disque`() = runTest {
        val cache = MemoryCache()
        val upstream = FakeStops(listOf(commerce))
        val repository = CachedStopRepository(upstream, cache, TestScope(testScheduler), NoopLogger)

        repository.departures("Commerce")
        repository.servingLines("Commerce")

        // Ils changent à la minute : les garder ferait afficher un passage qui
        // est déjà parti.
        assertTrue(cache.written.isEmpty())
        assertEquals(1, upstream.departureCalls)
        assertEquals(1, upstream.servingCalls)
    }

    @Test
    fun `un aller-retour sur le disque garde tous les champs`() {
        val restored = decodeStopCatalog(listOf(commerce, beaujoire).encodeCatalog())

        assertEquals(listOf(commerce, beaujoire), restored)
        // Le quai, l'accessibilité et le code exploitant survivent : ce sont eux
        // qui distinguent un lieu d'un quai à l'écran.
        assertEquals("OTAG2", restored[0].code)
        assertTrue(restored[0].isWheelchairAccessible)
        assertFalse(restored[1].isWheelchairAccessible)
        assertNull(restored[1].code)
    }

    @Test
    fun `une entree abimee est sautee et les autres restent`() {
        val raw = """
            [
              {"i":"COMM","n":"Commerce","y":47.2136,"x":-1.5601,"m":"TRAM"},
              {"i":"SANS-MODE","n":"X","y":47.0,"x":-1.0},
              {"n":"Sans identifiant","y":47.0,"x":-1.0,"m":"BUS"},
              {"i":"BEAU","n":"Beaujoire","y":47.2560,"x":-1.5250,"m":"BUS"}
            ]
        """.trimIndent()

        assertEquals(listOf("COMM", "BEAU"), decodeStopCatalog(raw).map { it.id })
    }

    private class MemoryCache : CacheStore {
        val written = mutableMapOf<String, String>()
        override fun read(name: String): String? = written[name]
        override fun write(name: String, content: String) {
            written[name] = content
        }
        override fun clear(name: String) {
            written.remove(name)
        }
        fun single(): String? = written[CATALOG]
    }

    private class FakeStops(
        private val catalog: List<TransitStop>,
        private val failWith: String? = null,
    ) : StopRepository {
        var calls = 0
        var departureCalls = 0
        var servingCalls = 0

        override suspend fun allStops(): List<TransitStop> {
            calls++
            failWith?.let { error(it) }
            return catalog
        }

        override suspend fun departures(atStopNamed: String): StopDepartures {
            departureCalls++
            return StopDepartures(
                stopName = atStopNamed,
                outcome = DeparturesOutcome.NOTHING_ANNOUNCED,
                fetchedAt = Instant.EPOCH,
            )
        }

        override suspend fun servingLines(atStopNamed: String): List<ServingLine> {
            servingCalls++
            return emptyList()
        }
    }

    private companion object {
        const val CATALOG = "stops-catalog-v1.json"
    }
}
