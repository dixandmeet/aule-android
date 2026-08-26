package io.aule.android.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
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
    val shapes = remember { auleShapes() }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
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

/**
 * La voix typographique d'un écran.
 *
 * Deux, et deux seulement. [TEXT] est l'application : Roboto, la police qu'on
 * lit en vingt secondes debout dans un véhicule, et qui n'a pas à se faire
 * remarquer. [BRAND] est la porte d'entrée — connexion, inscription — où le
 * produit se présente avant d'avoir rien à dire, et où la même grotesque que
 * sur aule.fr fait le travail qu'aucune mise en page ne fait à sa place : dire
 * qu'on est au bon endroit.
 *
 * C'est un paramètre du **thème** et non un style posé sur un titre : le web
 * met sa police d'affichage sur toute la coquille d'authentification, libellés
 * de champ et bouton compris. Un titre en Space Grotesk au-dessus d'un
 * formulaire en Roboto ne serait pas la charte du web, ce serait deux polices
 * sur un même écran.
 */
enum class AuleTypeface {
    TEXT,
    BRAND,
    ;

    internal val family: FontFamily
        get() = when (this) {
            TEXT -> Roboto
            BRAND -> SpaceGrotesk
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

/**
 * Le mouvement des **volets**, plus lent d'un cran que celui du reste.
 *
 * Material fait ouvrir un volet au ressort spatial par défaut — raide, 380 —
 * et le fait *redescendre* au ressort d'**effets rapides** — 3800, soit un
 * dixième de seconde. Ce dernier est un régime de couleur et d'opacité : posé
 * sur une surface qui traverse la moitié de l'écran, il ne se lit pas comme un
 * mouvement mais comme une disparition. Vu à l'écran : le socle de recherche
 * s'ouvrait et se fermait d'un claquement.
 *
 * Ce régime-ci rend au volet une course qu'on suit de l'œil : un ressort
 * spatial doux pour la montée — moins raide, moins rebondi, la surface est
 * grande et un dépassement s'y voit — et un ressort **sans rebond** pour la
 * descente, qui reste le geste le plus fréquent et n'a pas à s'attarder.
 *
 * ⚠️ **Il s'enveloppe autour du `BottomSheetScaffold`**, et pas plus loin : les
 * régimes de Material se lisent par le thème, et le seul moyen d'en changer
 * pour un composant est de lui en poser un autre. Tout le reste de
 * l'application garde le régime expressif de [AuleTheme] — un changement de
 * couleur de puce n'a aucune raison de durer une demi-seconde.
 *
 * Le **corps** de l'écran est dans ce sous-arbre lui aussi : le menu flottant
 * de la carte s'ouvre donc du même ressort adouci. C'est voulu, et c'est même
 * la cohérence qu'on cherchait : ce sont les deux grandes surfaces qui montent
 * du bas de l'écran, et rien ne justifierait qu'elles montent différemment.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleSheetMotion(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        motionScheme = SheetMotionScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("UNCHECKED_CAST")
private object SheetMotionScheme : MotionScheme {
    private val expressive = MotionScheme.expressive()

    /** La montée : douce, et à peine rebondie — la surface est large. */
    private val rise = spring<Any>(dampingRatio = 0.9f, stiffness = 200f)

    /** La descente : franche, sans rebond, mais pas expédiée. */
    private val fall = spring<Any>(dampingRatio = 1f, stiffness = 400f)

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        rise as FiniteAnimationSpec<T>

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = expressive.fastSpatialSpec()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = expressive.slowSpatialSpec()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = expressive.defaultEffectsSpec()

    /**
     * C'est **la descente d'un volet**, chez Material, et non un effet : voir
     * [AuleSheetMotion]. Le reste du sous-arbre n'anime pas d'effet rapide.
     */
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fall as FiniteAnimationSpec<T>

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = expressive.slowEffectsSpec()
}

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
