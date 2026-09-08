package io.aule.android.feature.hub

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.HubBootstrap
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubChannelKind
import io.aule.android.core.model.HubChannelMode
import io.aule.android.core.model.HubChannelStatus
import io.aule.android.core.model.HubColleague
import io.aule.android.core.model.HubDeliveryState
import io.aule.android.core.model.HubDirectory
import io.aule.android.core.model.HubException
import io.aule.android.core.model.HubFailureKind
import io.aule.android.core.model.HubFile
import io.aule.android.core.model.HubFileKind
import io.aule.android.core.model.HubMember
import io.aule.android.core.model.HubMessage
import io.aule.android.core.model.HubMessagePage
import io.aule.android.core.model.HubPendingMessage
import io.aule.android.core.model.HubUnread
import io.aule.android.core.model.OAuthProvider
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.HubOutboxStore
import io.aule.android.core.model.repository.HubRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

/**
 * La messagerie, par ce qui se voit à l'écran.
 *
 * Chaque épreuve porte le symptôme qu'elle empêche : un message envoyé deux
 * fois, une bulle muette dans une file que rien ne montre, une conversation
 * morte avec un bouton « réessayer ».
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HubViewModelTest {

    // ------------------------------------------------------------------
    // L'envoi optimiste
    // ------------------------------------------------------------------

    @Test
    fun `un message tape s affiche avant d etre parti`() = runTest {
        // Attendre le réseau pour montrer ce qu'on vient de taper est ce qui
        // fait taper deux fois.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL), echec = HubException(HubFailureKind.NOT_DEPLOYED))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.send("Bonjour", CANAL.id, "Camille Roy")
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.messages.size)
            assertEquals(HubDeliveryState.EN_ATTENTE, viewModel.state.value.messages[0].deliveryState)
            assertEquals(1, viewModel.state.value.pending.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `l echo du serveur prend la place de la bulle optimiste`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.send("Bonjour", CANAL.id, "Camille Roy")
            advanceUntilIdle()

            val messages = viewModel.state.value.messages
            assertEquals(1, messages.size, "deux bulles pour un message serait le défaut")
            assertEquals(HubDeliveryState.ENVOYE, messages[0].deliveryState)
            assertTrue(viewModel.state.value.pending.isEmpty(), "la file se vide quand c'est parti")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un reessai ne poste pas deux fois, le meme identifiant client repart`() = runTest {
        // ⚠️ Un identifiant neuf au réessai ferait deux messages en base : c'est
        // lui, et lui seul, qui permet à la base de reconnaître le doublon.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL), echec = HubException(HubFailureKind.NOT_DEPLOYED))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.send("Bonjour", CANAL.id, "Camille Roy")
            advanceUntilIdle()
            val premier = viewModel.state.value.pending.single().clientId

            hub.echec = null
            viewModel.retry(premier)
            advanceUntilIdle()

            assertEquals(listOf(premier), hub.envoyes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un refus definitif marque la bulle et arrete la file`() = runTest {
        // ⚠️ Réessayer sur un canal en lecture seule ferait tourner l'agent sans
        // qu'aucun écran ne dise pourquoi.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL), echec = HubException(HubFailureKind.READ_ONLY))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.send("Bonjour", CANAL.id, "Camille Roy")
            advanceUntilIdle()

            assertEquals(HubDeliveryState.ECHOUE, viewModel.state.value.messages[0].deliveryState)
            assertTrue(viewModel.state.value.pending.single().isRefused)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une coupure laisse le message en attente, pas en echec`() = runTest {
        // La distinction porte tout : « en attente » repartira au retour du
        // réseau, « non envoyé » demande un geste.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL), echec = HubException(HubFailureKind.NOT_DEPLOYED))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.send("Bonjour", CANAL.id, "Camille Roy")
            advanceUntilIdle()

            assertEquals(HubDeliveryState.EN_ATTENTE, viewModel.state.value.messages[0].deliveryState)
            assertEquals(false, viewModel.state.value.pending.single().isRefused)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la file survit au relancement`() = runTest {
        // Un message perdu avec le processus est un message dont l'auteur croit
        // qu'il est parti.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val magasin = FakeOutbox(
                listOf(
                    HubPendingMessage(
                        clientId = "cl-1", channelId = CANAL.id,
                        body = "dans le tunnel", createdAt = NOW,
                    ),
                ),
            )
            val hub = FakeHub(canaux = listOf(CANAL), echec = HubException(HubFailureKind.NOT_DEPLOYED))
            val viewModel = viewModel(hub, magasin)
            advanceUntilIdle()

            assertEquals("dans le tunnel", viewModel.state.value.pending.single().body)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------------------------
    // Ce qui retire une discussion de l'écran
    // ------------------------------------------------------------------

    @Test
    fun `etre retire d un groupe le fait sortir de la liste`() = runTest {
        // Un agent retiré pendant qu'il lisait ne doit pas rester devant une
        // conversation morte avec un bouton « réessayer ».
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.open(CANAL.id)
            advanceUntilIdle()

            hub.echec = HubException(HubFailureKind.NOT_MEMBER)
            viewModel.leave(CANAL.id)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.channels.isEmpty())
            assertNull(viewModel.state.value.openChannelId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un refus de droit n efface pas la discussion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            hub.echec = HubException(HubFailureKind.NOT_OWNER)
            viewModel.rename(CANAL.id, "Autre nom")
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.channels.size, "on montre le refus, on ne retire pas")
            assertEquals(HubFailureKind.NOT_OWNER, viewModel.state.value.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------------------------
    // L'amorçage
    // ------------------------------------------------------------------

    @Test
    fun `l amorcage ne se rejoue pas a chaque geste, c est une ecriture`() = runTest {
        // Il crée les canaux Réseau et Dépôt. Le rejouer toutes les quinze
        // secondes le ferait jouer des milliers de fois par jour et par agent.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val horloge = mutableListOf(NOW)
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub, now = { horloge.last() })
            advanceUntilIdle()

            horloge.add(NOW.plusSeconds(60))
            viewModel.resumed()
            advanceUntilIdle()
            horloge.add(NOW.plusSeconds(1800))
            viewModel.resumed()
            advanceUntilIdle()

            assertEquals(1, hub.amorcages)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `apres une heure il se rejoue, le depot a pu changer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val horloge = mutableListOf(NOW)
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub, now = { horloge.last() })
            advanceUntilIdle()

            horloge.add(NOW.plusSeconds(3601))
            viewModel.resumed()
            advanceUntilIdle()

            assertEquals(2, hub.amorcages)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un amorcage en echec n empeche pas de lire`() = runTest {
        // Les canaux existent peut-être déjà : refuser d'afficher la messagerie
        // parce que l'amorçage a échoué la rendrait inutilisable hors réseau.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL), amorcageEchoue = true)
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            assertNull(viewModel.state.value.failure, "un amorçage raté ne se montre pas")
            assertEquals(1, viewModel.state.value.channels.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un chargement automatique ne crie pas, il vieillit la liste`() = runTest {
        // Ouvrir la messagerie dans un tunnel et recevoir une alerte rouge
        // apprendrait à ne plus l'ouvrir.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = emptyList(), echec = HubException(HubFailureKind.UNKNOWN))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            assertNull(viewModel.state.value.failure)
            assertTrue(viewModel.state.value.isStale)

            // Le geste délibéré, lui, rapporte.
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(HubFailureKind.UNKNOWN, viewModel.state.value.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------------------------
    // Vide, ou inconnu
    // ------------------------------------------------------------------

    @Test
    fun `une messagerie non deployee ne se lit pas comme un compte neuf`() = runTest {
        // Sur une **lecture**, le 404 vaut vide : le BFF ne distingue pas une
        // route absente d'une liste sans discussion. Seul l'amorçage, qui
        // écrit, le sait — et sans ce relais l'écran affichait « aucune
        // discussion » à qui attendait des messages qui ne pouvaient pas venir.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(
                canaux = emptyList(),
                amorcageRefus = HubException(HubFailureKind.NOT_DEPLOYED),
            )
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isNotDeployed)
            assertNull(viewModel.state.value.failure, "un amorçage raté ne crie toujours pas")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une liste jamais arrivee n est pas une liste vide`() = runTest {
        // « Aucune discussion » est une affirmation. Une liste qui n'a pas
        // chargé n'autorise pas à l'écrire — et le genre du refus doit rester
        // disponible pour l'écran sans passer par la `Snackbar`.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = emptyList(), echec = HubException(HubFailureKind.NO_NETWORK))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            assertEquals(HubFailureKind.NO_NETWORK, viewModel.state.value.channelsFailure)
            assertNull(viewModel.state.value.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le tourniquet retombe au premier verdict`() = runTest {
        // Il part à `true` — l'écran s'ouvre pendant que la liste arrive — et
        // doit retomber, succès ou échec. Resté levé, il laisse un rond qui
        // tourne devant une messagerie parfaitement chargée.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = emptyList(), echec = HubException(HubFailureKind.UNKNOWN))
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isLoadingChannels.not())
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ------------------------------------------------------------------
    // Doublures
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Le répertoire
    // ------------------------------------------------------------------

    @Test
    fun `ouvrir le repertoire demande le reseau, sans rien taper`() = runTest {
        // C'est toute la différence entre un répertoire et une recherche : sans
        // cet appel à requête vide, un agent qui ne connaît pas le nom exact de
        // son collègue n'a aucun moyen de le trouver.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            hub.repertoire = { _, _ ->
                HubDirectory(colleagues = listOf(COLLEGUE), meAcceptsDirect = true)
            }
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.openDirectory()
            advanceUntilIdle()

            assertEquals(listOf("" to 0), hub.demandes)
            assertEquals(1, viewModel.state.value.directory.colleagues.size)
            assertTrue(viewModel.state.value.directory.isOpen)
            assertTrue(viewModel.state.value.directory.meAcceptsDirect)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `six touches ne font qu un appel, et c est la derniere qui gagne`() = runTest {
        // Sans report, un nom de six lettres part en six requêtes dont les
        // réponses reviennent dans le désordre : la liste finit par afficher le
        // résultat de « Vas » sous la saisie « Vasse ».
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            hub.repertoire = { q, _ -> HubDirectory(colleagues = listOf(COLLEGUE.copy(label = q))) }
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.openDirectory()
            advanceUntilIdle()
            hub.demandes.clear()

            "Vasse".forEachIndexed { index, _ -> viewModel.searchDirectory("Vasse".take(index + 1)) }
            advanceUntilIdle()

            assertEquals(listOf("Vasse" to 0), hub.demandes)
            assertEquals("Vasse", viewModel.state.value.directory.colleagues.single().label)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la page suivante ajoute, elle ne vide pas l ecran`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            hub.repertoire = { _, offset ->
                if (offset == 0) {
                    HubDirectory(colleagues = listOf(COLLEGUE), hasMore = true)
                } else {
                    HubDirectory(colleagues = listOf(COLLEGUE.copy(userId = "u2", label = "Bruno Bertin")))
                }
            }
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.openDirectory()
            advanceUntilIdle()

            viewModel.loadMoreDirectory()
            advanceUntilIdle()

            val repertoire = viewModel.state.value.directory
            assertEquals(listOf("Anne Aubry", "Bruno Bertin"), repertoire.colleagues.map { it.label })
            assertEquals(listOf("" to 0, "" to 1), hub.demandes)
            assertTrue(!repertoire.hasMore)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un repertoire non charge ne dit pas qu il est vide`() = runTest {
        // « Aucun collègue » est une affirmation. Une liste qui n'est jamais
        // arrivée ne permet pas de la faire.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            hub.refusRepertoire = HubException(HubFailureKind.NOT_DEPLOYED)
            val viewModel = viewModel(hub)
            advanceUntilIdle()

            viewModel.openDirectory()
            advanceUntilIdle()

            assertEquals(HubFailureKind.NOT_DEPLOYED, viewModel.state.value.directory.failure)
            assertTrue(viewModel.state.value.directory.colleagues.isEmpty())
            assertTrue(!viewModel.state.value.directory.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ouvrir un tete-a-tete met le canal dans la liste avant de l ouvrir`() = runTest {
        // L'écran lit la conversation courante dans `channels` : sans cette
        // insertion, ouvrir un tête-à-tête neuf refermerait la messagerie sur
        // la liste, et le message qu'on venait écrire n'aurait nulle part où
        // aller.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.openDirectory()
            advanceUntilIdle()

            viewModel.openDirectWith("u1")
            advanceUntilIdle()

            assertEquals("u1", hub.directOuvert)
            assertEquals("dm-u1", viewModel.state.value.openChannelId)
            assertTrue(viewModel.state.value.openChannel != null)
            assertTrue(!viewModel.state.value.directory.isOpen)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un collegue qui n accepte pas laisse le repertoire ouvert, avec sa raison`() = runTest {
        // Le refus se lit là où le doigt a cliqué. Refermer le répertoire sur
        // une `Snackbar` ferait disparaître la liste que l'agent parcourait.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            hub.repertoire = { _, _ -> HubDirectory(colleagues = listOf(COLLEGUE)) }
            hub.refusDirect = HubException(HubFailureKind.CONTACT_REFUSED)
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.openDirectory()
            advanceUntilIdle()

            viewModel.openDirectWith("u1")
            advanceUntilIdle()

            val repertoire = viewModel.state.value.directory
            assertTrue(repertoire.isOpen, "le répertoire reste ouvert sous le refus")
            assertEquals(HubFailureKind.CONTACT_REFUSED, repertoire.failure)
            assertEquals(1, repertoire.colleagues.size, "la liste parcourue ne disparaît pas")
            assertNull(viewModel.state.value.openChannelId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sa propre porte suit le serveur, pas le doigt`() = runTest {
        // Basculer l'écran avant d'écrire laisserait un interrupteur
        // « joignable » sur un compte que personne ne peut joindre, et l'agent
        // attendrait des messages qui ne peuvent pas venir.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val hub = FakeHub(canaux = listOf(CANAL))
            val viewModel = viewModel(hub)
            advanceUntilIdle()
            viewModel.openDirectory()
            advanceUntilIdle()

            hub.refusRepertoire = HubException(HubFailureKind.NOT_DEPLOYED)
            viewModel.setContactPreference(true)
            advanceUntilIdle()

            assertNull(hub.porte, "rien n'a été posé en base")
            assertTrue(
                !viewModel.state.value.directory.meAcceptsDirect,
                "l'interrupteur ne bascule pas sur une écriture refusée",
            )
            assertEquals(HubFailureKind.NOT_DEPLOYED, viewModel.state.value.directory.failure)

            hub.refusRepertoire = null
            viewModel.setContactPreference(true)
            advanceUntilIdle()

            assertEquals(true, hub.porte)
            assertTrue(viewModel.state.value.directory.meAcceptsDirect)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        hub: FakeHub,
        outbox: HubOutboxStore = FakeOutbox(),
        now: () -> Instant = { NOW },
    ) = HubViewModel(
        auth = FakeAuth(SESSION),
        hub = hub,
        outbox = outbox,
        logger = NoopLogger,
        now = now,
    )
    // Aucune boucle : elle n'est ouverte que par l'écran. Un poller vivant sous
    // `advanceUntilIdle` tournerait sans fin, l'horloge virtuelle avançant à
    // chaque `delay`.


    private class FakeOutbox(
        private var contenu: List<HubPendingMessage> = emptyList(),
    ) : HubOutboxStore {
        override suspend fun load(): List<HubPendingMessage> = contenu
        override suspend fun save(pending: List<HubPendingMessage>) {
            contenu = pending
        }
    }

    private class FakeHub(
        private val canaux: List<HubChannel>,
        var echec: HubException? = null,
        private val amorcageEchoue: Boolean = false,
        /** Un refus **nommé** de l'amorçage, quand le genre compte. */
        private val amorcageRefus: HubException? = null,
    ) : HubRepository {
        val envoyes = mutableListOf<String>()
        var amorcages = 0

        override suspend fun bootstrap(session: AuthSession): HubBootstrap {
            amorcages += 1
            amorcageRefus?.let { throw it }
            if (amorcageEchoue) throw HubException(HubFailureKind.NO_NETWORK)
            return HubBootstrap(networkChannelId = "reseau")
        }

        override suspend fun channels(session: AuthSession): List<HubChannel> {
            echec?.let { throw it }
            return canaux
        }

        override suspend fun messages(
            session: AuthSession,
            channelId: String,
            before: Instant?,
        ): HubMessagePage {
            echec?.let { throw it }
            return HubMessagePage(serverTime = NOW)
        }

        override suspend fun delta(
            session: AuthSession,
            channelId: String,
            after: Instant,
        ): HubMessagePage = messages(session, channelId, null)

        override suspend fun send(
            session: AuthSession,
            channelId: String,
            body: String,
            clientId: String,
            replyTo: String?,
            fileId: String?,
        ): HubMessage {
            echec?.let { throw it }
            envoyes += clientId
            return HubMessage(
                id = "serveur-${envoyes.size}", channelId = channelId,
                senderLabel = "Camille Roy", body = body, clientId = clientId,
                createdAt = NOW, activityAt = NOW,
            )
        }

        override suspend fun edit(session: AuthSession, messageId: String, body: String) =
            error("unused")

        override suspend fun delete(session: AuthSession, messageId: String) = error("unused")

        override suspend fun toggleReaction(
            session: AuthSession,
            messageId: String,
            emoji: String,
        ) = error("unused")

        override suspend fun markRead(session: AuthSession, channelId: String, at: Instant?) =
            canaux.first { it.id == channelId }

        override suspend fun updateMembership(
            session: AuthSession,
            channelId: String,
            muted: Boolean?,
            favorite: Boolean?,
            archived: Boolean?,
        ): HubChannel {
            echec?.let { throw it }
            return canaux.first { it.id == channelId }
        }

        override suspend fun createGroup(
            session: AuthSession,
            name: String,
            memberIds: List<String>,
        ): HubChannel {
            echec?.let { throw it }
            return CANAL.copy(id = "neuf", name = name)
        }

        override suspend fun rename(
            session: AuthSession,
            channelId: String,
            name: String,
        ): HubChannel {
            echec?.let { throw it }
            return CANAL.copy(id = channelId, name = name)
        }

        override suspend fun addMembers(
            session: AuthSession,
            channelId: String,
            userIds: List<String>,
        ): HubChannel {
            echec?.let { throw it }
            return CANAL.copy(id = channelId)
        }

        override suspend fun removeMember(
            session: AuthSession,
            channelId: String,
            userId: String,
        ): HubChannel {
            echec?.let { throw it }
            return CANAL.copy(id = channelId)
        }

        override suspend fun leave(session: AuthSession, channelId: String) {
            echec?.let { throw it }
        }

        override suspend fun closeGroup(session: AuthSession, channelId: String): HubChannel {
            echec?.let { throw it }
            return CANAL.copy(id = channelId)
        }

        override suspend fun members(session: AuthSession, channelId: String): List<HubMember> =
            emptyList()

        /** Ce que le répertoire a demandé, pour pouvoir l'affirmer. */
        val demandes = mutableListOf<Pair<String, Int>>()
        var repertoire: (String, Int) -> HubDirectory = { _, _ -> HubDirectory.VIDE }
        var refusRepertoire: HubException? = null
        var porte: Boolean? = null
        var directOuvert: String? = null
        var refusDirect: HubException? = null

        override suspend fun openDirect(session: AuthSession, userId: String): HubChannel {
            refusDirect?.let { throw it }
            directOuvert = userId
            return CANAL.copy(id = "dm-$userId", kind = HubChannelKind.DIRECT, name = "Anne Aubry")
        }

        override suspend fun directory(
            session: AuthSession,
            query: String,
            limit: Int,
            offset: Int,
        ): HubDirectory {
            demandes += query to offset
            refusRepertoire?.let { throw it }
            return repertoire(query, offset)
        }

        override suspend fun setContactPreference(session: AuthSession, accepts: Boolean): Boolean {
            refusRepertoire?.let { throw it }
            porte = accepts
            return accepts
        }

        override suspend fun unread(session: AuthSession): HubUnread {
            echec?.let { throw it }
            return HubUnread(discussions = 1)
        }

        override suspend fun upload(
            session: AuthSession,
            channelId: String,
            bytes: ByteArray,
            fileName: String,
            mimeType: String,
            kind: HubFileKind,
        ): HubFile = error("unused")

        override suspend fun registerPushToken(
            session: AuthSession,
            token: String,
            appEnv: String,
        ) = Unit

        override suspend fun unregisterPushToken(session: AuthSession, token: String) = Unit
    }

    private class FakeAuth(private val stored: AuthSession?) : AuthRepository {
        override fun currentSession() = stored
        override suspend fun restore() = stored
        override suspend fun signIn(email: String, password: String) = error("unused")
        override suspend fun signOut() = Unit
        override suspend fun fetchStaffRole(session: AuthSession) = null
        override suspend fun signUpProfessional(draft: ProRegistrationDraft, password: String) =
            error("unused")
        override suspend fun resendSignupConfirmation(email: String) = error("unused")
        override suspend fun beginOAuthSignUp(provider: OAuthProvider) = error("unused")
        override suspend fun sendPasswordRecovery(email: String) = error("unused")
        override suspend fun updatePassword(newPassword: String) = error("unused")
        override suspend fun pendingAuthFlow() = null
        override suspend fun exchangeAuthCode(code: String) = error("unused")
        override suspend fun deleteAccount() = error("unused")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-05T05:40:00Z")

        val SESSION = AuthSession(
            user = AuthUser(id = "user-1", email = "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 2_000_000_000,
        )

        val COLLEGUE = HubColleague(
            userId = "u1",
            label = "Anne Aubry",
            driverNumber = "70001",
            hasAccount = true,
            acceptsDirect = true,
        )

        val CANAL = HubChannel(
            id = "c1",
            kind = HubChannelKind.GROUP,
            name = "Roulement du matin",
            mode = HubChannelMode.NORMAL,
            status = HubChannelStatus.ACTIF,
            isAutomatic = false,
            canWrite = true,
            canLeave = true,
        )
    }
}
