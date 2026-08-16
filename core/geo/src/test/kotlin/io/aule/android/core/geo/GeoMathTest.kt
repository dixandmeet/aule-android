package io.aule.android.core.geo

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `Native/AuleTests/GeoTests.swift`, valeurs de référence comprises. */
class GeoMathTest {

    @Test
    fun `une distance connue tombe juste`() {
        // Commerce → Beaujoire, mesuré ≈ 5,3 km.
        val commerce = Coordinate(latitude = 47.2136, longitude = -1.5573)
        val beaujoire = Coordinate(latitude = 47.258828, longitude = -1.526169)
        val distance = GeoMath.distance(commerce, beaujoire)
        assertTrue(distance > 5_000 && distance < 5_700, "distance = $distance")
    }

    /**
     * Sans le plus court chemin, un cap qui passe de 359° à 1° fait tourner la
     * caméra de 358° dans le mauvais sens : l'écran fait un tour complet là où le
     * véhicule a bougé de deux degrés.
     */
    @Test
    fun `le cap prend toujours le chemin le plus court`() {
        assertEquals(2.0, GeoMath.shortestHeadingDelta(359.0, 1.0), 1e-9)
        assertEquals(-2.0, GeoMath.shortestHeadingDelta(1.0, 359.0), 1e-9)
        assertEquals(180.0, GeoMath.shortestHeadingDelta(90.0, 270.0), 1e-9)
    }

    @Test
    fun `un cap interpole ne passe jamais par le grand tour`() {
        val midway = GeoMath.interpolateHeading(350.0, 10.0, 0.5)
        assertTrue(midway < 0.001 || abs(midway - 360) < 0.001, "midway = $midway")
    }

    @Test
    fun `les caps restent dans zero-360`() {
        assertEquals(270.0, GeoMath.normalizeHeading(-90.0), 1e-9)
        assertEquals(90.0, GeoMath.normalizeHeading(450.0), 1e-9)
        assertEquals(0.0, GeoMath.normalizeHeading(360.0), 1e-9)
    }

    /**
     * L'ordre `lng,lat` n'est pas un détail : le backend Aule répond **404 en
     * silence** sur une paire inversée, ce qui se diagnostique très mal.
     */
    @Test
    fun `la paire envoyee a l API est bien lng,lat`() {
        assertEquals(
            "-1.5573,47.2136",
            Coordinate(latitude = 47.2136, longitude = -1.5573).apiPair,
        )
    }

    @Test
    fun `une paire GeoJSON se lit lng puis lat`() {
        val coordinate = Coordinate.fromGeoJsonPair(listOf(-1.555938, 47.219933))
        requireNotNull(coordinate)
        assertEquals(47.219933, coordinate.latitude, 1e-9)
        assertEquals(-1.555938, coordinate.longitude, 1e-9)
    }

    @Test
    fun `une paire GeoJSON incomplete ne rend rien`() {
        assertEquals(null, Coordinate.fromGeoJsonPair(listOf(-1.55)))
        assertEquals(null, Coordinate.fromGeoJsonPair(emptyList()))
    }

    @Test
    fun `un point hors du monde est rejete`() {
        // C'est la forme qu'un lat/lng inversé prend en Loire-Atlantique : une
        // latitude de −1,55 est valide en soi, mais une longitude de 47,2 ne l'est pas.
        assertFalse(Coordinate(latitude = 91.0, longitude = 0.0).isValid)
        assertFalse(Coordinate(latitude = 0.0, longitude = 0.0).isValid)
        assertTrue(Coordinate(latitude = 47.2136, longitude = -1.5573).isValid)
    }

    @Test
    fun `les distances se lisent comme un humain les dit`() {
        assertEquals("40 m", GeoMath.formatDistance(42.0))
        assertEquals("250 m", GeoMath.formatDistance(230.0))
        assertEquals("4,2 km", GeoMath.formatDistance(4_200.0))
        assertEquals("12 km", GeoMath.formatDistance(12_400.0))
    }

    /**
     * Le module ne connaît pas la langue de l'écran. Un anglophone doit pouvoir
     * lire « 4.2 km » sans qu'on touche à la géométrie.
     */
    @Test
    fun `le separateur decimal suit l appelant`() {
        assertEquals("4.2 km", GeoMath.formatDistance(4_200.0, decimalSeparator = '.'))
    }
}
