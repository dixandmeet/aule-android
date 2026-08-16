package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * La marque Aule : le chevron dans sa tuile, sous une onde qui s'écarte.
 *
 * L'onde est une boucle infinie, donc elle s'arrête quand l'appareil demande
 * moins de mouvement — la marque reste, la respiration part.
 */
@Composable
fun AuleBrandMark(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val reduceMotion = reduceMotionEnabled()
    val pulse = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "brand-mark")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(AuleMotion.PULSE_MS, easing = LinearEasing)),
            label = "brand-mark-pulse",
        )
        animated
    }
    val tile = RoundedCornerShape(TILE_RADIUS)
    Box(
        modifier = modifier
            .size(HALO_SIZE)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(WAVE_SIZE)
                .graphicsLayer {
                    val scale = 0.78f + pulse * 0.5f
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - pulse) * 0.28f
                }
                .border(AuleStroke.hairline, AuleBrand.teal.color, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(TILE_SIZE)
                .auleShadow(AuleElevation.LIFTED, tile, AuleShadowTint.ACCENT)
                .clip(tile)
                .background(AuleBrand.teal.color.copy(alpha = 0.14f))
                .border(
                    AuleStroke.hairline,
                    AuleBrand.teal.color.copy(alpha = AuleAlpha.OUTLINE),
                    tile,
                )
                .drawBehind {
                    val glyph = tokens.accentOnSurface.color
                    val w = size.width
                    val h = size.height
                    val width = 3.2.dp.toPx()
                    drawLine(
                        glyph,
                        Offset(w * 0.50f, h * 0.30f),
                        Offset(w * 0.30f, h * 0.70f),
                        width,
                        StrokeCap.Round,
                    )
                    drawLine(
                        glyph,
                        Offset(w * 0.50f, h * 0.30f),
                        Offset(w * 0.70f, h * 0.70f),
                        width,
                        StrokeCap.Round,
                    )
                    drawLine(
                        glyph,
                        Offset(w * 0.38f, h * 0.54f),
                        Offset(w * 0.62f, h * 0.54f),
                        width,
                        StrokeCap.Round,
                    )
                },
        )
    }
}

private val HALO_SIZE = 128.dp
private val WAVE_SIZE = 88.dp
private val TILE_SIZE = 76.dp
private val TILE_RADIUS = 21.dp
