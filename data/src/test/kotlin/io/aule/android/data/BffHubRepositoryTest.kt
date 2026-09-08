package io.aule.android.data

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubChannelMode
import io.aule.android.core.model.HubDeliveryState
import io.aule.android.core.model.HubException
import io.aule.android.core.model.HubFailureKind
import io.aule.android.core.model.HubFileKind
import io.aule.android.core.network.AuleEndpoints
import io.aule.android.core.network.AuleHttpClient
import io.aule.android.data.aule.BffHubRepository
import java.time.Instant
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

/**
 * Le dépôt de la messagerie, contre un BFF simulé.
 *
 * Les charges utiles sont la forme que `docs/CONTRAT-BFF.md` §12 décrit, en
 * camelCase. Ce qui se joue ici : qu'un refus nommé devienne le bon type, qu'un
 * 404 ne veuille pas dire la même chose en lecture et en écriture, et qu'un
 * message abîmé soit sauté plutôt qu'il n'emporte les autres.
 */
class BffHubRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: BffHubRepository

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = BffHubRepository(
            client = AuleHttpClient(OkHttpClient(), NoopLogger),
            endpoints = AuleEndpoints(server.url("/").toString().trimEnd('/')),
        )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse.Builder().code(status).body(body).build())
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Test
    fun `un canal en lecture seule traverse avec canWrite a faux`() = runTest {
        // ⚠️ C'est `canWrite` qui décide du composeur, jamais le type : un canal
        // Réseau est fermé à un conducteur et ouvert à un régulateur.
        respond(
            """
            {"channels":[{"channelId":"c1","kind":"network","name":"Réseau Naolib",
              "mode":"readonly","status":"active","creationMode":"automatic",
              "memberCount":312,"canWrite":false,"unreadCount":4,
              "lastMessage":{"id":"m1","bodyPreview":"Travaux dimanche",
                "senderLabel":"Régulation","type":"text",
                "createdAt":"2026-09-04T05:00:00Z"}}],
             "unreadTotal":4}
            """.trimIndent(),
        )

        val canaux = repository.channels(SESSION)

        assertEquals(1, canaux.size)
        assertEquals(HubChannelKind.NETWORK, canaux[0].kind)
        assertEquals(HubChannelMode.LECTURE_SEULE, canaux[0].mode)
        assertEquals(false, canaux[0].canWrite)
        assertTrue(canaux[0].isAutomatic)
        assertEquals("Régulation", canaux[0].lastMessage?.senderLabel)
    }

    @Test
    fun `un type de canal inconnu ne fait pas disparaitre le canal`() = runTest {
        // Un canal absent de la liste est un canal dont l'agent ne saura jamais
        // qu'il existe.
        respond("""{"channels":[{"channelId":"c9","kind":"vehicle","name":"Bus 412"}]}""")

        val canaux = repository.channels(SESSION)

        assertEquals(1, canaux.size)
        assertEquals(HubChannelKind.AUTRE, canaux[0].kind)
    }

    @Test
    fun `un canal sans identifiant est saute, les autres passent`() = runTest {
        // ⚠️ `kotlinx` lèverait sur la première ligne abîmée et emporterait la
        // liste entière : le décodage manuel saute la ligne et continue.
        respond("""{"channels":[{"name":"Orphelin"},{"channelId":"c1","kind":"group"}]}""")

        assertEquals(listOf("c1"), repository.channels(SESSION).map { it.id })
    }

    @Test
    fun `un 404 en lecture vaut vide, pas une panne`() = runTest {
        // La route peut ne pas être déployée sur cette instance : l'écran n'a
        // alors rien à montrer, et un bandeau rouge serait faux.
        respond("", status = 404)
        assertEquals(emptyList(), repository.channels(SESSION))
    }

    @Test
    fun `le jeton part en en-tete, et la requete vise la bonne route`() = runTest {
        respond("""{"channels":[]}""")

        repository.channels(SESSION)

        val requete = server.takeRequest()
        assertEquals("/api/hub/channels", requete.target)
        assertEquals("Bearer access-1", requete.headers["Authorization"])
    }

    @Test
    fun `le delta porte son curseur et rend celui du tour suivant`() = runTest {
        respond(
            """
            {"messages":[{"id":"m1","channelId":"c1","body":"Bonjour",
              "createdAt":"2026-09-05T05:40:00Z","activityAt":"2026-09-05T05:41:00Z"}],
             "members":{"u1":{"label":"Camille Roy","driverNumber":"10001"}},
             "hasMore":true,"nextAfter":"2026-09-05T05:41:00Z",
             "serverTime":"2026-09-05T05:45:00Z"}
            """.trimIndent(),
        )

        val page = repository.delta(SESSION, "c1", Instant.parse("2026-09-05T05:00:00Z"))

        assertEquals("after=2026-09-05T05%3A00%3A00Z", server.takeRequest().target.substringAfter('?'))
        assertEquals(1, page.messages.size)
        assertTrue(page.hasMore)
        assertEquals(Instant.parse("2026-09-05T05:41:00Z"), page.nextAfter)
        assertEquals("Camille Roy", page.members["u1"]?.label)
    }

    @Test
    fun `une pierre tombale traverse comme telle`() = runTest {
        // Le serveur ne rend un message supprimé que pour dire qu'il l'est.
        respond(
            """
            {"messages":[{"id":"m1","body":"","isDeleted":true,
              "createdAt":"2026-09-05T05:40:00Z","activityAt":"2026-09-05T05:42:00Z"}]}
            """.trimIndent(),
        )

        val message = repository.delta(SESSION, "c1", Instant.EPOCH).messages.single()

        assertTrue(message.isDeleted)
    }

    @Test
    fun `un collegue sans compte traverse avec hasAccount a faux`() = runTest {
        respond(
            """
            {"colleagues":[{"userId":null,"label":"Alix Loin","driverNumber":"20001",
              "depotName":"Dépôt Chantenay","hasAccount":false}]}
            """.trimIndent(),
        )

        val collegue = repository.directory(SESSION, "loin").colleagues.single()

        assertNull(collegue.userId)
        assertEquals(false, collegue.hasAccount)
        assertEquals("matricule:20001", collegue.key)
    }

    @Test
    fun `une joignabilite absente vaut non joignable, jamais l inverse`() = runTest {
        // Un BFF plus ancien que ce binaire ne rend pas ce champ. Le lire comme
        // « vrai » ouvrirait tout le réseau d'un coup et produirait, à chaque
        // rangée, un refus que rien n'explique. Grisé à tort se voit et se
        // corrige ; l'inverse, non.
        respond("""{"colleagues":[{"userId":"u1","label":"Anne Aubry","hasAccount":true}]}""")

        val collegue = repository.directory(SESSION, "aubry").colleagues.single()

        assertEquals(false, collegue.acceptsDirect)
        assertEquals(false, collegue.isContactable)
    }

    @Test
    fun `une porte refermee laisse ouvrable une discussion deja nouee`() = runTest {
        // La joignabilité garde la porte, pas le fil : la base autorise encore
        // ce canal, et griser la rangée le rendrait inaccessible depuis le
        // répertoire.
        respond(
            """
            {"colleagues":[{"userId":"u1","label":"Anne Aubry","hasAccount":true,
              "acceptsDirect":false,"directChannelId":"dm-1"}]}
            """.trimIndent(),
        )

        val collegue = repository.directory(SESSION, "aubry").colleagues.single()

        assertEquals(false, collegue.acceptsDirect)
        assertTrue(collegue.isContactable)
    }

    @Test
    fun `une recherche d une lettre n interroge pas le serveur`() = runTest {
        // Une frappe en cours n'est pas une intention — la base rendrait vide,
        // et l'aller-retour serait perdu.
        assertEquals(emptyList(), repository.directory(SESSION, "a").colleagues)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `le repertoire part sans requete, et ce n est pas une recherche vide`() = runTest {
        // ⚠️ La chaîne vide **part** : c'est ce qui distingue le répertoire de
        // la recherche. La court-circuiter ici, comme la lettre seule, rendrait
        // l'annuaire du réseau inatteignable.
        respond("""{"colleagues":[],"hasMore":true,"meAcceptsDirect":true}""")

        val page = repository.directory(SESSION, "", limit = 30, offset = 60)

        val demande = server.takeRequest()
        val cible = demande.target
        assertTrue(cible.contains("limit=30"), "limite transmise : $cible")
        assertTrue(cible.contains("offset=60"), "décalage transmis : $cible")
        assertTrue(!cible.contains("q="), "aucune requête, pas une requête vide : $cible")
        assertTrue(page.hasMore)
        assertTrue(page.meAcceptsDirect)
    }

    @Test
    fun `poser sa porte lit ce que le serveur a posé`() = runTest {
        respond("""{"acceptsDirect":true}""")

        assertTrue(repository.setContactPreference(SESSION, true))

        val demande = server.takeRequest()
        assertEquals("PUT", demande.method)
        assertTrue(demande.body?.utf8()?.contains("\"acceptsDirect\":true") == true)
    }

    // ------------------------------------------------------------------
    // Écriture et refus
    // ------------------------------------------------------------------

    @Test
    fun `un envoi rend le message ecrit par le serveur`() = runTest {
        respond(
            """
            {"message":{"id":"s1","channelId":"c1","body":"Bonjour","clientId":"cl-1",
              "senderLabel":"Camille Roy","type":"text",
              "createdAt":"2026-09-05T05:40:00Z","activityAt":"2026-09-05T05:40:00Z"},
             "duplicate":false}
            """.trimIndent(),
            status = 201,
        )

        val message = repository.send(SESSION, "c1", "Bonjour", clientId = "cl-1")

        assertEquals("s1", message.id)
        assertEquals("cl-1", message.clientId)
        assertEquals(HubDeliveryState.ENVOYE, message.deliveryState)
        assertTrue(server.takeRequest().body!!.utf8().contains("\"clientId\":\"cl-1\""))
    }

    @Test
    fun `un canal en lecture seule leve READ_ONLY, pas une panne`() = runTest {
        // ⚠️ Un refus n'est pas une panne : proposer « réessayer » ferait tourner
        // l'agent sans que rien n'explique pourquoi.
        respond("""{"error":"Ce canal est en lecture seule.","refusal":"hub_channel_readonly"}""", 403)

        val levee = assertThrows<HubException> {
            repository.send(SESSION, "c1", "Bonjour", clientId = "cl-1")
        }
        assertEquals(HubFailureKind.READ_ONLY, levee.kind)
        assertEquals(false, levee.isRetriable)
    }

    @Test
    fun `etre retire d un groupe retire la discussion de l ecran`() = runTest {
        respond("""{"refusal":"hub_not_member"}""", 403)

        val levee = assertThrows<HubException> { repository.markRead(SESSION, "c1") }

        assertEquals(HubFailureKind.NOT_MEMBER, levee.kind)
        assertTrue(levee.invalidatesChannel, "l'écran doit sortir de la conversation, pas y afficher une erreur")
    }

    @Test
    fun `un 404 en ecriture est un BFF plus ancien, pas une absence`() = runTest {
        // ⚠️ Le même statut veut dire l'inverse d'une lecture : confondre les
        // deux ferait croire le geste accompli.
        respond("", status = 404)

        val levee = assertThrows<HubException> {
            repository.send(SESSION, "c1", "Bonjour", clientId = "cl-1")
        }
        assertEquals(HubFailureKind.NOT_DEPLOYED, levee.kind)
        assertTrue(levee.isRetriable, "un déploiement en retard se rattrape tout seul")
    }

    @Test
    fun `un refus nomme inconnu de cette version reste une demande mal formee`() = runTest {
        // Le BFF ne nomme que ce qu'un geste a causé : le traiter en panne
        // ferait réessayer indéfiniment.
        respond("""{"refusal":"hub_futur_refus"}""", 400)

        assertEquals(
            HubFailureKind.BAD_REQUEST,
            assertThrows<HubException> { repository.leave(SESSION, "c1") }.kind,
        )
    }

    @Test
    fun `une reponse vide sur une ecriture n est pas un succes`() = runTest {
        // Contrat §7 : un client qui l'aplatirait afficherait un message que
        // personne n'a reçu.
        respond("", status = 200)

        assertThrows<HubException> { repository.send(SESSION, "c1", "Bonjour", clientId = "cl-1") }
    }

    @Test
    fun `une piece jointe part en trois temps, dont un hors du BFF`() = runTest {
        // ① l'URL signée, ② les octets vers Storage, ③ la déclaration.
        respond(
            """{"path":"c1/uuid_planning.pdf","signedUrl":"${server.url("/storage/objet")}","token":"t"}""",
            status = 200,
        )
        respond("", status = 200)
        respond(
            """{"file":{"id":"f1","fileName":"planning.pdf","path":"c1/uuid_planning.pdf",
                 "mimeType":"application/pdf","sizeBytes":1024,"kind":"planning"}}""",
            status = 201,
        )

        val fichier = repository.upload(
            SESSION, "c1", ByteArray(1024), "planning.pdf", "application/pdf", HubFileKind.PLANNING,
        )

        assertEquals(HubFileKind.PLANNING, fichier.kind)
        // ⚠️ `path` est un chemin d'objet, jamais une URL : une URL signée expire.
        assertEquals("c1/uuid_planning.pdf", fichier.path)
        assertEquals("/api/hub/channels/c1/files/sign-upload", server.takeRequest().target)
        val depot = server.takeRequest()
        assertEquals("PUT", depot.method)
        assertEquals("/storage/objet", depot.target)
        assertEquals("/api/hub/channels/c1/files", server.takeRequest().target)
    }

    @Test
    fun `un depot Storage en echec leve avant de declarer le fichier`() = runTest {
        // Déclarer un objet qui n'est pas arrivé donnerait un message portant
        // une pièce jointe introuvable.
        respond("""{"path":"c1/x.pdf","signedUrl":"${server.url("/storage/objet")}"}""")
        respond("", status = 500)

        assertEquals(
            HubFailureKind.FILE_NOT_UPLOADED,
            assertThrows<HubException> {
                repository.upload(SESSION, "c1", ByteArray(4), "x.pdf", "application/pdf", HubFileKind.DOCUMENT)
            }.kind,
        )
        assertEquals(2, server.requestCount, "la déclaration ne doit pas partir")
    }

    @Test
    fun `le jeton d appareil part avec sa plateforme et son environnement`() = runTest {
        // ⚠️ `appEnv` vient du binaire : il décide de l'hôte APNs côté serveur,
        // et un jeton de développement envoyé en production serait marqué mort.
        respond("""{"registered":true}""")

        repository.registerPushToken(SESSION, "jeton-fcm", appEnv = "development")

        val corps = server.takeRequest().body!!.utf8()
        assertTrue(corps.contains("\"platform\":\"android\""))
        assertTrue(corps.contains("\"appEnv\":\"development\""))
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser(id = "user-1", email = "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 2_000_000_000,
        )
    }
}
