package io.aule.android.core.model

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La fusion d'une page reçue dans ce qui est déjà affiché.
 *
 * C'est la règle la plus facile à casser de la messagerie, et la seule dont
 * l'erreur ne se voie pas tout de suite : un doublon apparaît une fois sur dix,
 * à la sortie d'un tunnel. Chaque épreuve porte donc le symptôme qu'elle
 * empêche, et non la mécanique qu'elle décrit.
 */
class HubMessageMergeTest {

    private val base = Instant.parse("2026-09-05T05:40:00Z")

    private fun message(
        id: String,
        secondes: Long,
        clientId: String? = null,
        state: HubDeliveryState = HubDeliveryState.ENVOYE,
        supprime: Boolean = false,
        corps: String = "Bonjour",
    ): HubMessage {
        val instant = base.plusSeconds(secondes)
        return HubMessage(
            id = id,
            channelId = "canal",
            senderId = "agent",
            senderLabel = "Camille Roy",
            body = corps,
            clientId = clientId,
            createdAt = instant,
            deletedAt = if (supprime) instant else null,
            activityAt = instant,
            deliveryState = state,
        )
    }

    @Test
    fun `le delta rend les memes messages plusieurs fois et n en duplique aucun`() {
        // Le recouvrement de cinq secondes garantit qu'un message revient. C'est
        // voulu ; ce qui ne l'est pas, c'est qu'il s'affiche deux fois.
        val deja = listOf(message("a", 0), message("b", 10))

        val fusion = HubMessageMerge.merge(deja, deja)

        assertEquals(listOf("a", "b"), fusion.map { it.id })
    }

    @Test
    fun `une edition remplace le texte au lieu d ajouter une ligne`() {
        val fusion = HubMessageMerge.merge(
            listOf(message("a", 0, corps = "5 h 40")),
            listOf(message("a", 0, corps = "5 h 30")),
        )

        assertEquals(1, fusion.size)
        assertEquals("5 h 30", fusion.first().body)
    }

    @Test
    fun `l echo d un envoi optimiste prend la place de l optimiste`() {
        // ⚠️ Le message affiché tout de suite n'a pas d'identifiant serveur.
        // Sans l'appariement par clientId, l'agent verrait son message deux fois.
        val optimiste = message("local-1", 5, clientId = "c-1", state = HubDeliveryState.EN_ATTENTE)
        val confirme = message("serveur-9", 5, clientId = "c-1")

        val fusion = HubMessageMerge.merge(listOf(message("a", 0), optimiste), listOf(confirme))

        assertEquals(listOf("a", "serveur-9"), fusion.map { it.id })
        assertEquals(HubDeliveryState.ENVOYE, fusion.last().deliveryState)
    }

    @Test
    fun `un message deja confirme ne se fait pas reprendre par un client identique`() {
        // Un clientId réutilisé par erreur ne doit pas faire disparaître un
        // message écrit.
        val confirme = message("serveur-9", 5, clientId = "c-1")
        val autre = message("serveur-10", 6, clientId = "c-1")

        assertEquals(2, HubMessageMerge.merge(listOf(confirme), listOf(autre)).size)
    }

    @Test
    fun `une pierre tombale retire le message de l ecran`() {
        // ⚠️ C'est le seul moyen qu'a un téléphone d'apprendre qu'il doit
        // retirer un message déjà reçu : le serveur ne le rend que pour dire
        // qu'il est supprimé.
        val fusion = HubMessageMerge.merge(
            listOf(message("a", 0), message("b", 10)),
            listOf(message("b", 10, supprime = true)),
        )

        assertEquals(listOf("a"), fusion.map { it.id })
    }

    @Test
    fun `une pierre tombale sur un message jamais recu ne le fait pas apparaitre`() {
        val fusion = HubMessageMerge.merge(
            listOf(message("a", 0)),
            listOf(message("z", 99, supprime = true)),
        )

        assertEquals(listOf("a"), fusion.map { it.id })
    }

    @Test
    fun `deux messages de la meme microseconde gardent un ordre stable`() {
        // Deux messages écrits dans la même transaction portent la même date. Un
        // tri instable les ferait sauter d'un rafraîchissement à l'autre.
        val premier = HubMessageMerge.merge(
            emptyList(),
            listOf(message("b", 3), message("a", 3)),
        )
        val second = HubMessageMerge.merge(
            emptyList(),
            listOf(message("a", 3), message("b", 3)),
        )

        assertEquals(listOf("a", "b"), premier.map { it.id })
        assertEquals(premier.map { it.id }, second.map { it.id })
    }

    @Test
    fun `une page vide ne vide pas l ecran`() {
        val deja = listOf(message("a", 0), message("b", 10))
        assertEquals(2, HubMessageMerge.merge(deja, emptyList()).size)
    }

    // ------------------------------------------------------------------
    // Le curseur
    // ------------------------------------------------------------------

    @Test
    fun `une page tronquee repart de nextAfter jamais de l heure du serveur`() {
        // ⚠️ Repartir de serverTime sauterait tout ce que la page n'a pas rendu
        // et qui date de plus de cinq secondes — c'est-à-dire ce qu'un téléphone
        // a manqué pendant sa veille.
        val suite = base.plusSeconds(100)
        val serveur = base.plusSeconds(500)

        val curseur = HubMessageMerge.nextCursor(
            HubMessagePage(hasMore = true, nextAfter = suite, serverTime = serveur),
            previous = null,
        )

        assertEquals(suite, curseur)
    }

    @Test
    fun `une page complete repart de l heure du serveur moins le recouvrement`() {
        val serveur = base.plusSeconds(500)

        val curseur = HubMessageMerge.nextCursor(
            HubMessagePage(hasMore = false, serverTime = serveur),
            previous = null,
        )

        assertEquals(serveur.minusSeconds(5), curseur)
    }

    @Test
    fun `sans heure de serveur le curseur ne bouge pas`() {
        // Une réponse tronquée ne doit pas faire redemander l'historique entier,
        // ni sauter en avant : on garde ce qu'on avait.
        val avant = base

        val curseur = HubMessageMerge.nextCursor(HubMessagePage(), previous = avant)

        assertEquals(avant, curseur)
    }

    // ------------------------------------------------------------------
    // Ce qu'un canal montre
    // ------------------------------------------------------------------

    @Test
    fun `un canal automatique vide se cache a qui ne peut pas y ecrire`() {
        // Un conducteur dont le dépôt n'a rien publié verrait sinon deux lignes
        // muettes en permanence, et apprendrait à ne plus regarder cette partie
        // de la liste.
        val vide = HubChannel(
            id = "depot", kind = HubChannelKind.DEPOT, name = "Dépôt Haluchâtre",
            mode = HubChannelMode.LECTURE_SEULE, status = HubChannelStatus.ACTIF,
            isAutomatic = true, canWrite = false,
        )

        assertTrue(!vide.isVisible(toStaff = false))
        assertTrue(vide.isVisible(toStaff = true), "le staff y publie : il doit le voir")
        assertTrue(
            vide.copy(canWrite = true).isVisible(toStaff = false),
            "qui peut y écrire doit le voir, staff ou non",
        )
        assertTrue(
            vide.copy(lastMessage = HubLastMessage("m", "Travaux dimanche")).isVisible(false),
            "un canal qui a parlé se montre à tout le monde",
        )
    }

    @Test
    fun `un favori passe avant son type`() {
        val groupe = HubChannel(
            id = "g", kind = HubChannelKind.GROUP, name = "Roulement",
            mode = HubChannelMode.NORMAL, status = HubChannelStatus.ACTIF, isAutomatic = false,
        )

        assertEquals(HubSection.GROUPES, groupe.section)
        assertEquals(HubSection.FAVORIS, groupe.copy(isFavorite = true).section)
        assertEquals(
            HubSection.DIFFUSION,
            groupe.copy(kind = HubChannelKind.NETWORK).section,
        )
    }
}
