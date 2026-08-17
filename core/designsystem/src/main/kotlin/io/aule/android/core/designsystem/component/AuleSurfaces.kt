package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleAlpha

/**
 * Le fond de marque : un lavis d'accent en haut, deux halos aux angles.
 *
 * C'est ce qui distingue un écran d'Aule d'un formulaire blanc. Il vit ici et
 * non dans un écran parce qu'un deuxième écran qui le redessinerait le
 * redessinerait forcément un peu différemment.
 *
 * Le lavis prend `primary` de jour et `primaryContainer` de nuit. Ce n'est pas
 * une inconséquence : Material éclaircit `primary` en ambiance sombre pour que
 * l'encre reste lisible, or on cherche ici l'inverse d'une encre — une lueur
 * qui reste **sous** le contenu. Le conteneur, lui, fonce la nuit, ce qui est
 * exactement le comportement voulu pour un fond.
 */
@Composable
fun AuleAmbientBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val night = AuleTheme.night
    val glow = if (night) colors.primaryContainer else colors.primary
    val glowAlpha = if (night) AuleAlpha.GLOW_STRONG else AuleAlpha.GLOW
    val haloAlpha = if (night) AuleAlpha.HALO_STRONG else AuleAlpha.HALO
    val farHaloAlpha = if (night) AuleAlpha.TINT else AuleAlpha.HALO_SOFT
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.surface)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = glowAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.12f),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                halo(
                    color = glow,
                    alpha = haloAlpha,
                    radius = HALO_RADIUS.toPx(),
                    center = Offset(size.width + HALO_OVERHANG.toPx(), -HALO_OVERHANG.toPx()),
                )
                halo(
                    color = glow,
                    alpha = farHaloAlpha,
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
 * Un halo d'angle.
 *
 * Un `drawCircle` d'une couleur unie donne un disque à **bord franc** : posé sur
 * la surface, il se lit comme une forme peinte — un quart de rond gris dans le
 * coin — et non comme une lumière. Le dégradé rend au halo ce qu'on lui
 * demandait : une clarté qui s'éteint avant d'atteindre son bord.
 */
private fun DrawScope.halo(
    color: Color,
    alpha: Float,
    radius: Float,
    center: Offset,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Les halos débordent volontairement de l'écran : un cercle entier se lit comme
 * une forme, un cercle coupé se lit comme une lumière.
 */
private val HALO_RADIUS = 220.dp
private val HALO_OVERHANG = 40.dp
