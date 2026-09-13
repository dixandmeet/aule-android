package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
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
        bearingBefore: Double? = null,
        bearingAfter: Double? = null,
    ) = RoadManeuver(
        instruction = type,
        modifier = modifier,
        streetName = street,
        location = at,
        distanceMeters = 0.0,
        durationSeconds = 0.0,
        bearingBefore = bearingBefore,
        bearingAfter = bearingAfter,
    )

    @Test
    fun `un virage se lit dans son modificateur`() {
        assertEquals(ManeuverKind.RIGHT, maneuverKindOf("turn", "right"))
        assertEquals(ManeuverKind.SLIGHT_LEFT, maneuverKindOf("turn", "slight left"))
        assertEquals(ManeuverKind.SHARP_LEFT, maneuverKindOf("end of road", "sharp left"))
        assertEquals(ManeuverKind.U_TURN, maneuverKindOf("continue", "uturn"))
        assertEquals(ManeuverKind.DEPART, maneuverKindOf("depart"))
        assertEquals(ManeuverKind.ROUNDABOUT, maneuverKindOf("roundabout"))
        assertEquals(ManeuverKind.STRAIGHT, maneuverKindOf("new name"))
        assertEquals(ManeuverKind.UNKNOWN, maneuverKindOf("turn", "diagonalement"))
    }

    /**
     * ⚠️ **La sortie d'un giratoire n'est pas du bruit.** Un anneau rend deux
     * manœuvres — l'entrée, qui porte le numéro de sortie et le nom de la voie,
     * puis la sortie, qui répète le même numéro. Les jeter retirerait la seule
     * borne qui dise *quand* on quitte l'anneau ; les confondre avec l'entrée
     * ferait annoncer « prendre le rond-point » une seconde fois, à l'instant
     * précis où l'on en sort.
     */
    @Test
    fun `la sortie d un giratoire se distingue de son entree`() {
        assertEquals(ManeuverKind.ROUNDABOUT, maneuverKindOf("roundabout turn", "right"))
        assertEquals(ManeuverKind.ROUNDABOUT, maneuverKindOf("rotary", "slight right"))
        assertEquals(ManeuverKind.ROUNDABOUT_EXIT, maneuverKindOf("exit roundabout", "right"))
        assertEquals(ManeuverKind.ROUNDABOUT_EXIT, maneuverKindOf("exit rotary", "right"))
    }

    @Test
    fun `la sortie d un giratoire est gardee, et ne s annonce pas`() {
        val pinned = listOf(
            PinnedManeuver(ManeuverKind.ROUNDABOUT, 0.20, "Route de Pornic"),
            PinnedManeuver(ManeuverKind.ROUNDABOUT_EXIT, 0.21, "Route de Pornic"),
            PinnedManeuver(ManeuverKind.LEFT, 0.60),
        )
        // Une fois l'anneau abordé, ce qui vient est le virage d'après — pas la
        // sortie, qui n'apprend rien de neuf.
        assertEquals(ManeuverKind.LEFT, nextManeuver(pinned, 0.205, 1000.0)!!.maneuver.kind)
    }

    /**
     * ## ⚠️ Le modificateur ment, et les caps disent quand
     *
     * Le côté vient de la topologie du carrefour, pas de l'angle parcouru.
     * Place Saint-Pierre, le moteur rend `turn` / `left` avec `bearingBefore:
     * 81` et `bearingAfter: 81` — zéro degré, et une flèche à gauche là où la
     * place se traverse tout droit. Deux pas `turn` sur treize, sur les trois
     * trajets nantais d'essai du 25/08/2026.
     */
    @Test
    fun `un cote contredit par la mesure cede a la mesure`() {
        assertEquals(
            ManeuverKind.STRAIGHT,
            maneuverKindOf("turn", "left", bearingBefore = 81.0, bearingAfter = 81.0),
        )
        // Le passage par le nord se compte au plus court : 350° → 10° vaut +20°,
        // pas −340°. Un « serrer à gauche » y deviendrait un virage à gauche
        // franc si l'on soustrayait bêtement.
        assertEquals(
            ManeuverKind.SLIGHT_RIGHT,
            maneuverKindOf("turn", "left", bearingBefore = 350.0, bearingAfter = 10.0),
        )
    }

    /**
     * **Tolérance d'un cran, et pas zéro.** Le moteur voit le carrefour, la
     * mesure ne voit que deux caps : un virage qu'il dit « franc » et que la
     * mesure classe « ordinaire » est un désaccord de vocabulaire, pas une
     * erreur de direction.
     */
    @Test
    fun `un desaccord d un cran laisse la main au moteur`() {
        // 100° mesurés : « à droite ». Le moteur dit « franchement à droite »,
        // un cran plus loin sur le même côté — on le croit.
        assertEquals(
            ManeuverKind.SHARP_RIGHT,
            maneuverKindOf("turn", "sharp right", bearingBefore = 0.0, bearingAfter = 100.0),
        )
        // Deux crans, et de l'autre côté de l'axe : la mesure gagne.
        assertEquals(
            ManeuverKind.RIGHT,
            maneuverKindOf("turn", "slight left", bearingBefore = 0.0, bearingAfter = 100.0),
        )
    }

    /**
     * ⚠️ **Un demi-tour annoncé est toujours cru.** Le moteur le publie quand la
     * route se referme sur elle-même, une configuration où les deux caps se
     * ressemblent trop pour qu'on les départage — un demi-tour par un rond-point
     * d'échangeur mesure trente degrés. Le contredire ferait rater la seule
     * manœuvre qu'on ne peut pas rattraper.
     */
    @Test
    fun `un demi-tour ne se laisse pas corriger`() {
        assertEquals(
            ManeuverKind.U_TURN,
            maneuverKindOf("turn", "uturn", bearingBefore = 0.0, bearingAfter = 30.0),
        )
        // Et réciproquement : à plus de cent soixante-dix degrés, aucun autre mot
        // ne décrit ce qu'on fait.
        assertEquals(
            ManeuverKind.U_TURN,
            maneuverKindOf("turn", "right", bearingBefore = 0.0, bearingAfter = 178.0),
        )
    }

    /**
     * ⚠️ **Une bretelle et une insertion gardent le côté du moteur, même
     * contredit.** Leur modificateur ne décrit pas un angle de volant mais de
     * quel côté de la chaussée on se tient : on « reste à droite » dans une
     * fourche dont les deux branches partent à moins de cinq degrés l'une de
     * l'autre. La mesure n'y parle que dans le silence.
     */
    @Test
    fun `une fourche garde le cote du moteur, la mesure ne parle que dans le silence`() {
        assertEquals(
            ManeuverKind.FORK,
            maneuverKindOf("fork", "slight right", bearingBefore = 0.0, bearingAfter = 2.0),
        )
        assertEquals(
            ManeuverKind.RAMP,
            maneuverKindOf("off ramp", "slight right", bearingBefore = 0.0, bearingAfter = 2.0),
        )
    }

    /**
     * ⚠️ **Les caps voyagent ensemble ou pas du tout** : un angle est une
     * différence, et une différence à laquelle il manque un terme ne vaut pas
     * zéro degré — elle ne vaut rien. `bearingBefore` ne veut d'ailleurs rien
     * dire sur un `depart`, où le moteur met 0.
     */
    @Test
    fun `un seul cap n arbitre rien`() {
        assertEquals(
            ManeuverKind.LEFT,
            maneuverKindOf("turn", "left", bearingBefore = 81.0),
        )
        assertEquals(
            ManeuverKind.LEFT,
            maneuverKindOf("turn", "left", bearingAfter = 81.0),
        )
        // Un départ ne tourne pas, et son cap d'avant vaut 0 par convention : ni
        // l'un ni l'autre ne doit teinter ce qu'on affiche.
        assertEquals(
            ManeuverKind.DEPART,
            maneuverKindOf("depart", "right", bearingBefore = 0.0, bearingAfter = 171.0),
        )
    }

    /**
     * Sans côté annoncé, la mesure est tout ce qu'on a — et un type que le
     * moteur publiera demain n'a qu'elle. Muet des deux, il reste inconnu :
     * mieux vaut « continuer » qu'un côté tiré au sort.
     */
    @Test
    fun `la mesure comble le silence du moteur`() {
        assertEquals(
            ManeuverKind.RIGHT,
            maneuverKindOf("new name", bearingBefore = 0.0, bearingAfter = 90.0),
        )
        assertEquals(
            ManeuverKind.SLIGHT_LEFT,
            maneuverKindOf("virage à la nantaise", bearingBefore = 90.0, bearingAfter = 60.0),
        )
        assertEquals(ManeuverKind.UNKNOWN, maneuverKindOf("virage à la nantaise"))
    }

    @Test
    fun `un virage agrafe garde le cote que la mesure a corrige`() {
        val painted = listOf(at(0.0, 0.0), at(500.0, 0.0), at(1000.0, 0.0))
        val pinned = pinManeuvers(
            painted,
            listOf(
                maneuver(
                    "turn",
                    at(500.0, 0.0),
                    modifier = "left",
                    bearingBefore = 81.0,
                    bearingAfter = 81.0,
                ),
            ),
        )
        assertEquals(ManeuverKind.STRAIGHT, pinned.single().kind)
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
}
