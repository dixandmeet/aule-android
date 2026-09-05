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
    val colleagues: List<HubColleague> = emptyList(),
    val failure: HubFailureKind? = null,
) {
    val openChannel: HubChannel? get() = channels.firstOrNull { it.id == openChannelId }
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
            _state.value = _state.value.copy(isStaff = amorce.isStaff)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Un amorçage raté n'empêche pas de lire : les canaux existent
            // peut-être déjà. Le montrer rendrait la messagerie inutilisable
            // hors réseau.
            logger.warn(LogDomain.NET, "Amorçage de la messagerie impossible.", failure)
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
        val session = auth.currentSession() ?: return
        try {
            _state.value = _state.value.copy(
                channels = hub.channels(session),
                unread = hub.unread(session),
                isStale = false,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.NET, "Liste de discussions non rafraîchie.", failure)
            _state.value = _state.value.copy(
                isStale = true,
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

    fun searchColleagues(query: String) {
        viewModelScope.launch {
            val session = auth.currentSession() ?: return@launch
            val trouves = runCatching { hub.searchColleagues(session, query) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(colleagues = trouves)
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
