package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleMotion

/**
 * Le point qui dit « cette donnée est mesurée, pas théorique ».
 *
 * Le vert vient de [realtimeInk] et non d'un rôle Material : « temps réel »
 * est une notion du métier transport, pas une place dans la hiérarchie
 * visuelle. Le gris de repli, lui, est bien un rôle — c'est simplement du
 * contenu secondaire.
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
    val color = if (isLive) {
        realtimeInk()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
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

/**
 * L'encre « temps réel » : une donnée mesurée, pas théorique.
 *
 * Ce n'est pas un rôle du ColorScheme. Un écran qui doit colorer un passage
 * live passe par ici, pas par `secondary`.
 */
@Composable
@ReadOnlyComposable
fun realtimeInk(): Color = AuleTheme.tokens.realtime.color.color

/**
 * L'encre « retard » : une perturbation prévue, pas une alerte bloquante.
 *
 * Même raison que [realtimeInk] : le rôle `tertiary` est de la hiérarchie
 * visuelle, pas du métier transport.
 */
@Composable
@ReadOnlyComposable
fun delayInk(): Color = AuleTheme.tokens.delay.color.color
