package io.aule.android.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.badgeInk
import io.aule.android.core.designsystem.token.parseLineColor
import io.aule.android.core.model.TransportMode

/**
 * Le numéro d'une ligne, dans sa couleur.
 *
 * Composant métier s'il en est : sa couleur ne vient pas du thème mais du GTFS
 * du réseau, et c'est tout l'intérêt — le voyageur reconnaît « C3 » à sa teinte
 * avant de lire le texte. Aucun rôle Material ne peut porter cela, puisque la
 * couleur change d'une ligne à l'autre.
 *
 * Ce que Material apporte quand même : la `Surface`, qui gère le découpage, la
 * couleur de contenu locale et l'élévation, et le `Text`, qui suit l'échelle
 * typographique. Seule la paire fond/encre est calculée à part, et elle l'est
 * par mesure de luminance plutôt qu'à vue.
 *
 * Discret par construction : c'est un repère, pas un titre. Il garde sa taille
 * quel que soit le contexte pour qu'on reconnaisse « C3 » d'un coup d'œil.
 *
 * ## Pourquoi il reste plat — ni ombre, ni reflet
 *
 * Un aplat de couleur GTFS posé à plat se lit comme un échantillon de peinture,
 * et la tentation est grande de lui donner du relief. Les deux moyens usuels
 * sont fermés ici, et pour deux raisons différentes.
 *
 * L'**ombre**, parce que l'échelle du kit commence à six points de hauteur
 * (`AuleElevation.RESTING`), qui est la distance d'une barre collée à un bord.
 * Trente badges élevés de six points dans une liste font un écran qui décolle.
 *
 * Le **reflet** — un voile clair sur le haut, comme sur [AuleBrandSurface] —
 * pour une raison plus dure : l'encre de ce badge n'est pas choisie, elle est
 * **mesurée**. [badgeInk] compare la luminance de la couleur GTFS à
 * [io.aule.android.core.designsystem.token.LINE_BADGE_LUMINANCE_FLIP] et bascule
 * en clair ou en sombre. Un voile blanc posé après coup éclaircit le fond sans
 * que la mesure le sache, et il le fait exactement là où les hampes des
 * caractères passent. Sur une ligne de teinte moyenne — l'orange des lignes
 * express, le vert des scolaires — le blanc du texte y perd le tiers de son
 * contraste, sur des rapports qui n'ont déjà pas de marge. Sur une surface de
 * marque le reflet est sûr parce que le couple fond/encre y est fixe ; ici il ne
 * l'est pas, et c'est toute la différence.
 *
 * Ce que le badge garde pour ne pas être plat : sa couleur, qui est déjà
 * l'information, et le contraste que la mesure lui garantit.
 */
@Composable
fun LineBadge(
    line: String,
    colorHex: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val background = parseLineColor(colorHex)
    val ink = badgeInk(background)
    Surface(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        // Le plus petit cran de l'échelle, et il vient de l'échelle : le badge
        // est la plus petite plaque du produit, il suit les autres quand elles
        // s'arrondissent.
        shape = MaterialTheme.shapes.extraSmall,
        color = background.color,
        contentColor = ink.color,
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = BADGE_MIN_WIDTH)
                .padding(horizontal = AuleSpacing.sm, vertical = BADGE_VERTICAL_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = line,
                // Le slot appuyé porte la graisse que ce badge écrivait à la
                // main. Mêmes taille et interligne — le badge ne change pas
                // d'un point — mais le poids vient de l'échelle, et l'écran
                // cesse d'avoir son avis sur le poids d'un caractère.
                style = MaterialTheme.typography.labelSmallEmphasized,
            )
        }
    }
}

/**
 * Le mode de transport, dans sa teinte.
 *
 * Même raison d'être que [LineBadge] : la teinte est celle du marqueur de
 * carte pour ce mode, donc une donnée métier et non un rôle du thème.
 *
 * ## Pourquoi il reste un contour quand la ligne prend l'aplat
 *
 * Les deux badges voisinent — « 2 » puis « Tram » — et deux plaques pleines
 * côte à côte se disputent le premier regard, qui ne va alors ni à l'une ni à
 * l'autre. La hiérarchie est pourtant claire : le numéro de ligne est le fait,
 * le mode n'en est que la qualification. Le plein dit la ligne, le contour dit
 * le mode.
 *
 * Ce qui change quand même : le lavis seul, sans trait, laissait le badge se
 * dissoudre dans une surface claire. Le contour à la teinte du mode lui rend un
 * bord — c'est ce qui sépare une étiquette d'une tache.
 */
@Composable
fun TransportBadge(
    mode: TransportMode,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(AuleRadius.pill),
        color = tint.copy(alpha = AuleAlpha.TINT),
        contentColor = tint,
        border = BorderStroke(AuleStroke.hairline, tint.copy(alpha = AuleAlpha.OUTLINE)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmallEmphasized,
            modifier = Modifier.padding(
                horizontal = AuleSpacing.sm,
                vertical = BADGE_VERTICAL_PADDING,
            ),
        )
    }
}

/** Le badge respire moins que le reste : c'est ce qui le fait lire comme une étiquette. */
private val BADGE_VERTICAL_PADDING = 3.dp

/** Deux caractères tiennent sans que le badge se déforme sur un numéro à un chiffre. */
private val BADGE_MIN_WIDTH = 26.dp
