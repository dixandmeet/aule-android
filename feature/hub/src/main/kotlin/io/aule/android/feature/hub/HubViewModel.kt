package io.aule.android.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.HubChannel
import io.aule.android.core.model.HubColleague
import io.aule.android.core.model.HubDeliveryState
import io.aule.android.core.model.HubException
import io.aule.android.core.model.HubFailureKind
import io.aule.android.core.model.HubMember
import io.aule.android.core.model.HubMessage
import io.aule.android.core.model.HubMessageMerge
import io.aule.android.core.model.HubMessageType
import io.aule.android.core.model.HubPendingMessage
import io.aule.android.core.model.HubUnread
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.HubOutboxStore
import io.aule.android.core.model.repository.HubRepository
import java.time.Instant
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ce que l'écran de la messagerie montre.
 *
 * ⚠️ L'erreur est portée par son **genre**, jamais par une phrase : c'est
 * `HubText` qui la formule (ADR-011).
 */
data class HubUiState(
    val channels: List<HubChannel> = emptyList(),
    val messages: List<HubMessage> = emptyList(),
    val members: List<HubMember> = emptyList(),
    val unread: HubUnread = HubUnread.AUCUN,
    val openChannelId: String? = null,
    val isLoading: Boolean = false,
    val isStale: Boolean = true,
    val isStaff: Boolean = false,
    val pending: List<HubPendingMessage> = emptyList(),
    val directory: HubDirectoryState = HubDirectoryState(),
    val failure: HubFailureKind? = null,
    /**
     * Le sort du **dernier chargement de la liste**, distinct de [failure].
     *
     * ⚠️ Les deux ne se confondent pas : [failure] fait apparaître une
     * `Snackbar`, et un chargement automatique n'a pas le droit de crier. Ce
     * genre-ci ne crie pas — il sert à ne pas écrire « aucune discussion » sur
     * une liste qui n'est **jamais arrivée**. Une liste vide et une liste
     * inconnue s'affichent pareil et ne veulent pas dire la même chose.
     */
    val channelsFailure: HubFailureKind? = null,
    /** Vrai tant que le premier chargement de la liste est en vol. */
    val isLoadingChannels: Boolean = true,
    /**
     * Le BFF de cette instance n'a pas de messagerie.
     *
     * ⚠️ **Une lecture ne peut pas le dire** : sur une lecture, le 404 vaut
     * « rien à montrer », par choix — une route absente ne doit pas devenir une
     * panne. Seul l'amorçage, qui **écrit**, distingue les deux. Sans ce
     * drapeau, un serveur sans messagerie affichait « aucune discussion », et
     * un conducteur attendait des messages qui ne pouvaient pas venir.
     */
    val isNotDeployed: Boolean = false,
) {
    val openChannel: HubChannel? get() = channels.firstOrNull { it.id == openChannelId }
}

/**
 * Le répertoire du réseau, et ce que l'agent en voit.
 *
 * ## Pourquoi un état à part
 *
 * Il a sa propre pagination, sa propre saisie et son propre échec. Fondu dans
 * [HubUiState], le refus d'une page de répertoire se serait confondu avec celui
 * de la liste des discussions — et un réseau qu'on n'a pas pu lire se serait
 * affiché « aucun collègue trouvé », ce qui est une affirmation.
 *
 * ## Ce que [meAcceptsDirect] fait là
 *
 * C'est **sa propre** porte, et le répertoire est le seul écran où elle se voit :
 * un agent y découvre qu'il peut écrire à tout le monde sans que personne ne
 * puisse lui répondre. Le réglage voyage donc avec la liste, en un seul appel.
 */
data class HubDirectoryState(
    val isOpen: Boolean = false,
    val query: String = "",
    val colleagues: List<HubColleague> = emptyList(),
    val hasMore: Boolean = false,
    val meAcceptsDirect: Boolean = false,
    /** Vrai pendant le premier chargement ; une page suivante ne vide pas l'écran. */
    val isLoading: Boolean = false,
    /**
     * ⚠️ Distinct de [HubUiState.failure] : un répertoire qu'on n'a pas pu lire
     * se dit **dans** le répertoire, à la place de la liste. Une `Snackbar`
     * au-dessus d'une page blanche laisserait « aucun collègue » comme seule
     * lecture possible.
     */
    val failure: HubFailureKind? = null,
) {
    /** Une frappe en cours n'est pas une intention : la base rendrait vide. */
    val isTypingTooShort: Boolean get() = query.trim().length == 1
}

/**
 * La messagerie : ce que l'écran montre, et ce qui le tient à jour.
 *
 * ## Les trois choses qu'elle sait faire, et qu'aucun écran ne refait
 *
 * 1. **Tenir un seul poller**, dont la cadence suit l'écran : dix secondes dans
 *    une conversation ouverte, quinze pour la liste, trente pour la seule
 *    pastille. Les faire tourner ensemble interrogerait le BFF trois fois par
 *    tour pour afficher la même chose.
 * 2. **Afficher avant d'envoyer.** Un message tapé apparaît immédiatement, en
 *    attente ; la file le porte jusqu'au réseau, et son écho prend sa place.
 * 3. **Distinguer un refus d'une panne.** « Ce canal est en lecture seule »
 *    n'appelle pas un bouton « réessayer » ; « le réseau s'est tu » si.
 *
 * ## Ce qu'elle ne décide pas
 *
 * Aucun droit : [HubChannel.canWrite] vient de la base, et le recalculer ici
 * donnerait deux réponses au même agent selon l'écran qu'il regarde.
 */
class HubViewModel(
    private val auth: AuthRepository,
    private val hub: HubRepository,
    private val outbox: HubOutboxStore,
    private val logger: AuleLogger,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {

    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    /** Le chargement du répertoire en cours. Un seul : le suivant annule le précédent. */
    private var directoryJob: Job? = null
    private var cursors = mutableMapOf<String, Instant>()
    private var bootstrappedAt: Instant? = null
    private var inBackground = false
    private var flushing = false

    /**
     * ⚠️ **La boucle ne démarre pas ici.** C'est l'écran qui l'ouvre, par un
     * `LaunchedEffect` — et c'est ce qui la rend arrêtable : un ViewModel qui
     * lance une boucle infinie dans son constructeur ne peut être ni éprouvé
     * (`advanceUntilIdle` ne rend jamais la main) ni suspendu sans le détruire.
     *
     * Ce que le constructeur fait, c'est **remplir l'écran** : la file relue, les
     * canaux amorcés, la liste chargée. Rien qui tourne.
     */
    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(pending = outbox.load())
            bootstrapIfNeeded()
            refreshChannels(reporting = false)
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    // ------------------------------------------------------------------
    // La boucle
    // ------------------------------------------------------------------

    fun setInBackground(inBackground: Boolean) {
        this.inBackground = inBackground
    }

    /**
     * Le retour au premier plan : on rejoue la file, on réamorce si l'heure a
     * tourné, et on repart.
     */
    fun resumed() {
        viewModelScope.launch {
            bootstrapIfNeeded()
            flushOutbox()
        }
    }

    /**
     * ⚠️ **L'écran est le seul à ouvrir la boucle**, et c'est délibéré. Un
     * `open()` ou un `resumed()` qui la rallumerait en ferait trois propriétaires
     * pour un objet unique — et rendrait le ViewModel inéprouvable : sous
     * `advanceUntilIdle`, une boucle vivante tourne sans fin, l'horloge virtuelle
     * avançant à chaque `delay`.
     *
     * La cadence, elle, suit l'écran sans redémarrage : [intervalCourant] relit
     * la conversation ouverte à chaque tour.
     */
    fun startPolling() {
        // Un seul poller vivant : deux boucles doubleraient la charge pour
        // afficher la même chose.
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var backoffMs = INTERVALLE_LISTE_MS
            while (isActive) {
                val issue = runCatching { tick() }
                issue
                    .onSuccess {
                        _state.value = _state.value.copy(isStale = false)
                        backoffMs = intervalCourant()
                    }
                    .onFailure { failure ->
                        if (!isActive) return@launch
                        if (failure is CancellationException) throw failure
                        logger.warn(LogDomain.NET, "Tour de messagerie en échec.", failure)
                        backoffMs = min(backoffMs * 2, RECUL_MAX_MS)
                    }
                delay(if (issue.isSuccess) backoffMs else jittered(backoffMs))
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * La cadence, relue à chaque tour : changer d'écran la change sans
     * redémarrer la boucle.
     *
     * ⚠️ En arrière-plan elle double, comme le heartbeat du service : un
     * téléphone en poche n'a pas d'écran à rafraîchir.
     */
    private fun intervalCourant(): Long {
        val base = when {
            _state.value.openChannelId != null -> INTERVALLE_CONVERSATION_MS
            else -> INTERVALLE_LISTE_MS
        }
        return if (inBackground) base * 2 else base
    }

    private suspend fun tick() {
        val session = auth.currentSession() ?: return
        val ouvert = _state.value.openChannelId
        if (ouvert != null) {
            pullDelta(ouvert)
            // La liste se rafraîchit aussi : le compteur d'un autre canal doit
            // bouger pendant qu'on lit celui-ci.
            _state.value = _state.value.copy(channels = hub.channels(session))
        } else {
            _state.value = _state.value.copy(
                channels = hub.channels(session),
                unread = hub.unread(session),
            )
        }
    }

    // ------------------------------------------------------------------
    // Amorçage et liste
    // ------------------------------------------------------------------

    /**
     * ⚠️ **L'amorçage écrit** : il crée les canaux Réseau et Dépôt. Il ne se
     * rejoue donc pas à chaque tour, mais à l'ouverture et après une heure — le
     * seul moment où un changement de dépôt a pu se produire.
     */
    suspend fun bootstrapIfNeeded(force: Boolean = false) {
        val session = auth.currentSession() ?: return
        val dernier = bootstrappedAt
        if (!force && dernier != null && now().epochSecond - dernier.epochSecond < AMORCAGE_TTL_S) return
        try {
            val amorce = hub.bootstrap(session)
            bootstrappedAt = now()
            _state.value = _state.value.copy(isStaff = amorce.isStaff, isNotDeployed = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Un amorçage raté n'empêche pas de lire : les canaux existent
            // peut-être déjà. Le montrer rendrait la messagerie inutilisable
            // hors réseau.
            logger.warn(LogDomain.NET, "Amorçage de la messagerie impossible.", failure)
            // Une exception, et une seule : « pas déployée » n'est pas une
            // panne passagère. Ça ne s'arrangera pas au prochain tour, et une
            // liste vide n'a alors pas le droit de se faire passer pour un
            // compte sans discussion.
            if (kindOf(failure) == HubFailureKind.NOT_DEPLOYED) {
                _state.value = _state.value.copy(isNotDeployed = true)
            }
        }
    }

    /**
     * Recharge la liste.
     *
     * ⚠️ **Un chargement automatique ne crie pas.** Il vieillit la liste, et
     * l'écran montre ce qu'il a avec un bandeau discret. Seul un geste
     * délibéré — la traction — rapporte une erreur : ouvrir la messagerie dans
     * un tunnel et recevoir une alerte rouge apprendrait à ne plus l'ouvrir.
     */
    fun refresh() {
        viewModelScope.launch { refreshChannels(reporting = true) }
    }

    private suspend fun refreshChannels(reporting: Boolean) {
        val session = auth.currentSession()
        if (session == null) {
            _state.value = _state.value.copy(isLoadingChannels = false)
            return
        }
        // ⚠️ **Le tourniquet couvre le premier chargement et les
        // rafraîchissements demandés, jamais les tours de sondage.** Le champ
        // part à `true` et retombe au premier verdict ; le relever à chaque
        // tour faisait clignoter un rond toutes les quinze secondes sur une
        // liste vide, sans jamais rien annoncer.
        if (reporting && _state.value.channels.isEmpty()) {
            _state.value = _state.value.copy(isLoadingChannels = true)
        }
        try {
            _state.value = _state.value.copy(
                channels = hub.channels(session),
                unread = hub.unread(session),
                isStale = false,
                isLoadingChannels = false,
                channelsFailure = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.NET, "Liste de discussions non rafraîchie.", failure)
            _state.value = _state.value.copy(
                isStale = true,
                isLoadingChannels = false,
                channelsFailure = kindOf(failure),
                failure = if (reporting) kindOf(failure) else _state.value.failure,
            )
        }
    }

    // ------------------------------------------------------------------
    // Ouvrir, lire
    // ------------------------------------------------------------------

    fun open(channelId: String) {
        _state.value = _state.value.copy(
            openChannelId = channelId,
            failure = null,
            isLoading = _state.value.messages.none { it.channelId == channelId },
            messages = emptyList(),
        )
        viewModelScope.launch {
            val session = auth.currentSession() ?: return@launch
            try {
                val page = hub.messages(session, channelId)
                apply(page.messages, channelId, page.nextAfter, page.serverTime, page.hasMore)
                flushOutbox()
                markRead(channelId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                handle(failure, channelId)
            } finally {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    fun closeConversation() {
        _state.value = _state.value.copy(openChannelId = null, messages = emptyList())
    }

    private suspend fun pullDelta(channelId: String) {
        val session = auth.currentSession() ?: return
        val curseur = cursors[channelId]
        val page = if (curseur == null) {
            hub.messages(session, channelId)
        } else {
            hub.delta(session, channelId, curseur)
        }
        apply(page.messages, channelId, page.nextAfter, page.serverTime, page.hasMore)
    }

    private fun apply(
        recus: List<HubMessage>,
        channelId: String,
        nextAfter: Instant?,
        serverTime: Instant?,
        hasMore: Boolean,
    ) {
        val fusion = HubMessageMerge.merge(_state.value.messages, recus)
        _state.value = _state.value.copy(messages = fusion)
        val curseur = HubMessageMerge.nextCursor(
            io.aule.android.core.model.HubMessagePage(
                hasMore = hasMore, nextAfter = nextAfter, serverTime = serverTime,
            ),
            cursors[channelId],
        )
        if (curseur != null) cursors[channelId] = curseur
    }

    private suspend fun markRead(channelId: String) {
        val session = auth.currentSession() ?: return
        try {
            replace(hub.markRead(session, channelId, now()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Marquer lu n'est pas un geste qu'on montre : au pire la pastille
            // reste une minute de plus.
            logger.warn(LogDomain.NET, "Lecture non enregistrée.", failure)
        }
    }

    // ------------------------------------------------------------------
    // Écrire
    // ------------------------------------------------------------------

    /**
     * Affiche tout de suite, envoie ensuite.
     *
     * ⚠️ L'identifiant client est tiré **ici**, à la saisie. Le tirer à l'envoi
     * ferait deux messages d'un réessai : la base ne pourrait pas reconnaître le
     * doublon.
     */
    fun send(body: String, channelId: String, senderLabel: String) {
        val texte = body.trim()
        if (texte.isEmpty()) return
        val instant = now()
        val attente = HubPendingMessage(
            clientId = UUID.randomUUID().toString(),
            channelId = channelId,
            body = texte,
            createdAt = instant,
        )
        val optimiste = HubMessage(
            id = "attente:${attente.clientId}",
            channelId = channelId,
            senderLabel = senderLabel,
            type = HubMessageType.TEXTE,
            body = texte,
            clientId = attente.clientId,
            createdAt = instant,
            activityAt = instant,
            deliveryState = HubDeliveryState.EN_ATTENTE,
        )
        _state.value = _state.value.copy(
            pending = _state.value.pending + attente,
            messages = HubMessageMerge.merge(_state.value.messages, listOf(optimiste)),
        )
        viewModelScope.launch {
            outbox.save(_state.value.pending)
            flushOutbox()
        }
    }

    /**
     * Vide la file, **en série et dans l'ordre**.
     *
     * Envoyer en parallèle ferait arriver « j'arrive » avant « je pars », et
     * l'ordre d'une conversation est la moitié de son sens.
     *
     * ⚠️ Un échec réseau **arrête le tour** : continuer épuiserait les cinq
     * essais de tous les messages en attente sur une seule coupure, et la file
     * se viderait en refus au lieu de repartir au retour du réseau.
     */
    suspend fun flushOutbox() {
        if (flushing) return
        val session = auth.currentSession() ?: return
        flushing = true
        try {
            while (true) {
                val attente = _state.value.pending.firstOrNull { !it.isRefused } ?: break
                try {
                    val message = hub.send(
                        session, attente.channelId, attente.body,
                        clientId = attente.clientId, replyTo = attente.replyTo,
                        fileId = attente.fileId,
                    )
                    _state.value = _state.value.copy(
                        pending = _state.value.pending.filterNot { it.clientId == attente.clientId },
                        messages = HubMessageMerge.merge(_state.value.messages, listOf(message)),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    val definitif = failure is HubException && !failure.isRetriable
                    marquer(attente.clientId) { pending ->
                        val essais = pending.attempts + 1
                        pending.copy(
                            attempts = essais,
                            isRefused = definitif || essais >= MAX_ESSAIS,
                        )
                    }
                    break
                }
            }
            outbox.save(_state.value.pending)
            syncPendingBubbles()
        } finally {
            flushing = false
        }
    }

    /** Le geste du bouton « réessayer ». */
    fun retry(clientId: String) {
        marquer(clientId) { it.copy(isRefused = false, attempts = 0) }
        syncPendingBubbles()
        viewModelScope.launch {
            outbox.save(_state.value.pending)
            flushOutbox()
        }
    }

    fun discard(clientId: String) {
        _state.value = _state.value.copy(
            pending = _state.value.pending.filterNot { it.clientId == clientId },
            messages = _state.value.messages.filterNot {
                it.clientId == clientId && it.deliveryState != HubDeliveryState.ENVOYE
            },
        )
        viewModelScope.launch { outbox.save(_state.value.pending) }
    }

    private fun marquer(clientId: String, change: (HubPendingMessage) -> HubPendingMessage) {
        _state.value = _state.value.copy(
            pending = _state.value.pending.map { if (it.clientId == clientId) change(it) else it },
        )
    }

    /** Reporte l'état de la file sur les bulles affichées. */
    private fun syncPendingBubbles() {
        val parClient = _state.value.pending.associateBy { it.clientId }
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { message ->
                val attente = message.clientId?.let { parClient[it] }
                when {
                    message.deliveryState == HubDeliveryState.ENVOYE -> message
                    attente == null -> message
                    attente.isRefused -> message.copy(deliveryState = HubDeliveryState.ECHOUE)
                    else -> message.copy(deliveryState = HubDeliveryState.EN_ATTENTE)
                }
            },
        )
    }

    // ------------------------------------------------------------------
    // Groupes, membres, collègues
    // ------------------------------------------------------------------

    fun createGroup(name: String, memberIds: List<String>, onCreated: (HubChannel) -> Unit = {}) {
        perform(null) { session ->
            val canal = hub.createGroup(session, name, memberIds)
            _state.value = _state.value.copy(channels = listOf(canal) + _state.value.channels)
            onCreated(canal)
        }
    }

    fun loadMembers(channelId: String) {
        perform(channelId) { session ->
            _state.value = _state.value.copy(members = hub.members(session, channelId))
        }
    }

    fun addMembers(channelId: String, userIds: List<String>) {
        perform(channelId) { session ->
            replace(hub.addMembers(session, channelId, userIds))
            _state.value = _state.value.copy(members = hub.members(session, channelId))
        }
    }

    fun removeMember(channelId: String, userId: String) {
        perform(channelId) { session ->
            replace(hub.removeMember(session, channelId, userId))
            _state.value = _state.value.copy(members = hub.members(session, channelId))
        }
    }

    fun rename(channelId: String, name: String) {
        perform(channelId) { session -> replace(hub.rename(session, channelId, name)) }
    }

    fun leave(channelId: String) {
        perform(channelId) { session ->
            hub.leave(session, channelId)
            forget(channelId)
        }
    }

    fun closeGroup(channelId: String) {
        perform(channelId) { session -> replace(hub.closeGroup(session, channelId)) }
    }

    fun setMuted(channelId: String, muted: Boolean) {
        perform(channelId) { session ->
            replace(hub.updateMembership(session, channelId, muted = muted))
        }
    }

    fun setFavorite(channelId: String, favorite: Boolean) {
        perform(channelId) { session ->
            replace(hub.updateMembership(session, channelId, favorite = favorite))
        }
    }

    fun toggleReaction(messageId: String, emoji: String, channelId: String) {
        perform(channelId) { session ->
            val message = hub.toggleReaction(session, messageId, emoji)
            _state.value = _state.value.copy(
                messages = HubMessageMerge.merge(_state.value.messages, listOf(message)),
            )
        }
    }

    fun deleteMessage(messageId: String, channelId: String) {
        perform(channelId) { session ->
            val message = hub.delete(session, messageId)
            _state.value = _state.value.copy(
                messages = HubMessageMerge.merge(_state.value.messages, listOf(message)),
            )
        }
    }

    // ------------------------------------------------------------------
    // Le répertoire
    // ------------------------------------------------------------------

    /**
     * Ouvre le répertoire, et charge sa première page.
     *
     * Le réseau se parcourt sans rien taper : c'est la différence entre un
     * répertoire et une recherche, et elle tient à ce seul appel — la base rend
     * l'annuaire sur une requête vide, et rien sur une lettre seule.
     */
    fun openDirectory() {
        _state.value = _state.value.copy(
            directory = HubDirectoryState(isOpen = true, isLoading = true),
        )
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch { loadDirectory(query = "", offset = 0) }
    }

    fun closeDirectory() {
        directoryJob?.cancel()
        directoryJob = null
        _state.value = _state.value.copy(directory = HubDirectoryState())
    }

    /**
     * La saisie, reportée.
     *
     * ⚠️ **Un appel par touche interrogerait le réseau six fois pour un nom de
     * six lettres**, et les réponses reviendraient dans le désordre : la liste
     * afficherait le résultat de « Vas » après celui de « Vasse ». Le job
     * précédent est donc annulé, et le suivant attend que la frappe s'arrête.
     */
    fun searchDirectory(query: String) {
        _state.value = _state.value.copy(
            directory = _state.value.directory.copy(query = query, failure = null),
        )
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch {
            delay(DEBOUNCE_REPERTOIRE_MS)
            loadDirectory(query, offset = 0)
        }
    }

    /**
     * La page suivante.
     *
     * Elle **ajoute** au lieu de remplacer, et ne relève pas le tourniquet : un
     * écran qui se viderait pour charger sa suite ferait perdre la position de
     * lecture à chaque fin de liste.
     */
    fun loadMoreDirectory() {
        val repertoire = _state.value.directory
        if (!repertoire.hasMore || repertoire.isLoading) return
        directoryJob?.cancel()
        directoryJob = viewModelScope.launch {
            loadDirectory(repertoire.query, offset = repertoire.colleagues.size)
        }
    }

    private suspend fun loadDirectory(query: String, offset: Int) {
        val session = auth.currentSession() ?: return
        if (offset == 0) {
            _state.value = _state.value.copy(
                directory = _state.value.directory.copy(isLoading = true, failure = null),
            )
        }
        try {
            val page = hub.directory(session, query, PAGE_REPERTOIRE, offset)
            val courant = _state.value.directory
            // ⚠️ La réponse d'une saisie abandonnée ne s'affiche pas. Le job est
            // annulé à chaque touche, mais celui-ci a pu finir entre-temps :
            // sans cette garde, un résultat périmé écraserait le bon.
            if (courant.query.trim() != query.trim()) return
            _state.value = _state.value.copy(
                directory = courant.copy(
                    colleagues = if (offset == 0) {
                        page.colleagues
                    } else {
                        // Dédoublonné par clé : une fiche peut changer de page
                        // si le réseau bouge entre deux appels.
                        (courant.colleagues + page.colleagues).distinctBy { it.key }
                    },
                    hasMore = page.hasMore,
                    meAcceptsDirect = page.meAcceptsDirect,
                    isLoading = false,
                    failure = null,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.NET, "Répertoire non chargé.", failure)
            _state.value = _state.value.copy(
                directory = _state.value.directory.copy(
                    isLoading = false,
                    failure = kindOf(failure),
                ),
            )
        }
    }

    /**
     * Ouvre — ou retrouve — le tête-à-tête avec un collègue.
     *
     * ⚠️ **Le canal entre dans la liste avant d'être ouvert.** L'écran lit la
     * conversation courante dans `channels` ; sans cette insertion, ouvrir un
     * tête-à-tête neuf refermerait la messagerie sur la liste, et le message
     * qu'on venait écrire n'aurait nulle part où aller.
     *
     * Le refus, lui, reste affiché **dans le répertoire** : c'est là que
     * l'agent a cliqué, et c'est là qu'il doit lire pourquoi rien ne s'ouvre.
     */
    fun openDirectWith(userId: String) {
        viewModelScope.launch {
            val session = auth.currentSession() ?: return@launch
            try {
                val canal = hub.openDirect(session, userId)
                _state.value = _state.value.copy(
                    channels = (_state.value.channels.filterNot { it.id == canal.id } + canal),
                    directory = HubDirectoryState(),
                )
                open(canal.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Tête-à-tête non ouvert.", failure)
                _state.value = _state.value.copy(
                    directory = _state.value.directory.copy(failure = kindOf(failure)),
                )
            }
        }
    }

    /**
     * Ouvre ou referme sa propre porte.
     *
     * ⚠️ **L'état affiché suit le serveur, pas le doigt.** Basculer l'écran puis
     * appeler le réseau laisserait un interrupteur « joignable » sur un compte
     * que personne ne peut joindre — et l'agent attendrait des messages qui ne
     * peuvent pas venir. Ici l'écriture précède l'affichage ; un échec se dit.
     */
    fun setContactPreference(accepts: Boolean) {
        viewModelScope.launch {
            val session = auth.currentSession() ?: return@launch
            try {
                val pose = hub.setContactPreference(session, accepts)
                _state.value = _state.value.copy(
                    directory = _state.value.directory.copy(
                        meAcceptsDirect = pose,
                        failure = null,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.NET, "Joignabilité non posée.", failure)
                _state.value = _state.value.copy(
                    directory = _state.value.directory.copy(failure = kindOf(failure)),
                )
            }
        }
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    // ------------------------------------------------------------------
    // Plomberie
    // ------------------------------------------------------------------

    /** Le motif d'un geste : on montre l'erreur, on n'avance pas la machine. */
    private fun perform(channelId: String?, block: suspend (session: io.aule.android.core.model.AuthSession) -> Unit) {
        viewModelScope.launch {
            val session = auth.currentSession() ?: return@launch
            _state.value = _state.value.copy(failure = null)
            try {
                block(session)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                handle(failure, channelId)
            }
        }
    }

    /**
     * ⚠️ Certains refus **retirent** la discussion au lieu d'afficher une erreur
     * dedans : un agent retiré d'un groupe pendant qu'il le lisait ne doit pas
     * rester devant une conversation morte avec un bouton « réessayer ».
     */
    private fun handle(failure: Throwable, channelId: String?) {
        logger.warn(LogDomain.NET, "Geste de messagerie en échec.", failure)
        _state.value = _state.value.copy(failure = kindOf(failure))
        val refus = failure as? HubException ?: return
        if (refus.invalidatesChannel && channelId != null) forget(channelId)
    }

    private fun kindOf(failure: Throwable): HubFailureKind =
        (failure as? HubException)?.kind ?: HubFailureKind.UNKNOWN

    private fun forget(channelId: String) {
        cursors.remove(channelId)
        _state.value = _state.value.copy(
            channels = _state.value.channels.filterNot { it.id == channelId },
            messages = if (_state.value.openChannelId == channelId) emptyList() else _state.value.messages,
            openChannelId = if (_state.value.openChannelId == channelId) null else _state.value.openChannelId,
        )
    }

    private fun replace(channel: HubChannel) {
        _state.value = _state.value.copy(
            channels = _state.value.channels.map { if (it.id == channel.id) channel else it },
        )
    }

    internal companion object {
        /** Dix secondes dans une conversation : c'est là que l'attente se voit. */
        const val INTERVALLE_CONVERSATION_MS = 10_000L
        const val INTERVALLE_LISTE_MS = 15_000L
        const val RECUL_MAX_MS = 120_000L

        /** Une heure : le seul délai après lequel un dépôt a pu changer. */
        const val AMORCAGE_TTL_S = 3_600L

        /**
         * Cinq essais, puis la file s'arrête sur ce message et le dit. Insister
         * sans fin masquerait les suivants derrière un message qui ne partira
         * jamais.
         */
        const val MAX_ESSAIS = 5

        /**
         * Le report de la saisie du répertoire.
         *
         * 250 ms : au-dessous, une frappe normale part encore en trois requêtes ;
         * au-dessus, la liste traîne derrière le doigt et l'agent tape à nouveau.
         */
        const val DEBOUNCE_REPERTOIRE_MS = 250L

        /** Une page de répertoire. Le serveur plafonne à 50. */
        const val PAGE_REPERTOIRE = 30
    }
}

/**
 * Disperse un délai de recul.
 *
 * Sans gigue, toutes les instances qui traversent la même panne repartent **en
 * phase**, et le serveur qui se relève reçoit d'un coup tout ce qui l'attendait —
 * c'est-à-dire une seconde panne, causée par la reprise. La moitié est fixe :
 * tirer entre zéro et le plafond ramènerait parfois à un intervalle quasi nul,
 * martelant précisément l'API en difficulté.
 */
internal fun jittered(backoffMs: Long, random: kotlin.random.Random = kotlin.random.Random.Default): Long {
    if (backoffMs <= 0) return backoffMs
    val moitie = backoffMs / 2
    return moitie + random.nextLong(moitie + 1)
}
