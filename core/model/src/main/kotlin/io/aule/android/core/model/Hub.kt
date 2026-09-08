package io.aule.android.core.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Une discussion de la messagerie Aule Pro.
 *
 * ## Ce que le client ne décide pas
 *
 * ⚠️ **[canWrite] décide du composeur, jamais [kind].** Un canal Réseau est en
 * lecture seule pour un conducteur et **ouvert à un régulateur** : les deux
 * voient le même type, et un seul doit voir la barre de saisie. Décider sur le
 * type reviendrait à cacher le composeur au staff, ou à le montrer à qui sera
 * refusé après avoir tapé son message.
 *
 * C'est la base qui calcule ces quatre droits — elle seule connaît les rôles de
 * canal et l'appartenance au réseau. Le client les lit ; il ne les recalcule pas.
 *
 * Aucune phrase ici (ADR-011) : `HubText` les formule.
 */
data class HubChannel(
    val id: String,
    val kind: HubChannelKind,
    /**
     * Le nom que l'agent voit. Pour un tête-à-tête, c'est **l'autre** : la base
     * le résout, parce qu'elle seule sait qui est en face.
     */
    val name: String,
    val mode: HubChannelMode,
    val status: HubChannelStatus,
    val isAutomatic: Boolean,
    val avatarUrl: String? = null,
    /** L'autre, dans un tête-à-tête. `null` partout ailleurs. */
    val peerUserId: String? = null,
    val memberCount: Int = 0,
    val myRole: String? = null,
    val canWrite: Boolean = false,
    val canInvite: Boolean = false,
    val canManage: Boolean = false,
    val canLeave: Boolean = false,
    val isMuted: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val lastReadAt: Instant? = null,
    val unreadCount: Int = 0,
    val lastMessage: HubLastMessage? = null,
) {
    /**
     * Le rang d'une discussion dans la liste.
     *
     * Les favoris d'abord, les canaux automatiques en dernier : ils sont
     * toujours là — Réseau et Dépôt ne se quittent pas — et les laisser en tête
     * reléguerait les conversations vivantes sous deux lignes immuables.
     */
    val section: HubSection
        get() = when {
            isFavorite -> HubSection.FAVORIS
            kind == HubChannelKind.DIRECT -> HubSection.DIRECTS
            kind == HubChannelKind.NETWORK ||
                kind == HubChannelKind.DEPOT ||
                kind == HubChannelKind.SUPPORT -> HubSection.DIFFUSION
            else -> HubSection.GROUPES
        }

    /**
     * Un canal automatique **vide** ne se montre pas à qui ne peut pas y écrire.
     *
     * Un conducteur dont le dépôt n'a rien publié verrait sinon deux lignes
     * muettes en permanence, et apprendrait à ne plus regarder cette partie de
     * la liste. Le staff, lui, doit le voir : c'est là qu'il publie.
     */
    fun isVisible(toStaff: Boolean): Boolean =
        !isAutomatic || lastMessage != null || toStaff || canWrite
}

/** La nature d'un canal. Elle sert à **ranger** la liste, pas à décider des droits. */
enum class HubChannelKind {
    GROUP,
    DIRECT,
    NETWORK,
    DEPOT,
    SUPPORT,

    /**
     * Un type que la base connaît et que cette version ignore — un canal de
     * véhicule, de mission. On l'affiche plutôt que de le faire disparaître :
     * un canal absent de la liste est un canal dont l'agent ne saura jamais
     * qu'il existe.
     */
    AUTRE,
}

enum class HubChannelMode { NORMAL, ANNONCE, LECTURE_SEULE }

/**
 * Un groupe fermé se lit, ne s'écrit plus. Fermer n'est pas supprimer :
 * l'historique d'un groupe de travail a de la valeur après son dernier message.
 */
enum class HubChannelStatus { ACTIF, SUSPENDU, FERME }

enum class HubSection { FAVORIS, GROUPES, DIRECTS, DIFFUSION }

data class HubLastMessage(
    val id: String,
    val bodyPreview: String,
    val senderId: String? = null,
    val senderLabel: String = "",
    val type: String = "text",
    val createdAt: Instant? = null,
)

/**
 * Un message, tel que l'écran l'affiche — envoyé, en attente, ou refusé.
 *
 * ## L'état de livraison n'est pas une propriété du serveur
 *
 * [deliveryState] n'existe **que** sur le téléphone : le serveur ne connaît que
 * des messages écrits. Un message en attente est une intention, posée dans la
 * file hors ligne et affichée immédiatement — parce qu'attendre le réseau pour
 * montrer ce qu'on vient de taper est ce qui fait taper deux fois.
 *
 * ## L'appariement se fait par [clientId], jamais par [id]
 *
 * ⚠️ Un message optimiste n'a pas encore d'identifiant serveur. C'est le
 * `clientId`, tiré par le téléphone **avant** l'envoi, qui permet de reconnaître
 * le sien quand il revient. La base porte le même identifiant et refuse le
 * doublon ; sans lui, un réessai après tunnel afficherait deux fois le même
 * message.
 */
data class HubMessage(
    val id: String,
    val channelId: String,
    val senderId: String? = null,
    val senderLabel: String = "",
    val type: HubMessageType = HubMessageType.TEXTE,
    val body: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val clientId: String? = null,
    val priority: String = "normal",
    val createdAt: Instant,
    val editedAt: Instant? = null,
    val deletedAt: Instant? = null,
    /**
     * La date que le delta compare. Bouge à l'édition, à la suppression, **et à
     * une réaction** — ce que `createdAt` ne fait jamais.
     */
    val activityAt: Instant,
    val replyTo: HubReplyTo? = null,
    val replyCount: Int = 0,
    val reactions: List<HubReaction> = emptyList(),
    val file: HubFile? = null,
    val deliveryState: HubDeliveryState = HubDeliveryState.ENVOYE,
) {
    val isDeleted: Boolean get() = deletedAt != null
    val isEdited: Boolean get() = editedAt != null && deletedAt == null

    /**
     * Une publication : un message long avec ses pièces jointes et son fil.
     *
     * C'est l'auteur qui la déclare, par une métadonnée — et non une longueur
     * seuil, qui ferait basculer un message bavard en publication sans que
     * personne ne l'ait voulu.
     */
    val isPost: Boolean get() = metadata["kind"] == "post"
}

enum class HubMessageType {
    TEXTE,
    IMAGE,
    DOCUMENT,
    ENTITE,
    LIEU,

    /** Un type que cette version ne sait pas rendre. On affiche le corps. */
    INCONNU,
}

enum class HubDeliveryState {
    /** Écrit par le serveur. */
    ENVOYE,

    /** Dans la file, pas encore parti. */
    EN_ATTENTE,

    /** Refusé, ou parti trop de fois. L'écran doit le dire et proposer de réessayer. */
    ECHOUE,
}

data class HubReplyTo(
    val id: String,
    val senderId: String? = null,
    val senderLabel: String = "",
    val bodyPreview: String = "",
)

data class HubReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

/**
 * Une pièce jointe.
 *
 * ⚠️ [path] est un **chemin d'objet**, jamais une URL. [signedUrl] en est une,
 * valable quinze minutes : la mettre en cache au-delà donnerait un lien mort
 * qu'aucun message ne signale.
 */
data class HubFile(
    val id: String,
    val fileName: String,
    val path: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val kind: HubFileKind = HubFileKind.DOCUMENT,
    val signedUrl: String? = null,
    val expiresAt: Instant? = null,
) {
    /**
     * L'URL est-elle encore bonne ? Une marge d'une minute évite d'ouvrir un
     * lien qui expire pendant le téléchargement.
     */
    fun isUsable(now: Instant): Boolean {
        val url = signedUrl ?: return false
        if (url.isEmpty()) return false
        val expiry = expiresAt ?: return true
        return expiry.epochSecond - now.epochSecond > 60
    }
}

enum class HubFileKind {
    DOCUMENT,
    IMAGE,

    /**
     * La v1 du partage de planning : une photo ou un PDF, typé pour être
     * affiché comme une carte de planning et non une pièce jointe quelconque.
     */
    PLANNING,
}

/**
 * Une page de messages, et **son curseur**.
 *
 * ⚠️ [nextAfter] n'est pas [serverTime]. Tant que [hasMore] est vrai, le tour
 * suivant repart de `nextAfter` : repartir de `serverTime` sauterait tout ce que
 * la page n'a pas rendu et qui date de plus de cinq secondes — c'est-à-dire,
 * précisément, ce qu'un téléphone a manqué pendant sa veille.
 */
data class HubMessagePage(
    val messages: List<HubMessage> = emptyList(),
    /** Les libellés, une fois par agent — pas une fois par message. */
    val members: Map<String, HubMemberBrief> = emptyMap(),
    val hasMore: Boolean = false,
    val nextAfter: Instant? = null,
    val serverTime: Instant? = null,
)

data class HubMemberBrief(
    val label: String,
    val avatarUrl: String? = null,
    val driverNumber: String? = null,
)

/**
 * Un membre d'une discussion.
 *
 * ⚠️ **Ni e-mail, ni téléphone.** C'est la doctrine de la relève, et la base la
 * tient : un agent se nomme par son libellé, son matricule et son dépôt.
 */
data class HubMember(
    val userId: String,
    val label: String,
    val avatarUrl: String? = null,
    val driverNumber: String? = null,
    val depotName: String? = null,
    val role: String? = null,
    val joinedAt: Instant? = null,
) {
    val isOwner: Boolean get() = role == "channel_owner"
}

/**
 * Un collègue du même réseau, trouvé par la recherche.
 *
 * ⚠️ **[hasAccount] ne se déduit pas de [userId]**, il l'accompagne. Un tiers
 * des fiches conducteur de production n'ont pas de compte Aule Pro : elles
 * apparaissent quand même dans la recherche — l'agent doit savoir que son
 * collègue existe — mais elles ne sont pas invitables. L'écran doit le dire ;
 * laisser essayer produirait un refus que rien n'explique.
 */
data class HubColleague(
    val userId: String? = null,
    val label: String,
    val driverNumber: String? = null,
    val depotName: String? = null,
    val avatarUrl: String? = null,
    val hasAccount: Boolean = false,
    /**
     * ⚠️ **Le consentement à être contacté, et il ne se déduit pas non plus.**
     *
     * Un agent n'est joignable en tête-à-tête que s'il l'a accepté. La base
     * refuse l'ouverture sinon ; l'écran doit le dire **avant**, sous peine de
     * produire un refus que rien n'explique — le défaut même que [hasAccount]
     * réparait.
     *
     * Faux par défaut, ici comme en base : un champ absent — un BFF plus ancien
     * que ce binaire — doit valoir « non joignable », jamais l'inverse.
     */
    val acceptsDirect: Boolean = false,
    val directChannelId: String? = null,
) {
    /**
     * La clé de liste. Elle vaut le matricule quand le compte manque : une
     * rangée sans identité stable saute à chaque rafraîchissement.
     */
    val key: String get() = userId ?: "matricule:${driverNumber ?: label}"

    /**
     * Peut-on lui écrire ?
     *
     * ⚠️ **La joignabilité garde la porte, pas le fil.** Une discussion déjà
     * ouverte se retrouve même si le collègue a refermé sa porte depuis : la
     * base l'autorise, et l'écran doit l'autoriser aussi. Recopier ici la
     * règle du serveur n'est pas la dédoubler — c'est la seule façon de ne pas
     * griser une rangée que le geste aurait acceptée.
     */
    val isContactable: Boolean
        get() = hasAccount && (acceptsDirect || directChannelId != null)
}

/**
 * Une page du répertoire.
 *
 * [hasMore] plutôt qu'un total : la base le lit sur une ligne de trop, là où
 * compter les conducteurs du réseau à chaque page coûterait un balayage complet
 * pour afficher une flèche.
 *
 * [meAcceptsDirect] voyage avec la liste parce que c'est là qu'il se voit :
 * l'écran du répertoire est le seul endroit où un agent constate qu'il n'est pas
 * joignable lui-même — et où lui proposer d'ouvrir sa porte a un sens.
 */
data class HubDirectory(
    val colleagues: List<HubColleague> = emptyList(),
    val hasMore: Boolean = false,
    val meAcceptsDirect: Boolean = false,
) {
    companion object {
        val VIDE = HubDirectory()
    }
}

/**
 * Les deux compteurs de la pastille.
 *
 * Deux, et non un : les notifications d'activité et les discussions non lues ne
 * se rangent pas au même endroit de l'écran, et les additionner ferait clignoter
 * la carte pour une annonce déjà lue dans le journal.
 */
data class HubUnread(
    val notifications: Int = 0,
    val discussions: Int = 0,
) {
    /** Ce que la pastille de la carte montre. */
    val badge: Int get() = discussions

    companion object {
        val AUCUN = HubUnread()
    }
}

/**
 * L'amorçage : les canaux que l'agent n'a pas créés et qui lui reviennent.
 *
 * ⚠️ Deux `null` **ne sont pas une erreur** : c'est un compte pro sans réseau,
 * en cours de rattachement. L'écran montre un Hub vide, pas une panne.
 */
data class HubBootstrap(
    val networkChannelId: String? = null,
    val depotChannelId: String? = null,
    /**
     * Le staff écrit dans les canaux en lecture seule. Le savoir sert à ne pas
     * masquer un canal automatique vide à qui doit y publier.
     */
    val isStaff: Boolean = false,
)

/**
 * Ce que la base refuse, et ce que l'écran doit en faire.
 *
 * Un refus n'est **pas une panne**. « Ce canal est en lecture seule » n'appelle
 * pas un bouton « réessayer » — l'agent réessaierait indéfiniment, et rien à
 * l'écran ne dirait pourquoi.
 */
enum class HubFailureKind {
    NOT_AUTHENTICATED,
    NOT_MEMBER,
    READ_ONLY,
    NOT_OWNER,
    CANNOT_INVITE,
    NOT_SENDER,
    CHANNEL_CLOSED,
    NOT_FOUND,
    OTHER_NETWORK,
    NO_NETWORK,

    /**
     * Le collègue n'accepte pas les tête-à-tête.
     *
     * Un refus de **personne**, pas de système : il n'y a rien à réessayer, et
     * rien à demander non plus — c'est à elle d'ouvrir sa porte. Le confondre
     * avec [OTHER_NETWORK] ferait dire « il n'est pas de votre réseau » d'un
     * collègue que l'agent croise tous les matins.
     */
    CONTACT_REFUSED,
    CANNOT_LEAVE,
    FILE_NOT_UPLOADED,
    BAD_REQUEST,

    /**
     * 404 sur une écriture : **la route n'est pas déployée**. Ce n'est pas une
     * absence de donnée, c'est un BFF plus ancien que ce binaire.
     */
    NOT_DEPLOYED,
    UNKNOWN,
}

class HubException(
    val kind: HubFailureKind,
) : Exception(kind.name) {

    /**
     * Un refus qui **retire** la discussion de l'écran plutôt que d'y afficher
     * une erreur.
     *
     * Un agent retiré d'un groupe pendant qu'il le lisait ne doit pas rester
     * devant une conversation morte avec un bouton « réessayer » : la liste se
     * recharge, et le groupe n'y est plus.
     */
    val invalidatesChannel: Boolean
        get() = kind == HubFailureKind.NOT_MEMBER || kind == HubFailureKind.NOT_FOUND

    /** Réessayer a-t-il un sens ? Non pour tout ce qui tient à des droits. */
    val isRetriable: Boolean get() = kind == HubFailureKind.NOT_DEPLOYED
}

/**
 * Ce qu'une bannière tapée demande à l'application d'ouvrir.
 *
 * Un type plutôt qu'un `String` : la notification arrive avant que la messagerie
 * ne soit prête — parfois avant que la session ne soit ouverte —, et l'intention
 * doit pouvoir **attendre** quelque part sans se confondre avec un identifiant
 * de canal déjà affiché.
 */
data class HubDeepLink(
    val channelId: String,
    val messageId: String? = null,
)

/** Un message tapé, pas encore parti. */
data class HubPendingMessage(
    /**
     * Tiré **à la saisie**, pas à l'envoi : le tirer à l'envoi ferait deux
     * messages d'un réessai, et la base ne pourrait pas reconnaître le doublon.
     */
    val clientId: String,
    val channelId: String,
    val body: String,
    val replyTo: String? = null,
    val fileId: String? = null,
    val createdAt: Instant,
    val attempts: Int = 0,
    /**
     * Un refus définitif — canal en lecture seule, groupe fermé. Réessayer n'y
     * changera rien, et l'écran doit le dire au lieu de tourner.
     */
    val isRefused: Boolean = false,
)

/**
 * La file hors ligne, sérialisée pour le disque.
 *
 * Écrite à la main plutôt que par `@Serializable` : `:core:model` n'applique pas
 * le plugin de sérialisation, et surtout **le décodage doit sauter une entrée
 * illisible plutôt que vider la file**. Vider la file parce qu'une entrée d'une
 * version future porte un champ inconnu effacerait un message que son auteur
 * croit parti.
 */
fun List<HubPendingMessage>.encodeHubPending(): String = buildJsonArray {
    this@encodeHubPending.forEach { attente ->
        addJsonObject {
            put("client_id", JsonPrimitive(attente.clientId))
            put("channel_id", JsonPrimitive(attente.channelId))
            put("body", JsonPrimitive(attente.body))
            attente.replyTo?.let { put("reply_to", JsonPrimitive(it)) }
            attente.fileId?.let { put("file_id", JsonPrimitive(it)) }
            put("created_at", JsonPrimitive(attente.createdAt.toEpochMilli()))
            put("attempts", JsonPrimitive(attente.attempts))
            put("refused", JsonPrimitive(attente.isRefused))
        }
    }
}.toString()

fun decodeHubPending(raw: String?): List<HubPendingMessage> {
    if (raw.isNullOrBlank()) return emptyList()
    val tableau = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull()
        ?: return emptyList()
    return tableau.mapNotNull { element ->
        runCatching {
            val ligne = element.jsonObject
            HubPendingMessage(
                clientId = ligne["client_id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null,
                channelId = ligne["channel_id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null,
                body = ligne["body"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null,
                replyTo = ligne["reply_to"]?.jsonPrimitive?.contentOrNull,
                fileId = ligne["file_id"]?.jsonPrimitive?.contentOrNull,
                createdAt = Instant.ofEpochMilli(
                    ligne["created_at"]?.jsonPrimitive?.longOrNull ?: return@runCatching null,
                ),
                attempts = ligne["attempts"]?.jsonPrimitive?.intOrNull ?: 0,
                isRefused = ligne["refused"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }.getOrNull()
    }
}
