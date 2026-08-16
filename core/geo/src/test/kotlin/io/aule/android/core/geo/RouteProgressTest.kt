package io.aule.android.core.geo

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class RouteProgressTest {

    private val line: List<Coordinate> = (0..10).map {
        Coordinate(latitude = 47.2136, longitude = -1.5600 + it * 0.002)
    }

    @Test
    fun `le premier appel cherche sur tout le trace`() {
        val progress = RouteProgress()
        val far = Coordinate(latitude = 47.2136, longitude = -1.5420)
        val match = progress.advance(line, far)
        requireNotNull(match)
        assertTrue(match.t > 0.8, "t = ${match.t}")
        assertTrue(progress.seeded)
    }

    @Test
    fun `ensuite la fenetre empeche le saut`() {
        val progress = RouteProgress(initial = 0.1)
        val far = Coordinate(latitude = 47.2136, longitude = -1.5420)
        val match = progress.advance(line, far)
        requireNotNull(match)
        assertTrue(match.t <= 0.1 + PolylineProjection.FORWARD_WINDOW + 0.001)
    }

    @Test
    fun `un trace vide laisse la progression telle quelle`() {
        val progress = RouteProgress(initial = 0.4)
        assertNull(progress.advance(emptyList(), Coordinate.NANTES))
        assertEquals(0.4, progress.t, 1e-9)
    }
}
