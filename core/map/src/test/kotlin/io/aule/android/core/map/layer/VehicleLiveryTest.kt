package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La livrée « neutre + accent » et le halo des véhicules choisis.
 *
 * Les nombres ci-dessous ne sont pas ceux du code relus au miroir : ce sont ceux de la spec
 * commune aux trois plateformes (`voyageur/docs/vehicules-livree-neutre-accent.md`). Si l'un d'eux
 * change, ce test échoue — et c'est le moment de se demander si iOS et le web ont suivi.
 */
class VehicleLiveryTest {

    private fun vehicle(
        mode: TransportMode = TransportMode.BUS,
        lineId: String = "C6",
        lineName: String = "C6",
        lineKey: String? = null,
    ) = TransportVehicle(
        id = "v1",
        mode = mode,
        feed = VehicleFeed.LIVE,
        lineId = lineId,
        lineName = lineName,
        coordinate = Coordinate(latitude = 47.2136, longitude = -1.5573),
        lineKey = lineKey,
    )

    // ------------------------------------------------------------------ la palette

    @Test
    fun `la palette neutre est celle de la spec`() {
        assertEquals(0xF4F6F8, VehicleLivery.neutral(night = false).body)
        assertEquals(0x5E6874, VehicleLivery.neutral(night = false).outline)
        assertEquals(0x222A33, VehicleLivery.neutral(night = false).ink)
        assertEquals(0x343C48, VehicleLivery.neutral(night = true).body)
        assertEquals(0xB8C0CB, VehicleLivery.neutral(night = true).outline)
        assertEquals(0x0B0F14, VehicleLivery.neutral(night = true).ink)
    }

    @Test
    fun `le turquoise est celui de la spec, trait et lueur`() {
        assertEquals(0x17A2A5, VehicleLivery.turquoiseStroke(night = false))
        assertEquals(0x44B4B6, VehicleLivery.turquoiseGlow(night = false))
        assertEquals(0x5FD0D3, VehicleLivery.turquoiseStroke(night = true))
        assertEquals(0x74D0D2, VehicleLivery.turquoiseGlow(night = true))
        assertEquals(0.55, VehicleLivery.OUTLINE_ALPHA)
        assertEquals(1.6, VehicleLivery.SELECTED_STROKE_PT)
    }

    @Test
    fun `de nuit la carrosserie est plus sombre que de jour, et le contour plus clair`() {
        val day = VehicleLivery.neutral(false)
        val night = VehicleLivery.neutral(true)
        fun luma(rgb: Int) = ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3
        assertTrue(luma(night.body) < luma(day.body))
        assertTrue(luma(night.outline) > luma(day.outline))
    }

    // ------------------------------------------------------------------- l'accent

    @Test
    fun `l'accent est la couleur de ligne telle quelle`() {
        assertEquals(0x7B2D8E, VehicleLivery.accent("#7B2D8E", TransportMode.BUS, night = false))
        // Sans l'éclaircir de nuit, sans la saturer : la même teinte aux deux ambiances.
        assertEquals(0x7B2D8E, VehicleLivery.accent("7B2D8E", TransportMode.BUS, night = true))
        assertEquals(0xFFFFFF, VehicleLivery.accent("#fff", TransportMode.BUS, night = false))
    }

    @Test
    fun `sans couleur lisible l'accent est celui du mode, jamais le gris de ligne inconnue`() {
        for (raw in listOf(null, "", "   ", "#12", "pas une couleur", "#GGGGGG", "#1234567")) {
            val accent = VehicleLivery.accent(raw, TransportMode.TRAM, night = false)
            assertNotEquals(0x525252, accent, "« $raw » est tombé sur le gris de ligne inconnue")
        }
        assertEquals(
            VehicleLivery.accent(null, TransportMode.TER, night = false),
            VehicleLivery.accent("rien", TransportMode.TER, night = false),
        )
        // Et le mode reste lisible : le TER n'est pas teinté comme le bus.
        assertNotEquals(
            VehicleLivery.accent(null, TransportMode.TER, night = false),
            VehicleLivery.accent(null, TransportMode.BUS, night = false),
        )
    }

    @Test
    fun `la couleur d'un vehicule vient du nuancier, par la cle d'abord`() {
        val palette = LinePalette(
            mapOf("naolib:C6" to "#7B2D8E", "C6" to "#0088CE", "aleop:C6" to "#00A650"),
        )
        assertEquals("#7B2D8E", VehicleLivery.lineColorHex(vehicle(lineKey = "naolib:C6"), palette))
        // Sans clé, le numéro nu donne la couleur de l'index.
        assertEquals("#0088CE", VehicleLivery.lineColorHex(vehicle(), palette))
        // Un TER ne prend pas la couleur du bus qui porte son numéro.
        assertEquals(
            "#00A650",
            VehicleLivery.lineColorHex(vehicle(mode = TransportMode.TER), palette),
        )
        assertNull(VehicleLivery.lineColorHex(vehicle(lineId = "ZZ", lineName = "ZZ"), palette))
    }

    @Test
    fun `une couleur se nomme en six chiffres, et une image par couleur`() {
        assertEquals("#00A650", VehicleLivery.css(0x00A650))
        assertEquals("0000FF", VehicleLivery.key(0x0000FF))
        val a = VehicleLivery.IconSpec(TransportMode.BUS, 0x7B2D8E, live = true, contoured = false, night = false)
        assertNotEquals(a.name, a.copy(accent = 0x7B2D8F).name)
        assertNotEquals(a.name, a.copy(live = false).name)
        assertNotEquals(a.name, a.copy(contoured = true).name)
        // La clé du cache porte l'ambiance : une même ligne change de contour d'une ambiance à
        // l'autre, et une clé réduite à la couleur ferait passer les silhouettes de jour pour
        // celles de nuit.
        assertNotEquals(a.name, a.copy(night = true).name)
        assertNotEquals(a.name, a.copy(mode = TransportMode.TRAM).name)
        assertEquals(a.name, a.copy().name)
    }

    // -------------------------------------------------------------------- l'état

    @Test
    fun `l'etat se lit sur le choix et sur le suivi`() {
        assertEquals(VehicleLivery.Role.REST, VehicleLivery.role(false, false, anyChosen = false))
        assertEquals(VehicleLivery.Role.RECEDED, VehicleLivery.role(false, false, anyChosen = true))
        assertEquals(VehicleLivery.Role.SELECTED, VehicleLivery.role(true, false, anyChosen = true))
        assertEquals(VehicleLivery.Role.FOLLOWED, VehicleLivery.role(true, true, anyChosen = true))
    }

    @Test
    fun `les autres reculent quand l'un est choisi, et le choisi s'eleve`() {
        assertEquals(1.0, VehicleLivery.scale(VehicleLivery.Role.REST))
        assertEquals(1.06, VehicleLivery.scale(VehicleLivery.Role.SELECTED))
        assertEquals(1.06, VehicleLivery.scale(VehicleLivery.Role.FOLLOWED))
        assertEquals(0.92, VehicleLivery.scale(VehicleLivery.Role.RECEDED))
        assertEquals(0.82, VehicleLivery.opacity(VehicleLivery.Role.RECEDED))
        assertEquals(1.0, VehicleLivery.opacity(VehicleLivery.Role.REST))
        assertEquals(1.0, VehicleLivery.opacity(VehicleLivery.Role.SELECTED))
        // Un volume n'ajoute pas l'« élévation » à l'exagération du suivi (×1,8).
        assertEquals(1.0, VehicleLivery.volumeScale(VehicleLivery.Role.FOLLOWED))
        assertEquals(0.92, VehicleLivery.volumeScale(VehicleLivery.Role.RECEDED))
    }

    @Test
    fun `l'ombre de contact pese x1,5 sous le vehicule suivi seulement`() {
        assertEquals(1.5, VehicleLivery.contactShadow(VehicleLivery.Role.FOLLOWED))
        for (role in listOf(VehicleLivery.Role.REST, VehicleLivery.Role.SELECTED, VehicleLivery.Role.RECEDED)) {
            assertEquals(1.0, VehicleLivery.contactShadow(role))
        }
    }

    @Test
    fun `le contour turquoise est celui du choisi, suivi ou non`() {
        assertTrue(VehicleLivery.isContoured(VehicleLivery.Role.SELECTED))
        assertTrue(VehicleLivery.isContoured(VehicleLivery.Role.FOLLOWED))
        assertFalse(VehicleLivery.isContoured(VehicleLivery.Role.REST))
        assertFalse(VehicleLivery.isContoured(VehicleLivery.Role.RECEDED))
    }

    @Test
    fun `le suivi se dessine au dessus du choisi, qui se dessine au dessus des autres`() {
        val rank = VehicleLivery::drawRank
        assertTrue(rank(VehicleLivery.Role.FOLLOWED) > rank(VehicleLivery.Role.SELECTED))
        assertTrue(rank(VehicleLivery.Role.SELECTED) > rank(VehicleLivery.Role.RECEDED))
        assertEquals(rank(VehicleLivery.Role.REST), rank(VehicleLivery.Role.RECEDED))
    }

    // --------------------------------------------------------------------- le halo

    @Test
    fun `un vehicule ordinaire ou reculé n'a pas de halo`() {
        assertNull(VehicleHalo.paint(VehicleLivery.Role.REST, 40.0, 0.0, false))
        assertNull(VehicleHalo.paint(VehicleLivery.Role.RECEDED, 40.0, 0.0, false))
    }

    @Test
    fun `le choisi porte un anneau fixe de 1,3 fois sa longueur, a 0,9`() {
        for (seconds in listOf(0.0, 0.7, 1.4, 2.1)) {
            val halo = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.SELECTED, 40.0, seconds, false))
            assertEquals(52.0, halo.diameterPt, 1e-9)
            assertEquals(0.9, halo.opacity, 1e-9)
            // Un anneau seul : la lueur est celle du véhicule suivi.
            assertFalse(halo.glow)
        }
    }

    @Test
    fun `le suivi pulse sur 2,8 secondes, entre 0,94 et 1,06 de son diametre`() {
        val length = 40.0
        val base = 1.55 * length // 62, dans les bornes
        val seen = (0..280).map { step ->
            assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, length, step / 100.0, false))
        }
        assertTrue(seen.all { it.glow })
        assertEquals(base * 0.94, seen.minOf { it.diameterPt }, 0.05)
        assertEquals(base * 1.06, seen.maxOf { it.diameterPt }, 0.05)
        assertEquals(0.55, seen.minOf { it.opacity }, 0.005)
        assertEquals(0.95, seen.maxOf { it.opacity }, 0.005)

        // La période : une pulsation de 2,8 s revient à son point de départ.
        val start = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, length, 0.37, false))
        val after = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, length, 0.37 + 2.8, false))
        assertEquals(start.diameterPt, after.diameterPt, 1e-9)
        assertEquals(start.opacity, after.opacity, 1e-9)
        // Et pas plus tôt : à mi-période, on est de l'autre côté de la vague.
        val half = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, length, 0.37 + 1.4, false))
        assertNotEquals(start.diameterPt, half.diameterPt)
    }

    @Test
    fun `la pulsation est lente et douce - sinus, sans saut`() {
        // 2,8 s : deux battements de plus d'une seconde et demie. Rien à voir avec un clignotant.
        assertTrue(VehicleHalo.PERIOD_SECONDS >= 2.5)
        var previous = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 40.0, 0.0, false)).opacity
        var step = 0.0
        for (i in 1..2800) {
            val now = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 40.0, i / 1000.0, false)).opacity
            step = maxOf(step, abs(now - previous))
            previous = now
        }
        assertTrue(step < 0.002, "un saut d'opacité de $step en une milliseconde")
    }

    @Test
    fun `le diametre du suivi est borne entre 44 et 140 points`() {
        // Un tout petit véhicule : le halo ne descend pas sous 44 pt (avant pulsation).
        val small = (0..280).map { VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 5.0, it / 100.0, false)!! }
        assertEquals(44.0 * 1.06, small.maxOf { it.diameterPt }, 0.05)
        // Un très grand : il ne dépasse pas 140 pt.
        val large = (0..280).map { VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 400.0, it / 100.0, false)!! }
        assertEquals(140.0 * 1.06, large.maxOf { it.diameterPt }, 0.05)
        assertTrue(large.minOf { it.diameterPt } >= 140.0 * 0.94 - 0.05)
    }

    @Test
    fun `sous reduire les animations le suivi garde un anneau fixe`() {
        val first = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 40.0, 0.0, reduceMotion = true))
        for (seconds in listOf(0.5, 1.4, 2.0, 99.0)) {
            val later = assertNotNull(VehicleHalo.paint(VehicleLivery.Role.FOLLOWED, 40.0, seconds, true))
            assertEquals(first, later, "l'anneau a bougé à $seconds s malgré « réduire les animations »")
        }
        // Fixe ne veut pas dire éteint : il reste visible, au diamètre de repos.
        assertEquals(62.0, first.diameterPt, 1e-9)
        assertTrue(first.opacity >= 0.55)
    }

    @Test
    fun `la longueur d'ecran suit le fondu entre la silhouette et le volume`() {
        // Un bus de 11 m à 0,4 m par point, exagéré de 1,8 (suivi) : 49,5 pt de volume.
        val volume = 11.0 * 1.8 / 0.4
        assertEquals(20.0, VehicleHalo.screenLengthPt(20.0, 11.0, 1.8, 0.4, volumeFade = 0.0), 1e-9)
        assertEquals(volume, VehicleHalo.screenLengthPt(20.0, 11.0, 1.8, 0.4, volumeFade = 1.0), 1e-9)
        assertEquals((20.0 + volume) / 2, VehicleHalo.screenLengthPt(20.0, 11.0, 1.8, 0.4, volumeFade = 0.5), 1e-9)
        // Une échelle illisible ne produit ni NaN ni infini : on garde la silhouette.
        assertEquals(20.0, VehicleHalo.screenLengthPt(20.0, 11.0, 1.8, Double.NaN, 1.0), 1e-9)
        assertEquals(20.0, VehicleHalo.screenLengthPt(20.0, 11.0, 1.8, 0.0, 1.0), 1e-9)
    }

    // ----------------------------------------------------------------- la silhouette

    @Test
    fun `la longueur de la silhouette est celle de la coque dessinee`() {
        // Le dessin pose le nez à `nose` unités au-dessus du centre et le talon à 12 en dessous,
        // dans une boîte de 48 unités pour 32 points.
        assertEquals((16f + 12f) * 32f / 48f, VehicleLivery.Silhouette.lengthDp(TransportMode.BUS), 1e-4f)
        assertEquals((16f + 12f) * 32f / 48f, VehicleLivery.Silhouette.lengthDp(TransportMode.TRAM), 1e-4f)
        assertEquals((18f + 12f) * 32f / 48f, VehicleLivery.Silhouette.lengthDp(TransportMode.TER), 1e-4f)
        assertEquals((14f + 12f) * 32f / 48f, VehicleLivery.Silhouette.lengthDp(TransportMode.BOAT), 1e-4f)
    }

    @Test
    fun `la bande de toit fait un quart de la largeur de la caisse, et tient dedans`() {
        for (mode in TransportMode.entries) {
            val hull = VehicleLivery.Silhouette.hullWidthUnits(mode)
            val stripe = VehicleLivery.Silhouette.roofStripeUnits(mode)
            assertEquals(0.24f, stripe / hull, 1e-4f)
            // Cernée d'un trait neutre de 1,4 unité de chaque côté, elle ne sort pas de la coque.
            assertTrue(stripe + 2.8f < hull, "$mode : la bande cernée déborde de la coque")
        }
    }

    @Test
    fun `la pastille de loin est un disque de 11 points a coeur de 6`() {
        assertEquals(11.0, VehicleLivery.DOT_DIAMETER_PT)
        assertEquals(6.0, VehicleLivery.DOT_CORE_DIAMETER_PT)
    }

    @Test
    fun `l'ancien rendu reste le defaut`() {
        assertEquals(FleetLook.CLASSIC, FleetRendering().look)
        assertFalse(FleetRendering().isNeutralAccent)
        assertTrue(FleetRendering.VOYAGEUR.isNeutralAccent)
    }
}
