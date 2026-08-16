package io.aule.android.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.min

/**
 * Vrai quand l'utilisateur a demandé moins de mouvement (échelle d'animation
 * nulle). Le point temps réel ne pulse plus : une animation infinie à côté
 * d'un « 3 min » est du bruit, pas de l'information.
 */
@Composable
fun reduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Plafonne le facteur de police, comme iOS le fait à `xxxLarge`.
 *
 * Aux tailles d'accessibilité, quatre lignes d'attribution mangeraient un
 * tiers de la carte. Le contenu reste entier pour TalkBack, qui en a la
 * version développée.
 */
@Composable
fun AuleCappedFontScale(
    maxScale: Float = 1.3f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val capped = remember(density, maxScale) {
        Density(
            density = density.density,
            fontScale = min(density.fontScale, maxScale),
        )
    }
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
