package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.network.ApiException
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.SupabaseDriverProfileRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SupabaseDriverProfileRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: SupabaseDriverProfileRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = SupabaseDriverProfileRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = server.url("/").toString().trimEnd('/'),
            publishableKey = "sb_publishable_test",
            nowMillis = { 1_700_000_000_000L },
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `une fiche se decode depuis un tableau PostgREST`() = runTest {
        respond(DRIVER_BODY)

        val profile = repository.fetchProfile(SESSION)

        assertEquals("drv-1", profile?.id)
        assertEquals("Kevin", profile?.firstName)
        assertEquals("Getbu", profile?.lastName)
        assertEquals("4218", profile?.driverNumber)
        assertEquals("depot-blx", profile?.depotId)
        val recorded = server.takeRequest()
        assertTrue(recorded.url.encodedPath.endsWith("/rest/v1/drivers"))
        assertTrue(recorded.url.toString().contains("ilike.agent%40aule.fr") || recorded.url.toString().contains("ilike.agent@aule.fr"))
        assertEquals("Bearer access-1", recorded.headers["Authorization"])
        assertEquals("sb_publishable_test", recorded.headers["apikey"])
        assertEquals("id,email,first_name,last_name,phone,driver_number,depot_id,network_id,avatar_url,msr_control,msr_intervention", recorded.url.queryParameter("select"))
    }

    @Test
    fun `un tableau vide n est pas une panne`() = runTest {
        respond("[]")
        assertNull(repository.fetchProfile(SESSION))
    }

    @Test
    fun `un depot se decode avec son libelle`() = runTest {
        respond(DEPOT_BODY)
        val depots = repository.fetchDepots(SESSION)
        assertEquals(1, depots.size)
        assertEquals("BLX · Dépôt Haluchère", depots.single().label)
    }

    @Test
    fun `un 401 se lit comme une requete fautive`() = runTest {
        respond("{\"message\":\"JWT expired\"}", status = 401)
        assertThrows<ApiException.BadRequest> {
            repository.fetchProfile(SESSION)
        }
    }

    @Test
    fun `sans configuration on ne parle pas au reseau`() = runTest {
        val bare = SupabaseDriverProfileRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            supabaseUrl = "",
            publishableKey = "",
        )
        assertNull(bare.fetchProfile(SESSION))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `un enregistrement part en PATCH et relit la ligne`() = runTest {
        respond(DRIVER_BODY)
        val saved = repository.updateProfile(
            SESSION,
            "drv-1",
            DriverProfileUpdate(
                firstName = "Kevin",
                lastName = null,
                phone = "0600000000",
                driverNumber = "4218",
                depotId = "depot-blx",
                networkId = "net-nan",
            ),
        )
        assertEquals("Kevin", saved.firstName)
        val recorded = server.takeRequest()
        assertEquals("PATCH", recorded.method)
        assertEquals("eq.drv-1", recorded.url.queryParameter("id"))
        assertEquals("return=representation", recorded.headers["Prefer"])
        val body = recorded.body?.utf8().orEmpty()
        assertTrue(body.contains("\"first_name\":\"Kevin\""))
        assertTrue(body.contains("\"last_name\":null"))
    }

    @Test
    fun `un envoi de photo passe par Storage puis PATCHe avatar_url`() = runTest {
        respond("{}")
        respond("""{"Key":"driver-avatars/user-1/avatar.jpg"}""")
        respond(driverBody(avatarUrl = "https://example.invalid/avatar.jpg?v=1"))

        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val saved = repository.uploadAvatar(
            SESSION,
            "drv-1",
            bytes,
            "image/jpeg",
            "jpg",
        )

        assertEquals("https://example.invalid/avatar.jpg?v=1", saved.avatarUrl)

        val deleted = server.takeRequest()
        assertEquals("DELETE", deleted.method)
        assertTrue(deleted.url.encodedPath.endsWith("/storage/v1/object/driver-avatars"))
        val deletedBody = deleted.body?.utf8().orEmpty()
        assertTrue(deletedBody.contains("user-1/avatar.jpg"))
        assertTrue(deletedBody.contains("user-1/avatar.webp"))

        val uploaded = server.takeRequest()
        assertEquals("POST", uploaded.method)
        assertTrue(uploaded.url.encodedPath.endsWith("/storage/v1/object/driver-avatars/user-1/avatar.jpg"))
        assertEquals("image/jpeg", uploaded.headers["Content-Type"])
        assertEquals("false", uploaded.headers["x-upsert"])
        assertEquals(bytes.toList(), uploaded.body!!.toByteArray().toList())

        val patched = server.takeRequest()
        assertEquals("PATCH", patched.method)
        assertTrue(patched.body?.utf8().orEmpty().contains("avatar_url"))
        assertTrue(patched.body?.utf8().orEmpty().contains("v=1700000000000"))
    }

    @Test
    fun `un fichier vide ne parle pas au reseau`() = runTest {
        val thrown = assertThrows<AvatarException> {
            repository.uploadAvatar(SESSION, "drv-1", ByteArray(0), "image/jpeg", "jpg")
        }
        assertEquals(AvatarFailureKind.EMPTY, thrown.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `un bucket absent se lit comme un stockage non configure`() = runTest {
        respond("{}")
        respond("""{"statusCode":"404","message":"Bucket not found"}""", status = 404)
        val thrown = assertThrows<AvatarException> {
            repository.uploadAvatar(
                SESSION,
                "drv-1",
                byteArrayOf(1, 2, 3),
                "image/jpeg",
                "jpg",
            )
        }
        assertEquals(AvatarFailureKind.NOT_CONFIGURED, thrown.kind)
    }

    @Test
    fun `un 403 Storage se lit comme un acces refuse`() = runTest {
        respond("""{"statusCode":"403","message":"new row violates policy"}""", status = 403)
        val thrown = assertThrows<AvatarException> {
            repository.removeAvatar(SESSION, "drv-1")
        }
        assertEquals(AvatarFailureKind.DENIED, thrown.kind)
    }

    @Test
    fun `un retrait vide avatar_url`() = runTest {
        respond("{}")
        respond(driverBody(avatarUrl = null))
        val saved = repository.removeAvatar(SESSION, "drv-1")
        assertNull(saved.avatarUrl)
        server.takeRequest()
        val patched = server.takeRequest()
        assertEquals("PATCH", patched.method)
        assertTrue(patched.body?.utf8().orEmpty().contains("\"avatar_url\":null"))
    }

    @Test
    fun `une photo publique se lit en octets`() = runTest {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        server.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(jpeg)).build())
        val bytes = repository.fetchAvatarImage(server.url("/storage/v1/object/public/x").toString())
        assertEquals(jpeg.toList(), bytes?.toList())
    }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )

        const val DRIVER_BODY = """
            [{
              "id": "drv-1",
              "email": "agent@aule.fr",
              "first_name": "Kevin",
              "last_name": "Getbu",
              "phone": "0600000000",
              "driver_number": "4218",
              "depot_id": "depot-blx",
              "network_id": "net-nan",
              "avatar_url": null,
              "msr_control": false,
              "msr_intervention": true
            }]
        """

        fun driverBody(avatarUrl: String?): String {
            val url = if (avatarUrl == null) "null" else "\"$avatarUrl\""
            return """
                [{
                  "id": "drv-1",
                  "email": "agent@aule.fr",
                  "first_name": "Kevin",
                  "last_name": "Getbu",
                  "phone": "0600000000",
                  "driver_number": "4218",
                  "depot_id": "depot-blx",
                  "network_id": "net-nan",
                  "avatar_url": $url,
                  "msr_control": false,
                  "msr_intervention": true
                }]
            """
        }

        const val DEPOT_BODY = """
            [{
              "id": "depot-blx",
              "code": "BLX",
              "name": "Dépôt Haluchère",
              "network_id": "net-nan"
            }]
        """
    }
}
