package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
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
    val still = reduceMotionEnabled()

    // Le pointillé défile d'une période complète, puis recommence : le motif
    // étant périodique, l'image de fin **est** l'image de départ, et la boucle
    // ne se voit pas. Une durée plus courte donnerait un fil qui court ; à
    // 2,4 s il dérive, ce qui est le mot du web (`animate-drift`).
    val phase = if (still) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "network-drift")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = -DASH_PERIOD,
            animationSpec = infiniteRepeatable(
                animation = tween(DRIFT_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "network-dash",
        )
        animated
    }

    val fade = if (quiet) TRACE_ALPHA_QUIET else TRACE_ALPHA

    Box(
        modifier = modifier
            .fillMaxSize()
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
) {
    val unit = size.width / VIEW_WIDTH
    fun stroke(width: Float, effect: PathEffect? = null) = Stroke(
        width = width * unit,
        cap = StrokeCap.Round,
        pathEffect = effect,
    )

    val main = path(NETWORK_MAIN)
    drawPath(main, color = ink.copy(alpha = MAIN_ALPHA * fade), style = stroke(MAIN_WIDTH))
    drawPath(
        path(NETWORK_SECONDARY),
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
        center = point(ACCENT_AT),
    )
    FADED_DOTS.forEach { (at, alpha) ->
        drawCircle(
            color = ink.copy(alpha = alpha * fade),
            radius = FADED_DOT * unit,
            center = point(at),
        )
    }
}

/**
 * Un point du repère web, ramené aux proportions de l'écran réel.
 *
 * La hauteur ne se ramène pas à l'écran entier mais à sa **bande basse**. Le
 * web n'a jamais son tracé sous le formulaire : il vit dans la colonne d'à
 * côté, et sur la page pleine largeur il passe sous un contenu court. Un
 * téléphone n'a pas de colonne d'à côté — étalé sur toute la hauteur, le
 * pointillé traversait le libellé du mot de passe, ce qui ne se lit pas comme
 * un fond mais comme une rayure. Ramené sous le contenu, il redevient ce qu'il
 * est : un pied de page graphique.
 */
private fun DrawScope.point(at: Offset): Offset = Offset(
    x = at.x / VIEW_WIDTH * size.width,
    y = (TRACE_TOP + at.y / VIEW_HEIGHT * TRACE_SPAN) * size.height,
)

/** Une polyligne du repère web, ramenée aux proportions de l'écran réel. */
private fun DrawScope.path(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, at ->
        val scaled = point(at)
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

/** La part centrale que la vignette laisse intacte. */
private const val VIGNETTE_CLEAR = 0.4f
private const val VIGNETTE_ALPHA = 0.6f
