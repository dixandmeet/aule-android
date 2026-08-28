package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/maneuvers_test.dart`. */
class ManeuversTest {

    private fun at(east: Double, north: Double) = Coordinate(
        latitude = 47.2184 + north / 111_320,
        longitude = -1.5536 + east / (111_320 * 0.6785),
    )

    private fun maneuver(
        type: String,
        at: Coordinate,
        modifier: String? = null,
        street: String? = null,
    ) = RoadManeuver(
        instruction = type,
        modifier = modifier,
        streetName = street,
        location = at,
        distanceMeters = 0.0,
        durationSeconds = 0.0,
    )

    @Test
    fun `un virage se lit dans son modificateur`() {
        assertEquals(ManeuverKind.RIGHT, maneuverKindOf("turn", "right"))
        assertEquals(ManeuverKind.SLIGHT_LEFT, maneuverKindOf("turn", "slight left"))
        assertEquals(ManeuverKind.SHARP_LEFT, maneuverKindOf("end of road", "sharp left"))
        assertEquals(ManeuverKind.U_TURN, maneuverKindOf("continue", "uturn"))
        assertEquals(ManeuverKind.DEPART, maneuverKindOf("depart"))
        assertEquals(ManeuverKind.ROUNDABOUT, maneuverKindOf("exit roundabout"))
        assertEquals(ManeuverKind.STRAIGHT, maneuverKindOf("new name"))
        assertEquals(ManeuverKind.UNKNOWN, maneuverKindOf("turn", "diagonalement"))
    }

    @Test
    fun `une manoeuvre sur le trace est retenue`() {
        val painted = listOf(at(0.0, 0.0), at(500.0, 0.0), at(1000.0, 0.0))
        val pinned = pinManeuvers(painted, listOf(maneuver("turn", at(500.0, 0.0), "right")))
        assertEquals(1, pinned.size)
        assertEquals(ManeuverKind.RIGHT, pinned.single().kind)
        assertEquals(0.5, pinned.single().t, 1e-3)
    }

    @Test
    fun `une manoeuvre d une rue voisine est ecartee`() {
        val painted = listOf(at(0.0, 0.0), at(500.0, 0.0), at(1000.0, 0.0))
        val pinned = pinManeuvers(painted, listOf(maneuver("turn", at(500.0, 40.0), "left")))
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun `une position inversee ne s agrafe sur rien`() {
        val painted = listOf(at(0.0, 0.0), at(500.0, 0.0), at(1000.0, 0.0))
        val inverted = Coordinate(latitude = -1.5536, longitude = 47.2184)
        val pinned = pinManeuvers(painted, listOf(maneuver("turn", inverted, "right")))
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun `une manoeuvre ecartee ne fait pas avancer le plancher`() {
        val painted = listOf(at(0.0, 0.0), at(1000.0, 0.0))
        val pinned = pinManeuvers(
            painted,
            listOf(
                maneuver("turn", at(800.0, 60.0), "left"),
                maneuver("turn", at(300.0, 0.0), "right", street = "la bonne"),
            ),
        )
        assertEquals(1, pinned.size)
        assertEquals("la bonne", pinned.single().streetName)
        assertEquals(0.3, pinned.single().t, 1e-3)
    }

    @Test
    fun `une manoeuvre de la jambe finale ne remonte pas sur la premiere`() {
        val aller = listOf(at(0.0, 0.0), at(300.0, 0.0))
        val pinned = pinManeuvers(
            aller,
            listOf(maneuver("turn", at(150.0, 0.0), "right")),
            minT = 0.8,
        )
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun `partir n est jamais ce qui vient`() {
        val pinned = listOf(
            PinnedManeuver(ManeuverKind.DEPART, 0.0),
            PinnedManeuver(ManeuverKind.RIGHT, 0.25, "Rue de l'Ouche Buron"),
            PinnedManeuver(ManeuverKind.LEFT, 0.75),
            PinnedManeuver(ManeuverKind.ARRIVE, 1.0),
        )
        val next = nextManeuver(pinned, 0.0, 4000.0)!!
        assertEquals(ManeuverKind.RIGHT, next.maneuver.kind)
        assertEquals(1000.0, next.meters, 1e-6)
    }

    @Test
    fun `plus rien devant se dit par un rien`() {
        val sansArrivee = listOf(
            PinnedManeuver(ManeuverKind.RIGHT, 0.25),
            PinnedManeuver(ManeuverKind.LEFT, 0.75),
        )
        assertNull(nextManeuver(sansArrivee, 0.9, 4000.0))
    }
    /**
     * Le relevé qui a motivé cette garde : sur `router.project-osrm.org`, les
     * profils `walking` et `driving` rendent la même réponse. Une jambe à pied
     * de 713 m recevait donc les consignes d'un trajet en voiture de 1 199 m.
     */
    @Test
    fun `un routeur qui decrit un autre trajet est ecarte`() {
        assertFalse(roadRouteDescribesLeg(roadMeters = 1_198.6, paintedMeters = 713.5))
    }

    @Test
    fun `deux routeurs d accord sur la meme jambe passent`() {
        assertTrue(roadRouteDescribesLeg(roadMeters = 3_400.0, paintedMeters = 3_423.0))
        assertTrue(
            roadRouteDescribesLeg(roadMeters = 4_100.0, paintedMeters = 3_423.0),
            "vingt pour cent d'écart restent le même chemin, décrit autrement",
        )
    }

    /**
     * Sur une jambe courte, vingt-cinq pour cent ne font que quelques mètres.
     * Deux routeurs ont le droit de ne pas être d'accord sur le côté de la rue
     * où elle s'arrête : c'est la marge absolue qui parle.
     */
    @Test
    fun `une jambe courte garde une marge absolue`() {
        assertTrue(roadRouteDescribesLeg(roadMeters = 130.0, paintedMeters = 90.0))
        assertFalse(roadRouteDescribesLeg(roadMeters = 260.0, paintedMeters = 90.0))
    }

    @Test
    fun `une longueur absurde ne decrit rien`() {
        assertFalse(roadRouteDescribesLeg(roadMeters = 0.0, paintedMeters = 500.0))
        assertFalse(roadRouteDescribesLeg(roadMeters = 500.0, paintedMeters = 0.0))
        assertFalse(roadRouteDescribesLeg(roadMeters = Double.NaN, paintedMeters = 500.0))
        assertFalse(roadRouteDescribesLeg(roadMeters = 500.0, paintedMeters = Double.POSITIVE_INFINITY))
    }

    /**
     * Le numéro de sortie voyage du DTO jusqu'à la manœuvre agrafée : sans lui,
     * la consigne se réduit à « Prendre le rond-point », ce qui ne dit pas quelle
     * branche prendre.
     */
    @Test
    fun `la sortie d un rond-point suit la manoeuvre jusqu au tracé`() {
        val painted = listOf(
            Coordinate(latitude = 47.2184, longitude = -1.5536),
            Coordinate(latitude = 47.2284, longitude = -1.5436),
        )
        val pinned = pinManeuvers(
            painted = painted,
            raw = listOf(
                RoadManeuver(
                    instruction = "roundabout",
                    location = Coordinate(latitude = 47.2234, longitude = -1.5486),
                    distanceMeters = 120.0,
                    durationSeconds = 20.0,
                    exit = 3,
                ),
            ),
        )
        val roundabout = assertNotNull(pinned.singleOrNull())
        assertEquals(ManeuverKind.ROUNDABOUT, roundabout.kind)
        assertEquals(3, roundabout.exit)
    }
}
