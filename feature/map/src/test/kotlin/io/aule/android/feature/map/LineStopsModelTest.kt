package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.LineJourneyStop
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
