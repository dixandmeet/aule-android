package io.aule.android.feature.auth

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AgentRole
import io.aule.android.core.model.AuthException
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

/**
 * Entrer dans Aule Pro quand on ne peut pas vérifier qu'on en a le droit.
 *
 * Le défaut relevé en recette : un conducteur garé en sous-sol relançait
 * l'application et se retrouvait à l'écran de connexion — un écran qui, sans
 * réseau, ne peut pas aboutir. La cause n'était pas la session, elle était
 * ici : toute lecture d'habilitation en échec fermait la session, sans
 * distinguer « le serveur dit non » de « je n'ai pas pu demander ».
 *
 * La règle tient en trois lignes, et les trois sont testées :
 *
 * - injoignable **et** un « oui » déjà reçu sur cet appareil → on ouvre ;
 * - injoignable **et** rien en réserve → on reste dehors ;
 * - refusé → on ferme, et la réserve part avec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelOfflineAccessTest {

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

    @Test
    fun `une habilitation injoignable ouvre sur la derniere connue`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val cache = MemoryAccess(
                mapOf("user-1" to AgentAccess(AccountModes.CONDUCTEUR, AgentRole.CONDUCTEUR)),
            )
            val viewModel = AuthViewModel(
                auth = OfflineAuth(session),
                profiles = OfflineProfiles(),
                logger = NoopLogger,
                accessCache = cache,
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isSignedIn, "la session reste ouverte")
            assertFalse(state.isCheckingAccess)
            assertNull(state.failure, "aucun bandeau de refus : rien n'a été refusé")
            assertEquals(AccountModes.CONDUCTEUR, state.access?.modes)
            // La fiche agent se lit sur le même réseau absent : le menu
            // retombe sur l'adresse de session, comme pour un compte sans
            // fiche `drivers`.
            assertTrue(state.profileFailed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans habilitation en reserve, la porte reste fermee`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = AuthViewModel(
                auth = OfflineAuth(session),
                profiles = OfflineProfiles(),
                logger = NoopLogger,
                accessCache = MemoryAccess(),
            )
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isSignedIn, "un compte jamais vérifié ici n'entre pas")
            assertEquals(AuthFailureKind.HABILITATION_UNVERIFIED, state.failure)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Le serveur a répondu, et il a dit non : la réserve ne doit pas laisser
     * entrer demain quelqu'un à qui on vient de retirer ses droits.
     */
    @Test
    fun `un refus explicite efface la reserve`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val cache = MemoryAccess(
                mapOf("user-1" to AgentAccess(AccountModes.CONDUCTEUR, AgentRole.CONDUCTEUR)),
            )
            val viewModel = AuthViewModel(
                // Le serveur répond, sans rôle ; aucune fiche `drivers` : le
                // compte est un voyageur.
                auth = OfflineAuth(session, staffRole = null, unreachable = false),
                profiles = OfflineProfiles(profile = null, fails = false),
                logger = NoopLogger,
                accessCache = cache,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertEquals(AuthFailureKind.NO_HABILITATION, viewModel.state.value.failure)
            assertTrue(cache.entries.isEmpty(), "la réserve part avec le refus")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * L'aller-retour complet : une vérification réussie constitue la réserve
     * qu'un démarrage hors ligne utilisera. Sans cette écriture, le premier
     * sous-sol trouverait la réserve vide et refermerait la porte.
     */
    @Test
    fun `une verification reussie constitue la reserve`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val cache = MemoryAccess()
            val viewModel = AuthViewModel(
                auth = OfflineAuth(session, staffRole = "driver", unreachable = false),
                profiles = OfflineProfiles(profile = profile, fails = false),
                logger = NoopLogger,
                accessCache = cache,
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isSignedIn)
            assertEquals(
                AgentAccess(AccountModes.CONDUCTEUR, AgentRole.CONDUCTEUR),
                cache.entries["user-1"],
            )

            // Et se déconnecter rend la réserve : le compte suivant sur ce
            // téléphone n'hérite pas des droits du précédent.
            viewModel.signOut()
            advanceUntilIdle()
            assertTrue(cache.entries.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class MemoryAccess(
        initial: Map<String, AgentAccess> = emptyMap(),
    ) : AgentAccessStore {
        val entries = initial.toMutableMap()
        override suspend fun read(userId: String) = entries[userId]
        override suspend fun write(userId: String, access: AgentAccess) {
            entries[userId] = access
        }
        override suspend fun clear() = entries.clear()
    }

    /** Un dépôt en sous-sol : la session tient, la vérification ne passe pas. */
    private class OfflineAuth(
        private val stored: AuthSession?,
        private val staffRole: String? = null,
        private val unreachable: Boolean = true,
    ) : AuthRepository {
        override fun currentSession() = stored
        override suspend fun restore() = stored
        override suspend fun signIn(email: String, password: String) = error("non sollicité")
        override suspend fun signOut() = Unit
        override suspend fun signUpProfessional(
            draft: ProRegistrationDraft,
            password: String,
        ) = error("non sollicité")
        override suspend fun resendSignupConfirmation(email: String) = error("non sollicité")
        override suspend fun sendPasswordRecovery(email: String) = error("non sollicité")
        override suspend fun updatePassword(newPassword: String) = error("non sollicité")
        override suspend fun exchangeAuthCode(code: String) = error("non sollicité")
        override suspend fun pendingAuthFlow() = null
        override suspend fun deleteAccount() = error("non sollicité")
        override suspend fun fetchStaffRole(session: AuthSession): String? {
            if (unreachable) throw AuthException(AuthFailureKind.NETWORK, "transport coupé")
            return staffRole
        }
    }

    private class OfflineProfiles(
        private val profile: DriverProfile? = null,
        private val fails: Boolean = true,
    ) : DriverProfileRepository {
        override suspend fun fetchProfile(session: AuthSession): DriverProfile? {
            if (fails) error("transport coupé")
            return profile
        }
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
