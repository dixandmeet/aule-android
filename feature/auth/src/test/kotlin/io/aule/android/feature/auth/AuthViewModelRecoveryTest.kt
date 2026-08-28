package io.aule.android.feature.auth

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthPkceFlow
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.TransportNetwork
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.DriverProfileRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

/**
 * Le mot de passe oublié, côté écran.
 *
 * Le test qui compte est `un lien de recuperation n ouvre pas l application` :
 * c'est la garde qui empêche d'entrer par la boîte e-mail, et elle ne se voit
 * nulle part à l'exécution — un lien de récupération ouvre une vraie session,
 * et rien ne distingue les deux sans le genre PKCE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelRecoveryTest {

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

    private fun viewModel(auth: AuthRepository) = AuthViewModel(
        auth = auth,
        profiles = FakeProfiles(profile),
        logger = NoopLogger,
    )

    /**
     * Le décor de chaque cas : un `Main` piloté par l'ordonnanceur du test, pour
     * que `viewModelScope` avance à l'appel d'`advanceUntilIdle` et pas avant.
     */
    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `l envoi du lien ne promet rien de plus que l adresse saisie`() = withMain {
        val auth = FakeAuth()
        val model = viewModel(auth)
        advanceUntilIdle()

        model.sendPasswordRecovery("  Agent@Aule.FR ")
        advanceUntilIdle()

        assertEquals("agent@aule.fr", auth.recoveryRequests.single())
        assertEquals("agent@aule.fr", model.state.value.recoverySentTo)
        assertNull(model.state.value.failure)
        assertFalse(model.state.value.isSubmitting)
    }

    @Test
    fun `une cadence depassee s affiche au lieu de l accuse`() = withMain {
        val auth = FakeAuth(recoveryFailure = AuthFailureKind.RATE_LIMITED)
        val model = viewModel(auth)
        advanceUntilIdle()

        model.sendPasswordRecovery("agent@aule.fr")
        advanceUntilIdle()

        assertEquals(AuthFailureKind.RATE_LIMITED, model.state.value.failure)
        assertNull(model.state.value.recoverySentTo)
    }

    @Test
    fun `un lien de recuperation n ouvre pas l application`() = withMain {
        val auth = FakeAuth(pendingFlow = AuthPkceFlow.RECOVERY, exchanged = session)
        val model = viewModel(auth)
        advanceUntilIdle()

        model.completeAuthCallback("code-de-recuperation")
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.isSignedIn, "la session du lien existe bel et bien")
        assertTrue(state.isResettingPassword, "mais elle n'ouvre que le nouveau mot de passe")
        assertFalse(state.isCheckingAccess)
        // Les habilitations ne sont pas résolues : rien ne doit se charger tant
        // que le mot de passe n'a pas été changé.
        assertNull(state.access)
        assertNull(state.profile)
    }

    @Test
    fun `une confirmation d inscription entre normalement`() = withMain {
        val auth = FakeAuth(
            pendingFlow = AuthPkceFlow.SIGN_UP,
            exchanged = session,
            staffRole = "driver",
        )
        val model = viewModel(auth)
        advanceUntilIdle()

        model.completeAuthCallback("code-de-confirmation")
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.isSignedIn)
        assertFalse(state.isResettingPassword)
        assertFalse(state.isCheckingAccess)
        assertEquals("Kevin Getbu", state.profile?.displayName())
    }

    @Test
    fun `le nouveau mot de passe enregistre puis entre dans l application`() = withMain {
        val auth = FakeAuth(
            pendingFlow = AuthPkceFlow.RECOVERY,
            exchanged = session,
            staffRole = "driver",
        )
        val model = viewModel(auth)
        advanceUntilIdle()
        model.completeAuthCallback("code-de-recuperation")
        advanceUntilIdle()

        model.updatePassword("un-mot-de-passe-neuf")
        advanceUntilIdle()

        assertEquals("un-mot-de-passe-neuf", auth.passwords.single())
        val state = model.state.value
        assertFalse(state.isResettingPassword, "la session redevient ordinaire")
        assertTrue(state.isSignedIn)
        assertEquals("Kevin Getbu", state.profile?.displayName())
    }

    @Test
    fun `un mot de passe refuse laisse l ecran ouvert`() = withMain {
        val auth = FakeAuth(
            pendingFlow = AuthPkceFlow.RECOVERY,
            exchanged = session,
            passwordFailure = AuthFailureKind.WEAK_PASSWORD,
        )
        val model = viewModel(auth)
        advanceUntilIdle()
        model.completeAuthCallback("code-de-recuperation")
        advanceUntilIdle()

        model.updatePassword("motdepasse")
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(AuthFailureKind.WEAK_PASSWORD, state.failure)
        assertTrue(state.isResettingPassword, "on reste sur l'écran pour réessayer")
        assertFalse(state.isSubmitting)
    }

    @Test
    fun `annuler depuis la recuperation ferme la session`() = withMain {
        val auth = FakeAuth(pendingFlow = AuthPkceFlow.RECOVERY, exchanged = session)
        val model = viewModel(auth)
        advanceUntilIdle()
        model.completeAuthCallback("code-de-recuperation")
        advanceUntilIdle()

        model.signOut()
        advanceUntilIdle()

        val state = model.state.value
        assertFalse(state.isSignedIn)
        assertFalse(state.isResettingPassword)
        assertEquals(1, auth.signOuts)
    }

    private class FakeAuth(
        private val pendingFlow: AuthPkceFlow? = null,
        private val exchanged: AuthSession? = null,
        private val staffRole: String? = null,
        private val recoveryFailure: AuthFailureKind? = null,
        private val passwordFailure: AuthFailureKind? = null,
    ) : AuthRepository {
        val recoveryRequests = mutableListOf<String>()
        val passwords = mutableListOf<String>()
        var signOuts = 0
        private var current: AuthSession? = null

        override fun currentSession() = current
        override suspend fun restore() = null
        override suspend fun signIn(email: String, password: String) = error("non sollicité")
        override suspend fun signOut() {
            signOuts++
            current = null
        }
        override suspend fun fetchStaffRole(session: AuthSession) = staffRole
        override suspend fun signUpProfessional(
            draft: ProRegistrationDraft,
            password: String,
        ) = error("non sollicité")
        override suspend fun resendSignupConfirmation(email: String) = error("non sollicité")
        override suspend fun sendPasswordRecovery(email: String) {
            recoveryFailure?.let { throw AuthException(it) }
            recoveryRequests += email
        }
        override suspend fun updatePassword(newPassword: String) {
            passwordFailure?.let { throw AuthException(it) }
            passwords += newPassword
        }
        override suspend fun pendingAuthFlow() = pendingFlow
        override suspend fun exchangeAuthCode(code: String): AuthSession {
            val opened = exchanged ?: error("non sollicité")
            current = opened
            return opened
        }
        override suspend fun deleteAccount() = error("non sollicité")
    }

    private class FakeProfiles(private val profile: DriverProfile?) : DriverProfileRepository {
        override suspend fun fetchProfile(session: AuthSession) = profile
        override suspend fun fetchDepots(session: AuthSession) = listOf(
            Depot("depot-blx", "BLX", "Dépôt Haluchère", "net-nan"),
        )
        override suspend fun fetchNetworks(session: AuthSession) = listOf(
            TransportNetwork("net-nan", "NAN", "Nantes"),
        )
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
        override suspend fun removeAvatar(session: AuthSession, driverId: String) =
            error("non sollicité")
        override suspend fun fetchAvatarImage(url: String): ByteArray? = null
    }
}
