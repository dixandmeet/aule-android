package io.aule.android.core.designsystem.mapui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.components.AuleGlassCard
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.foundation.AuleHaptic
import io.aule.android.core.designsystem.foundation.AuleLayout
import io.aule.android.core.designsystem.foundation.AuleMotion
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.foundation.rememberAuleHaptics

/** Le registre d'un contrôle de carte : le verre pour ce qui accompagne, la marque pour ce qui agit. */
enum class AuleMapControlEmphasis { Glass, Primary, Tonal }

/**
 * Un contrôle rond posé sur la carte : localiser, autour de vous, la voix du guidage.
 *
 * En verre par défaut — la même surface que la capsule de recherche, pour que le chrome de la
 * carte se lise comme **une** famille — et de la marque pour le seul qui agit sur la caméra.
 * L'ombre basse n'est pas décorative : sans elle, c'est la `MapView` qui reçoit le doigt.
 *
 * Il se dessine à 52 dp, au-dessus du plancher tactile : on le vise debout, en marchant.
 *
 * @param content ce que le contrôle montre à la place de l'icône — une initiale, un compteur.
 */
@Composable
fun AuleMapControl(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasis: AuleMapControlEmphasis = AuleMapControlEmphasis.Glass,
    large: Boolean = false,
    enabled: Boolean = true,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAuleHaptics()
    val interaction = remember { MutableInteractionSource() }
    val size = if (large) AuleLayout.mapControlLarge else AuleLayout.mapControl
    val (container, ink) = when (emphasis) {
        AuleMapControlEmphasis.Glass -> colors.surface.copy(alpha = AuleOpacity.GLASS) to colors.onSurface
        AuleMapControlEmphasis.Primary -> colors.primary to colors.onPrimary
        AuleMapControlEmphasis.Tonal -> colors.primaryContainer to colors.onPrimaryContainer
    }
    Surface(
        onClick = {
            if (emphasis == AuleMapControlEmphasis.Primary) haptics.play(AuleHaptic.ACTION)
            onClick()
        },
        modifier = modifier
            .size(size)
            .aulePress(interaction, AuleMotion.PRESSED_SCALE)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            },
        enabled = enabled,
        shape = CircleShape,
        color = container,
        contentColor = ink,
        border = if (emphasis == AuleMapControlEmphasis.Glass) {
            BorderStroke(AuleStroke.hairline, colors.outlineVariant.copy(alpha = AuleOpacity.GLASS_EDGE))
        } else {
            null
        },
        shadowElevation = AuleElevation.floating,
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                content != null -> content()
                icon != null -> Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AuleLayout.icon))
            }
        }
    }
}

/**
 * Le socle de la carte : les contrôles, puis la capsule de recherche, pleine largeur.
 *
 * La capsule prend toute la largeur — « Où allez-vous ? » doit se lire entier, y compris avec
 * une police agrandie — et les contrôles se rangent **au-dessus d'elle, à droite**, du plus
 * secondaire au plus important en descendant vers le pouce. C'est la seule zone que la main
 * atteint sans changer de prise, et tout ce qui est atteignable d'une main y vit.
 */
@Composable
fun AuleMapDock(
    modifier: Modifier = Modifier,
    controls: @Composable RowScope.() -> Unit,
    search: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            content = controls,
        )
        search()
    }
}

/** Un panneau flottant sur la carte : un bandeau de guidage, une invite. Du verre, au rayon des cartes. */
@Composable
fun AuleMapPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    AuleGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = AuleElevation.floating,
        content = content,
    )
}
