package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleShape
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.TransportMode
import java.text.DecimalFormatSymbols

/**
 * Ce que deux listes disent d'un même arrêt.
 *
 * « Autour de vous » et la recherche répondent à deux questions différentes —
 * *qu'est-ce qu'il y a près de moi* et *où est ce que je cherche* — mais elles
 * répondent avec les mêmes objets : un lieu, son mode, sa distance, ses lignes.
 * Un arrêt qui se présenterait autrement d'une liste à l'autre obligerait à
 * réapprendre à le lire en changeant d'écran, et c'est le même écran à une
 * frappe près : la recherche s'ouvre par-dessus la carte que le volet occupe.
 *
 * Ce fichier ne porte donc que ce que les deux listes ont en commun. Ce qui
 * distingue un sommaire de proximité d'un résultat de recherche — l'étiquette
 * du plus proche, le tableau des attentes — reste dans l'écran qui en a besoin.
 */

/**
 * Le mode, dans sa teinte de carte.
 *
 * La même que celle du marqueur : reconnaître un arrêt de tram à sa couleur
 * dans la liste puis sur la carte est ce qui relie les deux vues.
 *
 * La pastille n'est pas un rond. C'est un biscuit à neuf lobes —
 * [AuleShape.modeAvatar] — et de loin, à la distance où on lit cette liste,
 * ça reste un rond ; c'est de près que la différence se voit. Le rond parfait
 * qu'elle portait avant est ce qu'on obtient quand personne n'a tranché ; la
 * silhouette est ce qu'on obtient quand quelqu'un a dessiné.
 *
 * Sur une carte de marque, l'aplat a déjà mangé la teinte du mode : l'avatar y
 * bascule sur l'encre de l'accent, sans quoi un bus gris sur du teal sombre ne
 * se verrait plus.
 *
 * @param glyph l'icône posée dans la pastille. Par défaut celle du mode ; une
 *   adresse, qui n'a pas de mode, y pose son épingle.
 * @param tint la teinte de la pastille. Par défaut celle du mode.
 */
@Composable
internal fun ModeAvatar(
    mode: TransportMode?,
    onBrand: Boolean = false,
    glyph: ImageVector = mode.avatarGlyph(),
    tint: Color = mode.avatarTint(),
) {
    val ink = if (onBrand) AuleTheme.tokens.onAccent.color else tint
    Box(
        modifier = Modifier
            .size(AVATAR_SIZE)
            .background(color = ink.copy(alpha = AuleAlpha.TINT), shape = AuleShape.modeAvatar()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(AVATAR_ICON_SIZE),
        )
    }
}

/**
 * L'icône d'un mode — et l'épingle quand il n'y en a pas.
 *
 * Une adresse n'est pas un arrêt : elle n'a ni bus ni tram à montrer, et lui
 * prêter l'icône d'un mode la ferait passer pour un point du réseau.
 */
@Composable
internal fun TransportMode?.avatarGlyph(): ImageVector = when (this) {
    TransportMode.BUS -> AuleGlyph.BUS.asImageVector()
    TransportMode.TRAM -> AuleGlyph.TRAM.asImageVector()
    TransportMode.BOAT -> Icons.Outlined.DirectionsBoat
    null -> AuleGlyph.PIN.asImageVector()
}

/**
 * La teinte d'un mode — et l'encre neutre quand il n'y en a pas.
 *
 * Les couleurs de mode sont des faits de réseau. Une adresse n'en est pas un :
 * elle prend le gris de l'encre secondaire, qui la range visiblement dans
 * l'autre famille sans jamais prétendre à une ligne.
 */
@Composable
internal fun TransportMode?.avatarTint(): Color =
    this?.markerColor(AuleTheme.night)?.color ?: MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Les lignes desservies, en badges.
 *
 * La nuit, un arrêt n'annonce aucun passage mais dessert toujours les mêmes
 * lignes : c'est cette information-là qui reste utile. Dans la recherche, c'est
 * même la seule qui distingue deux arrêts du même nom — celui où passe la 1 de
 * celui où passe la C6.
 */
@Composable
internal fun ServingStrip(lines: List<ServingLine>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = AuleSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lines.forEach { serving ->
            LineBadge(
                line = serving.line,
                colorHex = serving.lineColor,
                contentDescription = stringResource(R.string.line_badge, serving.line),
            )
        }
    }
}

@Composable
internal fun formatDistance(meters: Double): String =
    GeoMath.formatDistance(meters, DecimalFormatSymbols.getInstance().decimalSeparator)

/**
 * La pastille de mode, au cran de la pastille.
 *
 * C'est elle, et non le texte, qui fixait la hauteur d'une carte : deux lignes
 * de quatorze points font trente-six points, la pastille en faisait quarante.
 * Une carte dont la hauteur est décidée par sa décoration plutôt que par ce
 * qu'elle dit est une carte qui a grandi sans qu'on le demande.
 *
 * Au cran de [AuleChrome.pill] elle passe sous le texte, et redevient ce
 * qu'elle est : la teinte du mode, reconnaissable au coin de l'œil, qui relie
 * la rangée au marqueur de la carte. Elle ne porte aucun geste — la carte
 * entière est la cible — donc rien ne l'oblige au plancher tactile.
 */
private val AVATAR_SIZE = AuleChrome.pill
private val AVATAR_ICON_SIZE = AuleChrome.pillGlyph
