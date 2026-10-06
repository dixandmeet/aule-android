package io.aule.android.core.designsystem.foundation

import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AuleSheetLayoutTest {
    @Test
    fun `le contenu et sa poignee restent sous la barre d etat`() {
        val height = auleSheetContentHeight(800.dp, 24.dp, 28.dp)
        assertEquals(748.dp, height)
        assertEquals(24.dp, 800.dp - height - 28.dp)
    }

    @Test
    fun `une marge de haut explicite reste disponible pour le chrome Pro`() {
        assertEquals(736.dp, auleSheetContentHeight(800.dp, 24.dp, 28.dp, 12.dp))
    }

    @Test
    fun `une fenetre minuscule ne produit pas de hauteur negative`() {
        assertEquals(0.dp, auleSheetContentHeight(40.dp, 24.dp, 28.dp))
        assertEquals(0.dp, auleSheetContentHeight(0.dp, 24.dp, 28.dp, 12.dp))
    }

    @Test
    fun `la carte flottante fermee ne reserve pas une poignee absente`() {
        assertEquals(764.dp, auleSheetContentHeight(800.dp, 24.dp, 0.dp, 12.dp))
    }

    @Test
    fun `le palier normal conserve la hauteur demandee`() {
        assertEquals(448.dp, auleSheetPeekHeight(448.dp, 776.dp))
    }

    @Test
    fun `le paysage conserve deux ancrages distincts`() {
        val expanded = 360.dp - 24.dp
        val peek = auleSheetPeekHeight(448.dp, expanded)
        assertTrue(peek < expanded)
        assertEquals(335.dp, peek)
    }

    @Test
    fun `le palier nul ou negatif ne sort pas de la fenetre`() {
        assertEquals(0.dp, auleSheetPeekHeight(0.dp, 776.dp))
        assertEquals(0.dp, auleSheetPeekHeight((-20).dp, 776.dp))
        assertEquals(0.dp, auleSheetPeekHeight(448.dp, 0.dp))
    }
}
