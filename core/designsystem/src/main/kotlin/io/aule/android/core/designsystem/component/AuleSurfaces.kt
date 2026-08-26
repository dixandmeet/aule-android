package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleStroke
import kotlinx.coroutines.delay

/**
 * Le fond de marque : un lavis d'accent en haut, deux halos aux angles.
 *
 * C'est ce qui distingue un écran d'Aule d'un formulaire blanc. Il vit ici et
 * non dans un écran parce qu'un deuxième écran qui le redessinerait le
 * redessinerait forcément un peu différemment.
 *
 * Le lavis prend `primary` de jour et `primaryContainer` de nuit. Ce n'est pas
 * une inconséquence : Material éclaircit `primary` en ambiance sombre pour que
 * l'encre reste lisible, or on cherche ici l'inverse d'une encre — une lueur
 * qui reste **sous** le contenu. Le conteneur, lui, fonce la nuit, ce qui est
 * exactement le comportement voulu pour un fond.
 */
@Composable
fun AuleAmbientBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val night = AuleTheme.night
    val glow = if (night) colors.primaryContainer else colors.primary
    val glowAlpha = if (night) AuleAlpha.GLOW_STRONG else AuleAlpha.GLOW
    val haloAlpha = if (night) AuleAlpha.HALO_STRONG else AuleAlpha.HALO
    val farHaloAlpha = if (night) AuleAlpha.TINT else AuleAlpha.HALO_SOFT
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.surface)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = glowAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.12f),
                        radius = size.maxDimension * 0.62f,
                    ),
                )
                halo(
                    color = glow,
                    alpha = haloAlpha,
                    radius = HALO_RADIUS.toPx(),
                    center = Offset(size.width + HALO_OVERHANG.toPx(), -HALO_OVERHANG.toPx()),
                )
                halo(
                    color = glow,
                    alpha = farHaloAlpha,
                    radius = HALO_RADIUS.toPx() * 1.09f,
                    center = Offset(
                        -HALO_OVERHANG.toPx() * 1.5f,
                        size.height + HALO_OVERHANG.toPx(),
                    ),
                )
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * La surface de marque : ce que l'écran veut qu'on regarde en premier.
 *
 * Un aplat de teal uni est correct, et mort. Les deux mêmes tons posés en
 * diagonale donnent une surface qui a une source de lumière — et c'est
 * exactement la distance entre « une carte coloriée » et « une carte ». Le
 * dégradé vient d'[io.aule.android.core.designsystem.token.AuleTokens.accentSweep] :
 * deux crans de la même famille, jamais deux couleurs.
 *
 * S'y ajoutent trois choses qu'on ne remarque que par leur absence :
 *
 * - le **reflet**, un voile clair sur la moitié haute, qui donne au dégradé une
 *   direction d'éclairage plutôt qu'une simple variation ;
 * - le **liseré**, un trait d'un point sur l'arête haute, qui s'éteint avant le
 *   bas. C'est ce qu'un objet réel fait de la lumière qui l'éclaire, et comme il
 *   épouse la forme, il redit l'arrondi que l'aplat laisse deviner ;
 * - l'**ombre teintée**, qui porte la couleur de l'accent au lieu du noir. Une
 *   ombre noire sous une surface colorée la salit ; la même ombre à la teinte
 *   de la surface la fait flotter.
 *
 * ## Le dégradé se calcule, il ne se déclare pas
 *
 * Deux couleurs passées à `Brush.linearGradient` sont interpolées **canal par
 * canal**, en sRGB. Or le sRGB n'est pas perceptuellement uniforme : sur deux
 * teals profonds, le milieu numérique tombe nettement plus sombre que le milieu
 * perçu, et le dégradé creuse une bande morte en son centre — le défaut qu'on
 * voit sans savoir le nommer, celui qui fait qu'un bouton a l'air *imprimé*.
 *
 * Les deux bouts sont donc échantillonnés en [SWEEP_STOPS] arrêts calculés avec
 * `lerp`, qui interpole en **Oklab**, où une distance égale se voit égale. Ce ne
 * sont pas des couleurs supplémentaires — la règle des deux tons tient — ce sont
 * des points sur la même course, posés là où l'œil les attend. Le banding
 * disparaît avec la bande morte : plus la course est échantillonnée, moins la
 * quantification a de quoi s'accrocher.
 *
 * ## Ce qui bouge
 *
 * Un **balayage** traverse la surface une fois, en diagonale, peu après qu'elle
 * s'est posée. C'est le geste qui manquait le plus : la surface arrivait déjà —
 * translation et opacité, comme le reste de l'écran — mais elle arrivait
 * *éteinte*, et rien ne distinguait l'action principale des trois blocs de texte
 * qui la précèdent. L'éclat ne dure pas, ne se rejoue pas, et se coupe net dès
 * que l'appareil demande moins de mouvement.
 *
 * Sous le doigt, elle **s'enfonce** — voir
 * [io.aule.android.core.designsystem.aulePress]. L'ondulation de Material y
 * répond aussi, mais elle y répond en clair sur du teal profond, c'est-à-dire à
 * peine.
 *
 * À réserver à **une** surface par écran. Deux surfaces de marque qui se font
 * face, et aucune des deux n'est plus l'action principale.
 *
 * @param onClick rend la surface actionnable, avec l'ondulation et la cible
 *   tactile que Material lui donne. `null` pour une surface qui n'est qu'un
 *   affichage — et qui, n'étant pas un contrôle, ne s'enfonce pas.
 */
@Composable
fun AuleBrandSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: AuleElevation = AuleElevation.FLOATING,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = AuleTheme.tokens
    val sweep = tokens.accentSweep
    val ink = tokens.onAccent.color
    val fill = remember(sweep) {
        auleSweepStops(from = sweep.from.color, to = sweep.to.color)
    }
    // Le voile s'éteint à mi-hauteur, et il s'éteint *dans* la surface : à
    // l'ancien découpage au tiers, le rectangle finissait là où le dégradé
    // finissait aussi, ce qui posait une arête horizontale en travers du
    // bouton. Un reflet n'a pas de bord.
    val sheen = remember(ink) {
        Brush.verticalGradient(
            0f to ink.copy(alpha = AuleAlpha.SHEEN),
            SHEEN_FADE to Color.Transparent,
            1f to Color.Transparent,
        )
    }
    val rim = remember(ink) {
        Brush.verticalGradient(
            0f to ink.copy(alpha = AuleAlpha.RIM),
            RIM_FADE to Color.Transparent,
        )
    }
    val glint = rememberGlint()

    val painted = Modifier
        .drawBehind {
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = fill,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawRect(brush = sheen)
            drawGlint(color = ink, progress = glint.value)
        }
        // Après le fond, jamais avant : `Modifier.border` dessine dans l'ordre
        // de la chaîne, et un liseré posé en premier disparaîtrait sous l'aplat
        // qui le suit.
        .border(BorderStroke(AuleStroke.hairline, rim), shape)

    if (onClick == null) {
        Surface(
            modifier = modifier.auleShadow(elevation, shape, AuleShadowTint.ACCENT),
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
        ) {
            Box(modifier = painted, content = content)
        }
    } else {
        // La même instance des deux côtés : l'enfoncement lit les appuis que le
        // `Surface` cliquable émet. Deux sources, et le bouton ne bougerait
        // jamais — sans qu'aucune erreur ne le signale.
        val interactions = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier
                .aulePress(interactions)
                .auleShadow(elevation, shape, AuleShadowTint.ACCENT),
            shape = shape,
            color = Color.Transparent,
            contentColor = ink,
            interactionSource = interactions,
        ) {
            Box(modifier = painted, content = content)
        }
    }
}

/**
 * La course du dégradé, échantillonnée là où l'œil l'attend.
 *
 * Les deux bouts restent les deux crans du jeton ; entre eux, `lerp` interpole
 * en Oklab et non canal par canal, ce qui est toute la différence entre un
 * dégradé qui se sent et un dégradé qui se voit.
 *
 * Six arrêts : assez pour que la quantification n'ait plus de marche où
 * s'accrocher sur un bouton pleine largeur, pas assez pour que la liste coûte
 * quoi que ce soit — elle est calculée une fois par ambiance, jamais par image.
 */
private fun auleSweepStops(from: Color, to: Color): Array<Pair<Float, Color>> =
    Array(SWEEP_STOPS) { index ->
        val fraction = index / (SWEEP_STOPS - 1f)
        fraction to lerp(from, to, fraction)
    }

/**
 * L'avancée du balayage, de 0 à 1, une fois pour toutes.
 *
 * Il part **après** l'entrée de la surface : joués ensemble, les deux mouvements
 * se disputent — on voit quelque chose bouger et briller sans savoir lequel des
 * deux on regarde. Il ne se rejoue pas non plus : un éclat périodique sur
 * l'action principale d'un écran de conduite est un clignotant, et il finirait
 * par être le seul mouvement de l'écran qu'on n'a pas demandé.
 *
 * Quand l'appareil demande moins de mouvement, la valeur naît **terminée**. Le
 * dessin s'en sert comme d'un interrupteur : à 1, il ne reste rien à peindre,
 * donc pas de version atténuée à maintenir.
 */
@Composable
private fun rememberGlint(): State<Float> {
    if (reduceMotionEnabled()) return remember { mutableFloatStateOf(1f) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(AuleMotion.SHEEN_DELAY_MS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = AuleMotion.SHEEN_MS, easing = LinearEasing),
        )
    }
    return progress.asState()
}

/**
 * Le balayage : une bande de lumière en diagonale, qui traverse et s'en va.
 *
 * Elle entre par la gauche et sort par la droite, en partant d'assez loin pour
 * qu'on ne la voie jamais *apparaître* — une lumière qui naît au bord se lit
 * comme un défaut d'affichage. Sa progression est linéaire, contrairement à tout
 * le reste du kit : un reflet qui ralentit en fin de course a l'air de se poser,
 * or celui-ci passe.
 *
 * Rien n'est dessiné aux deux extrémités de la course, ce qui est la seule chose
 * qui compte pour la facture : une fois l'éclat passé, la surface redevient
 * deux `drawRect` pour toujours.
 */
private fun DrawScope.drawGlint(color: Color, progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val half = size.width * GLINT_WIDTH
    val travel = size.width + half * 2f
    val center = -half + travel * progress
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = AuleAlpha.GLINT),
            1f to Color.Transparent,
            start = Offset(center - half, 0f),
            end = Offset(center + half, size.height),
        ),
    )
}

/**
 * Le verre posé sur la carte.
 *
 * Tout ce qui surplombe la `MapView` flotte au-dessus d'un fond imprévisible :
 * une place blanche, un toit sombre, un plan d'eau. Une surface opaque y est
 * lisible et coupe la carte en deux ; une surface trop transparente laisse
 * remonter un bâtiment sous le texte. Ce composant tient le milieu, et le tient
 * **au même endroit partout** — c'est sa raison d'être, plus que son aspect.
 *
 * ## Ce que ce verre n'est pas
 *
 * Ce n'est pas un flou d'arrière-plan. Compose ne sait pas flouter ce qu'il y a
 * **derrière** un composable — `Modifier.blur` floute le contenu, pas le fond —
 * et la `MapView` est une vue native rendue hors de l'arbre Compose, donc hors
 * d'atteinte de tout effet Compose. Un vrai verre dépoli demanderait de capturer
 * la carte image par image et de la flouter en Vulkan : sur un S21 en tournée,
 * c'est du budget GPU pris au rendu cartographique lui-même.
 *
 * Ce qu'on fait à la place : un aplat de surface à 92 %, une bordure claire d'un
 * point, et l'ombre. C'est ce que l'œil lit comme du verre, pour le prix d'un
 * rectangle.
 */
@Composable
fun AuleGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: AuleElevation = AuleElevation.FLOATING,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.auleShadow(elevation, shape),
        shape = shape,
        color = colors.surface.copy(alpha = AuleAlpha.GLASS),
        contentColor = colors.onSurface,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Box(content = content)
    }
}

/**
 * Un halo d'angle.
 *
 * Un `drawCircle` d'une couleur unie donne un disque à **bord franc** : posé sur
 * la surface, il se lit comme une forme peinte — un quart de rond gris dans le
 * coin — et non comme une lumière. Le dégradé rend au halo ce qu'on lui
 * demandait : une clarté qui s'éteint avant d'atteindre son bord.
 */
private fun DrawScope.halo(
    color: Color,
    alpha: Float,
    radius: Float,
    center: Offset,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Les halos débordent volontairement de l'écran : un cercle entier se lit comme
 * une forme, un cercle coupé se lit comme une lumière.
 */
private val HALO_RADIUS = 220.dp
private val HALO_OVERHANG = 40.dp

/** Le nombre d'arrêts où la course du dégradé est échantillonnée. */
private const val SWEEP_STOPS = 6

/**
 * Où le reflet a fini de s'éteindre.
 *
 * À mi-hauteur : au-dessus, la lumière ; en dessous, la surface. Plus bas, le
 * voile devient un second dégradé qui contredit le premier ; plus haut, il
 * redevient la bande à bord franc qu'on vient de retirer.
 */
private const val SHEEN_FADE = 0.5f

/** Où le liseré s'éteint. Une arête éclairée par le haut ne descend pas. */
private const val RIM_FADE = 0.45f

/**
 * La largeur du balayage, en fraction de la surface.
 *
 * Un tiers : assez large pour que la bande éclaire la surface plutôt que de la
 * rayer, assez étroite pour qu'on la voie passer.
 */
private const val GLINT_WIDTH = 0.34f
