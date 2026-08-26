package io.aule.android.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.aule.android.core.designsystem.token.AuleElevation

/**
 * De quoi l'ombre est faite.
 *
 * [NEUTRAL] éloigne une surface du fond. [ACCENT] ne le fait pas : c'est une
 * lueur de marque sous l'action principale, qui la désigne. Les mélanger donne
 * un écran où tout brille et où plus rien ne se détache.
 *
 * La lueur a **baissé d'un tiers**. À 0,28 d'ambiante, le teal débordait de
 * plusieurs points sous le bouton et se lisait comme un second aplat flou plus
 * que comme une lumière — le défaut classique de la lueur colorée, qui salit
 * d'autant plus qu'elle est saturée. À 0,20 elle fait ce pour quoi elle est là :
 * on ne la voit pas, on voit que le bouton flotte.
 */
enum class AuleShadowTint { NEUTRAL, ACCENT }

/**
 * L'ombre du design system.
 *
 * `Modifier.shadow` demande une élévation **et** deux couleurs, et c'est la
 * paire qu'on oublie : laissées par défaut, elles rendent une ombre noire pure
 * qui sur fond nocturne ne se voit pas et sur fond clair salit la surface.
 * Passer par ici, c'est ne plus avoir à s'en souvenir.
 */
@Composable
fun Modifier.auleShadow(
    level: AuleElevation,
    shape: Shape,
    tint: AuleShadowTint = AuleShadowTint.NEUTRAL,
): Modifier {
    val night = AuleTheme.night
    val height = level.height(night)
    if (level == AuleElevation.NONE) return this
    val base = when (tint) {
        AuleShadowTint.NEUTRAL -> Color.Black
        AuleShadowTint.ACCENT -> AuleTheme.tokens.accent.color
    }
    val ambient = when (tint) {
        AuleShadowTint.NEUTRAL -> if (night) 0.45f else 0.14f
        AuleShadowTint.ACCENT -> 0.20f
    }
    val spot = when (tint) {
        AuleShadowTint.NEUTRAL -> if (night) 0.40f else 0.12f
        AuleShadowTint.ACCENT -> 0.14f
    }
    return shadow(
        elevation = height,
        shape = shape,
        ambientColor = base.copy(alpha = ambient),
        spotColor = base.copy(alpha = spot),
    )
}
