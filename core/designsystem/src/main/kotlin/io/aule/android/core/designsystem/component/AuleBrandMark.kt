package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleSpacing
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
                .background(AuleBrand.teal.color.copy(alpha = AuleAlpha.WASH))
                .border(
                    AuleStroke.hairline,
                    AuleBrand.teal.color.copy(alpha = AuleAlpha.OUTLINE),
                    tile,
                )
                .drawBehind { chevron(tokens.accentOnSurface.color, TILE_STROKE) },
        )
    }
}

/**
 * Le « A » d'Aule : deux jambages et leur barre, tracés à la main.
 *
 * Un dessin et non un `ImageVector` parce qu'il est fait de trois traits dont
 * les proportions se lisent en fractions de la tuile : la même marque tient
 * alors à soixante-seize points comme à trente-deux, sans qu'aucune des deux
 * tailles ait son fichier.
 */
private fun DrawScope.chevron(color: Color, stroke: Dp) {
    val w = size.width
    val h = size.height
    val width = stroke.toPx()
    drawLine(color, Offset(w * 0.50f, h * 0.30f), Offset(w * 0.30f, h * 0.70f), width, StrokeCap.Round)
    drawLine(color, Offset(w * 0.50f, h * 0.30f), Offset(w * 0.70f, h * 0.70f), width, StrokeCap.Round)
    drawLine(color, Offset(w * 0.38f, h * 0.54f), Offset(w * 0.62f, h * 0.54f), width, StrokeCap.Round)
}

/**
 * La marque en ligne : la tuile, le nom, et ce que le nom ne dit pas.
 *
 * C'est le `Logo` du web (`SpacePro/components/brand/logo.tsx`), et c'est la
 * forme que prend la marque quand elle est **en tête** d'un écran plutôt qu'au
 * centre. La différence n'est pas une question de place : au centre, la marque
 * est le sujet — elle a son halo, son onde, ses soixante-seize points ; en
 * tête, elle est une signature, et une signature qui pèse plus que le titre
 * qu'elle surplombe est une signature ratée.
 *
 * Le sur-titre en capitales espacées vient du web lui aussi. Il dit ce que le
 * nom seul ne dit pas — de quel espace il s'agit — et c'est exactement ce qu'on
 * veut sur un écran de connexion : « Aule Pro » identifie la maison, « espace
 * de travail » dit qu'on n'est pas dans l'application des voyageurs.
 */
@Composable
fun AuleWordmark(
    name: String,
    kicker: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val colors = MaterialTheme.colorScheme
    val tile = RoundedCornerShape(MARK_RADIUS)
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(MARK_SIZE)
                .clip(tile)
                .background(AuleBrand.teal.color.copy(alpha = AuleAlpha.WASH))
                .border(
                    AuleStroke.hairline,
                    AuleBrand.teal.color.copy(alpha = AuleAlpha.OUTLINE),
                    tile,
                )
                .drawBehind { chevron(tokens.accentOnSurface.color, MARK_STROKE) },
        )
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                letterSpacing = KICKER_TRACKING,
            )
        }
    }
}

/** Le trait du glyphe, à l'échelle de la grande tuile. */
private val TILE_STROKE = 3.2.dp

/** La tuile de la marque en ligne, et le trait qui va avec. */
private val MARK_SIZE = 34.dp
private val MARK_RADIUS = 10.dp
private val MARK_STROKE = 1.6.dp

/**
 * L'espacement des capitales du sur-titre.
 *
 * Le web le pose à `tracking-widest`, soit un dixième de cadratin. Sur dix
 * points, c'est ce qui distingue un mot en capitales d'un mot crié.
 */
private val KICKER_TRACKING = 1.2.sp

private val HALO_SIZE = 128.dp
private val WAVE_SIZE = 88.dp
private val TILE_SIZE = 76.dp
private val TILE_RADIUS = 21.dp
