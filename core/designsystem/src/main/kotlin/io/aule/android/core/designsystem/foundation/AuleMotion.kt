package io.aule.android.core.designsystem.foundation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Le mouvement de la langue Aule.
 *
 * Six régimes, ceux de Material 3 Expressive que la landing rejoue en `linear()` : trois
 * **spatiaux** — un déplacement, une taille, une forme — qui dépassent leur cible avant de s'y
 * poser, et trois **d'effets** — une opacité, une couleur — qui n'y reviennent jamais. Les
 * durées du brief (379 / 454 / 619 ms · 169 / 250 / 346 ms) sont celles de ces ressorts au
 * repos ; on les garde en constantes pour les rares endroits où un `tween` est plus juste qu'un
 * ressort — une transition de contenu, un fondu interrompu.
 *
 * Confondre les deux familles se voit sans se nommer : une opacité sur un ressort spatial
 * dépasse 1, se fait plafonner et repart — un scintillement.
 */
object AuleMotion {
    const val SPATIAL_FAST_MS = 379
    const val SPATIAL_DEFAULT_MS = 454
    const val SPATIAL_SLOW_MS = 619

    const val EFFECT_FAST_MS = 169
    const val EFFECT_DEFAULT_MS = 250
    const val EFFECT_SLOW_MS = 346

    /** Ce que le doigt enfonce : la limite basse de ce qui se sent sans se voir. */
    const val PRESSED_SCALE = 0.96f

    /** Le décalage entre deux rangées qui entrent, et le rang au-delà duquel il cesse de croître. */
    const val STAGGER_MS = 40
    const val STAGGER_CAP = 6

    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> effectFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effectDefault(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
    fun <T> effectSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)

    /** La courbe d'un effet joué en durée : elle ne dépasse jamais. */
    val effectEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Un effet en durée, pour ce qui doit finir à un instant connu. */
    fun <T> effectTween(ms: Int = EFFECT_DEFAULT_MS): FiniteAnimationSpec<T> =
        tween(durationMillis = ms, easing = effectEasing)
}

/**
 * Le régime posé sur toute l'application par [io.aule.android.core.designsystem.AuleTheme].
 *
 * Les composants Material — puces, boutons, volets, interrupteurs — le lisent par le thème :
 * aucun écran n'a à le demander.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AuleMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = AuleMotion.spatialDefault()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = AuleMotion.spatialFast()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = AuleMotion.spatialSlow()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = AuleMotion.effectDefault()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = AuleMotion.effectFast()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = AuleMotion.effectSlow()
}

/**
 * Le mouvement des **volets**, un cran plus doux.
 *
 * Material fait monter un volet au ressort spatial par défaut et le fait redescendre au ressort
 * d'effets rapides — un dixième de seconde, ce qui se lit comme une disparition sur une surface
 * qui traverse la moitié de l'écran. Ce régime rend au volet une course qu'on suit de l'œil :
 * une montée douce, à peine rebondie, et une descente franche sans rebond.
 *
 * Il s'enveloppe autour du `BottomSheetScaffold` et de rien d'autre.
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
private object SheetMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 220f)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = AuleMotion.spatialFast()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = AuleMotion.spatialSlow()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = AuleMotion.effectDefault()

    /** La descente d'un volet, chez Material : franche, sans rebond, pas expédiée. */
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 420f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = AuleMotion.effectSlow()
}
