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
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.DriverProfileRepository
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
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val profiles: DriverProfileRepository,
    private val logger: AuleLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = runCatching { auth.restore() }.getOrNull()
            _state.value = AuthUiState(
                isReady = true,
                isSignedIn = session != null,
                isCheckingAccess = session != null,
                email = session?.user?.email,
            )
            if (session != null) loadAccount(session)
        }
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

    fun retryProfile() {
        val session = auth.currentSession() ?: return
        viewModelScope.launch { loadAccount(session) }
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
        if (_state.value.isCheckingAccess || _state.value.isSubmitting) return
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
                    )
                    return@launch
                }
                _state.value = AuthUiState(
                    isReady = true,
                    isSignedIn = true,
                    isCheckingAccess = true,
                    email = session.user.email,
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
            denyAccess(AuthFailureKind.NO_HABILITATION)
            return
        }

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
            )
        }
    }

    private suspend fun denyAccess(kind: AuthFailureKind) {
        auth.signOut()
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
