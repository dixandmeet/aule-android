package io.aule.android.core.location

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `MotionTests` dans `Native/AuleTests/GeoTests.swift`. */
class MotionTests {

    /**
     * Le « heading » d'un GPS est un cap **de route** : il se déduit du
     * déplacement. À l'arrêt il n'y a pas de déplacement, seulement l'erreur
     * de mesure — et le cap se met à tourner tout seul.
     */
    @Test
    fun `sous le seuil de vitesse, le cap gele au lieu de mentir`() {
        val stabilizer = HeadingStabilizer()
        stabilizer.ingest(course = 90.0, speed = 5.0)
        val held = stabilizer.stabilized

        stabilizer.ingest(course = 270.0, speed = 0.1)
        assertTrue(stabilizer.isFrozen)
        assertEquals(held, stabilizer.stabilized)
    }

    @Test
    fun `au-dessus du seuil, le cap converge sans sauter`() {
        val stabilizer = HeadingStabilizer()
        stabilizer.ingest(course = 0.0, speed = 10.0)
        stabilizer.ingest(course = 90.0, speed = 10.0)

        val value = stabilizer.stabilized ?: 0.0
        assertTrue(value > 0.0 && value < 90.0, "le cap doit progresser vers 90° sans y sauter : $value")
    }

    @Test
    fun `l ancre absorbe le tremblement a l arret`() {
        val anchor = MotionAnchor()
        val base = Coordinate(latitude = 47.2136, longitude = -1.5573)
        val settled = anchor.settle(base, speed = 0.0, accuracy = 8.0)

        // ~5 m plus loin, à l'arrêt : c'est du bruit, pas un déplacement.
        val jittered = Coordinate(latitude = 47.21365, longitude = -1.5573)
        assertEquals(settled, anchor.settle(jittered, speed = 0.1, accuracy = 8.0))
    }

    @Test
    fun `l ancre lache des qu on marche vraiment`() {
        val anchor = MotionAnchor()
        val base = Coordinate(latitude = 47.2136, longitude = -1.5573)
        anchor.settle(base, speed = 0.0, accuracy = 8.0)

        val moved = Coordinate(latitude = 47.21365, longitude = -1.5573)
        assertEquals(moved, anchor.settle(moved, speed = 1.4, accuracy = 8.0))
    }

    /**
     * En ville dense, entre deux immeubles, l'erreur atteint la centaine de
     * mètres. Suivre une telle position ferait traverser un pâté de maisons
     * au puck.
     */
    @Test
    fun `une position trop imprecise ne deplace pas l ancre`() {
        val anchor = MotionAnchor()
        val base = Coordinate(latitude = 47.2136, longitude = -1.5573)
        val settled = anchor.settle(base, speed = 0.0, accuracy = 8.0)

        val wild = Coordinate(latitude = 47.2200, longitude = -1.5500)
        assertEquals(settled, anchor.settle(wild, speed = 0.0, accuracy = 120.0))
    }

    @Test
    fun `une precision reduite n autorise pas le suivi`() {
        assertFalse(LocationAuthorization.REDUCED_ACCURACY.allowsPreciseTracking)
        assertTrue(LocationAuthorization.REDUCED_ACCURACY.allowsUpdates)
        assertFalse(LocationAuthorization.DENIED.canRequest)
        assertTrue(LocationAuthorization.UNKNOWN.canRequest)
    }
}
