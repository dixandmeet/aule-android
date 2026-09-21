package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.geo.PolylinePath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le cap d'un véhicule qui glisse.
 *
 * Il se vérifie ici et pas à l'écran : un bus posé de biais dans son virage ne
 * se voit qu'en roulant, à z18, sur la poignée de véhicules qui tournent au
 * moment où on regarde. La géométrie, elle, se relit à volonté.
 *
 * Les tracés sont écrits en mètres autour d'un point de Nantes, puis rendus en
 * coordonnées : c'est ainsi qu'on raisonne sur une voirie, et c'est ce que le
 * serveur envoie.
 */
class VehicleGlideTest {

    private val commerce = Coordinate(latitude = 47.2136, longitude = -1.5573)

    private val metresPerDegreeLat = Math.PI * GeoMath.EARTH_RADIUS_M / 180
    private val metresPerDegreeLon = metresPerDegreeLat * cos(Math.toRadians(commerce.latitude))

    /** Un point à [east] mètres à l'est et [north] au nord du Commerce. */
    private fun at(east: Double, north: Double) = Coordinate(
        latitude = commerce.latitude + north / metresPerDegreeLat,
        longitude = commerce.longitude + east / metresPerDegreeLon,
    )

    /** Une ligne droite de [length] mètres, de cap [heading], échantillonnée tous les [step]. */
    private fun straight(heading: Double, length: Double, step: Double = 23.0): PolylinePath {
        val east = sin(Math.toRadians(heading))
        val north = cos(Math.toRadians(heading))
        val points = buildList {
            var travelled = 0.0
            while (travelled < length) {
                add(at(east * travelled, north * travelled))
                travelled += step
            }
            add(at(east * length, north * length))
        }
        return PolylinePath(points)
    }

    /**
     * Un carrefour : une droite plein nord, un quart de tour à gauche de rayon
     * [radius], puis une droite plein ouest.
     *
     * Les alignements sont échantillonnés grossièrement et l'arc finement, comme
     * le fait la voirie servie par le serveur : 23 m entre deux sommets en
     * médiane, 5 m au premier décile — les sommets se resserrent là où ça tourne.
     */
    private fun streetCorner(radius: Double = 25.0, approach: Double = 40.0): PolylinePath {
        val points = buildList {
            var travelled = -approach
            while (travelled < 0) {
                add(at(east = 0.0, north = travelled))
                travelled += 20.0
            }
            val arc = Math.PI / 2 * radius
            var along = 0.0
            while (along <= arc + 1e-9) {
                val angle = along / radius
                // Centre du virage à l'ouest : on entre plein nord, on sort plein ouest.
                add(at(east = -radius + radius * cos(angle), north = radius * sin(angle)))
                along += 5.0
            }
            var after = 0.0
            while (after <= approach) {
                add(at(east = -radius - after, north = radius))
                after += 20.0
            }
        }
        return PolylinePath(points)
    }

    /** Là où le virage commence, en mètres depuis le début du tracé. */
    private val turnStart = 40.0

    private fun degreesBetween(a: Double, b: Double) = abs(GeoMath.shortestHeadingDelta(a, b))

    // ------------------------------------------------------------ la voie droite

    @Test
    fun `dans une ligne droite le cap est celui de la voie, partout`() {
        val path = straight(heading = 63.0, length = 160.0)
        var distance = 0.0
        while (distance <= path.length) {
            val tangent = assertNotNull(VehicleGlide.tangent(path, distance, spanMeters = 11.0))
            assertTrue(
                degreesBetween(tangent, 63.0) < 0.5,
                "à $distance m le cap vaut $tangent au lieu de 63",
            )
            distance += 3.0
        }
    }

    @Test
    fun `aux deux bouts du trace la fenetre glisse au lieu de se refermer`() {
        // Un tram de 28 m sur un tracé de 41 m — la longueur médiane servie : la
        // fenêtre ne tient pas centrée, et pourtant elle doit rester entière.
        val path = straight(heading = 180.0, length = 41.0)
        val atStart = assertNotNull(VehicleGlide.tangent(path, 0.0, spanMeters = 28.0))
        val atEnd = assertNotNull(VehicleGlide.tangent(path, path.length, spanMeters = 28.0))
        assertTrue(degreesBetween(atStart, 180.0) < 0.5)
        assertTrue(degreesBetween(atEnd, 180.0) < 0.5)
    }

    // ------------------------------------------------------------------ le virage

    @Test
    fun `dans un virage le cap tourne progressivement, sans palier`() {
        val path = streetCorner()
        val span = 11.0
        var distance = 0.0
        var previous = assertNotNull(VehicleGlide.tangent(path, 0.0, span))
        var largestStep = 0.0
        var swept = 0.0
        while (distance <= path.length) {
            val tangent = assertNotNull(VehicleGlide.tangent(path, distance, span))
            val step = GeoMath.shortestHeadingDelta(previous, tangent)
            // Tourner, oui ; sauter, non. Le cap du seul segment sous le point
            // d'ancrage basculait d'un palier entier au passage d'un sommet — et
            // les sommets sont à 23 m les uns des autres.
            largestStep = maxOf(largestStep, abs(step))
            // Et toujours dans le même sens : un virage à gauche ne se reprend pas.
            assertTrue(step <= 0.5, "à $distance m le cap est reparti à droite de $step°")
            swept += step
            previous = tangent
            distance += 1.0
        }
        assertTrue(largestStep < 4.0, "le cap saute de $largestStep° d'un mètre à l'autre")
        // Un carrefour pris en entier, c'est un quart de tour de cap, ni plus ni
        // moins : les deux alignements encadrent le virage.
        assertTrue(abs(swept) > 88.0, "le carrefour n'a fait tourner le cap que de ${abs(swept)}°")
    }

    @Test
    fun `les alignements droits du carrefour restent parfaitement dans l'axe`() {
        val path = streetCorner()
        val span = 11.0
        // Le véhicule entre plein nord et sort plein ouest : hors du virage, la
        // corde ne doit rien inventer.
        assertTrue(degreesBetween(assertNotNull(VehicleGlide.tangent(path, 10.0, span)), 0.0) < 0.5)
        val nearEnd = assertNotNull(VehicleGlide.tangent(path, path.length - 10.0, span))
        assertTrue(degreesBetween(nearEnd, 270.0) < 0.5)
    }

    @Test
    fun `un tram de 28 m s'engage dans la courbe avant un bus de 11 m`() {
        val path = streetCorner()
        // Au tout début du virage, le long véhicule couvre déjà une portion qui
        // tourne quand le court est encore sur l'alignement droit.
        val bus = assertNotNull(VehicleGlide.tangent(path, turnStart, spanMeters = 11.0))
        val tram = assertNotNull(VehicleGlide.tangent(path, turnStart, spanMeters = 28.0))
        assertTrue(
            degreesBetween(tram, 0.0) > degreesBetween(bus, 0.0) + 2.0,
            "bus à $bus°, tram à $tram° : le tram devrait être plus engagé",
        )
    }

    // -------------------------------------------------------- ce que la voie tait

    @Test
    fun `un trace dont les sommets se confondent ne dicte aucun cap`() {
        // L'arrondi du serveur est à cinq décimales, soit 1,1 m : un véhicule à
        // quai peut arriver avec deux sommets identiques.
        val still = PolylinePath(listOf(commerce, commerce, commerce))
        assertNull(VehicleGlide.tangent(still, 0.0, spanMeters = 11.0))
        assertNull(VehicleGlide.tangent(PolylinePath(listOf(commerce)), 0.0, spanMeters = 11.0))
    }

    @Test
    fun `sans voie, le cap precedent est garde plutot qu'un nord invente`() {
        assertEquals(212.0, VehicleGlide.heading(previous = 212.0, aim = null, dtSeconds = 0.016))
    }

    // ------------------------------------------------------------------ le lissage

    @Test
    fun `le rattrapage ne depend pas de la cadence d'affichage`() {
        val target = 90.0
        var slow = 0.0
        var fast = 0.0
        // Une seconde de glisse, à 30 Hz puis à 120 Hz.
        repeat(30) { slow = VehicleGlide.heading(slow, target, dtSeconds = 1 / 30.0) }
        repeat(120) { fast = VehicleGlide.heading(fast, target, dtSeconds = 1 / 120.0) }
        assertTrue(
            abs(slow - fast) < 1.0,
            "30 Hz rend $slow° et 120 Hz $fast° : la rotation suit l'écran, pas l'horloge",
        )
        // Et en une seconde, le cap est rejoint : c'est ce que l'ancien code
        // mettait une demi-minute à faire.
        assertTrue(degreesBetween(slow, target) < 5.0, "après une seconde il reste $slow°")
    }

    @Test
    fun `un redessin hors de la boucle d'image ne fait pas tourner la caisse`() {
        assertEquals(41.0, VehicleGlide.heading(previous = 41.0, aim = 300.0, dtSeconds = 0.0))
    }

    @Test
    fun `une image trop longue ne rattrape pas tout le retard d'un bloc`() {
        // Application reprise, horloge repartie : l'écart peut valoir des secondes.
        val afterHitch = VehicleGlide.heading(previous = 0.0, aim = 90.0, dtSeconds = 12.0)
        assertTrue(afterHitch < 45.0, "une seule image a rattrapé $afterHitch° sur 90")
    }

    @Test
    fun `un demi-tour se pose au lieu de se jouer`() {
        // Au terminus, la course repart en sens inverse : la caisse ne doit pas
        // pivoter sur elle-même au milieu du quai.
        assertEquals(190.0, VehicleGlide.heading(previous = 10.0, aim = 190.0, dtSeconds = 0.016))
    }

    // ----------------------------------------------------- la glisse, bout en bout

    @Test
    fun `sur un horizon entier, la caisse finit dans l'axe de sa voie`() {
        // Le scénario que la flotte sert vraiment : dix secondes de glisse, un
        // carrefour dedans, soixante images par seconde. Mesuré le 18/09/2026, un
        // véhicule sur cinq voit son cap tourner de plus de 20° sur un horizon.
        val path = streetCorner()
        val span = 11.0
        var heading = 0.0 // le cap relevé au début de l'horizon : plein nord.
        var frame = 0
        val frames = 600
        while (frame < frames) {
            val distance = path.length * frame / frames
            heading = VehicleGlide.heading(
                previous = heading,
                aim = VehicleGlide.tangent(path, distance, span),
                dtSeconds = 1 / 60.0,
            )
            frame++
        }
        // La voie est plein ouest en sortie de carrefour, et la caisse aussi.
        // L'ancienne dérivation finissait l'horizon encore tournée vers le nord.
        assertTrue(
            degreesBetween(heading, 270.0) < 3.0,
            "en sortie de carrefour la caisse regarde $heading° pour une voie à 270°",
        )
    }
}
