package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/** Le nuancier fait correspondre deux sources qui n'écrivent pas pareil. */
class LinePaletteTest {

    private val palette = LinePalette(
        mapOf(
            "C6" to "a877b2",
            "2" to "#e30613",
            " 1 " to "00a754",
            "N1" to "   ",
        ),
    )

    @Test
    fun `une ligne connue rend sa couleur`() {
        assertEquals("a877b2", palette.colorOf("C6"))
        assertEquals("#e30613", palette.colorOf("2"))
    }

    /**
     * Le flux de flotte et le catalogue GTFS ne viennent pas de la même table :
     * une casse ou un espace de plus ne doit pas coûter une couleur.
     */
    @Test
    fun `la correspondance ignore la casse et les espaces`() {
        assertEquals("a877b2", palette.colorOf("c6"))
        assertEquals("a877b2", palette.colorOf(" C6 "))
        assertEquals("00a754", palette.colorOf("1"))
    }

    /**
     * Une couleur vide vaut une couleur absente. Laissée passer, elle donnerait
     * un badge noir — pire que le gris de repli, qui au moins se lit.
     */
    @Test
    fun `une couleur vide ou inconnue ne rend rien`() {
        assertNull(palette.colorOf("N1"))
        assertNull(palette.colorOf("C7"))
        assertNull(palette.colorOf(null))
        assertNull(palette.colorOf("  "))
    }

    @Test
    fun `un nuancier vide se reconnait`() {
        assertTrue(LinePalette.EMPTY.isEmpty)
        assertTrue(LinePalette(mapOf("C6" to " ")).isEmpty)
    }
}
