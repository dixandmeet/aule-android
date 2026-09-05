package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.LineStopMarker
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.DriverServiceRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * La desserte d'une ligne, par sens.
 *
 * Deux choses comptent ici. La première : **rien de la ligne précédente ne
 * survit** — des arrêts justes attribués à la mauvaise ligne sont pires qu'un
 * volet en attente. La seconde : une ligne que le référentiel ne connaît pas
 * n'est pas une panne, c'est le cas des vingt-neuf cars interurbains.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LineStopsModelTest {

    private val session = AuthSession(
        user = AuthUser("user-1", "agent@aule.fr"),
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresAtEpochSeconds = 9_999_999_999L,
    )

    private fun model(
        repository: DriverServiceRepository?,
        scope: TestScope,
        dispatcher: CoroutineDispatcher,
        signedIn: Boolean = true,
    ) = LineStopsModel(
        repository = repository,
        session = { if (signedIn) session else null },
        dispatchers = TestDispatchers(dispatcher),
        scope = scope,
        logger = NoopLogger,
    )

    @Test
    fun `les deux sens partent ensemble et le premier est retenu`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService()
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("c6")
        advanceUntilIdle()

        val state = subject.state.value
        assertEquals("C6", state.line)
        assertFalse(state.isLoading)
        assertNull(state.failure)
        assertEquals(listOf("Pirmil", "Hôtel Dieu"), state.dessertes.map { it.terminus })
        assertEquals(0, state.selected?.directionId)
        assertTrue(state.hasChoice)
        // Le référentiel n'est lu qu'une fois, et chaque sens une fois.
        assertEquals(1, repository.lineCalls)
        assertEquals(2, repository.journeyCalls)
    }

    /**
     * Le menu ne propose que ce qui **finit ailleurs**.
     *
     * Trois parcours sortent d'un même sens, et deux n'ont rien à y faire : la
     * variante relie les mêmes bouts que la référence — le menu écrirait deux
     * fois la même ligne —, et la course partielle s'arrête en chemin, donc
     * entre deux arrêts que la référence dessert. Reste la branche, dont un bout
     * est hors de la référence.
     *
     * Les trois cas viennent du réseau : la C1 pour la variante (crochet par
     * Procé), le tram 2 pour la course partielle (Hôtel Dieu, Espace Diderot),
     * la ligne 1 pour la branche (Beaujoire ou Babinière).
     */
    @Test
    fun `le menu des branches ecarte variantes et courses partielles`() {
        fun desserte(id: String, label: String, vararg stops: String) = LineDesserte(
            id = id,
            directionId = 0,
            terminus = "Terminus annoncé",
            stops = stops.map { LineJourneyStop(id = it, name = it) },
            label = label,
        )

        val state = LineStopsUiState(
            dessertes = listOf(
                desserte("ref", "A → D", "A", "B", "C", "D"),
                desserte("variante", "A → D", "A", "B", "Crochet", "C", "D"),
                desserte("partielle", "A → C", "A", "B", "C"),
                desserte("branche", "A → Z", "A", "B", "Antenne", "Z"),
            ),
        )

        assertTrue(state.hasBranches)
        assertEquals(listOf("ref", "branche"), state.branches.map { it.id })
        // La référence reste ce qui s'affiche sans qu'on demande rien.
        assertEquals("ref", state.selected?.id)
    }

    /**
     * La C3 telle que le référentiel la publie, relevée sur l'appareil de
     * référence le 05/09/2026 — journal à l'appui :
     *
     * ```
     * C3-0=36 (Bd de Doulon → Armor) | C3-1=24 (Hôtel Dieu → Armor)
     * C3-2=9 (Bd de Doulon → Berlin) | C3-3=34 (Bd de Doulon → Armor)
     * C3-4=35 (Prairie de Mauves → Armor)          — retenu C3-3 (34 arrêts)
     * ```
     *
     * ⚠️ **La C3 n'a aucune branche**, et le menu en proposait deux : « Hôtel
     * Dieu → Armor » et « Prairie de Mauves → Armor ». Ce sont des courses qui
     * démarrent en chemin — la première au centre-ville, la seconde au deuxième
     * arrêt. En choisir une peignait sur la carte une desserte qui n'est pas la
     * ligne. Le filtre d'alors — « au moins un arrêt que la référence n'ait
     * pas » — les laissait passer : il suffit d'un crochet d'un arrêt, ou d'une
     * sortie de dépôt, pour qu'une troncature en profite.
     */
    @Test
    fun `la C3 ne propose aucune branche`() {
        fun desserte(id: String, vararg stops: String) = LineDesserte(
            id = id,
            directionId = 0,
            terminus = "Armor",
            stops = stops.map { LineJourneyStop(id = it, name = it) },
            label = "${stops.first()} → ${stops.last()}",
        )

        // La desserte relevée à l'écran, réduite à ce que le filtre regarde.
        val reference = arrayOf(
            "Bd de Doulon", "Prairie de Mauves", "Berlin",
            "Hôtel Dieu", "Commerce", "Zénith", "Armor",
        )
        val state = LineStopsUiState(
            dessertes = listOf(
                desserte("C3-3", *reference),
                // 9 arrêts : elle s'arrête à Berlin, en chemin.
                desserte("C3-2", "Bd de Doulon", "Prairie de Mauves", "Berlin"),
                // 24 arrêts : elle démarre au centre-ville.
                desserte("C3-1", "Hôtel Dieu", "Commerce", "Zénith", "Armor"),
                // 35 arrêts : elle démarre au deuxième et ajoute un crochet.
                desserte(
                    "C3-4",
                    "Prairie de Mauves", "Berlin", "Hôtel Dieu", "Crochet",
                    "Commerce", "Zénith", "Armor",
                ),
                // 36 arrêts : mêmes bouts que la référence, un renfort en plus.
                desserte(
                    "C3-0",
                    "Bd de Doulon", "Prairie de Mauves", "Renfort", "Berlin",
                    "Hôtel Dieu", "Commerce", "Zénith", "Armor",
                ),
            ),
        )

        assertFalse(state.hasBranches)
        assertEquals(listOf("C3-3"), state.branches.map { it.id })
    }

    /**
     * L'autre bout de la même règle : la ligne 1 se scinde pour de bon, et son
     * menu doit rester. « Berlin » n'est pas sur la desserte de référence.
     */
    @Test
    fun `une vraie branche reste proposee`() {
        fun desserte(id: String, vararg stops: String) = LineDesserte(
            id = id,
            directionId = 0,
            terminus = "Beaujoire / Babinière",
            stops = stops.map { LineJourneyStop(id = it, name = it) },
            label = "${stops.first()} → ${stops.last()}",
        )

        val state = LineStopsUiState(
            dessertes = listOf(
                desserte("1-0", "Beaujoire", "Halvèque", "Commerce"),
                desserte("1-2", "Babinière", "Halvèque", "Commerce"),
            ),
        )

        assertTrue(state.hasBranches)
        assertEquals(listOf("1-0", "1-2"), state.branches.map { it.id })
    }

    /**
     * Les noms se comparent normalisés : le référentiel écrit « Hôtel Dieu » et
     * « HOTEL-DIEU » pour le même lieu, et un bout non reconnu ferait
     * réapparaître le faux choix qu'on vient d'écarter.
     */
    @Test
    fun `un bout ecrit autrement reste le meme lieu`() {
        fun desserte(id: String, vararg stops: String) = LineDesserte(
            id = id,
            directionId = 0,
            terminus = "Armor",
            stops = stops.map { LineJourneyStop(id = it, name = it) },
            label = "${stops.first()} → ${stops.last()}",
        )

        val state = LineStopsUiState(
            dessertes = listOf(
                desserte("ref", "Bd de Doulon", "Hôtel Dieu", "Armor"),
                desserte("partielle", "HOTEL-DIEU", "Armor"),
            ),
        )

        assertFalse(state.hasBranches)
    }

    /**
     * La garantie que la correction du 05/09/2026 a rétablie, et que rien ne doit
     * reperdre : **les arrêts peints sont exactement ceux de la liste**. Les deux
     * lisent la même construction, et un lieu desservi deux fois n'y compte
     * qu'une — deux pastilles superposées, dont la seconde masque la première.
     */
    @Test
    fun `la carte et la liste lisent les memes marqueurs`() {
        val state = LineStopsUiState(
            dessertes = listOf(
                LineDesserte(
                    id = "boucle",
                    directionId = 0,
                    terminus = "Hermeland",
                    stops = listOf(
                        LineJourneyStop("a", "Hermeland", Coordinate(47.23, -1.61)),
                        LineJourneyStop("b", "Commerce", Coordinate(47.21, -1.55)),
                        LineJourneyStop("c", "Hermeland", Coordinate(47.23, -1.61)),
                    ),
                    label = "Hermeland → Hermeland",
                ),
            ),
        )

        assertEquals(listOf("Hermeland", "Commerce"), state.markers.map { it.name })
        assertEquals(LineStopMarker.Role.BOTH, state.markers.first().role)
    }

    @Test
    fun `changer de ligne efface la desserte precedente immediatement`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = model(FakeService(), TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        assertTrue(subject.state.value.dessertes.isNotEmpty())

        subject.open("1")

        // ⚠️ Sans cet effacement, la desserte du C6 resterait affichée sous le
        // badge de la 1 pendant toute la requête.
        assertEquals("1", subject.state.value.line)
        assertTrue(subject.state.value.dessertes.isEmpty())
        assertTrue(subject.state.value.isLoading)
    }

    @Test
    fun `redemander la meme ligne ne recharge pas`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService()
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        subject.open("C6")
        advanceUntilIdle()

        // C'est ce qui arrive à chaque recomposition de la vue : recharger ferait
        // clignoter la fiche.
        assertEquals(2, repository.journeyCalls)
    }

    @Test
    fun `le referentiel des services n est lu qu une fois par processus`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService()
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        subject.open("1")
        advanceUntilIdle()

        // Il change à la fréquence d'un dépôt GTFS : le redemander à chaque fiche
        // paierait un catalogue entier pour un identifiant.
        assertEquals(1, repository.lineCalls)
    }

    @Test
    fun `une ligne absente du referentiel n est pas une panne`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = model(FakeService(), TestScope(testScheduler), dispatcher)

        subject.open("E311")
        advanceUntilIdle()

        // C'est le cas des vingt-neuf cars Aléop : l'app ne suit ni leur flotte
        // ni leurs horaires, seuls leurs tracés sont dans les tuiles. Proposer
        // « Réessayer » promettrait qu'insister peut changer la réponse.
        assertEquals(LineStopsFailure.UNKNOWN_LINE, subject.state.value.failure)
        assertTrue(subject.state.value.dessertes.isEmpty())
    }

    @Test
    fun `sans session on le dit plutot que d afficher un volet vide`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = model(FakeService(), TestScope(testScheduler), dispatcher, signedIn = false)

        subject.open("C6")
        advanceUntilIdle()

        assertEquals(LineStopsFailure.NOT_SIGNED_IN, subject.state.value.failure)
        assertFalse(subject.state.value.isLoading)
    }

    @Test
    fun `une panne reseau se dit et se reessaie`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService(failLines = true)
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        assertEquals(LineStopsFailure.NETWORK, subject.state.value.failure)

        repository.failLines = false
        subject.retry()
        advanceUntilIdle()

        // Repart tout de suite, sans attendre qu'on referme et rouvre la fiche.
        assertNull(subject.state.value.failure)
        assertEquals(2, subject.state.value.dessertes.size)
    }

    @Test
    fun `un sens en echec ne fait pas tomber l autre`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService(failDirection = 1)
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()

        // Une fiche à un sens vaut mieux qu'une fiche vide.
        assertNull(subject.state.value.failure)
        assertEquals(listOf("Pirmil"), subject.state.value.dessertes.map { it.terminus })
        assertFalse(subject.state.value.hasChoice)
    }

    @Test
    fun `choisir un sens change ce qu on lit sans rien redemander`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeService()
        val subject = model(repository, TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        subject.selectDirection(1)

        assertEquals("Hôtel Dieu", subject.state.value.selected?.terminus)
        assertEquals(2, repository.journeyCalls)
    }

    @Test
    fun `fermer la fiche vide l etat`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val subject = model(FakeService(), TestScope(testScheduler), dispatcher)

        subject.open("C6")
        advanceUntilIdle()
        subject.close()

        assertNull(subject.state.value.line)
        assertTrue(subject.state.value.dessertes.isEmpty())
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }

    private class FakeService(
        var failLines: Boolean = false,
        private val failDirection: Int? = null,
    ) : DriverServiceRepository {
        var lineCalls = 0
        var journeyCalls = 0

        override suspend fun fetchLines(session: AuthSession): List<ServiceLine> {
            lineCalls++
            if (failLines) error("502")
            return listOf(
                ServiceLine(
                    id = "route-c6",
                    label = "C6",
                    description = "Hôtel Dieu - Pirmil",
                    mode = TransportMode.BUS,
                    directions = listOf(
                        ServiceDirection(key = "0", terminus = "Pirmil"),
                        ServiceDirection(key = "1", terminus = "Hôtel Dieu"),
                    ),
                ),
                ServiceLine(
                    id = "route-1",
                    label = "1",
                    description = "Beaujoire - François Mitterrand",
                    mode = TransportMode.TRAM,
                    directions = listOf(ServiceDirection(key = "0", terminus = "Beaujoire")),
                ),
            )
        }

        override suspend fun fetchJourney(
            session: AuthSession,
            lineId: String,
            directionId: Int,
        expectedTerminus: String,
        ): LineJourney {
            journeyCalls++
            if (directionId == failDirection) error("502")
            return LineJourney(
                tripId = "$lineId-$directionId",
                stops = listOf(
                    LineJourneyStop(
                        id = "s1",
                        name = "Commerce",
                        coordinate = Coordinate(latitude = 47.21, longitude = -1.56),
                    ),
                    LineJourneyStop(id = "s2", name = "Pirmil"),
                ),
            )
        }

        override suspend fun nearestActiveTrip(
            session: AuthSession,
            lineId: String,
            directionId: Int,
            destinationHint: String?,
            near: Coordinate,
            at: java.time.Instant,
        ) = error("non sollicité")

        override suspend fun fetchActiveService(session: AuthSession) = error("non sollicité")

        override suspend fun startService(
            session: AuthSession,
            request: io.aule.android.core.model.ServiceStartRequest,
        ) = error("non sollicité")

        override suspend fun endService(session: AuthSession, serviceId: String) =
            error("non sollicité")

        override suspend fun publishPosition(
            session: AuthSession,
            request: io.aule.android.core.model.PositionPublishRequest,
        ) = error("non sollicité")
    }
}
