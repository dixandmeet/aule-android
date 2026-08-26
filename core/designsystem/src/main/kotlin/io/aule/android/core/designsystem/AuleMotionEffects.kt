package io.aule.android.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleMotion
import kotlinx.coroutines.delay

/**
 * L'apparition en cascade d'une liste.
 *
 * Une liste dont toutes les rangées apparaissent au même instant arrive comme
 * un bloc : l'œil ne sait pas par où commencer, et le volet a l'air de s'être
 * téléporté. La même liste dont chaque rangée entre 40 ms après la précédente
 * **se déroule**, et le regard suit le déroulé sans qu'on le lui demande.
 *
 * C'est le geste le moins cher et le plus rentable de toute la refonte : il ne
 * change aucune couleur, aucune mesure, aucune mise en page, et c'est pourtant
 * lui qu'on remarque en premier à l'ouverture d'un volet.
 *
 * ## Ce qu'il fait exactement
 *
 * Deux animations menées ensemble, chacune sur le bon régime :
 *
 * - la **translation** monte de [rise] jusqu'à zéro, sur un ressort *spatial*
 *   du schéma expressif — celui qui dépasse légèrement sa cible avant de s'y
 *   poser ;
 * - l'**opacité** va de 0 à 1 sur un ressort d'*effets*, qui lui ne dépasse
 *   jamais. Une opacité animée sur un ressort spatial scintille : elle
 *   dépasserait 1, se ferait plafonner, et repartirait — un clignotement qu'on
 *   remarque sans savoir le nommer.
 *
 * ## Le plafond de la cascade
 *
 * Le décalage cesse de croître au-delà de [AuleMotion.STAGGER_CAP] rangées.
 * Sans ce plafond, la douzième rangée d'une liste d'arrêts arriverait une demi
 * seconde après la première, et la cascade cesserait d'être un déroulé pour
 * devenir une attente. On la **voit** au lieu de la sentir, ce qui est le
 * symptôme d'une animation ratée.
 *
 * ## Quand elle ne joue pas
 *
 * Si l'appareil a demandé moins de mouvement, le contenu est simplement là. Pas
 * de version atténuée, pas de fondu plus court : le réglage système dit « pas
 * d'animation », et une animation discrète reste une animation.
 *
 * @param index le rang de l'élément dans sa liste. C'est lui qui porte la
 *   cascade ; deux éléments de même rang entrent ensemble.
 * @param rise la hauteur dont l'élément monte en entrant. Assez pour qu'on
 *   perçoive une arrivée, pas assez pour qu'on voie un déplacement.
 */
@Composable
fun Modifier.auleEnter(
    index: Int = 0,
    rise: Dp = DEFAULT_RISE,
): Modifier = composed {
    if (reduceMotionEnabled()) return@composed this

    val motion = MaterialTheme.motionScheme
    val spatial = motion.defaultSpatialSpec<Float>()
    val effects = motion.defaultEffectsSpec<Float>()
    val risePx = with(LocalDensity.current) { rise.toPx() }
    val delayMs = (index.coerceAtMost(AuleMotion.STAGGER_CAP) * AuleMotion.STAGGER_MS).toLong()

    val offset = remember { Animatable(1f) }
    val opacity = remember { Animatable(0f) }
    val currentDelay by rememberUpdatedState(delayMs)

    // Deux effets et non un seul : `animateTo` suspend jusqu'à la fin du
    // ressort. Enchaînés dans la même coroutine, l'opacité attendrait que la
    // translation ait fini — l'élément monterait invisible, puis apparaîtrait
    // sur place. Deux coroutines, deux ressorts, un seul mouvement perçu.
    LaunchedEffect(Unit) {
        delay(currentDelay)
        offset.animateTo(0f, spatial)
    }
    LaunchedEffect(Unit) {
        delay(currentDelay)
        opacity.animateTo(1f, effects)
    }

    this
        .graphicsLayer { translationY = offset.value * risePx }
        .alpha(opacity.value)
}

/**
 * L'enfoncement sous le doigt.
 *
 * Une surface qui ne bouge pas quand on la touche laisse un doute d'un dixième
 * de seconde : *est-ce que ça a pris ?* L'ondulation de Material répond, mais
 * elle répond **dans** la surface, en clair sur du teal profond, c'est-à-dire à
 * peine. Le retrait, lui, se voit du coin de l'œil et se sent sous le pouce —
 * et c'est précisément ce qu'on cherche sur un contrôle qu'on presse sans le
 * regarder, en tenant le téléphone d'une main.
 *
 * Le ressort est le **rapide** du schéma expressif, et pas le régime ordinaire.
 * Un enfoncement doit rattraper le doigt : à la vitesse d'un volet qui s'ouvre,
 * le bouton finirait de reculer après qu'on l'a relâché, ce qui donne
 * l'impression d'une interface en retard sur la main. Le ressort dépasse
 * légèrement au relâchement, et ce dépassement est la moitié de l'effet : c'est
 * lui qui fait « rebondir » le contrôle plutôt que le reposer.
 *
 * Il s'applique en `graphicsLayer` et ne remet donc **rien** en page : la
 * surface est redessinée à l'échelle, ses voisines ne bougent pas d'un pixel.
 * Sur un bouton pleine largeur collé au bas d'un volet, une mise en page animée
 * ferait respirer tout le volet à chaque appui.
 *
 * @param interactionSource celui **que le contrôle utilise**. Un
 *   `MutableInteractionSource` créé ici pour l'occasion ne recevrait jamais
 *   d'appui : c'est le composant cliquable qui les émet, et il faut donc lui
 *   passer la même instance.
 */
@Composable
fun Modifier.aulePress(
    interactionSource: InteractionSource,
    pressedScale: Float = AuleMotion.PRESS_SCALE,
): Modifier = composed {
    if (reduceMotionEnabled()) return@composed this

    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "aulePress",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Une hauteur d'entrée volontairement courte.
 *
 * À 12 dp, on lit « c'est arrivé ». À 40, on lit « ça a glissé depuis le bas de
 * l'écran », ce qui raconte un déplacement qui n'a pas eu lieu.
 */
private val DEFAULT_RISE = 12.dp
