package io.aule.android.feature.map

import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.PositionPublishRequest
import io.aule.android.core.model.ScheduledTrip
import io.aule.android.core.model.ScheduledTripStop
import io.aule.android.core.model.ServiceHeartbeat
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.model.repository.DriverServiceRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * La desserte du véhicule suivi, du côté de l'écran.
 *
 * Ce qui se teste ici, c'est **quand** on demande une course — une fois par
 * véhicule suivi, jamais au rythme du sondage — et ce que devient le plan
 * quand le catalogue ne connaît pas la course.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleTripModelTest {

    @Test
    fun `suivre un vehicule charge sa course une seule fois`() = runTest {
        val services = FakeServices(TRIP)
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()
        // Le sondage rend le même véhicule, position rafraîchie : rien à
        // redemander, la desserte n'a pas changé de la journée.
        model.follow(VEHICLE.copy(coordinate = Coordinate(47.21, -1.58)))
        runCurrent()

        assertEquals(1, services.calls)
        assertEquals(TRIP, model.state.value.trip)
        assertFalse(model.state.value.isLoading)
        assertFalse(model.state.value.isUnavailable)
    }

    /** Le flux ne publie pas de sens : c'est au dépôt de le déduire du terminus. */
    @Test
    fun `le sens est laisse au depot, avec la destination pour indice`() = runTest {
        val services = FakeServices(TRIP)
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()

        assertEquals(-1, services.lastDirection)
        assertEquals("Ranzay", services.lastHint)
        assertEquals("C1", services.lastLine)
    }

    @Test
    fun `changer de vehicule suivi recharge la course`() = runTest {
        val services = FakeServices(TRIP)
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()
        model.follow(VEHICLE.copy(id = "v2"))
        runCurrent()

        assertEquals(2, services.calls)
        assertEquals("v2", model.state.value.vehicleId)
    }

    /**
     * Une course absente du catalogue n'est pas une panne : le volet le dit en
     * une ligne et garde tout le reste de ce qu'il savait.
     */
    @Test
    fun `une course inconnue se dit, et ne se retente pas`() = runTest {
        val services = FakeServices(trip = null)
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()

        assertNull(model.state.value.trip)
        assertTrue(model.state.value.isUnavailable)
        assertFalse(model.state.value.isLoading)
        assertEquals(1, services.calls)
    }

    /** Une desserte d'un seul arrêt ne fait pas un plan de ligne. */
    @Test
    fun `une desserte trop courte vaut une absence`() = runTest {
        val services = FakeServices(TRIP.copy(stops = TRIP.stops.take(1)))
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()

        assertNull(model.state.value.trip)
        assertTrue(model.state.value.isUnavailable)
    }

    @Test
    fun `sans session, aucune requete et pas de plan`() = runTest {
        val services = FakeServices(TRIP)
        val model = model(services, session = { null })

        model.follow(VEHICLE)
        runCurrent()

        assertEquals(0, services.calls)
        assertTrue(model.state.value.isUnavailable)
    }

    @Test
    fun `cesser de suivre oublie la course`() = runTest {
        val services = FakeServices(TRIP)
        val model = model(services)

        model.follow(VEHICLE)
        runCurrent()
        model.follow(null)

        assertNull(model.state.value.trip)
        assertNull(model.state.value.vehicleId)
    }

    // ------------------------------------------------------------- fabriques

    private fun TestScope.model(
        services: FakeServices,
        session: () -> AuthSession? = { SESSION },
    ): VehicleTripModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return VehicleTripModel(
            repository = services,
            session = session,
            dispatchers = TestDispatchers(dispatcher),
            scope = backgroundScope,
            logger = NoopLogger,
            now = { NOW },
        )
    }

    private class TestDispatchers(dispatcher: CoroutineDispatcher) : AuleDispatchers {
        override val main: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private class FakeServices(private val trip: ScheduledTrip?) : DriverServiceRepository {
        var calls = 0
        var lastLine: String? = null
        var lastDirection: Int? = null
        var lastHint: String? = null

        override suspend fun nearestActiveTrip(
            session: AuthSession,
            lineId: String,
            directionId: Int,
            destinationHint: String?,
            near: Coordinate,
            at: Instant,
        ): ScheduledTrip? {
            calls++
            lastLine = lineId
            lastDirection = directionId
            lastHint = destinationHint
            return trip
        }

        override suspend fun fetchLines(session: AuthSession): List<ServiceLine> = emptyList()
        override suspend fun fetchJourney(
            session: AuthSession,
            lineId: String,
            directionId: Int,
        ) = error("unused")
        override suspend fun fetchActiveService(session: AuthSession): ActiveDriverService? = null
        override suspend fun startService(session: AuthSession, request: ServiceStartRequest) =
            error("unused")
        override suspend fun endService(session: AuthSession, serviceId: String) = Unit
        override suspend fun publishPosition(
            session: AuthSession,
            request: PositionPublishRequest,
        ): ServiceHeartbeat = error("unused")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-16T16:00:00Z")

        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )

        val VEHICLE = TransportVehicle(
            id = "v1",
            mode = TransportMode.BUS,
            feed = VehicleFeed.SCHEDULED,
            lineId = "C1",
            lineName = "C1",
            destination = "Ranzay",
            coordinate = Coordinate(47.20, -1.58),
        )

        val TRIP = ScheduledTrip(
            departureId = "dep-1",
            lineId = "C1",
            lineLabel = "C1",
            directionId = 0,
            destination = "Ranzay",
            stops = listOf(
                ScheduledTripStop(
                    "a",
                    "Gare de Chantenay",
                    Coordinate(47.20, -1.58),
                    NOW.minusSeconds(600),
                ),
                ScheduledTripStop("b", "Ranzay", Coordinate(47.22, -1.58), NOW.plusSeconds(600)),
            ),
        )
    }
}
