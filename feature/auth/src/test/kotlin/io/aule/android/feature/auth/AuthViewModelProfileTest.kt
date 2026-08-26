package io.aule.android.feature.auth

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.AvatarException
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.DriverProfileUpdate
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelProfileTest {

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

    private val depot = Depot("depot-blx", "BLX", "Dépôt Haluchère", "net-nan")
    private val network = TransportNetwork("net-nan", "NAN", "Nantes")

    @Test
    fun `la restauration charge la fiche et le depot`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = FakeProfiles(profile, listOf(depot), listOf(network)),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertEquals("Kevin Getbu", viewModel.state.value.profile?.displayName())
            assertEquals("BLX · Dépôt Haluchère", viewModel.state.value.depot?.label)
            assertEquals("Nantes (NAN)", viewModel.state.value.network?.label)
            assertFalse(viewModel.state.value.profileFailed)
            assertEquals(listOf(depot), viewModel.state.value.depots)
            assertEquals(AccountModes.CONDUCTEUR, viewModel.state.value.access?.modes)
            assertFalse(viewModel.state.value.isCheckingAccess)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la restauration charge les octets de la photo`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = FakeProfiles(
                    profile.copy(avatarUrl = "https://example.invalid/avatar.jpg"),
                    listOf(depot),
                    listOf(network),
                    avatarBytes = jpeg,
                ),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertEquals(jpeg.toList(), viewModel.state.value.avatarBytes?.toList())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `enregistrer met a jour la fiche sans deconnecter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val profiles = FakeProfiles(profile, listOf(depot), listOf(network))
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = profiles,
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.saveProfile(
                DriverProfileUpdate(firstName = "Camille", lastName = "Martin"),
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertEquals("Camille", viewModel.state.value.profile?.firstName)
            assertEquals("Martin", viewModel.state.value.profile?.lastName)
            assertFalse(viewModel.state.value.profileSaveFailed)
            assertEquals(1, profiles.updates)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une fiche illisible ne deconnecte pas si un role staff ouvre`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session, staffRole = "driver"),
                profiles = FakeProfiles(fail = true),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertTrue(viewModel.state.value.profileFailed)
            assertNull(viewModel.state.value.profile)
            assertEquals("agent@aule.fr", viewModel.state.value.email)
            assertFalse(viewModel.state.value.isCheckingAccess)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une fiche illisible sans role staff deconnecte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = FakeProfiles(fail = true),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertEquals(AuthFailureKind.HABILITATION_UNVERIFIED, viewModel.state.value.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un voyageur sans habilitation est deconnecte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session, staffRole = "passenger"),
                profiles = FakeProfiles(),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertEquals(AuthFailureKind.NO_HABILITATION, viewModel.state.value.failure)
            assertFalse(viewModel.state.value.isCheckingAccess)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un role staff illisible deconnecte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session, failStaffRole = true),
                profiles = FakeProfiles(profile, listOf(depot), listOf(network)),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertEquals(AuthFailureKind.HABILITATION_UNVERIFIED, viewModel.state.value.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un envoi de photo met a jour la fiche sans deconnecter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val profiles = FakeProfiles(profile, listOf(depot), listOf(network))
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = profiles,
                logger = NoopLogger,
            )
            advanceUntilIdle()

            val jpeg = byteArrayOf(1, 2, 3, 4)
            viewModel.uploadAvatar(jpeg)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertEquals(jpeg.toList(), viewModel.state.value.avatarBytes?.toList())
            assertEquals("https://cdn.example/avatar.jpg", viewModel.state.value.profile?.avatarUrl)
            assertNull(viewModel.state.value.avatarFailure)
            assertEquals(1, profiles.uploads)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un envoi refuse ne deconnecte pas`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = FakeProfiles(profile, failAvatar = true),
                logger = NoopLogger,
            )
            advanceUntilIdle()

            viewModel.uploadAvatar(byteArrayOf(1, 2, 3))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertEquals(AvatarFailureKind.DENIED, viewModel.state.value.avatarFailure)
            assertNull(viewModel.state.value.avatarBytes)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un retrait vide la photo sans deconnecter`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val jpeg = byteArrayOf(9, 8, 7)
            val viewModel = AuthViewModel(
                auth = FakeAuth(session),
                profiles = FakeProfiles(
                    profile.copy(avatarUrl = "https://cdn.example/old.jpg"),
                    avatarBytes = jpeg,
                ),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            assertEquals(jpeg.toList(), viewModel.state.value.avatarBytes?.toList())

            viewModel.removeAvatar()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertNull(viewModel.state.value.avatarBytes)
            assertNull(viewModel.state.value.profile?.avatarUrl)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la suppression reussie deconnecte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = FakeAuth(session)
            val viewModel = AuthViewModel(
                auth = auth,
                profiles = FakeProfiles(profile, listOf(depot), listOf(network)),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isSignedIn)

            viewModel.deleteAccount()
            advanceUntilIdle()

            assertEquals(1, auth.deleted)
            assertFalse(viewModel.state.value.isSignedIn)
            assertFalse(viewModel.state.value.deleteFailed)
            assertFalse(viewModel.state.value.isDeletingAccount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un echec de suppression laisse la session ouverte`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val auth = FakeAuth(session, failDelete = true)
            val viewModel = AuthViewModel(
                auth = auth,
                profiles = FakeProfiles(profile, listOf(depot), listOf(network)),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isSignedIn)

            viewModel.deleteAccount()
            advanceUntilIdle()

            assertEquals(1, auth.deleted)
            assertTrue(viewModel.state.value.isSignedIn)
            assertTrue(viewModel.state.value.deleteFailed)
            assertFalse(viewModel.state.value.isDeletingAccount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeAuth(
        private val stored: AuthSession?,
        private val staffRole: String? = null,
        private val failStaffRole: Boolean = false,
        private val failDelete: Boolean = false,
    ) : AuthRepository {
        var deleted = 0
        override fun currentSession() = stored
        override suspend fun restore() = stored
        override suspend fun signIn(email: String, password: String) = error("non sollicité")
        override suspend fun signOut() = Unit
        override suspend fun signUpProfessional(
            draft: io.aule.android.core.model.ProRegistrationDraft,
            password: String,
        ) = error("non sollicité")
        override suspend fun resendSignupConfirmation(email: String) = error("non sollicité")
        override suspend fun sendPasswordRecovery(email: String) = error("non sollicité")
        override suspend fun updatePassword(newPassword: String) = error("non sollicité")
        override suspend fun exchangeAuthCode(code: String) = error("non sollicité")
        override suspend fun pendingAuthFlow() = null
        override suspend fun deleteAccount() {
            deleted++
            if (failDelete) error("500")
        }
        override suspend fun fetchStaffRole(session: AuthSession): String? {
            if (failStaffRole) error("502")
            return staffRole
        }
    }

    private class FakeProfiles(
        private val profile: DriverProfile? = null,
        private val depots: List<Depot> = emptyList(),
        private val networks: List<TransportNetwork> = emptyList(),
        private val fail: Boolean = false,
        private val failAvatar: Boolean = false,
        private val avatarBytes: ByteArray? = null,
    ) : DriverProfileRepository {
        var updates = 0
        var uploads = 0
        private var current = profile
        override suspend fun fetchProfile(session: AuthSession): DriverProfile? {
            if (fail) error("502")
            return current
        }
        override suspend fun fetchDepots(session: AuthSession) = depots
        override suspend fun fetchNetworks(session: AuthSession) = networks
        override suspend fun updateProfile(
            session: AuthSession,
            driverId: String,
            update: DriverProfileUpdate,
        ): DriverProfile {
            updates++
            return (current ?: error("pas de fiche")).copy(
                firstName = update.firstName,
                lastName = update.lastName,
                phone = update.phone,
                driverNumber = update.driverNumber,
                depotId = update.depotId,
                networkId = update.networkId,
            ).also { current = it }
        }
        override suspend fun uploadAvatar(
            session: AuthSession,
            driverId: String,
            bytes: ByteArray,
            contentType: String,
            extension: String,
        ): DriverProfile {
            if (failAvatar) throw AvatarException(AvatarFailureKind.DENIED)
            uploads++
            return (current ?: error("pas de fiche"))
                .copy(avatarUrl = "https://cdn.example/avatar.jpg")
                .also { current = it }
        }
        override suspend fun removeAvatar(
            session: AuthSession,
            driverId: String,
        ): DriverProfile {
            if (failAvatar) throw AvatarException(AvatarFailureKind.DENIED)
            return (current ?: error("pas de fiche"))
                .copy(avatarUrl = null)
                .also { current = it }
        }
        override suspend fun fetchAvatarImage(url: String): ByteArray? = avatarBytes
    }
}
