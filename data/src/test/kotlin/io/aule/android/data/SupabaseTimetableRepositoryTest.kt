package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.TimetableException
import io.aule.android.core.model.TimetableFailureKind
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseTimetableRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * La fiche horaire, reconstruite depuis le catalogue.
 *
 * Le catalogue ne publie pas d'heures de passage : il publie des profils de
 * course et des départs, et l'heure est la somme des deux. Ce test vérifie
 * l'addition — c'est-à-dire la seule chose que personne ne verrait si elle
 * était fausse, puisqu'une grille décalée de deux minutes a l'air d'une grille.
 */
class SupabaseTimetableRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseTimetableRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseTimetableRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `l heure de passage est le depart plus le decalage de l arret`() = runTest {
        respondAll()

        val timetable = repository.timetable(
            session = SESSION,
            stopName = "Ranzay",
            line = "80",
            destination = "Fac de Droit",
            date = DATE,
        )

        // 06:00 + 7 min de trajet, puis 06:23 + 7 min : la grille suit les
        // départs, décalés du temps que met la course jusqu'à notre arrêt.
        assertEquals(
            listOf("06:07", "06:30"),
            timetable.passages.map { it.atZone(PARIS).toLocalTime().toString().take(5) },
        )
        assertEquals(DATE, timetable.date)
    }

    /** Le sens inverse porte le même numéro de ligne et n'a rien à faire là. */
    @Test
    fun `seul le profil de la bonne direction compte`() = runTest {
        respondAll()

        repository.timetable(
            session = SESSION,
            stopName = "Ranzay",
            line = "80",
            destination = "Fac de Droit",
            date = DATE,
        )

        val paths = (1..5).map { server.takeRequest().url }
        val stopsQuery = paths.first { it.encodedPath.endsWith("gtfs_trip_profile_stops") }
        // Le profil « Chassay » — l'autre sens — n'est pas interrogé.
        assertTrue(stopsQuery.query!!.contains("P-FAC"), stopsQuery.query!!)
        assertTrue(!stopsQuery.query!!.contains("P-CHASSAY"), stopsQuery.query!!)
    }

    /** Un jour sans service est une réponse, pas une panne. */
    @Test
    fun `une date sans service rend une grille vide`() = runTest {
        respond(ROUTES)
        respond(PROFILES)
        respond(PROFILE_STOPS)
        respond(STOPS)
        respond("[]") // gtfs_calendar
        respond("[]") // gtfs_calendar_dates

        val timetable = repository.timetable(
            session = SESSION,
            stopName = "Ranzay",
            line = "80",
            destination = "Fac de Droit",
            date = DATE,
        )

        assertTrue(timetable.isEmpty)
    }

    /** Une ligne absente du catalogue ne se résoudra pas en réessayant. */
    @Test
    fun `une ligne inconnue se distingue d une panne`() = runTest {
        respond("[]")

        val failure = assertThrows<TimetableException> {
            repository.timetable(
                session = SESSION,
                stopName = "Ranzay",
                line = "999",
                destination = "Nulle part",
                date = DATE,
            )
        }
        assertEquals(TimetableFailureKind.NOT_IN_CATALOG, failure.kind)
    }

    /** Une session refusée n'est pas une panne réseau : elle se règle ailleurs. */
    @Test
    fun `un refus de la base se dit comme tel`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("{}").build())

        val failure = assertThrows<TimetableException> {
            repository.timetable(
                session = SESSION,
                stopName = "Ranzay",
                line = "80",
                destination = "Fac de Droit",
                date = DATE,
            )
        }
        assertEquals(TimetableFailureKind.NOT_SIGNED_IN, failure.kind)
    }

    private fun respondAll() {
        respond(ROUTES)
        respond(PROFILES)
        respond(PROFILE_STOPS)
        respond(STOPS)
        respond(CALENDAR)
        respond("[]") // gtfs_calendar_dates
        respond(DEPARTURES)
    }

    private fun respond(body: String) {
        server.enqueue(MockResponse.Builder().code(200).body(body).build())
    }

    private companion object {
        val PARIS: ZoneId = ZoneId.of("Europe/Paris")

        /** Un lundi ordinaire. */
        val DATE: LocalDate = LocalDate.of(2026, 8, 17)

        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )

        const val ROUTES = """[{"route_id":"80","route_short_name":"80","route_type":3}]"""

        const val PROFILES = """[
            {"profile_id":"P-FAC","direction_id":0,"headsign":"Fac de Droit","route_id":"80"},
            {"profile_id":"P-CHASSAY","direction_id":1,"headsign":"Chassay","route_id":"80"}
        ]"""

        const val PROFILE_STOPS = """[
            {"profile_id":"P-FAC","stop_sequence":1,"stop_id":"S-BEL","offset_seconds":0},
            {"profile_id":"P-FAC","stop_sequence":2,"stop_id":"S-RANZ","offset_seconds":420},
            {"profile_id":"P-FAC","stop_sequence":3,"stop_id":"S-FAC","offset_seconds":900}
        ]"""

        const val STOPS = """[
            {"stop_id":"S-BEL","stop_name":"Bellevue"},
            {"stop_id":"S-RANZ","stop_name":"Ranzay"},
            {"stop_id":"S-FAC","stop_name":"Fac de Droit"}
        ]"""

        const val CALENDAR =
            """[{"service_id":"SEM","runs_on":[true,true,true,true,true,false,false]}]"""

        const val DEPARTURES = """[
            {"departure_id":"D-1","profile_id":"P-FAC","start_seconds":21600},
            {"departure_id":"D-2","profile_id":"P-FAC","start_seconds":22980}
        ]"""
    }
}
