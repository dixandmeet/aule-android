package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Port de `SAE/test/route_plan_test.dart`.
 *
 * Ce qui se vérifie ici n'est pas qu'un itinéraire existe, mais qu'il ne
 * ment pas : l'ordre des coordonnées, la version d'algorithme, et le
 * recollage des tronçons sur les arrêts — le défaut « le tracé ne commence
 * pas à Ranzay ».
 */
class RouteTest {

    private val from = Coordinate(latitude = 47.2136, longitude = -1.5601)
    private val to = Coordinate(latitude = 47.2412, longitude = -1.5232)

    private fun lngLat(lng: Double, lat: Double) = Coordinate(latitude = lat, longitude = lng)

    private fun segment(points: List<Coordinate>, walk: Boolean, color: String = "#00A754", routeId: String? = null) =
        RouteSegment(coordinates = points, color = color, walk = walk, routeId = routeId)

    @Test
    fun `les coordonnees partent dans l ordre GeoJSON`() {
        // L'inversion produit un 404 « aucun arrêt à proximité » parfaitement
        // silencieux sur l'origine du mal. Le web s'y est fait prendre.
        val params = RouteApi.query(mode = RouteMode.TRANSIT, from = from, to = to)
        assertEquals("-1.5601,47.2136", params["from"])
        assertEquals("-1.5232,47.2412", params["to"])
    }

    @Test
    fun `la version de l algorithme est 28`() {
        val params = RouteApi.query(mode = RouteMode.CAR, from = from, to = to)
        assertEquals(ROUTE_ALGORITHM_VERSION, params["v"])
        assertEquals("28", params["v"])
        assertEquals("car", params["mode"])
    }

    @Test
    fun `la marche se demande foot et jamais walk`() {
        // Le verrou du lot 1.4, et il ne protège pas d'une faute de frappe : le
        // moteur accepte `walk` avec un 200, et rend la réponse **voiture** —
        // même géométrie, même durée. Mesuré côté iOS le 19/08/2026 : 1 198 m
        // en 191 s pour `walk`, 713 m en 528 s pour `foot`. À l'écran, « 1 min »
        // là où Plan dit 8, et rien pour dire pourquoi.
        val walk = RouteApi.query(mode = RouteMode.WALK, from = from, to = to)
        assertEquals("foot", walk["mode"])
        assertEquals("foot", RouteMode.WALK.apiValue)

        // Les trois seules valeurs que le moteur connaît. Toute autre retombe
        // sur le profil voiture, en silence.
        assertEquals(
            setOf("transit", "foot", "car"),
            RouteMode.entries.map { it.apiValue }.toSet(),
        )
    }

    @Test
    fun `les preferences ne partent qu en transit`() {
        val car = RouteApi.query(mode = RouteMode.CAR, from = from, to = to)
        assertFalse(car.containsKey("accessible"))
        assertFalse(car.containsKey("maxTransfers"))

        val walk = RouteApi.query(
            mode = RouteMode.WALK,
            from = from,
            to = to,
            preferences = RoutePreferences(accessible = true, maxTransfers = 1),
        )
        assertFalse(walk.containsKey("accessible"))
        assertFalse(walk.containsKey("maxTransfers"))
        assertFalse(walk.containsKey("avoidDisruptions"))

        val transit = RouteApi.query(
            mode = RouteMode.TRANSIT,
            from = from,
            to = to,
            preferences = RoutePreferences(accessible = true, maxTransfers = 1),
        )
        assertEquals("1", transit["accessible"])
        assertEquals("1", transit["avoidDisruptions"])
        assertEquals("1", transit["maxTransfers"])
    }

    @Test
    fun `une minute est le plancher d une duree`() {
        assertEquals(1, durationMinutesFromSeconds(12.0))
        assertEquals(32, durationMinutesFromSeconds(1916.8))
    }

    // La jambe C6 telle que le moteur la rendait : neuf points, le cinquième
    // étant très exactement l'arrêt de descente, et trois qui le dépassent.
    private val c6 = listOf(
        lngLat(-1.539216893, 47.237821331),
        lngLat(-1.541134, 47.235588),
        lngLat(-1.542486, 47.232147),
        lngLat(-1.544519, 47.22942),
        lngLat(-1.54642, 47.226734),
        lngLat(-1.548609, 47.225292),
        lngLat(-1.550528, 47.224026),
        lngLat(-1.552097, 47.222576),
        lngLat(-1.55056002, 47.219757711),
    )
    private val eraudiere = lngLat(-1.535011, 47.241669)
    private val breteche = lngLat(-1.548609, 47.225292)

    private fun anchoredC6(): List<RouteSegment> = anchorTransitSegments(
        listOf(
            segment(listOf(lngLat(-1.535243, 47.24268), eraudiere), walk = true, color = "#94a3b8"),
            segment(c6, walk = false, color = "#00A754", routeId = "C6"),
            segment(listOf(breteche, lngLat(-1.545, 47.2245)), walk = true, color = "#94a3b8"),
        ),
    )

    @Test
    fun `le trace part de l arret de montee et non 533 m plus loin`() {
        assertEquals(eraudiere, anchoredC6()[1].coordinates.first())
    }

    @Test
    fun `le trace s arrete a l arret de descente et non 633 m apres`() {
        val line = anchoredC6()[1].coordinates
        assertEquals(breteche, line.last())
        assertFalse(line.contains(c6[6]))
        assertFalse(line.contains(c6[7]))
        assertFalse(line.contains(c6[8]))
    }

    @Test
    fun `le milieu du trace est conserve`() {
        val line = anchoredC6()[1].coordinates
        for (point in c6.take(5)) {
            assertTrue(line.contains(point), "point perdu : $point")
        }
    }

    @Test
    fun `aucun trou ne subsiste entre deux troncons`() {
        val segments = anchoredC6()
        for (i in 1 until segments.size) {
            assertEquals(segments[i - 1].coordinates.last(), segments[i].coordinates.first())
        }
    }

    @Test
    fun `la marche n est jamais retouchee`() {
        val segments = anchoredC6()
        assertEquals(2, segments.first().coordinates.size)
        assertEquals(
            listOf(breteche, lngLat(-1.545, 47.2245)),
            segments.last().coordinates,
        )
    }

    @Test
    fun `couleur ligne et nature survivent au recollage`() {
        val bus = anchoredC6()[1]
        assertEquals("#00A754", bus.color)
        assertEquals("C6", bus.routeId)
        assertFalse(bus.walk)
    }

    @Test
    fun `deux troncons en vehicule ne s ancrent pas l un l autre`() {
        val first = listOf(lngLat(-1.56, 47.21), lngLat(-1.55, 47.22))
        val second = listOf(lngLat(-1.54, 47.23), lngLat(-1.53, 47.24))
        val segments = anchorTransitSegments(
            listOf(
                segment(first, walk = false, color = "#111111"),
                segment(second, walk = false, color = "#222222"),
            ),
        )
        assertEquals(first, segments.first().coordinates)
        assertEquals(second, segments.last().coordinates)
    }

    @Test
    fun `un profil inconnu est ecarte`() {
        assertEquals(RouteProfile.FASTEST, RouteProfile.fromApiValue("fastest"))
        assertEquals(RouteProfile.LEAST_WALK, RouteProfile.fromApiValue("least_walk"))
        assertEquals(null, RouteProfile.fromApiValue("invente"))
        assertEquals(null, RouteReliability.fromApiValue("theoretical"))
    }
}
