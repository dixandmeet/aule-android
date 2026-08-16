package io.aule.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Une surface qui flotte : ombre, coins, filet de contour, fond.
 *
 * Les quatre vont ensemble. Séparés, on obtient la carte à laquelle il manque
 * son filet — invisible de jour, et de nuit un bloc qui se fond dans le fond
 * sans qu'on sache pourquoi l'écran paraît plat.
 */
@Composable
fun AuleCard(
    modifier: Modifier = Modifier,
    elevation: AuleElevation = AuleElevation.OVERLAY,
    shape: Shape = RoundedCornerShape(AuleRadius.xl),
    contentPadding: PaddingValues = PaddingValues(AuleSpacing.xl),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .auleShadow(elevation, shape)
            .clip(shape)
            .background(tokens.surface.color)
            .border(AuleStroke.hairline, tokens.hairline.color, shape)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * Le fond de marque : un lavis d'accent en haut, deux halos aux angles.
 *
 * C'est ce qui distingue un écran d'Aule d'un formulaire blanc. Il vit ici et
 * non dans un écran parce qu'un deuxième écran qui le redessinerait le
 * redessinerait forcément un peu différemment.
 */
@Composable
fun AuleAmbientBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = AuleTheme.tokens
    val night = AuleTheme.night
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(tokens.surfaceSolid.color)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            tokens.accent.color.copy(alpha = if (night) 0.34f else 0.16f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.12f),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                drawCircle(
                    color = AuleBrand.teal.color.copy(alpha = if (night) 0.16f else 0.10f),
                    radius = HALO_RADIUS.toPx(),
                    center = Offset(size.width + HALO_OVERHANG.toPx(), -HALO_OVERHANG.toPx()),
                )
                drawCircle(
                    color = tokens.accent.color.copy(alpha = if (night) 0.12f else 0.08f),
                    radius = HALO_RADIUS.toPx() * 1.09f,
                    center = Offset(
                        -HALO_OVERHANG.toPx() * 1.5f,
                        size.height + HALO_OVERHANG.toPx(),
                    ),
                )
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Les halos débordent volontairement de l'écran : un cercle entier se lit comme
 * une forme, un cercle coupé se lit comme une lumière.
 */
private val HALO_RADIUS = 220.dp
private val HALO_OVERHANG = 40.dp
