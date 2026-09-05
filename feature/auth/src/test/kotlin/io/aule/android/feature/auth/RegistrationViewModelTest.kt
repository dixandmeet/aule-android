package io.aule.android.feature.auth

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AuthException
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.OAuthProvider
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.RegistrationDraftStore
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationViewModelTest {

    @Test
    fun `hydrate un brouillon persiste et ignore un mot de passe absent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val stored = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONTROLEUR),
                networkKey = "naolib",
                fullName = "Sam Dupont",
                employeeId = "MSR21",
                email = "sam@example.com",
                termsAccepted = true,
            )
            val drafts = MemoryDrafts(stored.encode(), "account")
            val viewModel = RegistrationViewModel(
                auth = FakeSignupAuth(),
                drafts = drafts,
                logger = NoopLogger,
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isHydrated)
            assertEquals(RegistrationStep.ACCOUNT, state.step)
            assertEquals(setOf(ProfessionalProfile.CONTROLEUR), state.draft.profiles)
            assertEquals("sam@example.com", state.draft.email)
            assertTrue(state.password.isEmpty())
            assertFalse("password" in drafts.draft.orEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une inscription reussie ouvre la confirmation sans conserver le mot de passe`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val stored = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONDUCTEUR),
                networkKey = "naolib",
                fullName = "Camille Martin",
                employeeId = "48271",
                transportMode = ProfessionalTransportMode.BUS,
                email = "camille@example.com",
                termsAccepted = true,
            )
            val drafts = MemoryDrafts(stored.encode(), "account")
            val auth = FakeSignupAuth()
            val viewModel = RegistrationViewModel(
                auth = auth,
                drafts = drafts,
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.setPassword("secretsecret")
            viewModel.setConfirmPassword("secretsecret")
            viewModel.continueForward()
            advanceUntilIdle()

            assertEquals(1, auth.signups)
            assertEquals("secretsecret", auth.lastPassword)
            assertEquals(RegistrationStep.CONFIRMATION, viewModel.state.value.step)
            assertTrue(viewModel.state.value.password.isEmpty())
            assertEquals("confirmation", drafts.step)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un refus GoTrue reste sur le compte et affiche l erreur`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val stored = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONTROLEUR),
                networkKey = "naolib",
                fullName = "Sam Dupont",
                employeeId = "MSR21",
                email = "sam@example.com",
                termsAccepted = true,
            )
            val viewModel = RegistrationViewModel(
                auth = FakeSignupAuth(failKind = AuthFailureKind.USER_ALREADY_EXISTS),
                drafts = MemoryDrafts(stored.encode(), "account"),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            viewModel.setPassword("secretsecret")
            viewModel.setConfirmPassword("secretsecret")
            viewModel.continueForward()
            advanceUntilIdle()

            assertEquals(RegistrationStep.ACCOUNT, viewModel.state.value.step)
            assertEquals(AuthFailureKind.USER_ALREADY_EXISTS, viewModel.state.value.failure)
            assertEquals("secretsecret", viewModel.state.value.password)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `Google n exige ni e-mail ni mot de passe, mais les conditions`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            // Un brouillon professionnel complet, sans adresse ni conditions :
            // c'est exactement ce qu'on a sous les yeux en arrivant à l'étape 5
            // quand on compte s'inscrire avec son compte Google.
            val stored = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONTROLEUR),
                networkKey = "naolib",
                fullName = "Sam Dupont",
                employeeId = "MSR21",
            )
            val auth = FakeSignupAuth()
            val viewModel = RegistrationViewModel(
                auth = auth,
                drafts = MemoryDrafts(stored.encode(), "account"),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.startOAuthSignUp(OAuthProvider.GOOGLE)
            advanceUntilIdle()
            assertEquals(0, auth.oauthStarts)
            assertTrue(viewModel.state.value.missingTerms)
            assertNull(viewModel.state.value.oauthUrl)

            viewModel.toggleTerms()
            viewModel.startOAuthSignUp(OAuthProvider.GOOGLE)
            advanceUntilIdle()
            assertEquals(1, auth.oauthStarts)
            assertFalse(viewModel.state.value.missingTerms)
            assertEquals(
                "https://auth.test/authorize?provider=google",
                viewModel.state.value.oauthUrl,
            )
            // L'adresse est un événement, pas un état : elle disparaît une fois
            // ouverte, sans quoi une recomposition rouvrirait l'onglet.
            viewModel.consumeOAuthUrl()
            assertNull(viewModel.state.value.oauthUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le brouillon est ecrit avant le depart vers le fournisseur`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val drafts = MemoryDrafts()
            val viewModel = RegistrationViewModel(
                auth = FakeSignupAuth(),
                drafts = drafts,
                logger = NoopLogger,
            )
            advanceUntilIdle()
            viewModel.toggleProfile(ProfessionalProfile.CONTROLEUR)
            viewModel.selectNetwork("naolib")
            viewModel.setFullName("Sam Dupont")
            viewModel.setEmployeeId("MSR21")
            viewModel.toggleTerms()
            drafts.draft = null

            viewModel.startOAuthSignUp(OAuthProvider.GOOGLE)
            advanceUntilIdle()

            // Le retour de Google peut arriver dans un processus neuf : le
            // brouillon doit être sur le disque avant que l'onglet s'ouvre, pas
            // à la prochaine frappe.
            val written = drafts.draft
            assertTrue(written != null && "MSR21" in written)
            assertTrue("termsAccepted\":true" in written)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un refus du fournisseur laisse l ecran sur le compte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val stored = ProRegistrationDraft(
                profiles = setOf(ProfessionalProfile.CONTROLEUR),
                networkKey = "naolib",
                fullName = "Sam Dupont",
                employeeId = "MSR21",
                termsAccepted = true,
            )
            val viewModel = RegistrationViewModel(
                auth = FakeSignupAuth(failKind = AuthFailureKind.NOT_CONFIGURED),
                drafts = MemoryDrafts(stored.encode(), "account"),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.startOAuthSignUp(OAuthProvider.GOOGLE)
            advanceUntilIdle()

            assertEquals(RegistrationStep.ACCOUNT, viewModel.state.value.step)
            assertEquals(AuthFailureKind.NOT_CONFIGURED, viewModel.state.value.failure)
            assertNull(viewModel.state.value.oauthUrl)
            assertFalse(viewModel.state.value.isSubmitting)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le mode de conduite n entre dans le flux que pour un conducteur`() {
        assertTrue(
            RegistrationStep.TRANSPORT_MODE in flowFor(
                ProRegistrationDraft(profiles = setOf(ProfessionalProfile.CONDUCTEUR)),
            ),
        )
        assertFalse(
            RegistrationStep.TRANSPORT_MODE in flowFor(
                ProRegistrationDraft(profiles = setOf(ProfessionalProfile.CONTROLEUR)),
            ),
        )
    }

    private class MemoryDrafts(
        var draft: String? = null,
        var step: String? = null,
    ) : RegistrationDraftStore {
        override suspend fun readDraft() = draft
        override suspend fun readStep() = step
        override suspend fun write(draftJson: String, step: String) {
            draft = draftJson
            this.step = step
        }
        override suspend fun clear() {
            draft = null
            step = null
        }
    }

    private class FakeSignupAuth(
        private val failKind: AuthFailureKind? = null,
    ) : AuthRepository {
        var signups = 0
        var oauthStarts = 0
        var lastPassword: String? = null
        override fun currentSession() = null
        override suspend fun restore() = null
        override suspend fun signIn(email: String, password: String) = error("non sollicité")
        override suspend fun signOut() = Unit
        override suspend fun fetchStaffRole(session: AuthSession) = null
        override suspend fun signUpProfessional(draft: ProRegistrationDraft, password: String) {
            signups++
            lastPassword = password
            if (failKind != null) throw AuthException(failKind)
        }
        override suspend fun resendSignupConfirmation(email: String) = Unit
        override suspend fun beginOAuthSignUp(provider: OAuthProvider): String {
            oauthStarts++
            if (failKind != null) throw AuthException(failKind)
            return "https://auth.test/authorize?provider=${provider.key}"
        }
        override suspend fun sendPasswordRecovery(email: String) = error("non sollicité")
        override suspend fun updatePassword(newPassword: String) = error("non sollicité")
        override suspend fun pendingAuthFlow() = null
        override suspend fun exchangeAuthCode(code: String) = AuthSession(
            user = AuthUser("u", "a@b.fr"),
            accessToken = "a",
            refreshToken = "r",
            expiresAtEpochSeconds = 9_999,
        )
        override suspend fun deleteAccount() = error("non sollicité")
    }
}
