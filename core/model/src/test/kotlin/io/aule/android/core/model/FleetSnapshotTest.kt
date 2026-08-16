package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/** Port de `FleetPresentationTests` dans `Native/AuleTests/BackendTests.swift`. */
class FleetSnapshotTest {

    private fun snapshot(live: Int, scheduled: Int, stale: Boolean): FleetSnapshot {
        val vehicles = (0 until live + scheduled).map { index ->
            TransportVehicle(
                id = "v$index",
                mode = TransportMode.BUS,
                feed = if (index < live) VehicleFeed.LIVE else VehicleFeed.SCHEDULED,
                lineId = "C3",
                lineName = "C3",
                coordinate = Coordinate.NANTES,
            )
        }
        return FleetSnapshot(
            vehicles = vehicles,
            generatedAt = Instant.EPOCH,
            horizonSeconds = 10.0,
            isStale = stale,
        )
    }

    /**
     * L'ordre compte : « périmé » se décide **avant** « aucun ». L'inverse a déjà
     * fait annoncer « Aucun véhicule en vue » pendant une coupure réseau — ce qui
     * est faux, et décourage précisément de réessayer.
     */
    @Test
    fun `un etat perime se dit avant un etat vide`() {
        assertEquals(FleetStatus.Stale, snapshot(live = 0, scheduled = 0, stale = true).status)
        assertEquals(FleetStatus.Empty, snapshot(live = 0, scheduled = 0, stale = false).status)
    }

    /** Un sondage périmé reste périmé même s'il porte encore des véhicules. */
    @Test
    fun `un etat perime prime aussi sur des vehicules encore affiches`() {
        assertEquals(FleetStatus.Stale, snapshot(live = 3, scheduled = 2, stale = true).status)
    }

    @Test
    fun `l etat distingue le mesure du theorique`() {
        assertEquals(
            FleetStatus.LiveOnly(3),
            snapshot(live = 3, scheduled = 0, stale = false).status,
        )
        assertEquals(
            FleetStatus.ScheduledOnly(4),
            snapshot(live = 0, scheduled = 4, stale = false).status,
        )
        assertEquals(
            FleetStatus.Mixed(live = 2, scheduled = 5),
            snapshot(live = 2, scheduled = 5, stale = false).status,
        )
    }
}
