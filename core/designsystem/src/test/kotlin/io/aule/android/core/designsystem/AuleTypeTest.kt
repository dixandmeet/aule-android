package io.aule.android.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * La garde de l'échelle Material 3.
 *
 * Les chiffres viennent des jetons `TypeScaleTokens` de
 * `androidx.compose.material3`. Un tracking de −0,25 à la place de −0,2 sur
 * `displayLarge` ne se voit pas en revue ; il se voit dès que le thème n'est
 * plus celui que Material documente.
 */
class AuleTypeTest {

    @Test
    fun `chaque slot public reprend le jeton Material 3`() {
        val t = auleTypography()

        assertSlot(t.displayLarge, 57f, 64f, -0.2f, FontWeight.Normal)
        assertSlot(t.displayMedium, 45f, 52f, 0f, FontWeight.Normal)
        assertSlot(t.displaySmall, 36f, 44f, 0f, FontWeight.Normal)

        assertSlot(t.headlineLarge, 32f, 40f, 0f, FontWeight.Normal)
        assertSlot(t.headlineMedium, 28f, 36f, 0f, FontWeight.Normal)
        assertSlot(t.headlineSmall, 24f, 32f, 0f, FontWeight.Normal)

        assertSlot(t.titleLarge, 22f, 28f, 0f, FontWeight.Normal)
        assertSlot(t.titleMedium, 16f, 24f, 0.2f, FontWeight.Medium)
        assertSlot(t.titleSmall, 14f, 20f, 0.1f, FontWeight.Medium)

        assertSlot(t.bodyLarge, 16f, 24f, 0.5f, FontWeight.Normal)
        assertSlot(t.bodyMedium, 14f, 20f, 0.2f, FontWeight.Normal)
        assertSlot(t.bodySmall, 12f, 16f, 0.4f, FontWeight.Normal)

        assertSlot(t.labelLarge, 14f, 20f, 0.1f, FontWeight.Medium)
        assertSlot(t.labelMedium, 12f, 16f, 0.5f, FontWeight.Medium)
        assertSlot(t.labelSmall, 11f, 16f, 0.5f, FontWeight.Medium)
    }

    @Test
    fun `toute l echelle s ecrit en Roboto`() {
        val t = auleTypography()
        listOf(
            t.displayLarge, t.displayMedium, t.displaySmall,
            t.headlineLarge, t.headlineMedium, t.headlineSmall,
            t.titleLarge, t.titleMedium, t.titleSmall,
            t.bodyLarge, t.bodyMedium, t.bodySmall,
            t.labelLarge, t.labelMedium, t.labelSmall,
        ).forEach { style ->
            assertEquals(Roboto, style.fontFamily, "un slot n'est pas en Roboto")
        }
    }

    private fun assertSlot(
        style: TextStyle,
        size: Float,
        lineHeight: Float,
        tracking: Float,
        weight: FontWeight,
    ) {
        assertEquals(size.sp, style.fontSize)
        assertEquals(lineHeight.sp, style.lineHeight)
        assertEquals(tracking.sp, style.letterSpacing)
        assertEquals(weight, style.fontWeight)
    }
}
