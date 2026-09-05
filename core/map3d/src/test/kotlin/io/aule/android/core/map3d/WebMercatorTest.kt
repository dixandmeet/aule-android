package io.aule.android.core.map3d

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le repère de scène, vérifié par ses invariants plutôt que par ses formules.
 *
 * Un test qui recalculerait la projection ne dirait rien : il ferait la même
 * erreur. Ceux-ci tiennent l'aller-retour, les signes, et l'ordre de grandeur —
 * les trois façons dont ce fichier peut se tromper sans que rien ne compile de
 * travers.
 */
class WebMercatorTest {

    /** Nantes, à peu près la Haluchère. */
    private val anchorLat = 47.2534
    private val anchorLon = -1.5268

    private val anchorX = WebMercator.x(anchorLon)
    private val anchorY = WebMercator.y(anchorLat)

    @Test
    fun `l aller-retour redonne la coordonnee d origine`() {
        // Le natif remultiplie exactement ce que le décalage divise. Si les deux
        // divergent, la flotte se décale — et rien ne le signale à la compilation.
        val longitude = anchorLon + 0.004
        val latitude = anchorLat + 0.003

        val east = WebMercator.eastOffsetMeters(longitude, anchorX, anchorLat)
        val north = WebMercator.northOffsetMeters(latitude, anchorY, anchorLat)

        val backX = anchorX + east / WebMercator.circumference(anchorLat)
        val backY = anchorY - north / WebMercator.circumference(anchorLat)

        assertEquals(WebMercator.x(longitude), backX, 1e-12)
        assertEquals(WebMercator.y(latitude), backY, 1e-12)
    }

    @Test
    fun `l est est positif et le nord aussi`() {
        assertTrue(
            WebMercator.eastOffsetMeters(anchorLon + 0.01, anchorX, anchorLat) > 0,
            "une longitude plus grande est plus à l'est",
        )
        assertTrue(
            WebMercator.northOffsetMeters(anchorLat + 0.01, anchorY, anchorLat) > 0,
            "une latitude plus grande est plus au nord — le y mercator, lui, descend",
        )
    }

    /**
     * Un degré de latitude vaut environ 111 km partout ; un degré de longitude,
     * autant multiplié par le cosinus de la latitude. C'est le garde-fou contre
     * une échelle inversée ou un facteur oublié.
     */
    @Test
    fun `les distances tombent a l ordre de grandeur attendu`() {
        val north = WebMercator.northOffsetMeters(anchorLat + 1.0, anchorY, anchorLat)
        assertTrue(
            abs(north - 111_000.0) < 3_000.0,
            "un degré de latitude devrait valoir ~111 km, mesuré $north",
        )

        val east = WebMercator.eastOffsetMeters(anchorLon + 1.0, anchorX, anchorLat)
        // cos(47,25°) ≈ 0,678 → ~75,5 km
        assertTrue(
            abs(east - 75_500.0) < 2_000.0,
            "un degré de longitude à cette latitude devrait valoir ~75,5 km, mesuré $east",
        )
    }

    @Test
    fun `un metre vaut deux fois plus de pixels au zoom suivant`() {
        fun metersToPixels(zoom: Double): Double =
            WebMercator.TILE_SIZE * Math.pow(2.0, zoom) / WebMercator.circumference(anchorLat)

        assertEquals(2.0, metersToPixels(17.0) / metersToPixels(16.0), 1e-9)
    }

    @Test
    fun `la projection ne diverge pas aux poles`() {
        assertTrue(WebMercator.y(89.9).isFinite())
        assertTrue(WebMercator.y(-89.9).isFinite())
    }
}
