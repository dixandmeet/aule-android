package io.aule.android.core.designsystem.component

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La répartition des icônes : Material Symbol dès qu'il existe, Aule dès que
 * le symbole générique mentirait sur le métier.
 */
class AuleGlyphMappingTest {

    @Test
    fun `les glyphes generiques prennent un Material Symbol`() {
        val generic = listOf(
            AuleGlyph.MAIL,
            AuleGlyph.LOCK,
            AuleGlyph.EYE,
            AuleGlyph.EYE_OFF,
            AuleGlyph.SHIELD,
            AuleGlyph.SEARCH,
            AuleGlyph.BACK,
            AuleGlyph.CLOSE,
            AuleGlyph.MENU,
            AuleGlyph.SIGN_OUT,
            AuleGlyph.PERSON,
            AuleGlyph.CHEVRON,
            AuleGlyph.CAMERA,
            AuleGlyph.EDIT,
            AuleGlyph.IMAGE,
            AuleGlyph.TRASH,
            AuleGlyph.CHECK,
            AuleGlyph.SUN,
            AuleGlyph.MOON,
            AuleGlyph.AUTO,
            AuleGlyph.PLAY,
            AuleGlyph.SWAP,
        )
        generic.forEach { glyph ->
            assertTrue(
                !glyph.asImageVector().name.startsWith("Aule."),
                "$glyph doit rester un Material Symbol, pas une icône Aule",
            )
        }
    }

    @Test
    fun `les glyphes metier restent des icones Aule`() {
        val business = listOf(
            AuleGlyph.BUS,
            AuleGlyph.TRAM,
            AuleGlyph.TICKET,
            AuleGlyph.PIN,
            AuleGlyph.HEADING,
            AuleGlyph.ROUTE,
            AuleGlyph.FLAG,
        )
        business.forEach { glyph ->
            assertTrue(
                glyph.asImageVector().name.startsWith("Aule."),
                "$glyph doit rester une icône métier Aule",
            )
        }
    }

    @Test
    fun `l etat plein change le vecteur metier sans changer de famille`() {
        assertEquals("Aule.Heading", AuleGlyph.HEADING.asImageVector().name)
        assertEquals("Aule.HeadingFilled", AuleGlyph.HEADING.asImageVector(filled = true).name)
        assertEquals("Aule.Stop", AuleGlyph.PIN.asImageVector().name)
        assertEquals("Aule.StopFilled", AuleGlyph.PIN.asImageVector(filled = true).name)
    }
}
