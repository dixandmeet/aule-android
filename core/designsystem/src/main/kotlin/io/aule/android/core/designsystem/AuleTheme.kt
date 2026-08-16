package io.aule.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import io.aule.android.core.model.AppearanceMode
import io.aule.android.core.designsystem.token.AuleRole
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
 * Il ne s'appuie pas sur Material 3 (ADR-010). Material apporterait une palette,
 * des formes et des ondulations qui donneraient au produit l'air d'une
 * démonstration ; l'identité Aule passe avant.
 */
@Composable
fun AuleTheme(
    night: Boolean = resolvedNight(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAuleTokens provides AuleTokens.of(night),
        LocalAuleNight provides night,
        content = content,
    )
}

object AuleTheme {
    val tokens: AuleTokens
        @Composable @ReadOnlyComposable get() = LocalAuleTokens.current

    val night: Boolean
        @Composable @ReadOnlyComposable get() = LocalAuleNight.current
}

/**
 * Le style d'un rôle typographique.
 *
 * L'interligne est posé en absolu et **découpé au centre** : sans
 * [LineHeightStyle], Compose ajoute tout l'espace supplémentaire sous la
 * dernière ligne, ce qui décentre un texte d'une ligne dans sa boîte — visible
 * dès qu'on aligne un chiffre à côté d'une icône.
 *
 * Les chiffres à chasse fixe (`tnum`) évitent qu'un compte à rebours fasse
 * danser la ligne à chaque seconde.
 */
fun auleTextStyle(role: AuleRole, weight: FontWeight = FontWeight.Normal): TextStyle = TextStyle(
    fontSize = role.sizeSp.sp,
    lineHeight = role.lineHeightSp.sp,
    letterSpacing = role.trackingSp.sp,
    fontWeight = weight,
    fontFeatureSettings = if (role.usesTabularFigures) "tnum" else null,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)
