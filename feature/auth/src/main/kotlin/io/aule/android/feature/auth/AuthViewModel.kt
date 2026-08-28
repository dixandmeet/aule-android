package io.aule.android.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.resolveAgentAccess
import io.aule.android.core.model.repository.AgentAccessStore
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.BiometricEnrollmentStore
import io.aule.android.core.model.repository.DriverProfileRepository
import io.aule.android.core.security.BiometricAvailability
import io.aule.android.core.security.BiometricSupport
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    /** Vrai quand [AuthRepository.restore] a fini. */
    val isReady: Boolean = false,
    val isSignedIn: Boolean = false,
    val email: String? = null,
    val isSubmitting: Boolean = false,
    val failure: AuthFailureKind? = null,
    /** Vrai le temps de résoudre les habilitations, avant d'ouvrir la carte. */
    val isCheckingAccess: Boolean = false,
    val access: AgentAccess? = null,
    val profile: DriverProfile? = null,
    val depot: Depot? = null,
    val network: TransportNetwork? = null,
    val depots: List<Depot> = emptyList(),
    val networks: List<TransportNetwork> = emptyList(),
    val isLoadingProfile: Boolean = false,
    val profileFailed: Boolean = false,
    val isSavingProfile: Boolean = false,
    val profileSaveFailed: Boolean = false,
    val avatarBytes: ByteArray? = null,
    val isUploadingAvatar: Boolean = false,
    val avatarFailure: AvatarFailureKind? = null,
    val isDeletingAccount: Boolean = false,
    val deleteFailed: Boolean = false,
    /**
     * Vrai quand une session est ouverte **par un lien de récupération**, et
     * qu'elle ne donne donc accès qu'au choix d'un nouveau mot de passe.
     *
     * Distinct de [isSignedIn], qui est vrai aussi : la session existe, mais la
     * racine ne doit pas ouvrir la carte tant que ce drapeau tient.
     */
    val isResettingPassword: Boolean = false,
    /** L'adresse à laquelle le lien vient d'être envoyé, ou `null` avant l'envoi. */
    val recoverySentTo: String? = null,
    /**
     * L'identifiant du compte ouvert. Distinct de [email], qui se voit à
     * l'écran : celui-ci range et garde ce qui est propre à l'appareil — la
     * biométrie et l'habilitation en dépendent.
     */
    val userId: String? = null,
    /** Vrai quand il faut proposer d'activer la biométrie, une fois, après la connexion. */
    val showBiometricProposal: Boolean = false,
    /**
     * Vrai tant que le verrou biométrique n'est pas levé, au lancement.
     *
     * ⚠️ **[isSignedIn] reste faux pendant ce temps**, bien qu'une session
     * existe. C'est ce qui fait tout tenir : la chaîne de la racine retombe
     * naturellement sur l'écran de connexion en cas de refus, sans qu'aucune
     * branche de sortie ait à être écrite. Le thème forcé sombre de la porte
     * d'entrée suit pour la même raison.
     */
    val isAwaitingBiometricUnlock: Boolean = false,
    /**
     * Vrai après un refus **rattrapable** : l'écran de connexion offre alors de
     * relancer la biométrie. Faux après une invalidation, où il n'y a plus rien
     * à relancer.
     */
    val canRetryBiometric: Boolean = false,
    /** Vrai une fois, quand la clé a été invalidée et l'activation effacée. */
    val biometricInvalidatedNotice: Boolean = false,
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val profiles: DriverProfileRepository,
    private val logger: AuleLogger,
    /**
     * Ce que le serveur avait déjà accordé à ce compte sur cet appareil.
     *
     * Optionnel, et c'est volontaire : un test qui ne s'intéresse pas aux
     * habilitations hors ligne n'a pas à en fabriquer un. Absent, l'écran se
     * comporte comme avant — une vérification impossible ferme la session.
     */
    private val accessCache: AgentAccessStore? = null,
    /**
     * L'activation biométrique de cet appareil, ou `null` si la fonctionnalité
     * n'est pas câblée.
     *
     * Optionnel comme [accessCache], et pour la même raison : un test qui ne
     * s'intéresse pas à la biométrie n'a pas à en fabriquer un. Absent, l'écran
     * se comporte exactement comme avant — aucune proposition, aucun verrou.
     *
     * ⚠️ **C'est la seule pièce du verrou qui entre ici.** Le coffre de clés et
     * le dialogue vivent sur `AuleGraph` et sont consommés par les Composables :
     * ouvrir un dialogue demande une `Activity`, qu'un `ViewModel` ne doit
     * jamais tenir — il survit aux recréations de configuration, et la garder
     * ferait fuir une fenêtre à chaque rotation.
     */
    private val biometricEnrollment: BiometricEnrollmentStore? = null,
    /** De quoi savoir si l'appareil sait reconnaître son porteur. Interface : feintable. */
    private val biometricSupport: BiometricSupport? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    /**
     * Restaurer d'abord, demander l'empreinte ensuite.
     *
     * ## Pourquoi cet ordre, et non l'inverse
     *
     * [AuthRepository.restore] sait déjà distinguer une session **révoquée**
     * d'une simple panne de réseau (`REVOKING_FAILURES`, dans
     * `SupabaseAuthRepository`) : un conducteur en sous-sol garde sa session,
     * un compte fermé la perd. Demander l'empreinte avant de savoir cela
     * reviendrait à faire poser un doigt pour, parfois, éjecter aussitôt la
     * personne vers l'écran de connexion.
     *
     * Vu de l'écran, rien ne change — ouverture, dialogue, carte. Seul l'ordre
     * interne diffère, et il évite la seule séquence qui aurait été absurde.
     */
    init {
        viewModelScope.launch {
            val session = runCatching { auth.restore() }.getOrNull()
            if (session == null) {
                // Plus de session : le secret ne garde plus rien. Le laisser
                // ferait afficher un verrou devant une porte déjà ouverte.
                runCatching { biometricEnrollment?.clear() }
            }
            val gate = session != null &&
                runCatching { biometricEnrollment?.read(session.user.id) }.getOrNull() != null
            _state.value = AuthUiState(
                isReady = true,
                isSignedIn = session != null && !gate,
                isCheckingAccess = session != null && !gate,
                isAwaitingBiometricUnlock = gate,
                userId = session?.user?.id,
                email = session?.user?.email,
            )
            if (session != null && !gate) loadAccount(session)
        }
    }

    /**
     * L'empreinte a été reconnue **et** le marqueur rouvert : la suite est
     * exactement celle d'un lancement ordinaire.
     */
    fun onBiometricUnlockSucceeded() {
        val session = auth.currentSession()
        if (session == null) {
            // La session a disparu pendant que le dialogue était ouvert. Rien à
            // déverrouiller : on retombe sur le formulaire, sans bandeau.
            onBiometricUnlockDeclined()
            return
        }
        _state.value = _state.value.copy(
            isAwaitingBiometricUnlock = false,
            isSignedIn = true,
            isCheckingAccess = true,
        )
        viewModelScope.launch { loadAccount(session) }
    }

    /**
     * Refus, annulation, ou clé invalidée : on rend la main au formulaire.
     *
     * [isSignedIn] restant faux, la chaîne de la racine y retombe d'elle-même.
     *
     * @param invalidated vrai quand la clé ne vaut plus rien (empreinte
     *   ajoutée ou retirée). L'activation est alors effacée — elle ne pourrait
     *   plus rien ouvrir — et rien n'est proposé de relancer, contrairement à
     *   une simple annulation.
     */
    fun onBiometricUnlockDeclined(invalidated: Boolean = false) {
        if (invalidated) {
            viewModelScope.launch { runCatching { biometricEnrollment?.clear() } }
        }
        _state.value = _state.value.copy(
            isAwaitingBiometricUnlock = false,
            canRetryBiometric = !invalidated,
            biometricInvalidatedNotice = invalidated,
        )
    }

    /**
     * La proposition a été traitée — activée, refusée, ou simplement fermée.
     *
     * Le drapeau « déjà proposé » est posé **dans tous les cas**, et c'est
     * délibéré : une proposition qu'on a écartée et qui revient au lancement
     * suivant n'est plus une proposition, c'est une insistance.
     */
    fun onBiometricProposalDone() {
        val userId = _state.value.userId
        _state.value = _state.value.copy(showBiometricProposal = false)
        if (userId.isNullOrBlank()) return
        viewModelScope.launch { runCatching { biometricEnrollment?.markOffered(userId) } }
    }

    /**
     * Relancer le dialogue depuis l'écran de connexion.
     *
     * ## Pourquoi reposer un drapeau plutôt que rouvrir un dialogue
     *
     * Le verrou est déjà un écran, monté par la racine quand
     * [AuthUiState.isAwaitingBiometricUnlock] est vrai. Le remettre à vrai le
     * fait revenir, avec sa séquence entière — lecture du scellé, `Cipher`,
     * dialogue, réouverture — sans qu'une ligne de tout cela soit réécrite
     * ailleurs. Un second chemin d'appel depuis l'écran de connexion aurait été
     * une copie, et les copies divergent.
     *
     * Sans session, il n'y a rien à déverrouiller : le bouton disparaît au lieu
     * d'ouvrir un dialogue qui ne pourrait mener nulle part.
     */
    fun retryBiometricUnlock() {
        val current = _state.value
        if (!current.canRetryBiometric || current.userId.isNullOrBlank()) return
        if (auth.currentSession() == null) {
            _state.value = current.copy(canRetryBiometric = false)
            return
        }
        _state.value = current.copy(
            isAwaitingBiometricUnlock = true,
            canRetryBiometric = false,
            failure = null,
        )
    }

    /** Retire le bandeau d'invalidation, une fois lu. */
    fun clearBiometricNotice() {
        if (!_state.value.biometricInvalidatedNotice) return
        _state.value = _state.value.copy(biometricInvalidatedNotice = false)
    }

    /**
     * Faut-il proposer la biométrie à ce compte ?
     *
     * Appelée depuis les trois sorties en succès de [loadAccount] plutôt que
     * recopiée trois fois : le jour où la règle change, elle change à un seul
     * endroit. [BiometricAvailability.isOfferable] inclut « rien d'enrôlé » —
     * c'est le cas qui mène aux réglages du téléphone, pas une raison de se
     * taire.
     */
    private suspend fun shouldOfferBiometrics(session: AuthSession): Boolean {
        val store = biometricEnrollment ?: return false
        val support = biometricSupport ?: return false
        return runCatching {
            store.read(session.user.id) == null &&
                !store.hasBeenOffered(session.user.id) &&
                support.availability().isOfferable
        }.getOrDefault(false)
    }

    /**
     * Oublier le verrou de ce compte.
     *
     * ## Pourquoi la clé du Keystore n'est pas touchée ici
     *
     * Ce `ViewModel` ne tient pas le coffre — voir [biometricEnrollment]. Ce
     * n'est pas un trou : sans le marqueur scellé, la clé ne garde plus rien.
     * Elle ne déchiffre qu'une suite d'octets tirés au sort qui n'existe plus,
     * ne donne accès à aucune session, et la prochaine activation la détruit
     * avant d'en créer une neuve (`BiometricKeyVault.createKey`). Ce qui
     * protège, c'est ce que le dépôt contient ; c'est donc lui qu'on vide.
     */
    private suspend fun forgetBiometrics() {
        runCatching { biometricEnrollment?.clear() }
    }

    fun signIn(email: String, password: String) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, failure = null)
        viewModelScope.launch {
            try {
                val session = auth.signIn(email, password)
                _state.value = AuthUiState(
                    isReady = true,
                    isSignedIn = true,
                    isCheckingAccess = true,
                    email = session.user.email,
                    userId = session.user.id,
                    isLoadingProfile = true,
                )
                loadAccount(session)
            } catch (failure: AuthException) {
                logger.info(LogDomain.AUTH, "Connexion refusée (${failure.kind}).")
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    isSignedIn = false,
                    failure = failure.kind,
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Connexion en échec.", failure)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    isSignedIn = false,
                    failure = AuthFailureKind.NETWORK,
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            // Se déconnecter, c'est aussi renoncer à la réserve : le compte
            // suivant ne doit pas hériter des droits de celui qui part.
            runCatching { accessCache?.clear() }
            // Et au verrou : une empreinte seule ne doit jamais rouvrir une
            // session qu'on a volontairement fermée.
            forgetBiometrics()
            _state.value = AuthUiState(isReady = true, isSignedIn = false)
        }
    }

    /**
     * Suppression définitive. Un échec **laisse** la session ouverte : le
     * widget Flutter (`SAE/test/widget_test.dart`) exige de pouvoir réessayer
     * sans se reconnecter. Le succès, lui, referme la session.
     */
    fun deleteAccount() {
        if (_state.value.isDeletingAccount) return
        _state.value = _state.value.copy(isDeletingAccount = true, deleteFailed = false)
        viewModelScope.launch {
            try {
                auth.deleteAccount()
                // Ce chemin ne passe **ni** par `signOut`, **ni** par
                // `denyAccess` : sans cette ligne, un compte supprimé
                // laisserait derrière lui un verrou biométrique gardant une
                // session qui n'existe plus.
                forgetBiometrics()
                _state.value = AuthUiState(isReady = true, isSignedIn = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Suppression du compte impossible.", failure)
                _state.value = _state.value.copy(
                    isDeletingAccount = false,
                    deleteFailed = true,
                )
            }
        }
    }

    fun clearDeleteFailure() {
        if (!_state.value.deleteFailed) return
        _state.value = _state.value.copy(deleteFailed = false)
    }

    /**
     * Réessayer, c'est presque toujours réessayer **avec du réseau**.
     *
     * D'où le passage par [AuthRepository.restore] plutôt qu'un simple
     * [AuthRepository.currentSession] : depuis qu'une panne ne referme plus la
     * session, on peut tourner avec un jeton d'accès périmé qu'on n'a pas pu
     * rafraîchir. Ce bouton est le seul moment où quelqu'un demande
     * explicitement qu'on retente — c'est donc là que le jeton se remet à jour.
     */
    fun retryProfile() {
        if (auth.currentSession() == null) return
        viewModelScope.launch {
            val session = runCatching { auth.restore() }.getOrNull()
                ?: auth.currentSession()
                ?: return@launch
            loadAccount(session)
        }
    }

    fun saveProfile(update: DriverProfileUpdate) {
        val session = auth.currentSession() ?: return
        val current = _state.value.profile ?: return
        if (_state.value.isSavingProfile) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingProfile = true, profileSaveFailed = false)
            try {
                val saved = profiles.updateProfile(session, current.id, update)
                _state.value = _state.value.copy(
                    isSavingProfile = false,
                    profileSaveFailed = false,
                    profile = saved,
                    depot = _state.value.depots.find { it.id == saved.depotId },
                    network = _state.value.networks.find { it.id == saved.networkId },
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Enregistrement de la fiche impossible.", failure)
                _state.value = _state.value.copy(
                    isSavingProfile = false,
                    profileSaveFailed = true,
                )
            }
        }
    }

    fun clearProfileSaveFailure() {
        if (!_state.value.profileSaveFailed) return
        _state.value = _state.value.copy(profileSaveFailed = false)
    }

    fun uploadAvatar(bytes: ByteArray) {
        val session = auth.currentSession() ?: return
        val current = _state.value.profile ?: return
        if (_state.value.isUploadingAvatar) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingAvatar = true, avatarFailure = null)
            try {
                val saved = profiles.uploadAvatar(
                    session,
                    current.id,
                    bytes,
                    contentType = "image/jpeg",
                    extension = "jpg",
                )
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = null,
                    profile = saved,
                    avatarBytes = bytes,
                )
            } catch (failure: AvatarException) {
                logger.warn(LogDomain.AUTH, "Envoi de la photo impossible (${failure.kind}).")
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = failure.kind,
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Envoi de la photo impossible.", failure)
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = AvatarFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun removeAvatar() {
        val session = auth.currentSession() ?: return
        val current = _state.value.profile ?: return
        if (_state.value.isUploadingAvatar) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingAvatar = true, avatarFailure = null)
            try {
                val saved = profiles.removeAvatar(session, current.id)
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = null,
                    profile = saved,
                    avatarBytes = null,
                )
            } catch (failure: AvatarException) {
                logger.warn(LogDomain.AUTH, "Retrait de la photo impossible (${failure.kind}).")
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = failure.kind,
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Retrait de la photo impossible.", failure)
                _state.value = _state.value.copy(
                    isUploadingAvatar = false,
                    avatarFailure = AvatarFailureKind.UNKNOWN,
                )
            }
        }
    }

    fun clearAvatarFailure() {
        if (_state.value.avatarFailure == null) return
        _state.value = _state.value.copy(avatarFailure = null)
    }

    fun clearFailure() {
        if (_state.value.failure == null) return
        _state.value = _state.value.copy(failure = null)
    }

    /**
     * Demande le lien « mot de passe oublié ».
     *
     * Le succès pose [AuthUiState.recoverySentTo] **sans rien promettre** : le
     * serveur répond pareil pour une adresse inconnue, et l'écran dit « si un
     * compte existe ». Un refus est une vraie panne — réseau, cadence — et
     * s'affiche.
     */
    fun sendPasswordRecovery(email: String) {
        if (_state.value.isSubmitting) return
        val trimmed = email.trim().lowercase()
        _state.value = _state.value.copy(isSubmitting = true, failure = null)
        viewModelScope.launch {
            try {
                auth.sendPasswordRecovery(trimmed)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    failure = null,
                    recoverySentTo = trimmed,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthException) {
                logger.info(LogDomain.AUTH, "Lien de récupération refusé (${failure.kind}).")
                _state.value = _state.value.copy(isSubmitting = false, failure = failure.kind)
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Lien de récupération en échec.", failure)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    failure = AuthFailureKind.NETWORK,
                )
            }
        }
    }

    /** Quitte l'écran « mot de passe oublié » et efface ce qu'il affichait. */
    fun clearRecovery() {
        _state.value = _state.value.copy(recoverySentTo = null, failure = null)
    }

    /**
     * Pose le nouveau mot de passe, puis **entre dans l'application**.
     *
     * La session du lien devient une session ordinaire une fois le mot de passe
     * choisi : renvoyer vers la connexion ferait retaper à l'instant ce qu'on
     * vient de saisir deux fois. Les habilitations sont donc résolues ici, comme
     * après [signIn] — et un compte sans habilitation ressort par le même
     * chemin, mot de passe changé.
     */
    fun updatePassword(newPassword: String) {
        if (_state.value.isSubmitting) return
        _state.value = _state.value.copy(isSubmitting = true, failure = null)
        viewModelScope.launch {
            try {
                auth.updatePassword(newPassword)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: AuthException) {
                logger.info(LogDomain.AUTH, "Mot de passe refusé (${failure.kind}).")
                _state.value = _state.value.copy(isSubmitting = false, failure = failure.kind)
                return@launch
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Changement de mot de passe en échec.", failure)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    failure = AuthFailureKind.NETWORK,
                )
                return@launch
            }
            val session = auth.currentSession()
            if (session == null) {
                // Le mot de passe est bien changé : la seule suite honnête est
                // l'écran de connexion, sans bandeau d'erreur.
                _state.value = AuthUiState(isReady = true, isSignedIn = false)
                return@launch
            }
            _state.value = AuthUiState(
                isReady = true,
                isSignedIn = true,
                isCheckingAccess = true,
                email = session.user.email,
                userId = session.user.id,
                isLoadingProfile = true,
            )
            loadAccount(session)
        }
    }

    /**
     * Retour d'un lien reçu par e-mail : échange PKCE, puis les mêmes
     * habilitations que [signIn]. Sans vérifieur, ou sans habilitation, on
     * retombe sur l'écran de connexion.
     *
     * **Sauf pour un lien de récupération**, qui ouvre une session mais n'entre
     * pas dans l'application : elle ne sert qu'à choisir un nouveau mot de passe,
     * et les habilitations attendent ce moment-là. Le genre se lit dans le dépôt
     * PKCE avant l'échange, qui le consomme.
     */
    fun completeAuthCallback(code: String) {
        // ⚠️ `isAwaitingBiometricUnlock` fait partie de la garde, et il a fallu
        // le lire dans la racine pour s'en apercevoir : le `LaunchedEffect` qui
        // consomme le lien se déclenche dès `isReady`, sans regarder
        // `isSignedIn`. Sans cette condition, un lien de confirmation reçu
        // pendant que le verrou est affiché remplacerait la session en attente
        // — éventuellement par celle d'un **autre compte** — avant que
        // quiconque ait posé un doigt sur le capteur.
        if (_state.value.isCheckingAccess ||
            _state.value.isSubmitting ||
            _state.value.isAwaitingBiometricUnlock
        ) {
            return
        }
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        _state.value = _state.value.copy(
            isSubmitting = true,
            isCheckingAccess = true,
            failure = null,
        )
        viewModelScope.launch {
            val recovery = runCatching { auth.pendingAuthFlow() }.getOrNull() ==
                AuthPkceFlow.RECOVERY
            try {
                val session = auth.exchangeAuthCode(trimmed)
                if (recovery) {
                    logger.info(LogDomain.AUTH, "Session de récupération ouverte.")
                    _state.value = AuthUiState(
                        isReady = true,
                        isSignedIn = true,
                        isCheckingAccess = false,
                        isResettingPassword = true,
                        email = session.user.email,
                        userId = session.user.id,
                    )
                    return@launch
                }
                _state.value = AuthUiState(
                    isReady = true,
                    isSignedIn = true,
                    isCheckingAccess = true,
                    email = session.user.email,
                    userId = session.user.id,
                    isLoadingProfile = true,
                )
                loadAccount(session)
            } catch (failure: AuthException) {
                logger.info(LogDomain.AUTH, "Confirmation refusée (${failure.kind}).")
                _state.value = AuthUiState(
                    isReady = true,
                    isSignedIn = false,
                    isCheckingAccess = false,
                    failure = failure.kind,
                )
            } catch (failure: Throwable) {
                logger.warn(LogDomain.AUTH, "Confirmation en échec.", failure)
                _state.value = AuthUiState(
                    isReady = true,
                    isSignedIn = false,
                    isCheckingAccess = false,
                    failure = AuthFailureKind.NETWORK,
                )
            }
        }
    }

    /**
     * La fiche, le dépôt, le réseau, et les habilitations.
     *
     * Une fiche illisible **ne déconnecte pas** si un rôle staff suffit à
     * ouvrir. L'inverse des habilitations : aucune, ou illisibles, et la
     * session se ferme — un compte voyageur ne doit pas voir la carte Pro.
     */
    private suspend fun loadAccount(session: AuthSession) {
        _state.value = _state.value.copy(
            isCheckingAccess = true,
            isLoadingProfile = true,
            profileFailed = false,
        )
        var profileFailed = false
        val profile = try {
            profiles.fetchProfile(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Fiche agent illisible.", failure)
            profileFailed = true
            null
        }

        val staffRole = try {
            auth.fetchStaffRole(session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unreachable: AuthException) {
            // « Je n'ai pas pu demander » n'est pas « le serveur dit non ».
            // Confondre les deux fermait la session d'un conducteur garé en
            // sous-sol — et l'écran de connexion vers lequel on le renvoyait
            // avait besoin, lui aussi, du réseau qui manquait.
            if (unreachable.kind == AuthFailureKind.NETWORK &&
                openOnLastKnownAccess(session, profile, profileFailed)
            ) {
                return
            }
            logger.warn(LogDomain.AUTH, "Habilitations illisibles (${unreachable.kind}).")
            denyAccess(AuthFailureKind.HABILITATION_UNVERIFIED)
            return
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Habilitations illisibles.", failure)
            denyAccess(AuthFailureKind.HABILITATION_UNVERIFIED)
            return
        }

        if (profileFailed && staffRole.isNullOrBlank()) {
            denyAccess(AuthFailureKind.HABILITATION_UNVERIFIED)
            return
        }

        val access = resolveAgentAccess(
            staffRole = staffRole,
            hasDriverProfile = profile != null,
            msrControl = profile?.msrControl ?: false,
            msrIntervention = profile?.msrIntervention ?: false,
        )
        if (access == null) {
            logger.info(LogDomain.AUTH, "Aucune habilitation Aule Pro.")
            // Le serveur a répondu, et il a dit non : ce qu'on gardait en
            // réserve pour les jours sans réseau ne vaut plus rien.
            runCatching { accessCache?.clear() }
            denyAccess(AuthFailureKind.NO_HABILITATION)
            return
        }
        // La réponse du serveur devient la réserve du prochain sous-sol.
        runCatching { accessCache?.write(session.user.id, access) }

        try {
            val (depots, networks) = coroutineScope {
                val depotsDeferred = async {
                    runCatching { profiles.fetchDepots(session) }.getOrDefault(emptyList())
                }
                val networksDeferred = async {
                    runCatching { profiles.fetchNetworks(session) }.getOrDefault(emptyList())
                }
                depotsDeferred.await() to networksDeferred.await()
            }
            val avatarBytes = readAvatar(profile?.avatarUrl)
            _state.value = _state.value.copy(
                isCheckingAccess = false,
                isSignedIn = true,
                isSubmitting = false,
                isLoadingProfile = false,
                profileFailed = profileFailed,
                access = access,
                profile = profile,
                depots = depots,
                networks = networks,
                depot = depots.find { it.id == profile?.depotId },
                network = networks.find { it.id == profile?.networkId },
                avatarBytes = avatarBytes,
                userId = session.user.id,
                showBiometricProposal = shouldOfferBiometrics(session),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            logger.warn(LogDomain.AUTH, "Catalogues de la fiche illisibles.", failure)
            _state.value = _state.value.copy(
                isCheckingAccess = false,
                isSignedIn = true,
                isSubmitting = false,
                isLoadingProfile = false,
                profileFailed = profileFailed,
                access = access,
                profile = profile,
                depot = null,
                network = null,
                depots = emptyList(),
                networks = emptyList(),
                avatarBytes = null,
                userId = session.user.id,
                showBiometricProposal = shouldOfferBiometrics(session),
            )
        }
    }

    /**
     * Ouvrir sur la dernière habilitation connue, faute d'avoir pu la vérifier.
     *
     * Rend `false` quand il n'y a rien en réserve — un compte jamais vérifié
     * sur cet appareil reste dehors. La porte ne s'ouvre pas sur une absence de
     * donnée : elle s'ouvre sur un « oui » que le serveur a déjà prononcé ici.
     *
     * La fiche agent, elle, manque presque toujours dans ce cas (elle se lit au
     * même moment, sur le même réseau absent). C'est déjà un état prévu :
     * `profileFailed` fait retomber la carte d'identité du menu sur l'adresse de
     * session, comme pour un compte sans fiche `drivers`.
     */
    private suspend fun openOnLastKnownAccess(
        session: AuthSession,
        profile: DriverProfile?,
        profileFailed: Boolean,
    ): Boolean {
        val remembered = runCatching { accessCache?.read(session.user.id) }.getOrNull()
            ?: return false
        logger.warn(
            LogDomain.AUTH,
            "Habilitations injoignables — ouverture sur la dernière connue (${remembered.modes}).",
        )
        _state.value = _state.value.copy(
            isCheckingAccess = false,
            isSignedIn = true,
            isSubmitting = false,
            isLoadingProfile = false,
            profileFailed = profileFailed,
            access = remembered,
            profile = profile,
            depots = emptyList(),
            networks = emptyList(),
            depot = null,
            network = null,
            avatarBytes = null,
            userId = session.user.id,
            // Proposée même ici, faute de réseau : le verrou est local, il ne
            // demande rien à personne, et c'est justement le lancement où il
            // rend le plus service.
            showBiometricProposal = shouldOfferBiometrics(session),
        )
        return true
    }

    private suspend fun denyAccess(kind: AuthFailureKind) {
        auth.signOut()
        // Ici plutôt qu'aux sites d'appel : `denyAccess` ferme la session pour
        // *tous* ses appelants — habilitation absente comme invérifiable — et
        // le verrou doit tomber dans les deux cas. Le vider au site d'appel
        // n'en couvrirait qu'un, comme c'est déjà le cas pour `accessCache`.
        forgetBiometrics()
        _state.value = AuthUiState(
            isReady = true,
            isSignedIn = false,
            isCheckingAccess = false,
            failure = kind,
        )
    }

    /**
     * Une photo illisible n'est pas une panne de fiche : les initiales
     * restent, et on n'affiche pas un bandeau rouge pour un JPEG manquant.
     */
    private suspend fun readAvatar(url: String?): ByteArray? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            profiles.fetchAvatarImage(trimmed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }
}
