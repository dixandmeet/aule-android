package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/journey_progress_test.dart`. */
class JourneyProgressTest {

    private val points = listOf(
        Coordinate(latitude = 47.2184, longitude = -1.56),
        Coordinate(latitude = 47.2184, longitude = -1.55),
    )

    private fun leg(mode: LegMode, startT: Double, endT: Double, meters: Double) = JourneyLeg(
        mode = mode,
        title = "jambe",
        startT = startT,
        endT = endT,
        distanceMeters = meters,
    )

    private fun troisJambes() = JourneyPlan(
        points = points,
        distanceMeters = 1250.0,
        destinationLabel = "Commerce",
        legs = listOf(
            leg(LegMode.WALK, 0.0, 0.2, 250.0),
            leg(LegMode.TRANSIT, 0.2, 0.8, 750.0),
            leg(LegMode.WALK, 0.8, 1.0, 250.0),
        ),
    )

    @Test
    fun `au depart, la premiere`() {
        val p = journeyProgressAt(troisJambes(), 0.0)!!
        assertEquals(0, p.legIndex)
        assertEquals(0.0, p.legT, 1e-9)
    }

    @Test
    fun `a la frontiere exacte, c est la jambe qui commence qui gagne`() {
        val p = journeyProgressAt(troisJambes(), 0.2)!!
        assertEquals(1, p.legIndex)
        assertEquals(0.0, p.legT, 1e-9)
    }

    @Test
    fun `les jambes restantes comptent celle en cours`() {
        assertEquals(3, journeyProgressAt(troisJambes(), 0.0)!!.remainingLegs)
        assertEquals(2, journeyProgressAt(troisJambes(), 0.5)!!.remainingLegs)
        assertEquals(1, journeyProgressAt(troisJambes(), 0.9)!!.remainingLegs)
    }

    @Test
    fun `l arrivee s annonce avant 1 exactement`() {
        assertFalse(journeyProgressAt(troisJambes(), 0.99)!!.arrived)
        assertTrue(journeyProgressAt(troisJambes(), JOURNEY_ARRIVED_T)!!.arrived)
        assertTrue(journeyProgressAt(troisJambes(), 1.0)!!.arrived)
    }

    @Test
    fun `une jambe d etendue nulle ne rend pas NaN`() {
        val plan = JourneyPlan(
            points = points,
            distanceMeters = 500.0,
            legs = listOf(
                leg(LegMode.WALK, 0.0, 0.5, 250.0),
                leg(LegMode.TRANSIT, 0.5, 0.5, 0.0),
                leg(LegMode.WALK, 0.5, 1.0, 250.0),
            ),
        )
        val p = journeyProgressAt(plan, 0.5)!!
        assertFalse(p.legT.isNaN())
        assertFalse(p.legRemainingMeters.isNaN())
    }

    @Test
    fun `un plan vide ne rend aucune lecture`() {
        assertNull(
            journeyProgressAt(
                JourneyPlan(
                    points = listOf(Coordinate(latitude = 47.2, longitude = -1.5)),
                    legs = emptyList(),
                    distanceMeters = 0.0,
                ),
                0.5,
            ),
        )
    }
}
