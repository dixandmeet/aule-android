package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.aule.android.core.designsystem.reduceMotionEnabled

/**
 * Le fond des écrans d'accueil : un réseau qui passe derrière le formulaire.
 *
 * C'est le portage du panneau de marque du web — `SpacePro/components/brand/
 * aule-screen.tsx`, lui-même porté du dashboard — et il n'y a qu'une raison de
 * le porter : la connexion est la seule image du produit que tout le monde
 * voit, quelle que soit la surface. Elle doit être la **même** sur aule.fr,
 * dans l'espace de travail et sur le téléphone, faute de quoi trois produits
 * répondent au même nom.
 *
 * ## Ce que le web montre, et ce qu'un téléphone peut en montrer
 *
 * Sur un écran large, le web coupe la page en deux : le réseau à gauche, le
 * formulaire à droite. Sous 1024 points la colonne de marque **disparaît** — un
 * panneau de moitié n'a plus de place — et il ne reste que le formulaire. Un
 * téléphone tombe donc dans ce cas, et le porter tel quel donnerait un fond
 * noir uni.
 *
 * Mais la charte web a déjà répondu à cette question ailleurs : `AuleBackdrop`
 * pose le même tracé **pleine page**, derrière un contenu qui n'a pas de
 * colonne de marque. C'est ce cas-là qu'on porte, et ce sont ses chemins —
 * décalés plus haut, plus resserrés — qu'on retrouve dans [NETWORK_MAIN] et
 * [NETWORK_SECONDARY].
 *
 * ## Le cadrage
 *
 * Le web dessine dans une boîte de 600 × 900 et la **recadre** pour couvrir le
 * panneau. Un téléphone est bien plus haut que large : le même recadrage
 * grossirait le motif jusqu'à ne plus montrer qu'un trait. Les points sont donc
 * ramenés en proportion des deux côtés — le tracé s'étire avec l'écran, ce
 * qu'une ligne droite supporte — et seules les **épaisseurs** suivent la
 * largeur seule, sans quoi un trait de trois points deviendrait une barre.
 *
 * ## Les trois couches
 *
 * Elles se lisent dans cet ordre et pas dans un autre :
 *
 * 1. le **lavis**, un dégradé en diagonale du conteneur bas vers la surface,
 *    qui donne au noir une direction plutôt qu'un aplat ;
 * 2. le **tracé** : deux lignes épaisses et presque éteintes — le réseau qu'on
 *    devine — puis la même ligne principale redite en pointillé d'accent, qui
 *    défile. C'est la seule chose qui bouge de l'écran, et elle bouge lentement ;
 * 3. la **vignette**, un voile de surface qui se referme sur les bords, pour que
 *    le texte du formulaire ne se pose jamais sur le motif à pleine intensité.
 *
 * @param modifier la taille du fond, qui **ne se décide pas ici** : plein
 *   écran chez Aule Pro (`fillMaxSize()`), la hauteur du seul formulaire dans
 *   le volet de compte du voyageur, qui laisse la carte visible au-dessus. Le
 *   dessin suit la taille reçue, quelle qu'elle soit — ses points sont posés
 *   en proportion.
 * @param quiet vrai quand le fond passe sous un contenu dense — l'inscription
 *   et ses cartes de choix. Le tracé s'éteint alors d'un tiers : le même motif
 *   qui pose la connexion devient un bruit derrière six lignes de formulaire.
 */
@Composable
fun AuleNetworkBackdrop(
    modifier: Modifier = Modifier,
    quiet: Boolean = false,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val phase = networkDrift()
    val fade = if (quiet) TRACE_ALPHA_QUIET else TRACE_ALPHA

    Box(
        modifier = modifier
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(colors.surfaceContainerLow, colors.surface),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.35f, size.height),
                    ),
                )
                networkTrace(
                    ink = colors.onSurface,
                    accent = colors.primary,
                    fade = fade,
                    dashPhase = phase,
                    // La bande basse d'un écran plein : voir [TRACE_TOP] et [TRACE_SPAN].
                    originY = TRACE_TOP * size.height,
                    scaleY = TRACE_SPAN * size.height / VIEW_HEIGHT,
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            VIGNETTE_CLEAR to Color.Transparent,
                            1f to colors.surface.copy(alpha = VIGNETTE_ALPHA),
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.3f),
                        radius = size.maxDimension * 0.9f,
                    ),
                )
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Le tracé seul, posé derrière un contenu qui a **déjà** son fond.
 *
 * ## Ce qu'il emprunte à la scène, et ce qu'il lui laisse
 *
 * [AuleNetworkBackdrop] pose trois couches : un lavis en diagonale, le tracé, puis une vignette
 * qui referme les bords. Les deux premières et la dernière **font un fond** — elles décident de
 * la couleur du rectangle — et c'est bien ce qu'on veut d'un écran de connexion, dont la nuit
 * est permanente.
 *
 * Un volet, lui, a déjà sa couleur, sa forme et son ombre : le blanc franc de
 * `surfaceContainerLowest`, qui lui rend son arête au-dessus d'une carte claire. Y poser la
 * scène entière repeindrait la feuille en noir et ferait de chaque fiche d'arrêt un écran de
 * marque. Ce modificateur ne garde donc que la **couche du milieu** : le réseau qu'on devine, et
 * le pointillé d'accent qui dérive dessus.
 *
 * ## Où il se pose, et pourquoi il ne défile pas
 *
 * Sur le cadre du volet, jamais sur son contenu : `drawBehind` dessine sous les enfants du nœud
 * qui le porte, et un tracé accroché à une colonne qui défile deviendrait un motif de papier
 * peint qui glisse avec la liste. Posé sur le cadre, il reste ce qu'il est — un fond.
 *
 * Le **cadrage**, en revanche, n'est pas celui de la scène : il part du haut du volet et se
 * mesure à la largeur, pour la raison dite à l'appel ci-dessous. Le motif tient alors entre 45 %
 * et 120 % de la largeur sous le bord de la feuille — sous son en-tête, et dans le palier replié
 * d'une fiche d'arrêt.
 *
 * @param quiet vrai par défaut, **à l'inverse de la scène**. Derrière un formulaire de
 *   connexion, le tracé pose l'écran ; derrière une liste de départs, il ne doit que se
 *   deviner.
 * @param animated faux quand le volet est fermé ou replié à zéro. Une animation infinie
 *   continue de réclamer une image par battement tant qu'elle est composée, et l'hôte d'un
 *   volet garde son contenu composé bien après l'avoir escamoté. Le mouvement suit donc ce qui
 *   est visible, et le tracé reste dessiné — immobile — quand il ne l'est pas.
 */
@Composable
fun Modifier.auleNetworkTrace(
    quiet: Boolean = true,
    animated: Boolean = true,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val phase = if (animated) networkDrift() else 0f
    val fade = if (quiet) TRACE_ALPHA_QUIET else TRACE_ALPHA
    return drawBehind {
        networkTrace(
            ink = colors.onSurface,
            accent = colors.primary,
            fade = fade,
            dashPhase = phase,
            // ⚠️ **La hauteur du nœud n'est pas celle du volet visible**, et c'est pour cela
            // que le cadrage de la scène ne marche pas ici. Un contenu de volet est mesuré à
            // sa hauteur **déployée** — `heightIn(max = …)` pour la feuille de la carte, la
            // hauteur de la liste entière pour un volet modal qui défile — tandis que ce qu'on
            // en voit dépend de l'endroit où la feuille s'est arrêtée. Un motif posé aux deux
            // tiers de cette hauteur-là tombe sous le bord de l'écran, et ne s'y montre jamais.
            // Relevé sur un S21 le 15/09/2026 : le tracé était bien dessiné, à 1 300 pixels
            // d'un volet qui n'en montrait que 1 176.
            //
            // Il est donc posé **en haut du nœud** et mis à l'échelle de la **largeur** : le
            // haut d'un volet est ce qu'on en voit toujours, replié comme déployé, et une
            // largeur ne dépend pas de l'endroit où la feuille s'est arrêtée.
            originY = 0f,
            scaleY = size.width * SHEET_SPAN / VIEW_HEIGHT,
        )
    }
}

/**
 * De combien le pointillé a dérivé, à cette image-ci.
 *
 * Il défile d'une période complète, puis recommence : le motif étant périodique, l'image de fin
 * **est** l'image de départ, et la boucle ne se voit pas. Une durée plus courte donnerait un fil
 * qui court ; à 2,4 s il dérive, ce qui est le mot du web (`animate-drift`).
 *
 * Zéro quand le système demande moins de mouvement : c'est la seule chose qui bouge de l'écran
 * de connexion, et la retirer n'en retire rien d'autre.
 */
@Composable
private fun networkDrift(): Float {
    if (reduceMotionEnabled()) return 0f
    val transition = rememberInfiniteTransition(label = "network-drift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = -DASH_PERIOD,
        animationSpec = infiniteRepeatable(
            animation = tween(DRIFT_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "network-dash",
    )
    return phase
}

/**
 * Le tracé lui-même, dans le repère du web.
 *
 * Les deux lignes de fond sont posées à l'encre du texte et à quelques
 * centièmes : ce n'est pas un dessin qu'on regarde, c'est ce qui empêche le
 * fond d'être un rectangle. L'accent ne revient que sur la principale, et
 * seulement en pointillé — un second trait plein ferait deux réseaux.
 */
private fun DrawScope.networkTrace(
    ink: Color,
    accent: Color,
    fade: Float,
    dashPhase: Float,
    originY: Float,
    scaleY: Float,
) {
    val unit = size.width / VIEW_WIDTH
    fun stroke(width: Float, effect: PathEffect? = null) = Stroke(
        width = width * unit,
        cap = StrokeCap.Round,
        pathEffect = effect,
    )

    val main = path(NETWORK_MAIN, originY, scaleY)
    drawPath(main, color = ink.copy(alpha = MAIN_ALPHA * fade), style = stroke(MAIN_WIDTH))
    drawPath(
        path(NETWORK_SECONDARY, originY, scaleY),
        color = ink.copy(alpha = SECONDARY_ALPHA * fade),
        style = stroke(SECONDARY_WIDTH),
    )
    drawPath(
        main,
        color = accent.copy(alpha = ACCENT_ALPHA * fade),
        style = stroke(
            ACCENT_WIDTH,
            PathEffect.dashPathEffect(
                floatArrayOf(DASH_ON * unit, DASH_OFF * unit),
                dashPhase * unit,
            ),
        ),
    )

    drawCircle(
        color = accent.copy(alpha = fade),
        radius = ACCENT_DOT * unit,
        center = point(ACCENT_AT, originY, scaleY),
    )
    FADED_DOTS.forEach { (at, alpha) ->
        drawCircle(
            color = ink.copy(alpha = alpha * fade),
            radius = FADED_DOT * unit,
            center = point(at, originY, scaleY),
        )
    }
}

/**
 * Un point du repère web, ramené aux proportions de la surface réelle.
 *
 * La largeur se ramène toujours à la largeur : le tracé s'étire avec l'écran,
 * ce qu'une ligne droite supporte. La **hauteur**, elle, se décide dehors —
 * [originY] dit où commence la boîte de dessin et [scaleY] combien vaut une
 * unité de ses neuf cents.
 *
 * Elle se décide dehors parce que les deux surfaces qui portent ce tracé ne
 * mesurent pas la même chose. Un écran plein connaît sa hauteur, et le motif y
 * prend sa bande basse : le web n'a jamais son tracé sous le formulaire — il
 * vit dans la colonne d'à côté — et étalé sur toute la hauteur d'un téléphone,
 * le pointillé traversait le libellé du mot de passe, ce qui ne se lit pas
 * comme un fond mais comme une rayure. Un **volet**, lui, ne connaît pas la
 * sienne : ce qu'il mesure est sa hauteur déployée, et ce qu'on en voit dépend
 * de l'endroit où la feuille s'est arrêtée. Son cadrage part donc du haut et
 * suit la largeur — voir [Modifier.auleNetworkTrace].
 */
private fun DrawScope.point(at: Offset, originY: Float, scaleY: Float): Offset = Offset(
    x = at.x / VIEW_WIDTH * size.width,
    y = originY + at.y * scaleY,
)

/** Une polyligne du repère web, ramenée aux proportions de la surface réelle. */
private fun DrawScope.path(points: List<Offset>, originY: Float, scaleY: Float): Path =
    Path().apply {
        points.forEachIndexed { index, at ->
            val scaled = point(at, originY, scaleY)
            if (index == 0) moveTo(scaled.x, scaled.y) else lineTo(scaled.x, scaled.y)
        }
    }

/** La boîte de dessin du web, dont les chemins ci-dessous portent les valeurs. */
private const val VIEW_WIDTH = 600f
private const val VIEW_HEIGHT = 900f

/** `M-20 660 L120 640 L240 520 L360 480 L480 360 L620 300` */
private val NETWORK_MAIN = listOf(
    Offset(-20f, 660f),
    Offset(120f, 640f),
    Offset(240f, 520f),
    Offset(360f, 480f),
    Offset(480f, 360f),
    Offset(620f, 300f),
)

/** `M-20 780 L160 760 L320 630 L640 570` */
private val NETWORK_SECONDARY = listOf(
    Offset(-20f, 780f),
    Offset(160f, 760f),
    Offset(320f, 630f),
    Offset(640f, 570f),
)

/** Le point d'accent, posé sur un sommet du tracé principal. */
private val ACCENT_AT = Offset(360f, 480f)

private val FADED_DOTS = listOf(
    Offset(480f, 360f) to 0.7f,
    Offset(120f, 640f) to 0.5f,
)

private const val MAIN_WIDTH = 26f
private const val SECONDARY_WIDTH = 16f
private const val ACCENT_WIDTH = 3f
private const val ACCENT_DOT = 7f
private const val FADED_DOT = 5f

private const val DASH_ON = 10f
private const val DASH_OFF = 12f
private const val DASH_PERIOD = DASH_ON + DASH_OFF
private const val DRIFT_MS = 2400

private const val MAIN_ALPHA = 0.1f
private const val SECONDARY_ALPHA = 0.08f
private const val ACCENT_ALPHA = 0.6f

/**
 * L'opacité d'ensemble du tracé, celle du web (`opacity-40` sur la page pleine
 * largeur), et sa version assourdie pour les écrans qui portent un formulaire
 * long.
 */
private const val TRACE_ALPHA = 0.4f
private const val TRACE_ALPHA_QUIET = 0.26f

/**
 * La bande de l'écran où le tracé vit : là où il commence, et ce qu'il occupe.
 *
 * Le motif du web tient dans les deux tiers bas de sa boîte ; ramené à ces
 * deux valeurs, il occupe le tiers bas d'un téléphone, sous l'action et
 * derrière la mention légale — les deux seules choses de l'écran qu'on ne lit
 * pas caractère par caractère.
 */
private const val TRACE_TOP = 0.45f
private const val TRACE_SPAN = 0.62f

/**
 * Ce que vaut la boîte de dessin sous un volet, en parts de sa **largeur**.
 *
 * Le motif occupe les unités 300 à 800 de ses neuf cents : à 1,35 largeur, il tient donc entre
 * 45 % et 120 % de la largeur sous le haut du volet — un peu moins de la moitié d'un écran de
 * téléphone. C'est ce qu'il faut pour qu'il soit **entier dans le palier replié** d'une fiche
 * d'arrêt sans monter derrière son titre, et qu'il reste dans le tiers haut une fois la feuille
 * déployée. Plus grand, il devient un décor qu'on regarde ; plus petit, un gribouillis.
 */
private const val SHEET_SPAN = 1.35f

/** La part centrale que la vignette laisse intacte. */
private const val VIGNETTE_CLEAR = 0.4f
private const val VIGNETTE_ALPHA = 0.6f
