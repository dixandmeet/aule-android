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
    fun `l arrivee se mesure en metres, pas en fraction`() {
        // 1 250 m : le seuil de 50 m tombe à t = 0,96.
        assertFalse(journeyProgressAt(troisJambes(), 0.95)!!.arrived)
        assertTrue(journeyProgressAt(troisJambes(), 0.97)!!.arrived)
        assertTrue(journeyProgressAt(troisJambes(), 1.0)!!.arrived)
    }

    /**
     * Le cas de la campagne du 28/08/2026, en un test.
     *
     * Le tracé s'arrête sur la voirie, à quelques dizaines de mètres de
     * l'adresse. Le véhicule va au bout de ce qui est carrossable et n'atteint
     * jamais la fin géométrique du tracé — avec l'ancien seuil de 0,999, soit
     * moins de deux mètres sur cette longueur, l'arrivée n'était jamais
     * déclarée et le service de premier plan survivait au trajet.
     */
    @Test
    fun `un tracé qui finit à quarante metres de l adresse arrive quand meme`() {
        val plan = JourneyPlan(
            points = points,
            distanceMeters = 1832.0,
            destinationLabel = "Gare de Nantes",
            legs = listOf(leg(LegMode.CAR, 0.0, 1.0, 1832.0)),
        )
        val reste40m = 1.0 - 40.0 / 1832.0
        assertTrue(journeyProgressAt(plan, reste40m)!!.arrived)
        assertEquals(40.0, journeyProgressAt(plan, reste40m)!!.remainingMeters, 0.5)
        // Deux cents mètres, en revanche, c'est encore du trajet.
        assertFalse(journeyProgressAt(plan, 1.0 - 200.0 / 1832.0)!!.arrived)
    }

    /**
     * Un seuil en mètres, seul, déclarerait « arrivé » au départ d'un trajet
     * plus court que lui. La fraction plancher est là pour ça.
     */
    @Test
    fun `un trajet plus court que le seuil n arrive pas des le depart`() {
        val plan = JourneyPlan(
            points = points,
            distanceMeters = 40.0,
            legs = listOf(leg(LegMode.WALK, 0.0, 1.0, 40.0)),
        )
        assertFalse(journeyProgressAt(plan, 0.0)!!.arrived)
        assertTrue(journeyProgressAt(plan, 1.0)!!.arrived)
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
