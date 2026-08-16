package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleBusyIndicator
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.drawAuleGlyph
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Une entrée d'un rail.
 *
 * [primary] est l'entrée qui **commence** quelque chose — une seule par
 * colonne. Démarrer / En service, pas Signaler.
 */
internal data class MapActionItem(
    val glyph: AuleGlyph,
    val label: String,
    val onClick: () -> Unit,
    val semanticsLabel: String? = null,
    val active: Boolean = false,
    val busy: Boolean = false,
    val primary: Boolean = false,
)

/**
 * Le rail d'actions, posé contre un bord.
 *
 * Pas de `BackdropFilter` : sur Android le flou relit la carte à chaque
 * image, et un rail qui saccade au-dessus d'une carte qui défile est pire
 * qu'un rail opaque. La surface, le filet et l'ombre suffisent à le
 * détacher — port de `blurIsAffordable` côté Flutter.
 */
@Composable
internal fun MapActionRail(
    items: List<MapActionItem>,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(if (compact) RAIL_COMPACT_RADIUS else RAIL_RADIUS)
    val a11y = stringResource(R.string.rail_a11y)
    Column(
        modifier = modifier
            .auleShadow(AuleElevation.FLOATING, shape)
            .clip(shape)
            .background(tokens.surface.color)
            .border(AuleStroke.hairline, tokens.hairline.color, shape)
            .padding(if (compact) RAIL_COMPACT_PAD else RAIL_PAD)
            .semantics { contentDescription = a11y },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEach { item ->
            RailButton(item = item, compact = compact)
        }
    }
}

@Composable
private fun RailButton(
    item: MapActionItem,
    compact: Boolean,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val foreground = if (item.active) tokens.realtime.color else tokens.onSurface.color
    val labelColor = if (item.active || item.primary) {
        foreground
    } else {
        tokens.onSurfaceMuted.color
    }
    val announced = item.semanticsLabel ?: item.label
    val cellShape = RoundedCornerShape(RAIL_CELL_RADIUS)
    Column(
        modifier = Modifier
            .width(if (compact) RAIL_COMPACT_WIDTH else RAIL_CELL_WIDTH)
            .defaultMinSize(minHeight = if (compact) RAIL_COMPACT_TARGET else RAIL_FULL_TARGET)
            .clip(cellShape)
            .clickable(enabled = !item.busy) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                item.onClick()
            }
            .padding(
                horizontal = RAIL_HORIZONTAL_INSET,
                vertical = if (compact) RAIL_COMPACT_PAD else RAIL_CELL_PAD,
            )
            .semantics {
                role = Role.Button
                contentDescription = announced
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(RAIL_GLYPH_SLOT),
            contentAlignment = Alignment.Center,
        ) {
            RailGlyph(item = item, foreground = foreground)
        }
        if (!compact) {
            BasicText(
                text = item.label,
                style = auleTextStyle(
                    AuleRole.KICKER,
                    if (item.active || item.primary) FontWeight.Bold else FontWeight.SemiBold,
                ).copy(color = labelColor, textAlign = TextAlign.Center),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = RAIL_LABEL_GAP),
            )
        }
    }
}

@Composable
private fun RailGlyph(
    item: MapActionItem,
    foreground: Color,
) {
    val tokens = AuleTheme.tokens
    val onGlyph = if (item.active) AuleBrand.onRealtime.color else tokens.onAccent.color
    val glyphColor = if (item.primary) onGlyph else foreground
    val glyph = @Composable {
        if (item.busy) {
            AuleBusyIndicator(color = if (item.primary) onGlyph else tokens.onSurfaceMuted.color)
        } else {
            Box(
                modifier = Modifier
                    .size(RAIL_GLYPH)
                    .drawBehind { drawAuleGlyph(item.glyph, glyphColor) },
            )
        }
    }
    if (!item.primary) {
        glyph()
        return
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (item.active) tokens.realtime.color else tokens.accent.color)
            .padding(RAIL_GLYPH_PAD),
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

private val RAIL_CELL_WIDTH = 68.dp
private val RAIL_COMPACT_WIDTH = 44.dp
private val RAIL_FULL_TARGET = 52.dp
private val RAIL_COMPACT_TARGET = 44.dp
private val RAIL_GLYPH = 21.dp
private val RAIL_GLYPH_PAD = 7.dp
private val RAIL_GLYPH_SLOT = 35.dp
private val RAIL_PAD = 6.dp
private val RAIL_CELL_PAD = 6.dp
private val RAIL_COMPACT_PAD = 4.dp
private val RAIL_LABEL_GAP = 4.dp
private val RAIL_HORIZONTAL_INSET = 2.dp
private val RAIL_RADIUS = 28.dp
private val RAIL_COMPACT_RADIUS = 22.dp
private val RAIL_CELL_RADIUS = 20.dp
