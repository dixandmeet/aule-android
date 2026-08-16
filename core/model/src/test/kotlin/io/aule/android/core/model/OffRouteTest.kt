package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/navigation_engine_test.dart`. */
class OffRouteTest {

    @Test
    fun `confirme une sortie seulement apres plusieurs points GPS`() {
        val detector = OffRouteDetector(requiredFixes = 3)
        assertFalse(detector.update(deviationMeters = 50.0, accuracy = 8.0))
        assertFalse(detector.update(deviationMeters = 52.0, accuracy = 8.0))
        assertTrue(detector.update(deviationMeters = 54.0, accuracy = 8.0))
    }

    @Test
    fun `ignore un ecart couvert par l imprecision GPS`() {
        val detector = OffRouteDetector(requiredFixes = 2)
        assertFalse(detector.update(deviationMeters = 55.0, accuracy = 50.0))
        assertFalse(detector.update(deviationMeters = 55.0, accuracy = 50.0))
        assertEquals(0, detector.outsideFixes)
    }

    @Test
    fun `40 m de sortie redonnent les 28 m historiques`() {
        val detector = OffRouteDetector(baseThresholdMeters = 40.0)
        assertEquals(28.0, detector.rejoinThresholdMeters, 0.001)
        assertTrue(detector.rejoined(27.9))
        assertFalse(detector.rejoined(28.1))
        assertFalse(detector.rejoined(28.0))
    }
}
