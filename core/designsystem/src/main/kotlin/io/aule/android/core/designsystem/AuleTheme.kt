package io.aule.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleTokens

/**
 * Les jetons de couleur de l'ambiance courante.
 *
 * `staticCompositionLocalOf` et non `compositionLocalOf` : l'ambiance change
 * quelques fois par jour, pas par image. Le local statique recompose tout le
 * sous-arbre quand il change, ce qui est exactement ce qu'on veut ici, et ne
 * coûte rien le reste du temps.
 */
val LocalAuleTokens = staticCompositionLocalOf { AuleTokens.day }

val LocalAuleNight = staticCompositionLocalOf { false }

val LocalAppearanceMode = staticCompositionLocalOf { AppearanceMode.LIGHT }

/**
 * Nuit telle que le choix d'apparence la résout.
 *
 * Sans [LocalAppearanceMode] fourni, le défaut est clair — comme Flutter.
 */
@Composable
@ReadOnlyComposable
fun resolvedNight(): Boolean =
    LocalAppearanceMode.current.isNight(isSystemInDarkTheme())

/**
 * Le thème Aule.
 *
 * Material 3 est le design system de l'application ; ce thème est le seul
 * endroit où l'identité d'Aule y entre. Il ne fait que trois choses, et c'est
 * volontaire : poser les rôles de couleur, poser l'échelle typographique, poser
 * les formes. Tout le reste de l'application se sert de `MaterialTheme`.
 *
 * Les couleurs dynamiques restent désactivées : l'identité du produit ne dépend
 * pas du fond d'écran du téléphone.
 */
@Composable
fun AuleTheme(
    night: Boolean = resolvedNight(),
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(night) {
        if (night) auleDarkColorScheme() else auleLightColorScheme()
    }
    val typography = remember { auleTypography() }
    val shapes = remember { auleShapes() }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
    ) {
        CompositionLocalProvider(
            LocalAuleTokens provides AuleTokens.of(night),
            LocalAuleNight provides night,
            content = content,
        )
    }
}

object AuleTheme {
    val tokens: AuleTokens
        @Composable @ReadOnlyComposable get() = LocalAuleTokens.current

    val night: Boolean
        @Composable @ReadOnlyComposable get() = LocalAuleNight.current
}

/**
 * Les cinq formes de Material 3, servies par l'échelle de rayons d'Aule.
 *
 * Material attribue déjà une forme à chaque composant : `extraSmall` aux menus,
 * `small` aux chips, `medium` aux cartes, `large` aux volets, `extraLarge` aux
 * dialogues. Renseigner l'échelle ici suffit donc à arrondir toute
 * l'application — et dispense les écrans d'écrire leur propre
 * `RoundedCornerShape`.
 */
private fun auleShapes() = Shapes(
    extraSmall = RoundedCornerShape(AuleRadius.sm),
    small = RoundedCornerShape(AuleRadius.md),
    medium = RoundedCornerShape(AuleRadius.lg),
    large = RoundedCornerShape(AuleRadius.xl),
    extraLarge = RoundedCornerShape(AuleRadius.xxl),
)
