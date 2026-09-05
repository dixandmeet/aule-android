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
        val avant = PolylineProjection.FORWARD_WINDOW_M / PolylineProjection.length(line)
        val match = progress.advance(line, far)
        requireNotNull(match)
        // Le point visé est à 1,2 km : bien au-delà de ce qu'un rattrapage
        // accepte de corriger, donc la fenêtre tient.
        assertTrue(match.t <= 0.1 + avant + 0.001, "t = ${match.t}")
    }

    /**
     * Le trajet de recette, en petit : un aller vers l'est, un demi-tour, un
     * retour parallèle à trente mètres. C'est la figure d'un giratoire — deux
     * brins du même tracé qui se frôlent.
     */
    private val giratoire: List<Coordinate> =
        (0..10).map { Coordinate(latitude = 47.2136, longitude = -1.5600 + it * 0.0002) } +
            (10 downTo 0).map {
                Coordinate(latitude = 47.21387, longitude = -1.5600 + it * 0.0002)
            }

    /**
     * Le défaut mesuré le 28/08/2026 : la fenêtre laisse la projection passer
     * sur le brin d'en face, l'avancement décroche, et comme la fenêtre arrière
     * ne fait que quarante mètres, plus rien ne ramène — le guidage annonce une
     * sortie d'itinéraire à qui n'a pas quitté sa route.
     */
    @Test
    fun `un decrochage sur le brin d en face se rattrape`() {
        val progress = RouteProgress()
        // On suit le brin aller, jusqu'au tiers.
        val surLAller = Coordinate(latitude = 47.2136, longitude = -1.5594)
        progress.advance(giratoire, surLAller)
        val bon = progress.t
        assertTrue(bon < 0.5, "on est sur l'aller : t = $bon")

        // Un décrochage pose l'avancement sur le brin retour, en face.
        progress.reset(initial = 1.0 - bon)
        // La position, elle, n'a pas bougé : elle est toujours sur l'aller.
        val match = progress.advance(giratoire, surLAller)
        requireNotNull(match)
        assertTrue(
            match.deviationMeters < PolylineProjection.RECOVERY_DEVIATION_M,
            "l'écart doit retomber, il vaut ${match.deviationMeters} m",
        )
        assertEquals(bon, progress.t, 0.05)
    }

    /** Le rattrapage ne doit pas transformer une vraie sortie en fausse alerte. */
    @Test
    fun `une sortie reelle reste une sortie`() {
        val progress = RouteProgress(initial = 0.3)
        // 300 m au nord de la ligne : loin du tracé, quelle que soit la mesure.
        val dehors = Coordinate(latitude = 47.2163, longitude = -1.5540)
        val match = progress.advance(line, dehors)
        requireNotNull(match)
        assertTrue(
            match.deviationMeters > 200,
            "l'écart doit rester grand, il vaut ${match.deviationMeters} m",
        )
    }

    @Test
    fun `un trace vide laisse la progression telle quelle`() {
        val progress = RouteProgress(initial = 0.4)
        assertNull(progress.advance(emptyList(), Coordinate.NANTES))
        assertEquals(0.4, progress.t, 1e-9)
    }
}
