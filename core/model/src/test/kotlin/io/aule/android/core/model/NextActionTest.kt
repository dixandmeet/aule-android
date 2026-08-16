package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Port de `SAE/test/next_action_test.dart`. */
class NextActionTest {

    private val points = listOf(
        Coordinate(latitude = 47.2184, longitude = -1.56),
        Coordinate(latitude = 47.2184, longitude = -1.55),
    )

    private fun leg(
        mode: LegMode,
        startT: Double,
        endT: Double,
        meters: Double,
        title: String = "jambe",
        line: String? = null,
    ) = JourneyLeg(
        mode = mode,
        title = title,
        startT = startT,
        endT = endT,
        distanceMeters = meters,
        line = line,
    )

    private fun multimodal() = JourneyPlan(
        points = points,
        distanceMeters = 1250.0,
        destinationLabel = "Commerce",
        legs = listOf(
            leg(LegMode.WALK, 0.0, 0.2, 250.0, "Marche jusqu'à l'arrêt Ranzay"),
            leg(LegMode.TRANSIT, 0.2, 0.8, 750.0, "C1 direction Gare de Chantenay", "C1"),
            leg(LegMode.WALK, 0.8, 1.0, 250.0, "Marche jusqu'à votre destination"),
        ),
    )

    private fun voiture() = JourneyPlan(
        points = points,
        distanceMeters = 4700.0,
        destinationLabel = "Dépôt",
        arrivalAt = java.time.Instant.parse("2026-08-14T09:42:00Z"),
        duration = java.time.Duration.ofMinutes(17),
        legs = listOf(leg(LegMode.CAR, 0.0, 1.0, 4700.0, "En voiture jusqu'au dépôt")),
    )

    private fun at(
        plan: JourneyPlan,
        t: Double,
        maneuvers: List<PinnedManeuver> = emptyList(),
        stopsToAlight: Int? = null,
        alightStopName: String? = null,
        transferStopName: String? = null,
        transferPlatform: String? = null,
    ) = nextAction(
        plan = plan,
        progress = journeyProgressAt(plan, t)!!,
        maneuvers = maneuvers,
        stopsToAlight = stopsToAlight,
        alightStopName = alightStopName,
        transferStopName = transferStopName,
        transferPlatform = transferPlatform,
    )!!

    @Test
    fun `aucun resume de trajet n entre dans le bandeau`() {
        val forbidden = listOf("11:42", "17 min", GeoMath.formatDistance(4700.0), "4700")
        for (t in listOf(0.0, 0.1, 0.5, 0.9, 0.995, 1.0)) {
            val action = at(voiture(), t)
            val rendered = listOfNotNull(
                action.leadMeters?.let { GeoMath.formatDistance(it) },
                action.title,
                action.detail,
                action.destinationLabel.takeIf { action.kind == NextActionKind.ARRIVE },
            ).joinToString(" | ")
            for (word in forbidden) {
                assertFalse(rendered.contains(word), "à t=$t le bandeau porte « $word » : $rendered")
            }
        }
    }

    @Test
    fun `sur un trajet d une seule jambe, le repli ne porte aucune distance`() {
        assertNull(at(voiture(), 0.2).leadMeters)
        assertTrue(at(multimodal(), 0.05).leadMeters != null)
    }

    @Test
    fun `voiture — la rue prend le titre`() {
        val action = at(
            voiture(),
            0.0,
            maneuvers = listOf(
                PinnedManeuver(ManeuverKind.LEFT, 230.0 / 4700.0, "Avenue de la Gare de Saint-Joseph"),
            ),
        )
        assertEquals(NextActionKind.MANEUVER, action.kind)
        assertEquals("Avenue de la Gare de Saint-Joseph", action.title)
        assertEquals(ManeuverKind.LEFT, action.maneuver)
        assertEquals(230.0, action.leadMeters!!, 1e-6)
    }

    @Test
    fun `transport — descendre`() {
        val action = at(multimodal(), 0.5, stopsToAlight = 2, alightStopName = "Ranzay")
        assertEquals(NextActionKind.ALIGHT, action.kind)
        assertEquals(2, action.leadStops)
        assertEquals("Ranzay", action.title)
        assertEquals("C1", action.line)
    }

    @Test
    fun `une marche d acces n est pas une correspondance`() {
        assertTrue(at(multimodal(), 0.02).kind != NextActionKind.TRANSFER)
    }

    @Test
    fun `la montee s annonce quand l arret est en vue`() {
        val action = at(multimodal(), 0.18)
        assertEquals(NextActionKind.BOARD, action.kind)
        assertEquals("C1", action.title)
    }

    @Test
    fun `un virage de la marche finale n est pas annonce pendant la premiere`() {
        val action = at(
            multimodal(),
            0.05,
            maneuvers = listOf(PinnedManeuver(ManeuverKind.RIGHT, 0.9, "Rue finale")),
        )
        assertTrue(action.kind != NextActionKind.MANEUVER)
        assertTrue(action.title != "Rue finale")
    }

    @Test
    fun `l arrivee passe avant tout le reste`() {
        val action = at(
            multimodal(),
            1.0,
            maneuvers = listOf(PinnedManeuver(ManeuverKind.RIGHT, 1.0)),
            stopsToAlight = 3,
        )
        assertEquals(NextActionKind.ARRIVE, action.kind)
        assertEquals("Commerce", action.destinationLabel)
    }
}
