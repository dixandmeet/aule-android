package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleMotion

/**
 * Le point qui dit « cette donnée est mesurée, pas théorique ».
 *
 * Décoratif pour TalkBack : le texte voisin (ou le `contentDescription` du
 * parent) porte déjà la nuance. L'annoncer deux fois n'apprend rien.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RealtimeDot(
    isLive: Boolean,
    liveDescription: String,
    scheduledDescription: String,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val color = if (isLive) tokens.realtime.color else tokens.onSurfaceMuted.color
    val reduceMotion = reduceMotionEnabled()
    val pulse = if (isLive && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "realtime")
        val animated by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(AuleMotion.PULSE_MS),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "realtime-opacity",
        )
        animated
    } else {
        1f
    }

    Canvas(
        modifier = modifier
            .size(6.dp)
            .semantics { hideFromAccessibility() },
    ) {
        drawCircle(color = color, alpha = pulse)
    }
}
