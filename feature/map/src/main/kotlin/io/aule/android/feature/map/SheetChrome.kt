package io.aule.android.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * La grammaire commune des volets de la carte.
 *
 * Les six volets — arrêt, véhicule, lieu, autour de vous, itinéraire, détail du
 * trajet — vivent dans le **même** `BottomSheetScaffold`. On y passe de l'un à
 * l'autre sans transition : toucher un arrêt puis un bus ne change pas de page,
 * ça change de contenu dans le même cadre. Un titre qui grossit d'un volet à
 * l'autre, un cartouche qui a des coins ici et pas là, un filet qui se décale,
 * et le cadre se met à bouger sous le contenu.
 *
 * Ce fichier ne contient donc que ce que les volets ont en commun, et rien de
 * ce qui les distingue.
 */

/**
 * Le titre d'un volet.
 *
 * `headlineSmall` et non `titleLarge` : ce dernier porte le rôle `DATA`, dont
 * les chiffres sont à chasse fixe. Sur un nom de lieu — « Gare Nord 2 » — ces
 * chiffres de tableau d'affichage se voient.
 */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * L'intitulé d'une section, à l'intérieur d'un volet.
 *
 * Annoncé comme un titre : c'est ce qui permet à TalkBack de sauter de
 * « prochains passages » à « lignes desservies » sans traverser les rangées.
 */
@Composable
internal fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Le cartouche d'une section.
 *
 * Un seul aplat plutôt qu'une suite de rangées posées sur le fond du volet :
 * c'est ce qui distingue « le tableau des passages » de « du texte dans un
 * volet ». Posé **dans** un volet, il ne porte pas d'ombre — l'ombre est déjà
 * celle du volet, et une seconde ferait flotter une carte au-dessus d'une
 * carte.
 */
@Composable
internal fun SheetCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Column { content() }
    }
}

/**
 * Le filet entre deux rangées d'un cartouche — pleine largeur.
 *
 * Un alinéa supposerait une colonne de tête de largeur constante, or un badge
 * de ligne se règle sur son numéro : « 1 » et « TRAM2 » ne finissent pas au
 * même endroit, et le filet se décalerait d'une rangée à l'autre.
 */
@Composable
internal fun SheetRowDivider() {
    HorizontalDivider(
        thickness = AuleStroke.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
