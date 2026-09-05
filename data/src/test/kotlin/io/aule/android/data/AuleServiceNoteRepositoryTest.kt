package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.ServiceNote
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.AuleServiceNoteRepository
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * Le décodage des notes de service, contre la forme que rend
 * `dashboard/app/api/driver/service-notes/route.ts`.
 *
 * Pendant de `ServiceNoteDecodingTests` côté iOS : les deux clients lisent la même
 * route, et deux décodeurs qui divergeraient feraient afficher deux consignes
 * différentes pour la même note.
 */
class AuleServiceNoteRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var endpoints: AuleEndpoints
    private lateinit var client: AuleHttpClient

    private val session = AuthSession(
        user = AuthUser(id = "u1", email = "conducteur@example.org"),
        accessToken = "jeton",
        refreshToken = "rafraichissement",
        expiresAtEpochSeconds = Long.MAX_VALUE,
    )

    private val payload = """
    {"notes":[{"id":"a1","reference":"26/639","issuer":"DGRT — Réseau armature",
      "signatory":"Antony ARDOUIN","title":"Ligne 1 — fin des travaux d'été",
      "summary":"Retour à l'exploitation nominale.",
      "body":"Secteur Quai de la Fosse\nLes 2 quais sont de part et d'autre.",
      "lines":["1"],"kind":"info_trafic","scheduled":true,
      "issuedOn":"2026-08-26","effectiveOn":"2026-08-31","displayUntil":"2026-09-30"}]}
    """.trimIndent()

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    private fun repository() = AuleServiceNoteRepository(client, endpoints)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        endpoints = AuleEndpoints(server.url("/").toString().trimEnd('/'))
        client = AuleHttpClient(OkHttpClient(), NoopLogger)
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `la note se decode, dates comprises`() = runTest {
        respond(payload)

        val note = repository().fetchNotes(session, line = "1").firstOrNull()

        assertNotNull(note)
        assertEquals("26/639", note.reference)
        assertEquals(listOf("1"), note.lines)
        assertEquals(ServiceNote.Kind.INFO_TRAFIC, note.kind)
        assertEquals(true, note.isScheduled)
        assertEquals(LocalDate.parse("2026-08-31"), note.effectiveOn)
        assertEquals(LocalDate.parse("2026-09-30"), note.displayUntil)
        assertEquals(
            ServiceNote.Block.Heading("Secteur Quai de la Fosse"),
            note.blocks.first(),
        )
    }

    @Test
    fun `le jeton de l'appelant part avec la requete`() = runTest {
        respond(payload)

        repository().fetchNotes(session, line = "C3")

        val request = server.takeRequest()
        assertEquals("Bearer jeton", request.headers["Authorization"])
        // L'indice part sous sa forme canonique : le serveur compare à ce que
        // `route_ids` porte, et « c3 » n'y apparierait rien.
        assertTrue(request.target.contains("line=C3"), request.target)
    }

    @Test
    fun `une note sans date de prise d'effet est ecartee, sans emporter les autres`() = runTest {
        respond(
            """
            {"notes":[
              {"id":"bad","reference":"26/000","issuer":null,"signatory":null,"title":"Sans date",
               "summary":null,"body":"x","lines":[],"kind":"consigne","scheduled":null,
               "issuedOn":"2026-08-26","effectiveOn":"hier","displayUntil":null},
              {"id":"good","reference":"26/001","issuer":null,"signatory":null,"title":"Datée",
               "summary":null,"body":"x","lines":[],"kind":"consigne","scheduled":null,
               "issuedOn":"2026-08-26","effectiveOn":"2026-08-31","displayUntil":null}]}
            """.trimIndent(),
        )

        assertEquals(listOf("good"), repository().fetchNotes(session).map { it.id })
    }

    /**
     * Sur une **lecture**, 404 est une absence : la route n'est pas déployée sur
     * cette instance, et l'écran n'a rien à montrer. C'est l'inverse d'une écriture,
     * où le même statut ferait croire à un geste enregistré.
     */
    @Test
    fun `une route absente rend une liste vide, une panne leve`() = runTest {
        respond("""{"error":"not found"}""", status = 404)
        assertTrue(repository().fetchNotes(session, line = "1").isEmpty())

        respond("""{"error":"boom"}""", status = 500)
        assertThrows<ApiException.Server> { repository().fetchNotes(session, line = "1") }
    }
}
