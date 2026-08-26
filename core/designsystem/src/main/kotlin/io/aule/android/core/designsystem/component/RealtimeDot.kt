package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Le point qui dit « cette donnée est mesurée, pas théorique ».
 *
 * Le vert vient de [realtimeInk] et non d'un rôle Material : « temps réel »
 * est une notion du métier transport, pas une place dans la hiérarchie
 * visuelle. Le gris de repli, lui, est bien un rôle — c'est simplement du
 * contenu secondaire.
 *
 * ## L'onde, plutôt que le clignotement
 *
 * L'ancien point faisait varier **sa propre** opacité, de 1 à 0,45. Le geste se
 * voyait, et il disait l'inverse de ce qu'on voulait : une seconde sur deux, la
 * marque qui atteste que la donnée est réelle s'effaçait à moitié. Ce n'est pas
 * un détail de goût — c'est la seule information de la rangée qui s'affaiblit
 * périodiquement, et elle le fait en plein soleil, quand elle est déjà à la
 * limite.
 *
 * Le point est donc **plein en permanence**, et c'est une onde qui part de lui :
 * elle naît au bord du disque, s'écarte jusqu'au bord de la boîte et s'éteint en
 * chemin. L'écho d'un radar : le disque garde ses six points, la boîte en fait
 * douze pour loger l'onde. L'information ne bouge plus, seule l'émission bouge —
 * ce qui est exactement ce qu'on voulait dire.
 *
 * ## Le plein et l'anneau
 *
 * Mesuré, le point est **plein** ; théorique, c'est un **anneau**. La nuance
 * était portée par la couleur seule — vert contre gris — donc perdue pour qui
 * ne distingue pas les deux, et fragile sur un écran baissé au soleil. La forme
 * la redit, et ne coûte rien : c'est le même appel de dessin.
 *
 * ## Sur une surface de marque
 *
 * [onBrand] n'est pas une commodité d'appel : sans lui, le point posé sur le
 * dégradé de marque disparaît. Le gris de repli est `onSurfaceVariant`, un rôle
 * fait pour une surface **claire** ; sur le teal profond il tombe autour de
 * 1,4:1, c'est-à-dire qu'il n'existe plus — et c'est précisément la rangée de
 * l'arrêt recommandé, celle qu'on lit en premier. Le vert du temps réel n'y
 * tient pas beaucoup mieux, et il y perd de toute façon son sens : sur l'aplat
 * de marque, la couleur ne code plus rien.
 *
 * Sur ce fond les deux points prennent donc la même encre — celle de l'accent —
 * et c'est la **forme** qui porte seule la nuance. C'est le moment où le plein
 * et l'anneau cessent d'être un doublon de la couleur pour en devenir le
 * remplaçant, et la raison pour laquelle ils valaient d'être ajoutés.
 *
 * ## Ce qu'il coûte, puisqu'il s'affiche des dizaines de fois
 *
 * Une liste d'arrêts en porte trente, et elle défile. Trois décisions tiennent
 * la facture :
 *
 * - **Aucune recomposition par image.** La phase n'est déréférencée qu'à
 *   l'intérieur du `Canvas`, donc une image ne réinvalide que le *dessin*.
 *   L'ancien point lisait sa valeur par `by` au moment de composer : trente
 *   recompositions par image, pour trente disques de six points.
 * - **Une seule horloge.** La phase se calcule sur l'horloge d'animation
 *   infinie — la même pour toute l'application — et non sur l'instant où le
 *   point est entré en composition. Les points d'une liste qui défile battent
 *   donc **ensemble** : trente pulsations en phase se lisent comme une
 *   respiration de l'écran, les mêmes déphasées comme du grésillement.
 * - **Pas de polygone.** [AuleShape.live] ferait un plus beau point, et
 *   construirait un `Path` par instance — ce que le kit interdit précisément
 *   dans une rangée de liste. Deux `drawCircle` suffisent à dire la même chose.
 *
 * Décoratif pour TalkBack : le texte voisin (ou le `contentDescription` du
 * parent) porte déjà la nuance. L'annoncer deux fois n'apprend rien.
 *
 * @param onBrand le point est posé sur le dégradé de marque, et non sur une
 *   surface du thème.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RealtimeDot(
    isLive: Boolean,
    liveDescription: String,
    scheduledDescription: String,
    modifier: Modifier = Modifier,
    onBrand: Boolean = false,
) {
    val color = when {
        onBrand -> AuleTheme.tokens.onAccent.color
        isLive -> realtimeInk()
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val phase = rememberPulsePhase(active = isLive && !reduceMotionEnabled())

    Canvas(
        modifier = modifier
            .size(DOT_BOX)
            .semantics { hideFromAccessibility() },
    ) {
        val core = DOT_CORE.toPx() / 2f
        if (!isLive) {
            // L'anneau se dessine sur le bord du disque, pas autour : à six
            // points, un contour posé à l'extérieur ferait un point plus gros
            // que le point plein, et la rangée boiterait.
            val stroke = AuleStroke.emphasis.toPx()
            drawCircle(color = color, radius = core - stroke / 2f, style = Stroke(width = stroke))
            return@Canvas
        }

        val progress = phase.value
        if (progress > 0f) {
            // L'onde s'écarte vite puis ralentit, et s'éteint en s'écartant.
            // Une progression linéaire donnerait un cercle qui grossit ; celle-ci
            // donne quelque chose qui a été **émis**.
            val spread = 1f - (1f - progress) * (1f - progress)
            drawCircle(
                color = color,
                radius = core + (size.minDimension / 2f - core) * spread,
                alpha = (1f - spread) * AuleAlpha.SUBDUED,
            )
        }
        drawCircle(color = color, radius = core)
    }
}

/**
 * La phase de la pulsation, entre 0 et 1, commune à tous les points de l'écran.
 *
 * `withInfiniteAnimationFrameMillis` donne l'instant de l'image sur l'horloge
 * d'animation infinie — celle-là même que `rememberInfiniteTransition` emploie,
 * et celle que les tests neutralisent par `InfiniteAnimationPolicy`. Deux
 * propriétés en découlent, et ce sont les deux qu'on cherchait :
 *
 * - l'origine du temps est **partagée**, donc deux points composés à dix
 *   secondes d'écart — ce qui est le cas ordinaire d'une liste qui défile —
 *   battent malgré tout ensemble ;
 * - l'écriture de la phase n'invalide que ce qui la **lit**, et seul le dessin
 *   la lit.
 *
 * Inactif, l'état reste à zéro et rien ne tourne : ni coroutine, ni image
 * demandée. C'est le cas de tous les passages théoriques d'une liste, et de
 * tous les points quand l'appareil demande moins de mouvement.
 */
@Composable
private fun rememberPulsePhase(active: Boolean): State<Float> =
    produceState(initialValue = 0f, active) {
        if (!active) return@produceState
        while (true) {
            withInfiniteAnimationFrameMillis { millis ->
                value = (millis % AuleMotion.PULSE_MS) / AuleMotion.PULSE_MS.toFloat()
            }
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
fun realtimeInk(): Color = AuleTheme.tokens.realtime.ink.color

/**
 * L'encre « retard » : une perturbation prévue, pas une alerte bloquante.
 *
 * Même raison que [realtimeInk] : le rôle `tertiary` est de la hiérarchie
 * visuelle, pas du métier transport.
 */
@Composable
@ReadOnlyComposable
fun delayInk(): Color = AuleTheme.tokens.delay.ink.color

/** Le point lui-même : la marque qu'on lit. Elle n'a pas changé de taille. */
private val DOT_CORE = 6.dp

/**
 * La boîte, qui loge l'onde.
 *
 * Six points de plus que le disque, soit trois de chaque côté : de quoi voir
 * l'onde partir. Elle pourrait déborder de sa boîte — Compose ne découpe pas un
 * `Canvas` — mais un composant qui dessine chez le voisin est un composant qui
 * se fera découper le jour où on le posera dans une carte.
 */
private val DOT_BOX = 12.dp
