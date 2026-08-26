package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.DepartureWatch
import io.aule.android.core.model.Timetable
import io.aule.android.core.model.TimetableException
import io.aule.android.core.model.TimetableFailureKind
import io.aule.android.core.model.repository.TimetableRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * La fiche horaire, du côté de l'écran.
 *
 * Ce qui se teste ici n'est pas l'exactitude des heures — c'est l'affaire du
 * repository — mais **quand** on redemande une grille, et ce qui reste à
 * l'écran pendant ce temps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimetableModelTest {

    private val monday = LocalDate.of(2026, 8, 17)

    private val line = DepartureWatch(
        stopName = "Ranzay",
        line = "80",
        destination = "Fac de Droit",
    )

    @Test
    fun `la ligne ouverte charge la journee en cours`() = runTest {
        val source = FakeTimetables()
        val model = model(source)

        model.open(line)
        runCurrent()

        assertEquals(monday, model.state.value.date)
        assertEquals(1, source.calls)
        assertTrue(model.state.value.isReady)
    }

    /** Une grille ne se périme pas : la redemander ne changerait pas une minute. */
    @Test
    fun `rouvrir la meme ligne ne recharge rien`() = runTest {
        val source = FakeTimetables()
        val model = model(source)

        model.open(line)
        runCurrent()
        model.open(line)
        runCurrent()

        assertEquals(1, source.calls)
    }

    @Test
    fun `changer de date recharge, et vide la grille precedente`() = runTest {
        val source = FakeTimetables()
        val model = model(source)

        model.open(line)
        runCurrent()
        model.setDate(monday.plusDays(1))

        // Avant la réponse : plus aucune heure affichée. Garder celles de la
        // veille sous une date exacte ferait lire des horaires faux.
        assertNull(model.state.value.timetable)
        assertTrue(model.state.value.isLoading)

        runCurrent()
        assertEquals(monday.plusDays(1), model.state.value.timetable?.date)
        assertEquals(2, source.calls)
    }

    /** Sans session, les tables du catalogue restent fermées — et on le dit. */
    @Test
    fun `sans session, rien n est demande`() = runTest {
        val source = FakeTimetables()
        val model = model(source, session = { null })

        model.open(line)
        runCurrent()

        assertEquals(0, source.calls)
        assertEquals(TimetableFailureKind.NOT_SIGNED_IN, model.state.value.failure)
    }

    /** Un échec ne se retente pas tout seul : l'écran le dit, l'usager décide. */
    @Test
    fun `un echec attend qu on redemande`() = runTest {
        val source = FakeTimetables(failing = TimetableFailureKind.UNAVAILABLE)
        val model = model(source)

        model.open(line)
        runCurrent()
        assertEquals(TimetableFailureKind.UNAVAILABLE, model.state.value.failure)
        assertEquals(1, source.calls)

        source.failing = null
        model.retry()
        runCurrent()

        assertNull(model.state.value.failure)
        assertEquals(2, source.calls)
        assertTrue(model.state.value.isReady)
    }

    @Test
    fun `refermer le volet oublie la grille`() = runTest {
        val source = FakeTimetables()
        val model = model(source)

        model.open(line)
        runCurrent()
        model.close()

        assertNull(model.state.value.timetable)
        assertNull(model.state.value.line)
    }

    // ------------------------------------------------------------- fabriques

    private fun kotlinx.coroutines.test.TestScope.model(
        source: FakeTimetables,
        session: () -> AuthSession? = { SESSION },
    ): TimetableModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TimetableModel(
            repository = source,
            session = session,
            dispatchers = TestDispatchers(dispatcher),
            scope = backgroundScope,
            logger = NoopLogger,
            today = { monday },
        )
    }

    private class FakeTimetables(
        var failing: TimetableFailureKind? = null,
    ) : TimetableRepository {
        var calls = 0

        override suspend fun timetable(
            session: AuthSession,
            stopName: String,
            line: String,
            destination: String,
            date: LocalDate,
        ): Timetable {
            calls++
            failing?.let { throw TimetableException(it) }
            return Timetable(
                date = date,
                line = line,
                destination = destination,
                stopName = stopName,
                passages = listOf(Instant.ofEpochSecond(1_000)),
            )
        }
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val default = dispatcher
        override val io = dispatcher
        override val main = dispatcher
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )
    }
}
