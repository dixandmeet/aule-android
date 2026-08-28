package io.aule.android.feature.auth

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AgentRole
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.repository.AgentAccessStore
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.BiometricEnrollment
import io.aule.android.core.model.repository.BiometricEnrollmentStore
import io.aule.android.core.model.repository.DriverProfileRepository
import io.aule.android.core.security.BiometricAvailability
import io.aule.android.core.security.BiometricSupport
import io.aule.android.core.security.BiometricType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * Le verrou biométrique, du côté de l'état.
 *
 * Ce qui se vérifie ici, c'est **l'enchaînement** — quand le verrou s'affiche,
 * ce qu'il laisse passer, et surtout ce qu'il efface. Le dialogue système et le
 * Keystore, eux, n'existent que sur l'appareil et se vérifient sur le S21.
 *
 * La règle que tous ces tests entourent : *la biométrie est un verrou local
 * devant une session qui existe déjà.* Elle n'ouvre jamais rien toute seule, et
 * elle ne doit jamais rouvrir ce qu'on a volontairement fermé.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelBiometricTest {

    private val session = AuthSession(
        user = AuthUser("user-1", "agent@aule.fr"),
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresAtEpochSeconds = 9_999_999_999L,
    )

    private val profile = DriverProfile(
        id = "drv-1",
        email = "agent@aule.fr",
        firstName = "Kevin",
        lastName = "Getbu",
        driverNumber = "4218",
        depotId = "depot-blx",
        networkId = "net-nan",
    )

    private val scelle = BiometricEnrollment(cipherText = "chiffre", iv = "vecteur")

    private fun viewModel(
        auth: AuthRepository,
        enrollment: BiometricEnrollmentStore? = null,
        support: BiometricSupport? = FakeSupport(),
        access: AgentAccessStore = MemoryAccess(),
    ) = AuthViewModel(
        auth = auth,
        profiles = FakeProfiles(profile),
        logger = NoopLogger,
        accessCache = access,
        biometricEnrollment = enrollment,
        biometricSupport = support,
    )

    private fun <T> onMain(body: suspend () -> T) = body

    @Test
    fun `une session verrouillee n'entre pas avant l'empreinte`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            val state = model.state.value
            assertTrue(state.isAwaitingBiometricUnlock, "le verrou doit s'afficher")
            // Le point qui fait tout tenir : la session existe, et pourtant
            // `isSignedIn` est faux. C'est ce qui fait retomber la racine sur
            // l'écran de connexion en cas de refus, sans branche de sortie.
            assertFalse(state.isSignedIn, "aucune entrée tant que le verrou tient")
            assertFalse(state.isCheckingAccess, "rien ne se vérifie derrière le verrou")
            assertEquals("user-1", state.userId)
            assertEquals("agent@aule.fr", state.email)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans activation le lancement reste celui d'avant`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeAuth(session), MemoryEnrollment())
            advanceUntilIdle()

            val state = model.state.value
            assertFalse(state.isAwaitingBiometricUnlock)
            assertTrue(state.isSignedIn)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * ⚠️ Le cas du poste partagé, et il n'est pas théorique : fermer
     * l'application n'est pas se déconnecter, et un conducteur qui rend le
     * téléphone en fin de service laisse sa session ouverte derrière lui.
     *
     * Ce qui protège son collègue n'est pas une impossibilité de changer de
     * compte — il n'y en a aucune — mais la garde d'identité à la lecture. Le
     * scellé de l'un ne verrouille jamais la session de l'autre.
     */
    @Test
    fun `le scelle d'un autre compte ne verrouille rien`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-2" to scelle))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            assertFalse(
                model.state.value.isAwaitingBiometricUnlock,
                "l'empreinte d'un collègue ne garde pas cette session",
            )
            assertTrue(model.state.value.isSignedIn)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans session le scelle est efface`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            viewModel(FakeAuth(stored = null), store)
            advanceUntilIdle()

            // Un secret qui ne garde plus rien : le laisser afficherait un
            // verrou devant une porte déjà ouverte.
            assertTrue(store.entries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `l'empreinte reconnue rejoint le lancement ordinaire`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeAuth(session), MemoryEnrollment(mapOf("user-1" to scelle)))
            advanceUntilIdle()

            model.onBiometricUnlockSucceeded()
            advanceUntilIdle()

            val state = model.state.value
            assertFalse(state.isAwaitingBiometricUnlock)
            assertTrue(state.isSignedIn)
            assertEquals(AccountModes.CONDUCTEUR, state.access?.modes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une annulation rend la main au formulaire, sans rien effacer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            model.onBiometricUnlockDeclined()
            advanceUntilIdle()

            val state = model.state.value
            assertFalse(state.isAwaitingBiometricUnlock)
            assertFalse(state.isSignedIn, "on retombe sur l'écran de connexion")
            assertTrue(state.canRetryBiometric, "une annulation se rattrape")
            assertFalse(state.biometricInvalidatedNotice)
            assertTrue(store.entries.isNotEmpty(), "annuler n'est pas désactiver")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une cle invalidee efface l'activation et ne propose pas de reessayer`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            model.onBiometricUnlockDeclined(invalidated = true)
            advanceUntilIdle()

            val state = model.state.value
            assertTrue(state.biometricInvalidatedNotice, "le bandeau explique ce qui s'est passé")
            assertFalse(state.canRetryBiometric, "il n'y a plus rien à relancer")
            assertTrue(store.entries.isEmpty(), "un scellé illisible ne doit pas rester")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Le lien de récupération arrive pendant que le verrou est affiché.
     *
     * Sans la garde, il remplacerait la session en attente — éventuellement par
     * celle d'un autre compte — avant qu'un doigt ait touché le capteur.
     */
    @Test
    fun `un lien recu derriere le verrou n'est pas consomme`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = FakeAuth(session)
            val model = viewModel(auth, MemoryEnrollment(mapOf("user-1" to scelle)))
            advanceUntilIdle()

            model.completeAuthCallback("code-en-vol")
            advanceUntilIdle()

            assertEquals(0, auth.exchanges, "l'échange PKCE ne doit pas partir")
            assertTrue(model.state.value.isAwaitingBiometricUnlock, "le verrou tient toujours")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `se deconnecter emporte le verrou`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle), offered = setOf("user-1"))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            model.signOut()
            advanceUntilIdle()

            assertTrue(store.entries.isEmpty(), "une empreinte ne rouvre pas une session fermée")
            // Mais le souvenir de la proposition reste : on ne redemande pas.
            assertTrue(store.offeredTo.contains("user-1"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `supprimer son compte emporte le verrou`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            model.deleteAccount()
            advanceUntilIdle()

            // Ce chemin ne passe ni par `signOut` ni par `denyAccess` : sans
            // ligne dédiée, le verrou survivrait au compte qu'il gardait.
            assertTrue(store.entries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * L'habilitation se vérifie **derrière** le verrou, pas devant : le doigt
     * d'abord, la question au serveur ensuite. Un compte à qui l'on vient de
     * retirer ses droits déverrouille donc bien son téléphone — et ressort
     * aussitôt, verrou effacé au passage.
     */
    @Test
    fun `un refus d'habilitation emporte le verrou`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(mapOf("user-1" to scelle))
            // Le serveur répond et dit non : compte voyageur, aucune fiche.
            val model = AuthViewModel(
                auth = FakeAuth(session, staffRole = null),
                profiles = FakeProfiles(profile = null),
                logger = NoopLogger,
                accessCache = MemoryAccess(),
                biometricEnrollment = store,
                biometricSupport = FakeSupport(),
            )
            advanceUntilIdle()
            assertTrue(model.state.value.isAwaitingBiometricUnlock, "le verrou passe en premier")

            model.onBiometricUnlockSucceeded()
            advanceUntilIdle()

            assertFalse(model.state.value.isSignedIn)
            assertEquals(AuthFailureKind.NO_HABILITATION, model.state.value.failure)
            assertTrue(store.entries.isEmpty(), "plus de droits, plus de verrou")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la proposition se fait une fois, et sur un appareil capable`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment()
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            assertTrue(model.state.value.showBiometricProposal)

            model.onBiometricProposalDone()
            advanceUntilIdle()

            assertFalse(model.state.value.showBiometricProposal)
            assertTrue(store.offeredTo.contains("user-1"), "on ne repropose pas")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `rien n'est propose deux fois`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = MemoryEnrollment(offered = setOf("user-1"))
            val model = viewModel(FakeAuth(session), store)
            advanceUntilIdle()

            assertFalse(model.state.value.showBiometricProposal)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un appareil sans capteur ne propose rien`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(
                FakeAuth(session),
                MemoryEnrollment(),
                support = FakeSupport(BiometricAvailability.NO_HARDWARE),
            )
            advanceUntilIdle()

            assertFalse(
                model.state.value.showBiometricProposal,
                "annoncer une fonction que l'appareil ne peut pas rendre",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Capteur présent, rien d'enrôlé : on propose quand même. C'est le chemin
     * « ouvrir les paramètres », pas une erreur — refuser ici priverait de la
     * fonction quelqu'un qui n'a jamais posé son doigt sur un téléphone neuf.
     */
    @Test
    fun `un capteur sans empreinte enrolee se propose quand meme`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(
                FakeAuth(session),
                MemoryEnrollment(),
                support = FakeSupport(BiometricAvailability.NONE_ENROLLED),
            )
            advanceUntilIdle()

            assertTrue(model.state.value.showBiometricProposal)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans depot cable l'ecran se comporte comme avant`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = viewModel(FakeAuth(session), enrollment = null, support = null)
            advanceUntilIdle()

            val state = model.state.value
            assertTrue(state.isSignedIn)
            assertFalse(state.isAwaitingBiometricUnlock)
            assertFalse(state.showBiometricProposal)
            assertNull(state.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class MemoryEnrollment(
        initial: Map<String, BiometricEnrollment> = emptyMap(),
        offered: Set<String> = emptySet(),
    ) : BiometricEnrollmentStore {
        val entries = initial.toMutableMap()
        val offeredTo = offered.toMutableSet()

        override suspend fun read(userId: String) = entries[userId]

        override suspend fun write(userId: String, enrollment: BiometricEnrollment) {
            entries[userId] = enrollment
        }

        /** Comme le vrai dépôt : n'emporte **pas** le souvenir de la proposition. */
        override suspend fun clear() = entries.clear()

        override suspend fun hasBeenOffered(userId: String) = userId in offeredTo

        override suspend fun markOffered(userId: String) {
            offeredTo += userId
        }
    }

    private class FakeSupport(
        private val state: BiometricAvailability = BiometricAvailability.READY,
    ) : BiometricSupport {
        override fun availability() = state
        override fun detectedType() = BiometricType.GENERIC
    }

    private class MemoryAccess : AgentAccessStore {
        val entries = mutableMapOf<String, AgentAccess>()
        override suspend fun read(userId: String) = entries[userId]
        override suspend fun write(userId: String, access: AgentAccess) {
            entries[userId] = access
        }
        override suspend fun clear() = entries.clear()
    }

    private class FakeAuth(
        private val stored: AuthSession?,
        private val staffRole: String? = "driver",
    ) : AuthRepository {
        var exchanges = 0
            private set

        override fun currentSession() = stored
        override suspend fun restore() = stored
        override suspend fun fetchStaffRole(session: AuthSession) = staffRole
        override suspend fun signOut() = Unit
        override suspend fun deleteAccount() = Unit
        override suspend fun exchangeAuthCode(code: String): AuthSession {
            exchanges += 1
            return stored ?: error("aucune session à rendre")
        }
        override suspend fun pendingAuthFlow() = null
        override suspend fun signIn(email: String, password: String) = error("non sollicité")
        override suspend fun signUpProfessional(
            draft: ProRegistrationDraft,
            password: String,
        ) = error("non sollicité")
        override suspend fun resendSignupConfirmation(email: String) = error("non sollicité")
        override suspend fun sendPasswordRecovery(email: String) = error("non sollicité")
        override suspend fun updatePassword(newPassword: String) = error("non sollicité")
    }

    private class FakeProfiles(
        private val profile: DriverProfile?,
    ) : DriverProfileRepository {
        override suspend fun fetchProfile(session: AuthSession) = profile
        override suspend fun fetchDepots(session: AuthSession) = emptyList<Depot>()
        override suspend fun fetchNetworks(session: AuthSession) = emptyList<TransportNetwork>()
        override suspend fun updateProfile(
            session: AuthSession,
            driverId: String,
            update: DriverProfileUpdate,
        ) = error("non sollicité")
        override suspend fun uploadAvatar(
            session: AuthSession,
            driverId: String,
            bytes: ByteArray,
            contentType: String,
            extension: String,
        ) = error("non sollicité")
        override suspend fun removeAvatar(
            session: AuthSession,
            driverId: String,
        ) = error("non sollicité")
        override suspend fun fetchAvatarImage(url: String): ByteArray? = null
    }
}
