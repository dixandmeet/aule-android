package io.aule.android.feature.map

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.DriverServiceRepository
import java.time.Instant
import java.time.ZoneOffset
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
class PriseServiceViewModelTest {

    private val c6 = ServiceLine(
        id = "C6",
        label = "C6",
        description = "Hermeland - Chantrerie",
        mode = TransportMode.BUS,
        networkId = "net-nan",
        directions = listOf(
            ServiceDirection("0", "Hermeland"),
            ServiceDirection("1", "Chantrerie"),
        ),
    )
    private val foreign = c6.copy(id = "X", label = "X", networkId = "other")

    @Test
    fun `le catalogue se filtre sur le reseau du conducteur`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = PriseServiceViewModel(
                session = SESSION,
                networkId = "net-nan",
                services = FakeServices(listOf(c6, foreign)),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            assertEquals(listOf("C6"), viewModel.state.value.lines.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `choisir une ligne ouvre le sens, choisir un sens ouvre l heure`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = PriseServiceViewModel(
                session = SESSION,
                networkId = "net-nan",
                services = FakeServices(listOf(c6)),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            viewModel.pickLine("C6")
            assertEquals(PriseServiceStep.DIRECTION, viewModel.state.value.step)
            viewModel.pickDirection("0")
            assertEquals(PriseServiceStep.TIME, viewModel.state.value.step)
            assertTrue(viewModel.state.value.canContinue)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une heure deja passee de plus de deux heures bascule au lendemain`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = PriseServiceViewModel(
                session = SESSION,
                networkId = null,
                services = FakeServices(emptyList()),
                logger = NoopLogger,
            )
            advanceUntilIdle()
            val now = Instant.parse("2026-08-16T16:00:00Z")
            viewModel.setTimeOfDay(8, 15, now = now, zone = ZoneOffset.UTC)
            val at = viewModel.state.value.scheduledDeparture
            assertEquals(Instant.parse("2026-08-17T08:15:00Z"), at)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans GPS le dernier cran refuse de partir`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(listOf(c6))
            val viewModel = PriseServiceViewModel(
                session = SESSION,
                networkId = "net-nan",
                services = services,
                logger = NoopLogger,
            )
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.pickDirection("0")
            viewModel.continueOrStart()
            viewModel.continueOrStart()
            viewModel.continueOrStart()
            assertEquals(PriseServiceStep.GPS, viewModel.state.value.step)
            assertFalse(viewModel.state.value.canContinue)
            viewModel.continueOrStart()
            advanceUntilIdle()
            assertNull(services.started)
            viewModel.setGpsReady(true)
            viewModel.continueOrStart()
            advanceUntilIdle()
            assertEquals("C6", services.started?.lineId)
            assertEquals(0, services.started?.directionId)
            assertEquals("Hermeland", services.started?.terminus)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeServices(
        private val lines: List<ServiceLine>,
    ) : DriverServiceRepository {
        var started: ServiceStartRequest? = null

        override suspend fun fetchLines(session: AuthSession) = lines
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

        override suspend fun fetchActiveService(session: AuthSession) = null

        override suspend fun startService(
            session: AuthSession,
            request: ServiceStartRequest,
        ): ActiveDriverService {
            started = request
            return ActiveDriverService(
                id = "svc-1",
                lineId = request.lineId,
                lineLabel = request.lineLabel,
                directionId = request.directionId,
                terminus = request.terminus,
                startedAt = Instant.parse("2026-08-16T16:00:00Z"),
                vehicleId = request.vehicleId,
                trainNumber = request.trainNumber,
            )
        }

        override suspend fun endService(session: AuthSession, serviceId: String) = Unit

        override suspend fun publishPosition(
            session: AuthSession,
            request: io.aule.android.core.model.PositionPublishRequest,
        ) = error("unused")
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )
    }
}
