package io.aule.android.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.foundation.AuleMotion
import io.aule.android.core.designsystem.foundation.AuleOpacity

/** Le cran de surface d'une carte. L'échelle porte la hiérarchie ; l'ombre ne la porte pas. */
enum class AuleCardTone { Lowest, Low, Container, High, Highest, Accent, Inverse }

/**
 * La carte d'Aule : un aplat de l'échelle de surfaces, sans ombre.
 *
 * Le décollement vient de la couleur — les crans de conteneur ont été écartés pour ça — et une
 * carte posée dans un volet qui porte déjà son ombre n'a pas à en ajouter une. Un `onClick`
 * en fait une surface cliquable qui s'enfonce sous le doigt.
 */
@Composable
fun AuleCard(
    modifier: Modifier = Modifier,
    tone: AuleCardTone = AuleCardTone.Container,
    shape: Shape = MaterialTheme.shapes.medium,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = null,
    bordered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        AuleCardTone.Lowest -> colors.surfaceContainerLowest
        AuleCardTone.Low -> colors.surfaceContainerLow
        AuleCardTone.Container -> colors.surfaceContainer
        AuleCardTone.High -> colors.surfaceContainerHigh
        AuleCardTone.Highest -> colors.surfaceContainerHighest
        AuleCardTone.Accent -> colors.primaryContainer
        AuleCardTone.Inverse -> colors.inverseSurface
    }
    val ink = when (tone) {
        AuleCardTone.Accent -> colors.onPrimaryContainer
        AuleCardTone.Inverse -> colors.inverseOnSurface
        else -> colors.onSurface
    }
    val border = if (bordered) BorderStroke(AuleStroke.hairline, colors.outlineVariant) else null
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = ink, border = border) {
            Column(content = content)
        }
    } else {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier
                .aulePress(interaction, AuleMotion.PRESSED_SCALE)
                .semantics {
                    if (role != null) this.role = role
                    if (onClickLabel != null) onClick(label = onClickLabel, action = null)
                },
            shape = shape,
            color = container,
            contentColor = ink,
            border = border,
            interactionSource = interaction,
        ) {
            Column(content = content)
        }
    }
}

/**
 * Le verre au-dessus de la carte.
 *
 * Compose ne floute pas ce qu'il y a **derrière** un composable, et la `MapView` est une vue
 * native hors de l'arbre : un vrai verre dépoli demanderait de capturer la carte image par
 * image, au prix du rendu cartographique lui-même. Le brief dit : ne pas sacrifier les FPS.
 *
 * Ce verre-ci coûte un rectangle : la surface à 92 %, un liseré clair d'un point, et une ombre
 * basse. C'est ce que l'œil lit comme du verre, et c'est le même partout — capsule de
 * recherche, contrôles, bandeaux — ce qui compte davantage que l'effet lui-même.
 */
@Composable
fun AuleGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Dp = AuleElevation.floating,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val glass = colors.surface.copy(alpha = AuleOpacity.GLASS)
    val edge = BorderStroke(AuleStroke.hairline, colors.outlineVariant.copy(alpha = AuleOpacity.GLASS_EDGE))
    if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = glass,
            contentColor = colors.onSurface,
            border = edge,
            shadowElevation = elevation,
        ) {
            Box(content = content)
        }
    } else {
        val interaction = interactionSource ?: remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.aulePress(interaction, AuleMotion.PRESSED_SCALE),
            shape = shape,
            color = glass,
            contentColor = colors.onSurface,
            border = edge,
            shadowElevation = elevation,
            interactionSource = interaction,
        ) {
            Box(content = content)
        }
    }
}

/** La couleur d'un aplat teinté d'une couleur métier — un mode, une ligne — sur la surface. */
fun Color.wash(): Color = copy(alpha = AuleOpacity.WASH)
