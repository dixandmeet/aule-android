package io.aule.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.badgeInk
import io.aule.android.core.designsystem.token.parseLineColor
import io.aule.android.core.model.TransportMode

/**
 * Le numéro d'une ligne, dans sa couleur.
 *
 * Discret par construction : c'est un repère, pas un titre. Il garde sa
 * taille quel que soit le contexte pour qu'on reconnaisse « C3 » d'un coup
 * d'œil.
 */
@Composable
fun LineBadge(
    line: String,
    colorHex: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = parseLineColor(colorHex)
    val ink = badgeInk(background)
    BasicText(
        text = line,
        style = auleTextStyle(AuleRole.KICKER, FontWeight.Bold).copy(color = ink.color),
        modifier = modifier
            .clip(RoundedCornerShape(AuleRadius.sm))
            .background(background.color)
            .defaultMinSize(minWidth = 26.dp)
            .padding(horizontal = AuleSpacing.sm, vertical = 3.dp)
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
fun TransportBadge(
    mode: TransportMode,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AuleRadius.pill))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = AuleSpacing.sm, vertical = 3.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold).copy(color = tint),
        )
    }
}
