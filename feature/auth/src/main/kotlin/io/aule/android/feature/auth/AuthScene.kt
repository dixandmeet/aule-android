package io.aule.android.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.foundation.AuleOpacity

/** Le moteur de carte est fourni par la racine ; le compte ne dépend pas de la feature carte. */
@Composable
internal fun AuthScene(
    quiet: Boolean,
    background: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val night = AuleTheme.night
    val veil = remember(colors, night) {
        Brush.verticalGradient(
            0f to colors.surface,
            0.12f to colors.surface.copy(alpha = AuleOpacity.GLASS_EDGE),
            0.28f to colors.surface.copy(alpha = if (night) AuleOpacity.SCRIM else AuleOpacity.SKELETON),
            0.64f to colors.surface.copy(alpha = AuleOpacity.GLASS),
            1f to colors.surface,
        )
    }
    Box(modifier = modifier.fillMaxSize().background(colors.surface)) {
        background(quiet)
        Box(Modifier.fillMaxSize().background(veil))
        content()
    }
}
