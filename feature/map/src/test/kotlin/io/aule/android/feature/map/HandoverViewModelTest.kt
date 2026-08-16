package io.aule.android.feature.map

import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import io.aule.android.core.model.DeparturesOutcome
import io.aule.android.core.model.DriverServiceException
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.HandoverAlert
import io.aule.android.core.model.HandoverAlertKind
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.HandoverEngagement
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverFix
import io.aule.android.core.model.HandoverStatus
import io.aule.android.core.model.HandoverSummary
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.HandoverTrack
import io.aule.android.core.model.LineJourney
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.ServiceStartRequest
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import io.aule.android.core.model.repository.DriverServiceRepository
import io.aule.android.core.model.repository.HandoverAlertPrefsStore
import io.aule.android.core.model.repository.HandoverRepository
import io.aule.android.core.model.repository.RoadProfile
import io.aule.android.core.model.repository.RoadRoute
import io.aule.android.core.model.repository.RoadRouter
import io.aule.android.core.model.repository.StopRepository
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandoverViewModelTest {

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

    @Test
    fun `sans releve en cours on ouvre le choix de ligne`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            advanceUntilIdle()
            assertEquals(HandoverStep.LINE, viewModel.state.value.step)
            assertEquals(listOf("C6"), viewModel.state.value.lines.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `la flotte certifiee nourrit les lignes en service`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val tram = ServiceLine(
                id = "1",
                label = "1",
                description = "François Mitterrand - Beaujoire",
                mode = TransportMode.TRAM,
                networkId = "net-nan",
                directions = listOf(ServiceDirection("0", "Beaujoire")),
            )
            val prefs = MemoryAlertPrefs(initialRecent = listOf("C6", "ghost"))
            val viewModel = viewModel(
                services = FakeServices(listOf(c6, tram)),
                alertPrefsStore = prefs,
            )
            advanceUntilIdle()
            assertEquals(listOf(c6), viewModel.state.value.recentLines)
            assertTrue(viewModel.state.value.activeLines.isEmpty())
            assertTrue(viewModel.state.value.filteredLines.isEmpty())

            viewModel.onFleetSnapshot(
                FleetSnapshot(
                    vehicles = listOf(
                        liveVehicle(lineId = "1"),
                        liveVehicle(lineId = "1"),
                        scheduledVehicle(lineId = "C6"),
                    ),
                ),
            )
            assertEquals(setOf("1"), viewModel.state.value.activeLineIds)
            assertEquals(listOf(tram), viewModel.state.value.activeLines)
            // Une ligne active ne se répète pas dans les récentes.
            assertEquals(listOf(c6), viewModel.state.value.recentLines)

            viewModel.setSearch("T1")
            assertEquals(listOf(tram), viewModel.state.value.filteredLines)

            viewModel.pickLine("1")
            assertEquals(listOf("1", "C6", "ghost"), prefs.readRecentLines())
            assertEquals(HandoverStep.VEHICLE, viewModel.state.value.step)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une releve deja engagee se propose a la reprise`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(pending = ENGAGEMENT)
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            assertEquals(HandoverStep.RESUME, viewModel.state.value.step)
            viewModel.resumePending()
            advanceUntilIdle()
            assertEquals(HandoverStep.STOP, viewModel.state.value.step)
            assertEquals("hov-1", viewModel.state.value.handover?.id)
            assertEquals(listOf("Commerce", "Chantrerie"), viewModel.state.value.liveStops.map { it.name })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `chercher un vehicule sans candidat ouvre le repli`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("999")
            viewModel.searchVehicle()
            advanceUntilIdle()
            assertEquals(HandoverStep.DIRECTION, viewModel.state.value.step)
            assertTrue(viewModel.state.value.lookupEmpty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le repli demarre le service sur le passage choisi`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val services = FakeServices(listOf(c6))
            val viewModel = viewModel(services = services)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("999")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.pickDirection(c6.directions[0])
            advanceUntilIdle()
            assertEquals(HandoverStep.FALLBACK_STOP, viewModel.state.value.step)
            assertEquals("Commerce", viewModel.state.value.visibleFallbackStops.first().name)
            val stop = viewModel.state.value.fallbackStops.first { it.name == "Commerce" }
            viewModel.pickFallbackStop(stop)
            advanceUntilIdle()
            assertEquals(HandoverStep.FALLBACK_TIME, viewModel.state.value.step)
            val passage = viewModel.state.value.fallbackPassages.first()
            viewModel.startFallback(passage)
            advanceUntilIdle()
            assertEquals("svc-fb", viewModel.state.value.started?.id)
            assertEquals("999", services.started?.trainNumber)
            assertEquals(0, services.started?.directionId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `engager puis confirmer adopte le service`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(candidates = listOf(TARGET))
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            assertEquals(HandoverStep.CANDIDATES, viewModel.state.value.step)
            viewModel.engage(TARGET)
            advanceUntilIdle()
            assertEquals(HandoverStep.STOP, viewModel.state.value.step)
            assertEquals(listOf("Commerce", "Chantrerie"), viewModel.state.value.liveStops.map { it.name })
            assertEquals(
                listOf(Instant.parse("2026-08-16T16:12:00Z")),
                viewModel.state.value.neighbourPassages["Commerce"],
            )
            val stop = viewModel.state.value.liveStops.first { it.name == "Commerce" }
            viewModel.pickLiveStop(stop)
            advanceUntilIdle()
            assertEquals(HandoverStep.ALERTS, viewModel.state.value.step)
            assertEquals("2", handovers.stopId)
            assertEquals(Instant.parse("2026-08-16T16:12:00Z"), handovers.plannedAt)
            viewModel.beginTracking()
            assertEquals(HandoverStep.CONFIRM, viewModel.state.value.step)
            viewModel.confirm()
            advanceUntilIdle()
            assertEquals("svc-in", viewModel.state.value.started?.id)
            assertEquals("C6", viewModel.state.value.started?.lineLabel)
            assertEquals("Hermeland", viewModel.state.value.started?.terminus)
            assertEquals("hov-1", handovers.confirmed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `revenir de la confirmation relache l engagement`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(candidates = listOf(TARGET))
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            assertEquals(HandoverStep.STOP, viewModel.state.value.step)
            assertFalse(viewModel.back())
            advanceUntilIdle()
            assertEquals("hov-1", handovers.cancelled)
            assertEquals(HandoverStep.CANDIDATES, viewModel.state.value.step)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `revenir du suivi rouvre le choix d arret sans relacher`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(candidates = listOf(TARGET))
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            viewModel.pickLiveStop(viewModel.state.value.liveStops.first())
            advanceUntilIdle()
            viewModel.beginTracking()
            assertEquals(HandoverStep.CONFIRM, viewModel.state.value.step)
            assertFalse(viewModel.back())
            advanceUntilIdle()
            assertEquals(HandoverStep.ALERTS, viewModel.state.value.step)
            assertEquals(null, handovers.cancelled)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `sans desserte l engagement est relache`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(candidates = listOf(TARGET))
            val services = FakeServices(listOf(c6)).also {
                it.journeyFailure = DriverServiceException(DriverServiceFailureKind.LINES_EMPTY)
            }
            val viewModel = viewModel(handovers = handovers, services = services)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            assertEquals("hov-1", handovers.cancelled)
            assertEquals(HandoverFailureKind.JOURNEY_UNAVAILABLE, viewModel.state.value.failure)
            assertEquals(HandoverStep.CANDIDATES, viewModel.state.value.step)
            assertEquals(null, viewModel.state.value.handover)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `une releve deja posee reprend le suivi`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val pending = HandoverEngagement(
                handover = SUMMARY.copy(
                    reliefStopId = "2",
                    reliefStopName = "Commerce",
                    reliefStopCoordinate = Coordinate(47.2134, -1.558),
                ),
                target = TARGET,
            )
            val handovers = FakeHandovers(pending = pending)
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.resumePending()
            advanceUntilIdle()
            assertEquals(HandoverStep.CONFIRM, viewModel.state.value.step)
            assertEquals("Commerce", viewModel.state.value.selectedLiveStop?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `l arrivee du collegue declenche l alerte sans attendre`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fired = mutableListOf<HandoverAlertKind>()
            val atStop = Coordinate(47.28, -1.5201)
            val handovers = FakeHandovers(candidates = listOf(TARGET)).also {
                it.track = HandoverTrack(
                    handover = SUMMARY,
                    serverTime = Instant.parse("2026-08-16T16:00:00Z"),
                    fix = HandoverFix(
                        coordinate = atStop,
                        recordedAt = Instant.parse("2026-08-16T16:00:00Z"),
                        ageSeconds = 3,
                        speed = 0.4,
                    ),
                )
            }
            val viewModel = viewModel(
                handovers = handovers,
                onAlert = { alert, _ -> fired += alert.kind },
            )
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            val terminus = viewModel.state.value.liveStops.last()
            viewModel.pickLiveStop(terminus)
            advanceUntilIdle()
            viewModel.beginTracking()
            viewModel.startTracking()
            runCurrent()
            assertEquals(listOf(HandoverAlertKind.ARRIVED), fired)
            assertTrue(viewModel.state.value.reliefArrived)
            assertTrue(viewModel.state.value.progress?.arrived == true)
            viewModel.stopTracking()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le suivi pose la position du collegue`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val fix = HandoverFix(
                coordinate = Coordinate(47.21, -1.55),
                recordedAt = Instant.parse("2026-08-16T16:00:00Z"),
                ageSeconds = 4,
                heading = 90.0,
            )
            val handovers = FakeHandovers(
                candidates = listOf(TARGET),
            ).also {
                it.track = HandoverTrack(
                    handover = SUMMARY,
                    serverTime = Instant.parse("2026-08-16T16:00:00Z"),
                    fix = fix,
                )
            }
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            viewModel.startTracking()
            runCurrent()
            assertEquals(47.21, viewModel.state.value.trackFix?.coordinate?.latitude)
            assertEquals("hov-1", handovers.tracked)
            viewModel.stopTracking()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `le suivi mesure la distance et le depart conseille`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val roads = FakeRoads(durationSeconds = 180.0)
            val handovers = FakeHandovers(candidates = listOf(TARGET)).also {
                it.track = HandoverTrack(
                    handover = SUMMARY,
                    serverTime = Instant.parse("2026-08-16T16:00:00Z"),
                    fix = HandoverFix(
                        coordinate = Coordinate(47.2134, -1.558),
                        recordedAt = Instant.parse("2026-08-16T16:00:00Z"),
                        ageSeconds = 3,
                        speed = 12.0,
                    ),
                )
            }
            val viewModel = viewModel(handovers = handovers, roads = roads)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            val terminus = viewModel.state.value.liveStops.last()
            viewModel.pickLiveStop(terminus)
            advanceUntilIdle()
            viewModel.beginTracking()
            viewModel.startTracking()
            runCurrent()
            val state = viewModel.state.value
            assertEquals(1, state.progress?.stopsRemaining)
            assertTrue((state.progress?.metersRemaining ?: 0.0) > 100.0)
            assertEquals(Duration.ofSeconds(180), state.travelToRelief)
            assertEquals(
                state.progress?.estimatedAt
                    ?.minusSeconds(180)
                    ?.minusSeconds(120),
                state.leaveBy,
            )
            viewModel.stopTracking()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `un collegue qui cloture son service annule la releve`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val handovers = FakeHandovers(candidates = listOf(TARGET)).also {
                it.track = HandoverTrack(
                    handover = SUMMARY.copy(
                        status = HandoverStatus.CANCELLED,
                        cancelReason = "outgoing_service_closed",
                    ),
                    serverTime = Instant.parse("2026-08-16T16:00:00Z"),
                )
            }
            val viewModel = viewModel(handovers = handovers)
            advanceUntilIdle()
            viewModel.pickLine("C6")
            viewModel.setQuery("324")
            viewModel.searchVehicle()
            advanceUntilIdle()
            viewModel.engage(TARGET)
            advanceUntilIdle()
            viewModel.startTracking()
            advanceUntilIdle()
            assertEquals(HandoverFailureKind.CLOSED, viewModel.state.value.failure)
            assertEquals("outgoing_service_closed", viewModel.state.value.abortedReason)
            assertEquals(null, viewModel.state.value.trackFix)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        handovers: FakeHandovers = FakeHandovers(),
        alreadyOnService: Boolean = false,
        services: FakeServices = FakeServices(listOf(c6)),
        stops: FakeStops = FakeStops(),
        roads: RoadRouter = FakeRoads(),
        alertPrefsStore: HandoverAlertPrefsStore = MemoryAlertPrefs(),
        onAlert: (HandoverAlert, String) -> Unit = { _, _ -> },
    ) = HandoverViewModel(
        session = SESSION,
        networkId = "net-nan",
        alreadyOnService = alreadyOnService,
        services = services,
        handovers = handovers,
        stops = stops,
        around = { Coordinate(47.213, -1.558) },
        roads = roads,
        alertPrefsStore = alertPrefsStore,
        onAlert = onAlert,
        logger = NoopLogger,
        now = { Instant.parse("2026-08-16T16:00:00Z") },
    )

    private class FakeRoads(
        private val durationSeconds: Double = 180.0,
    ) : RoadRouter {
        override suspend fun route(
            from: Coordinate,
            to: Coordinate,
            profile: RoadProfile,
        ) = RoadRoute(
            points = listOf(from, to),
            distanceMeters = 1_200.0,
            durationSeconds = durationSeconds,
        )
    }

    private class MemoryAlertPrefs(
        initialRecent: List<String> = emptyList(),
    ) : HandoverAlertPrefsStore {
        private var value = HandoverAlertPrefs.DEFAULTS
        private var recent = initialRecent
        override fun read() = value
        override fun write(prefs: HandoverAlertPrefs) {
            value = prefs
        }
        override fun readRecentLines() = recent
        override fun pushRecentLine(lineId: String): List<String> {
            recent = listOf(lineId) + recent.filter { it != lineId }
            recent = recent.take(4)
            return recent
        }
    }

    private class FakeServices(
        private val lines: List<ServiceLine>,
    ) : DriverServiceRepository {
        var started: ServiceStartRequest? = null
        var journeyFailure: DriverServiceException? = null

        override suspend fun fetchLines(session: AuthSession) = lines
        override suspend fun fetchJourney(
            session: AuthSession,
            lineId: String,
            directionId: Int,
        ) = journeyFailure?.let { throw it } ?: JOURNEY

        override suspend fun fetchActiveService(session: AuthSession) = null
        override suspend fun startService(session: AuthSession, request: ServiceStartRequest): ActiveDriverService {
            started = request
            return ActiveDriverService(
                id = "svc-fb",
                lineId = request.lineId,
                lineLabel = request.lineLabel,
                directionId = request.directionId,
                terminus = request.terminus,
                startedAt = Instant.parse("2026-08-16T16:00:00Z"),
                trainNumber = request.trainNumber,
            )
        }
        override suspend fun endService(session: AuthSession, serviceId: String) = Unit

        override suspend fun publishPosition(
            session: AuthSession,
            request: io.aule.android.core.model.PositionPublishRequest,
        ) = error("unused")
    }

    private class FakeStops : StopRepository {
        override suspend fun allStops() = error("unused")
        override suspend fun departures(atStopNamed: String) = StopDepartures(
            stopName = atStopNamed,
            departures = listOf(
                StopDeparture(
                    id = "p1",
                    line = "C6",
                    destination = "Hermeland",
                    expectedAt = Instant.parse("2026-08-16T16:12:00Z"),
                    isRealtime = false,
                ),
            ),
            outcome = DeparturesOutcome.ANNOUNCED,
            fetchedAt = Instant.parse("2026-08-16T16:00:00Z"),
        )
        override suspend fun servingLines(atStopNamed: String) = listOf(
            ServingLine("C6", "Hermeland"),
            ServingLine("C6", "Chantrerie"),
        )
    }

    private class FakeHandovers(
        private val candidates: List<HandoverTarget> = emptyList(),
        private val pending: HandoverEngagement? = null,
    ) : HandoverRepository {
        var cancelled: String? = null
        var confirmed: String? = null
        var tracked: String? = null
        var stopId: String? = null
        var plannedAt: Instant? = null
        var track: HandoverTrack = HandoverTrack(
            handover = SUMMARY,
            serverTime = Instant.parse("2026-08-16T16:00:00Z"),
            fix = HandoverFix(
                coordinate = Coordinate(47.2134, -1.558),
                recordedAt = Instant.parse("2026-08-16T15:59:48Z"),
                ageSeconds = 12,
            ),
        )

        override suspend fun lookup(session: AuthSession, lineId: String, query: String) = candidates

        override suspend fun request(session: AuthSession, outgoingServiceId: String) = SUMMARY

        override suspend fun setStop(
            session: AuthSession,
            handoverId: String,
            stopId: String,
            stopName: String,
            latitude: Double,
            longitude: Double,
            plannedAt: Instant?,
        ): HandoverSummary {
            this.stopId = stopId
            this.plannedAt = plannedAt
            val updated = SUMMARY.copy(
                reliefStopId = stopId,
                reliefStopName = stopName,
                reliefStopCoordinate = Coordinate(latitude, longitude),
                reliefPlannedAt = plannedAt,
            )
            track = track.copy(handover = updated)
            return updated
        }

        override suspend fun confirm(session: AuthSession, handoverId: String): HandoverSummary {
            confirmed = handoverId
            return SUMMARY.copy(status = HandoverStatus.COMPLETED, incomingServiceId = "svc-in")
        }

        override suspend fun cancel(
            session: AuthSession,
            handoverId: String,
            reason: String?,
        ): HandoverSummary {
            cancelled = handoverId
            return SUMMARY.copy(status = HandoverStatus.CANCELLED)
        }

        override suspend fun activeForMe(session: AuthSession) = pending

        override suspend fun track(session: AuthSession, handoverId: String): HandoverTrack {
            tracked = handoverId
            return track
        }
    }

    private companion object {
        val SESSION = AuthSession(
            user = AuthUser("user-1", "agent@aule.fr"),
            accessToken = "access-1",
            refreshToken = "refresh-1",
            expiresAtEpochSeconds = 9_999_999_999L,
        )
        val TARGET = HandoverTarget(
            serviceId = "svc-out",
            lineId = "C6",
            driverDisplay = "Martin",
            directionId = 0,
            terminus = "Hermeland",
            vehicleId = "324",
            trainNumber = "1-12",
        )
        val SUMMARY = HandoverSummary(
            id = "hov-1",
            status = HandoverStatus.ENGAGED,
            lineId = "C6",
            outgoingServiceId = "svc-out",
            vehicleId = "324",
            outgoingDisplay = "Martin",
        )
        val ENGAGEMENT = HandoverEngagement(handover = SUMMARY, target = TARGET)
        val JOURNEY = LineJourney(
            tripId = "trip-1",
            stops = listOf(
                LineJourneyStop("1", "Hermeland", Coordinate(47.29, -1.52)),
                LineJourneyStop("2", "Commerce", Coordinate(47.2134, -1.558)),
                LineJourneyStop("3", "Chantrerie", Coordinate(47.28, -1.52)),
            ),
        )

        fun liveVehicle(lineId: String) = TransportVehicle(
            id = "v-$lineId-live",
            mode = TransportMode.BUS,
            feed = VehicleFeed.LIVE,
            lineId = lineId,
            lineName = lineId,
            coordinate = Coordinate(47.21, -1.55),
        )

        fun scheduledVehicle(lineId: String) = TransportVehicle(
            id = "v-$lineId-sched",
            mode = TransportMode.BUS,
            feed = VehicleFeed.SCHEDULED,
            lineId = lineId,
            lineName = lineId,
            coordinate = Coordinate(47.21, -1.55),
        )
    }
}
