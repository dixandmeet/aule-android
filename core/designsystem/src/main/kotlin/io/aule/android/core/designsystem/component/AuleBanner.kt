package io.aule.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch

/**
 * Le registre d'un message.
 *
 * [ALERT] est réservé à ce qui a échoué et que l'utilisateur peut corriger.
 * L'étendre à l'information rendrait le rouge banal, et le prochain vrai échec
 * se lirait comme le reste.
 */
enum class AuleTone { NEUTRAL, ALERT }

/**
 * Un message posé dans le flux, avec au plus une action.
 *
 * Il s'annonce en région vivante : un texte qui apparaît après un appui n'est
 * pas lu par TalkBack si personne ne le lui demande, et l'échec de connexion
 * resterait muet pour qui ne voit pas l'écran.
 */
@Composable
fun AuleBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: AuleTone = AuleTone.NEUTRAL,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    val ink = when (tone) {
        AuleTone.NEUTRAL -> tokens.onSurface.color
        AuleTone.ALERT -> tokens.alert.color
    }
    val background = when (tone) {
        AuleTone.NEUTRAL -> tokens.surface.color
        AuleTone.ALERT -> tokens.alert.color.copy(alpha = AuleAlpha.TINT)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(
                if (tone == AuleTone.ALERT) {
                    Modifier.border(
                        AuleStroke.hairline,
                        tokens.alert.color.copy(alpha = AuleAlpha.OUTLINE),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (tone == AuleTone.ALERT) error(message)
            },
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = message,
            style = auleTextStyle(AuleRole.BODY).copy(color = ink),
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            BasicText(
                text = action,
                style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                    .copy(color = tokens.accentOnSurface.color),
                modifier = Modifier
                    .defaultMinSize(minHeight = AuleTouch.minimum)
                    .clip(RoundedCornerShape(AuleRadius.sm))
                    .clickable(onClick = onAction)
                    .padding(horizontal = AuleSpacing.sm),
            )
        }
    }
}
