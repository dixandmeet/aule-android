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
            AuleGlyph.EXPLORE,
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
    fun `chaque icone de favori a un dessin, et l epingle reste metier`() {
        io.aule.android.core.model.SavedPlaceIcon.entries.forEach { icon ->
            // Un `when` exhaustif suffit au compilateur ; ce test attrape le cas
            // qu'il ne voit pas — une valeur neuve renvoyée vers le même dessin
            // qu'une autre, donc deux intentions qu'on ne distingue plus.
            assertTrue(icon.asImageVector().name.isNotEmpty(), "$icon n'a pas de dessin")
        }
        val distinct = io.aule.android.core.model.SavedPlaceIcon.entries
            .map { it.asImageVector().name }
            .distinct()
        assertEquals(io.aule.android.core.model.SavedPlaceIcon.entries.size, distinct.size)
        assertEquals(
            "Aule.Stop",
            io.aule.android.core.model.SavedPlaceIcon.PIN.asImageVector().name,
        )
    }

    @Test
    fun `l etat plein change le vecteur metier sans changer de famille`() {
        assertEquals("Aule.Heading", AuleGlyph.HEADING.asImageVector().name)
        assertEquals("Aule.HeadingFilled", AuleGlyph.HEADING.asImageVector(filled = true).name)
        assertEquals("Aule.Stop", AuleGlyph.PIN.asImageVector().name)
        assertEquals("Aule.StopFilled", AuleGlyph.PIN.asImageVector(filled = true).name)
    }
}
