package io.aule.android.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.reduceMotionEnabled
import kotlinx.coroutines.delay

/** Les entrées ne bloquent jamais les champs ni leurs actions. */
internal fun Modifier.authEnter(delayMs: Int): Modifier = composed {
    val reduced = reduceMotionEnabled()
    val offset = remember { Animatable(if (reduced) 0f else 1f) }
    val opacity = remember { Animatable(if (reduced) 1f else 0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val rise = with(LocalDensity.current) { AuleSpacing.md.toPx() }
    LaunchedEffect(reduced) {
        if (reduced) offset.snapTo(0f) else {
            delay(delayMs.toLong())
            offset.animateTo(0f, spatial)
        }
    }
    LaunchedEffect(reduced) {
        if (reduced) opacity.snapTo(1f) else {
            delay(delayMs.toLong())
            opacity.animateTo(1f, effects)
        }
    }
    graphicsLayer {
        translationY = offset.value * rise
        alpha = opacity.value
    }
}

@Composable
internal fun AuthFieldMotion(
    error: String?,
    attempt: Int,
    content: @Composable (Modifier) -> Unit,
) {
    val reduced = reduceMotionEnabled()
    val displacement = remember { Animatable(0f) }
    val distance = with(LocalDensity.current) { AuleSpacing.xs.toPx() }
    LaunchedEffect(error, attempt, reduced) {
        displacement.snapTo(0f)
        if (error != null && !reduced) {
            for (target in listOf(-1f, 1f, -0.5f, 0f)) {
                displacement.animateTo(target, tween(45))
            }
        }
    }
    content(Modifier.graphicsLayer { translationX = displacement.value * distance })
}
