package io.aule.android.feature.map

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.Place
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceIcon
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.SavedPlaceRepository
import io.aule.android.core.model.repository.SavedPlacesStore
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
 * Les adresses favorites de l'écran : ce que l'appareil décide, ce que le compte
 * rattrape.
 *
 * Le point vérifié ici n'est pas la règle de fusion — elle est testée sans
 * plateforme dans `:core:model` — mais **l'ordre des opérations** : lire avant
 * d'afficher, fusionner avant d'écrire, et ne rien défaire quand le réseau se
 * tait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedPlacesModelTest {

    private val t0: Instant = Instant.parse("2026-08-26T08:00:00Z")
    private val session = AuthSession(
        user = AuthUser(id = "u1", email = "agent@aule.fr"),
        accessToken = "jeton",
        refreshToken = "rafraichir",
        expiresAtEpochSeconds = Instant.parse("2026-08-26T12:00:00Z").epochSecond,
    )

    private fun place(
        id: String,
        name: String = "",
        slot: SavedPlaceSlot = SavedPlaceSlot.CUSTOM,
        label: String = name.ifEmpty { id },
        lat: Double = 47.21,
        updated: Instant = t0,
    ) = SavedPlace(
        id = id,
        name = name,
        slot = slot,
        icon = SavedPlaceIcon.forSlot(slot),
        label = label,
        coordinate = Coordinate(latitude = lat, longitude = -1.55),
        createdAt = t0,
        updatedAt = updated,
    )

    @Test
    fun `les favoris sont la avant le reseau`() = runTest {
        val store = MemoryStore(listOf(place("h", slot = SavedPlaceSlot.HOME, label = "Maison")))
        // Aucun dépôt distant : c'est le cas du premier lancement hors ligne.
        val model = model(store = store, repository = null)

        // Lus à la construction, sans coroutine et sans attendre : la recherche
        // montre ses raccourcis à l'instant où elle s'ouvre.
        assertEquals(listOf("h"), model.places.map { it.id })
        assertNotNull(model.at(SavedPlaceSlot.HOME))
    }

    @Test
    fun `enregistrer ecrit sur le disque tout de suite`() = runTest {
        val store = MemoryStore()
        val model = model(store = store, repository = null)

        model.save(
            SavedPlaceEdit(
                name = "  ",
                slot = SavedPlaceSlot.CUSTOM,
                icon = SavedPlaceIcon.SCHOOL,
                place = Place("5 rue de Bel Air, Nantes", Coordinate(47.21, -1.55), TransportMode.TRAM),
            ),
        )

        assertEquals(1, store.written.size)
        val saved = model.places.single()
        // Un nom laissé vide se propose à partir de l'adresse : un raccourci
        // sans intitulé serait une carte muette dans la rangée.
        assertEquals("5 rue de Bel Air", saved.name)
        assertEquals(SavedPlaceIcon.SCHOOL, saved.icon)
        // Le mode d'arrêt survit : c'est lui qui dira qu'on peut en demander les
        // passages.
        assertEquals(TransportMode.TRAM, saved.stopMode)
    }

    @Test
    fun `un emplacement nomme ne prend pas de nom saisi`() = runTest {
        val model = model(store = MemoryStore(), repository = null)

        model.save(
            SavedPlaceEdit(
                name = "Chez moi",
                slot = SavedPlaceSlot.HOME,
                icon = SavedPlaceIcon.HOME,
                place = Place("12 rue Paul Bellamy, Nantes", Coordinate(47.22, -1.55)),
            ),
        )

        // « Domicile » est une phrase : elle vit dans les ressources, et
        // l'enregistrer en base perdrait sa traduction (ADR-011).
        assertEquals("", model.places.single().name)
    }

    @Test
    fun `la fusion precede l ecriture`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = MemoryStore(listOf(place("a", name = "Crèche", updated = t0)))
            // L'autre appareil a renommé le même favori, plus tard.
            val repository = MemoryRepository(
                remote = listOf(place("a", name = "Halte-garderie", updated = t0.plusSeconds(600))),
            )
            val model = model(store, repository, TestScope(dispatcher))

            model.sync(force = true)
            advanceUntilIdle()

            // Pousser le local tel quel aurait écrasé ce que l'autre appareil a
            // enregistré pendant qu'on était hors ligne.
            assertEquals("Halte-garderie", model.places.single().name)
            assertEquals("Halte-garderie", repository.pushed.last().single().name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une synchronisation ratee ne defait rien`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = MemoryStore(listOf(place("a", name = "Crèche")))
            val repository = MemoryRepository(failing = true)
            val model = model(store, repository, TestScope(dispatcher))

            model.sync(force = true)
            advanceUntilIdle()

            // Les favoris locaux sont déjà à l'écran : il n'y a rien à annoncer,
            // et surtout rien à vider.
            assertEquals(listOf("a"), model.places.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une suppression part au serveur avant d etre oubliee`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val store = MemoryStore(listOf(place("a", name = "Crèche")))
            val repository = MemoryRepository()
            val model = model(store, repository, TestScope(dispatcher))

            model.remove("a")
            // Avant la synchronisation, la pierre tombale est encore sur le
            // disque : c'est elle qui empêchera la résurrection si l'appareil
            // s'éteint maintenant.
            assertTrue(store.written.last().single().isDeleted)
            assertTrue(model.places.isEmpty())

            advanceUntilIdle()

            // Elle a voyagé…
            assertTrue(repository.pushed.last().single().isDeleted)
            // …et n'est plus gardée localement une fois le serveur au courant.
            assertTrue(store.written.last().isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans session, rien ne part`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = MemoryRepository()
            val model = SavedPlacesModel(
                store = MemoryStore(),
                repository = repository,
                session = { null },
                dispatchers = TestDispatchers(dispatcher),
                scope = TestScope(dispatcher),
                logger = NoopLogger,
                now = { t0 },
                newId = { "neuf" },
            )

            model.save(
                SavedPlaceEdit(
                    name = "Crèche",
                    slot = SavedPlaceSlot.CUSTOM,
                    icon = SavedPlaceIcon.PIN,
                    place = Place("Bel Air", Coordinate(47.21, -1.55)),
                ),
            )
            advanceUntilIdle()

            // Déconnecté, les favoris restent : ils n'ont pas attendu le compte
            // pour exister, et ils ne l'attendent pas pour être écrits.
            assertEquals(1, model.places.size)
            assertTrue(repository.pushed.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la synchronisation se debraye entre deux ouvertures`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = MemoryRepository()
            var clock = t0
            val model = SavedPlacesModel(
                store = MemoryStore(),
                repository = repository,
                session = { session },
                dispatchers = TestDispatchers(dispatcher),
                scope = TestScope(dispatcher),
                logger = NoopLogger,
                now = { clock },
                newId = { "neuf" },
            )

            model.sync()
            advanceUntilIdle()
            val first = repository.fetches
            model.sync()
            advanceUntilIdle()

            // La recherche s'ouvre et se referme des dizaines de fois par
            // service : une requête à chaque ouverture ferait payer un
            // aller-retour à un geste qui n'attend rien.
            assertEquals(first, repository.fetches)

            clock = t0.plusSeconds(600)
            model.sync()
            advanceUntilIdle()
            assertEquals(first + 1, repository.fetches)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans depot local, rien ne casse`() = runTest {
        val model = model(store = null, repository = null)

        model.save(
            SavedPlaceEdit(
                name = "Crèche",
                slot = SavedPlaceSlot.CUSTOM,
                icon = SavedPlaceIcon.PIN,
                place = Place("Bel Air", Coordinate(47.21, -1.55)),
            ),
        )

        // C'est ce que voient les tests d'écran qui ne parlent pas de favoris :
        // la liste vit en mémoire, et l'absence de dépôt n'est pas une panne.
        assertEquals(1, model.places.size)
        assertNull(model.at(SavedPlaceSlot.HOME))
    }

    @Test
    fun `changer de compte n emporte pas les favoris du precedent`() = runTest {
        val store = MemoryStore(listOf(place("a", name = "Crèche")))
        var current = session
        val model = SavedPlacesModel(
            store = store,
            repository = null,
            session = { current },
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            scope = TestScope(testScheduler),
            logger = NoopLogger,
            now = { t0 },
            newId = { "neuf" },
        )

        assertEquals(listOf("a"), model.places.map { it.id })

        // Un poste de conduite se partage : le téléphone passe au collègue.
        current = session.copy(user = session.user.copy(id = "u2", email = "autre@aule.fr"))
        // Ce que fait l'écran carte à son ouverture, et ce que fait toute
        // écriture : se rattacher au compte connecté.
        model.sync()

        // Le domicile du premier agent ne doit ni rester à l'écran, ni partir
        // sur le compte du second à la première synchronisation.
        assertNull(model.at(SavedPlaceSlot.HOME))
        assertTrue(model.places.isEmpty())
    }

    @Test
    fun `chaque compte retrouve les siens`() = runTest {
        val store = MemoryStore(listOf(place("a", name = "Crèche")))
        var current = session
        val model = SavedPlacesModel(
            store = store,
            repository = null,
            session = { current },
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            scope = TestScope(testScheduler),
            logger = NoopLogger,
            now = { t0 },
            newId = { "neuf" },
        )

        current = session.copy(user = session.user.copy(id = "u2", email = "autre@aule.fr"))
        model.save(
            SavedPlaceEdit(
                name = "Sport",
                slot = SavedPlaceSlot.CUSTOM,
                icon = SavedPlaceIcon.GYM,
                place = Place("Petit Port", Coordinate(47.24, -1.55)),
            ),
        )
        current = session
        model.sync()

        // Le premier agent retrouve les siens, intacts, en reprenant l'appareil.
        assertEquals(listOf("a"), model.places.map { it.id })
        assertEquals(listOf("neuf"), store.byOwner["u2"]?.map { it.id })
    }

    private fun model(
        store: SavedPlacesStore?,
        repository: SavedPlaceRepository?,
        scope: TestScope = TestScope(),
    ) = SavedPlacesModel(
        store = store,
        repository = repository,
        session = { session },
        dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler)),
        scope = scope,
        logger = NoopLogger,
        now = { t0 },
        newId = { "neuf" },
    )

    private class TestDispatchers(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) : io.aule.android.core.common.AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }

    private class MemoryStore(initial: List<SavedPlace> = emptyList()) : SavedPlacesStore {
        val byOwner = mutableMapOf<String, List<SavedPlace>>("u1" to initial)
        val written = mutableListOf<List<SavedPlace>>()

        override fun read(owner: String?): List<SavedPlace> = byOwner[owner ?: ANON].orEmpty()

        override fun write(owner: String?, places: List<SavedPlace>) {
            byOwner[owner ?: ANON] = places
            written += places
        }

        private companion object {
            const val ANON = "anonyme"
        }
    }

    private class MemoryRepository(
        var remote: List<SavedPlace> = emptyList(),
        private val failing: Boolean = false,
    ) : SavedPlaceRepository {
        var fetches = 0
        val pushed = mutableListOf<List<SavedPlace>>()

        override suspend fun fetch(session: AuthSession): List<SavedPlace> {
            fetches += 1
            if (failing) error("le serveur ne répond pas")
            return remote
        }

        override suspend fun push(session: AuthSession, places: List<SavedPlace>) {
            if (failing) error("le serveur ne répond pas")
            pushed += places
            remote = places
        }
    }
}
