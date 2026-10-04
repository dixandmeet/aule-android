package io.aule.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import io.aule.android.core.designsystem.foundation.AuleMotionScheme
import io.aule.android.core.designsystem.foundation.LocalAuleSemantics
import io.aule.android.core.designsystem.foundation.auleSemanticColors
import io.aule.android.core.designsystem.foundation.auleBridgeTokens
import io.aule.android.core.designsystem.foundation.auleShapes as sharedShapes
import io.aule.android.core.designsystem.foundation.AuleFontFamily
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import io.aule.android.core.model.AppearanceMode
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
 * endroit où l'identité d'Aule y entre. Il ne fait que quatre choses, et c'est
 * volontaire : poser les rôles de couleur, l'échelle typographique, les formes,
 * et le régime de mouvement. Tout le reste de l'application se sert de
 * `MaterialTheme`.
 *
 * ## Pourquoi `MaterialExpressiveTheme`
 *
 * C'est la porte d'entrée de Material 3 Expressive, et elle apporte une chose
 * qu'on ne peut pas se donner soi-même : le **`MotionScheme`**. Sous un thème
 * ordinaire, les composants Material animent en durées fixes. Sous le schéma
 * expressif, ils animent en **ressorts** — un volet qu'on relâche à mi-course
 * repart de là où il est au lieu de rejouer une courbe depuis le début, une
 * puce qu'on sélectionne dépasse légèrement sa taille avant de s'y poser. Sur
 * un écran de conduite où un geste sur deux s'interrompt, la différence n'est
 * pas cosmétique : le ressort suit le doigt, la durée le contredit.
 *
 * Ce schéma se diffuse par le thème, donc **tous** les composants Material de
 * l'application en héritent d'un coup — le volet, les puces, la barre de
 * navigation, les boutons — sans qu'un seul écran ait à le demander.
 *
 * Les couleurs dynamiques restent désactivées : l'identité du produit ne dépend
 * pas du fond d'écran du téléphone.
 */
@Composable
fun AuleTheme(
    night: Boolean = resolvedNight(),
    typeface: AuleTypeface = AuleTypeface.TEXT,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(night) {
        if (night) auleDarkColorScheme() else auleLightColorScheme()
    }
    val typography = remember(typeface) { auleTypography(typeface.family) }
    val shapes = remember { sharedShapes() }
    val semantics = remember(night) { auleSemanticColors(night) }
    val tokens = remember(night) { auleBridgeTokens(night) }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = AuleMotionScheme,
        typography = typography,
        shapes = shapes,
    ) {
        CompositionLocalProvider(
            LocalAuleTokens provides tokens,
            LocalAuleSemantics provides semantics,
            LocalAuleNight provides night,
            content = content,
        )
    }
}

/** La police partagée de l'interface, ou la variante de marque pour les usages dédiés. */
enum class AuleTypeface {
    TEXT,
    BRAND,
    ;

    internal val family: FontFamily
        get() = when (this) {
            TEXT -> AuleFontFamily
            BRAND -> SpaceGrotesk
        }
}

object AuleTheme {
    val tokens: AuleTokens
        @Composable @ReadOnlyComposable get() = LocalAuleTokens.current

    val night: Boolean
        @Composable @ReadOnlyComposable get() = LocalAuleNight.current
}

/** API historique de Pro ; le régime des volets est désormais celui du socle commun. */
@Composable
fun AuleSheetMotion(content: @Composable () -> Unit) =
    io.aule.android.core.designsystem.foundation.AuleSheetMotion(content)

/**
 * Le régime de mouvement courant, tel que le thème l'a posé.
 *
 * Un raccourci, mais un raccourci utile : `MaterialTheme.motionScheme` porte le
 * nom du kit, et un écran qui anime doit pouvoir dire « le ressort d'Aule »
 * sans avoir à savoir d'où il vient. Les six régimes se lisent sur l'objet
 * rendu : trois **spatiaux**, pour ce qui change de forme ou de place, et
 * trois d'**effets**, pour ce qui ne change que de couleur ou d'opacité.
 * Confondre les deux — animer une couleur avec un ressort spatial — donne un
 * scintillement qu'on remarque sans savoir le nommer.
 */
val AuleMotionScheme: MotionScheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.motionScheme
