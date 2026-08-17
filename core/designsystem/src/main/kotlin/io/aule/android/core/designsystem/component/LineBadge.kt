package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.badgeInk
import io.aule.android.core.designsystem.token.parseLineColor
import io.aule.android.core.model.TransportMode

/**
 * Le numéro d'une ligne, dans sa couleur.
 *
 * Composant métier s'il en est : sa couleur ne vient pas du thème mais du GTFS
 * du réseau, et c'est tout l'intérêt — le voyageur reconnaît « C3 » à sa teinte
 * avant de lire le texte. Aucun rôle Material ne peut porter cela, puisque la
 * couleur change d'une ligne à l'autre.
 *
 * Ce que Material apporte quand même : la `Surface`, qui gère le découpage, la
 * couleur de contenu locale et l'élévation, et le `Text`, qui suit l'échelle
 * typographique. Seule la paire fond/encre est calculée à part, et elle l'est
 * par mesure de luminance plutôt qu'à vue.
 *
 * Discret par construction : c'est un repère, pas un titre. Il garde sa taille
 * quel que soit le contexte pour qu'on reconnaisse « C3 » d'un coup d'œil.
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
    Surface(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(AuleRadius.sm),
        color = background.color,
        contentColor = ink.color,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = BADGE_MIN_WIDTH)
                .padding(horizontal = AuleSpacing.sm, vertical = BADGE_VERTICAL_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Le mode de transport, dans sa teinte.
 *
 * Même raison d'être que [LineBadge] : la teinte est celle du marqueur de
 * carte pour ce mode, donc une donnée métier et non un rôle du thème.
 */
@Composable
fun TransportBadge(
    mode: TransportMode,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(AuleRadius.pill),
        color = tint.copy(alpha = AuleAlpha.TINT),
        contentColor = tint,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = AuleSpacing.sm,
                vertical = BADGE_VERTICAL_PADDING,
            ),
        )
    }
}

/** Le badge respire moins que le reste : c'est ce qui le fait lire comme une étiquette. */
private val BADGE_VERTICAL_PADDING = 3.dp

/** Deux caractères tiennent sans que le badge se déforme sur un numéro à un chiffre. */
private val BADGE_MIN_WIDTH = 26.dp
