package io.aule.android.feature.map

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.location.LocationFix
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.HandoverStatus
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ProRegistrationDraft
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.repository.AuthRepository
import io.aule.android.core.model.repository.DriverServiceRepository
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
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
class ServiceViewModelTest {

    @Test
    fun `le premier fix se publie`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(active = ACTIVE)
            val clock = mutableListOf(NOW)
            val viewModel = viewModel(services, now = { clock.last() })
            advanceUntilIdle()
            viewModel.onLocationFix(fix(NOW))
            advanceUntilIdle()
            assertEquals(1, services.published.size)
            assertEquals("svc-1", services.published[0].driverServiceId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une position simulee ne se publie pas`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(active = ACTIVE)
            val viewModel = viewModel(services)
            advanceUntilIdle()
            viewModel.onLocationFix(fix(NOW, mocked = true))
            advanceUntilIdle()
            assertTrue(services.published.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la releve s annonce une fois`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(
                active = ACTIVE,
                heartbeat = BEAT.copy(handover = HANDOVER),
            )
            val clock = mutableListOf(NOW)
            val viewModel = viewModel(services, now = { clock.last() })
            advanceUntilIdle()
            viewModel.onLocationFix(fix(clock.last()))
            advanceUntilIdle()
            assertEquals("hov-1", viewModel.state.value.liveHandover?.id)

            clock += NOW.plus(Duration.ofSeconds(5))
            viewModel.onLocationFix(fix(clock.last()))
            advanceUntilIdle()
            assertEquals(2, services.published.size)
            assertEquals("hov-1", viewModel.state.value.liveHandover?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un vehicule remis ne passe pas par endService`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(
                active = ACTIVE,
                heartbeat = ServiceHeartbeat(
                    serviceStatus = "ended",
                    published = false,
                    serverTime = NOW,
                    handover = HANDOVER.copy(status = HandoverStatus.COMPLETED),
                ),
            )
            val viewModel = viewModel(services)
            advanceUntilIdle()
            viewModel.onLocationFix(fix(NOW))
            advanceUntilIdle()
            assertNull(viewModel.state.value.active)
            assertEquals(ServiceNoticeKind.HANDED_OVER, viewModel.state.value.notice?.kind)
            assertEquals("Camille", viewModel.state.value.notice?.handover?.incomingDisplay)
            assertEquals(0, services.ended)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        services: FakeServices,
        now: () -> Instant = { NOW },
    ) = ServiceViewModel(
        auth = FakeAuth(SESSION),
        services = services,
        logger = NoopLogger,
        now = now,
    )

    private fun fix(at: Instant, mocked: Boolean = false) = LocationFix(
        coordinate = Coordinate(47.21, -1.55),
        accuracyMeters = 6.0,
        courseDegrees = 90.0,
        speedMetersPerSecond = 8.0,
        timestampMillis = at.toEpochMilli(),
        stabilizedHeading = 90.0,
        isHeadingFrozen = false,
        isMocked = mocked,
    )

    private class FakeAuth(private val stored: AuthSession?) : AuthRepository {
        override fun currentSession() = stored
        override suspend fun restore() = stored
        override suspend fun signIn(email: String, password: String) = error("unused")
        override suspend fun signOut() = Unit
        override suspend fun fetchStaffRole(session: AuthSession) = null
        override suspend fun signUpProfessional(draft: ProRegistrationDraft, password: String) =
            error("unused")
        override suspend fun resendSignupConfirmation(email: String) = error("unused")
        override suspend fun sendPasswordRecovery(email: String) = error("unused")
        override suspend fun updatePassword(newPassword: String) = error("unused")
        override suspend fun pendingAuthFlow() = null
        override suspend fun exchangeAuthCode(code: String) = error("unused")
        override suspend fun deleteAccount() = error("unused")
    }

    private class FakeServices(
        private val active: ActiveDriverService? = null,
        private val heartbeat: ServiceHeartbeat = BEAT,
    ) : DriverServiceRepository {
        val published = mutableListOf<PositionPublishRequest>()
        var ended = 0

        override suspend fun fetchLines(session: AuthSession): List<ServiceLine> = emptyList()
        override suspend fun fetchJourney(
            session: AuthSession,
            lineId: String,
            directionId: Int,
        ) = error("unused")

        override suspend fun nearestActiveTrip(
            session: AuthSession,
            lineId: String,
            directionId: Int,
            destinationHint: String?,
            near: io.aule.android.core.geo.Coordinate,
            at: java.time.Instant,
        ) = null
        override suspend fun fetchActiveService(session: AuthSession) = active
        override suspend fun startService(session: AuthSession, request: ServiceStartRequest) =
            error("unused")
        override suspend fun endService(session: AuthSession, serviceId: String) {
            ended++
        }
        override suspend fun publishPosition(
            session: AuthSession,
            request: PositionPublishRequest,
        ): ServiceHeartbeat {
            published += request
            return heartbeat
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T16:00:00Z")
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )
        val ACTIVE = ActiveDriverService(
            id = "svc-1",
            lineId = "C6",
            lineLabel = "C6",
            directionId = 0,
            terminus = "Hermeland",
            startedAt = NOW,
            vehicleId = "324",
        )
        val HANDOVER = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.ENGAGED,
            lineId = "C6",
            outgoingServiceId = "svc-1",
            incomingDisplay = "Camille",
            reliefStopName = "Commerce",
        )
        val BEAT = ServiceHeartbeat(
            serviceStatus = "active",
            published = true,
            serverTime = NOW,
        )
    }
}
