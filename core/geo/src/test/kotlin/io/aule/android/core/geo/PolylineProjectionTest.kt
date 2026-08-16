package io.aule.android.core.geo

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `PolylineTests` dans `Native/AuleTests/GeoTests.swift`. */
class PolylineProjectionTest {

    /** Une ligne droite est-ouest, ~1,5 km, à hauteur de Nantes. */
    private val line: List<Coordinate> = (0..10).map {
        Coordinate(latitude = 47.2136, longitude = -1.5600 + it * 0.002)
    }

    @Test
    fun `le milieu du trace se projette au milieu`() {
        val match = PolylineProjection.project(
            Coordinate(latitude = 47.2136, longitude = -1.5500),
            onto = line,
        )
        requireNotNull(match)
        assertTrue(abs(match.t - 0.5) < 0.01, "t = ${match.t}")
        assertTrue(match.deviationMeters < 1, "écart = ${match.deviationMeters}")
    }

    @Test
    fun `l ecart perpendiculaire est mesure`() {
        // ~110 m au nord de la ligne.
        val match = PolylineProjection.project(
            Coordinate(latitude = 47.2146, longitude = -1.5500),
            onto = line,
        )
        requireNotNull(match)
        assertTrue(
            match.deviationMeters > 80 && match.deviationMeters < 140,
            "écart = ${match.deviationMeters}",
        )
    }

    /**
     * Le cœur du sujet : une projection au plus proche saute d'un brin à l'autre
     * sur un corridor emprunté à l'aller et au retour, et l'avancement fait des
     * bonds de plusieurs kilomètres. La fenêtre l'en empêche.
     */
    @Test
    fun `la fenetre empeche l avancement de sauter en avant`() {
        val far = Coordinate(latitude = 47.2136, longitude = -1.5420) // ≈ 90 % du tracé

        val constrained = PolylineProjection.project(far, onto = line, currentT = 0.1)
        requireNotNull(constrained)
        assertTrue(
            constrained.t <= 0.1 + PolylineProjection.FORWARD_WINDOW + 0.001,
            "t contraint = ${constrained.t}",
        )

        val unconstrained = PolylineProjection.project(far, onto = line)
        requireNotNull(unconstrained)
        assertTrue(unconstrained.t > 0.8, "t libre = ${unconstrained.t}")
    }

    @Test
    fun `la fenetre arriere est serree — un bus ne recule pas`() {
        val behind = Coordinate(latitude = 47.2136, longitude = -1.5600)
        val match = PolylineProjection.project(behind, onto = line, currentT = 0.6)
        requireNotNull(match)
        assertTrue(
            match.t >= 0.6 - PolylineProjection.BACK_WINDOW - 0.001,
            "t = ${match.t}",
        )
    }

    @Test
    fun `un point a un avancement donne revient au bon endroit`() {
        val sample = PolylineProjection.pointAt(line, 0.25)
        requireNotNull(sample)
        val back = PolylineProjection.project(sample.point, onto = line)
        requireNotNull(back)
        assertTrue(abs(back.t - 0.25) < 0.01, "t = ${back.t}")
    }

    @Test
    fun `le cap du segment est celui qu on suit`() {
        val sample = PolylineProjection.pointAt(line, 0.5)
        requireNotNull(sample)
        assertTrue(abs(sample.bearing - 90) < 2, "cap = ${sample.bearing}") // plein est
    }

    @Test
    fun `un trace degenere ne fait rien exploser`() {
        assertNull(PolylineProjection.project(Coordinate.NANTES, onto = emptyList()))
        assertNull(PolylineProjection.project(Coordinate.NANTES, onto = listOf(Coordinate.NANTES)))
        assertEquals(0.0, PolylineProjection.length(emptyList()), 1e-9)
        assertNull(PolylineProjection.pointAt(emptyList(), 0.5))
    }

    /**
     * Deux points identiques donnent une longueur nulle. Le calcul d'avancement
     * diviserait par zéro ; il doit rendre `null` plutôt qu'un `NaN` qui
     * remonterait jusqu'à l'écran.
     */
    @Test
    fun `un trace de longueur nulle ne rend pas NaN`() {
        val degenerate = listOf(Coordinate.NANTES, Coordinate.NANTES)
        assertNull(PolylineProjection.project(Coordinate.NANTES, onto = degenerate))
        assertNull(PolylineProjection.pointAt(degenerate, 0.5))
    }

    @Test
    fun `la fraction d un sommet suit les longueurs cumulees`() {
        assertEquals(0.0, PolylineProjection.tAtIndex(line, 0), 1e-9)
        assertEquals(1.0, PolylineProjection.tAtIndex(line, line.lastIndex), 1e-9)
        assertEquals(0.5, PolylineProjection.tAtIndex(line, 5), 0.01)
    }
}
