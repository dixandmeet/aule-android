package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleSpacing
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
 * Le corps d'un volet : ce qui défile, et les gouttières autour.
 *
 * ## Dix copies d'un même préambule
 *
 * Chacun des volets ouvrait sur les mêmes six lignes — pleine largeur, défilement
 * vertical, seize points de gouttière, seize en pied, douze entre les blocs. Six
 * lignes recopiées dix fois, qui avaient déjà divergé : l'un espaçait ses blocs
 * de seize au lieu de douze, l'autre avait perdu sa marge de pied. Un cadre
 * partagé dont chaque volet garde sa propre copie n'est plus un cadre partagé,
 * c'est une ressemblance.
 *
 * ## Pourquoi le défilement est ici et pas dans le volet
 *
 * `BottomSheetScaffold` mesure son contenu pour en déduire le cran
 * intermédiaire. Un contenu qui défile déjà ne se mesure plus : il déclarerait
 * la hauteur de l'écran, et le volet s'ouvrirait toujours plein. Le défilement
 * appartient donc au corps de chaque volet, sous la borne de hauteur que
 * `MapScreen` lui pose — et c'est cette borne, pas le contenu, qui arrête le
 * volet sous la barre d'état.
 *
 * @param gutters les seize points de côté. Le volet « autour de vous » les
 *   refuse : ses rangées vont d'un bord à l'autre, et c'est leur cartouche qui
 *   porte la marge.
 * @param footer la marge de pied. Un volet qui se termine par une barre
 *   d'action la refuse : la barre a la sienne.
 * @param spacing l'écart entre deux blocs du volet.
 */
@Composable
internal fun SheetBody(
    modifier: Modifier = Modifier,
    gutters: Boolean = true,
    footer: Boolean = true,
    spacing: Dp = AuleSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (gutters) AuleSpacing.lg else 0.dp)
            .padding(bottom = if (footer) AuleSpacing.lg else 0.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * Le titre d'un volet, avec ce qui le chiffre.
 *
 * ## Le compte appartient au titre, pas à la liste
 *
 * « 138 lignes » traînait entre le champ de recherche et la première famille, à
 * mi-hauteur d'un volet, seul sur sa ligne. À cet endroit il ne se rattache à
 * rien : ni au titre qu'il précise, ni à la liste qu'il compte. Posé sous le
 * titre, serré à quatre points, il devient ce qu'il a toujours été — la seconde
 * moitié de la phrase « Lignes du réseau, il y en a cent trente-huit ». Et
 * quand la recherche filtre, c'est le titre lui-même qui répond.
 *
 * Le bloc entier s'annonce d'une traite à TalkBack : le compte n'est pas une
 * étape de plus au balayage, c'est une précision sur le titre.
 */
@Composable
internal fun SheetHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le champ de recherche d'un volet.
 *
 * ## Un aplat, et non un contour
 *
 * C'était un `OutlinedTextField` au cran médian — quatorze points de rayon,
 * cerné d'un trait. Dans un volet dont tous les blocs sont des aplats sans
 * contour à vingt-deux points, ce champ était la seule chose encadrée et la
 * seule chose moins ronde : il se lisait comme un élément rapporté, posé au
 * milieu de la grammaire du volet plutôt qu'écrit dedans.
 *
 * Il prend donc l'aplat des cartouches, `surfaceContainerHigh`, et le rayon
 * plein — la forme que Material donne à ses propres champs de recherche. Le
 * même aplat **partout**, socle de la carte compris : un champ posé sur une
 * carte blanche sans aplat cesse de se lire comme un champ, et l'essai a été
 * fait — il ne restait qu'une phrase grise au milieu du blanc.
 *
 * ## La croix
 *
 * Une recherche qui filtre à la frappe se **défait** aussi souvent qu'elle se
 * fait : on tape trois lettres, on regarde, on revient à la liste entière.
 * Sans la croix, ce retour coûte autant d'appuis que la recherche elle-même,
 * visés sur la touche la plus étroite du clavier.
 *
 * La touche de validation ferme le clavier : le filtre s'applique déjà à la
 * frappe, et promettre une action qui n'existe pas est pire que de ne rien
 * promettre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetSearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fieldStyle = MaterialTheme.typography.bodyMedium

    // Le champ tient sa saisie, l'appelant tient la sienne, et les deux se
    // recopient. C'est le prix de l'API à état de Material 3 — la seule qui
    // laisse régler la marge intérieure, voir plus bas — devant des appelants
    // qui, eux, n'ont qu'une chaîne dans leur modèle d'écran.
    val field = rememberTextFieldState(query)
    val latestQuery = rememberUpdatedState(query)
    val latestOnQuery = rememberUpdatedState(onQuery)
    LaunchedEffect(query) {
        if (query != field.text.toString()) field.setTextAndPlaceCursorAtEnd(query)
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text != latestQuery.value) latestOnQuery.value(text)
        }
    }

    TextField(
        state = field,
        // ⚠️ **Un plancher, et non une hauteur — et la marge intérieure à nous.**
        //
        // Material réserve seize points au-dessus et seize en dessous de la
        // ligne de saisie, et se donne un minimum de 56. Forcer 48 par le
        // modificateur ne recentrait rien : il **rognait par le bas**, et le
        // « y » de « Ranzay » perdait sa jambe — vu à l'écran, sur un réseau
        // qui compte aussi Bouffay et Longchamp.
        //
        // Quatre points suffisent à la ligne. Le plancher tactile donne les
        // quarante-huit, le volet récupère les huit que le minimum de Material
        // lui prenait, et un texte agrandi pousse le champ au-delà du plancher
        // au lieu de se faire couper.
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuleChrome.bar),
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = fieldStyle,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(text = placeholder, style = fieldStyle) },
        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.search_clear),
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
            top = AuleSpacing.xs,
            bottom = AuleSpacing.xs,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            disabledContainerColor = colors.surfaceContainerHigh,
            // Le trait sous un champ **plein** est un reste de Material 2 : il
            // souligne un aplat qui se voit déjà, et il casse le rayon plein en
            // bas. La mise au point se dit par le curseur et par le libellé.
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Le titre d'un volet.
 *
 * `titleMediumEmphasized` : le slot appuyé de Material 3 Expressive, au cran que
 * Material réserve aux **en-têtes de conteneur** — un volet, une carte, une
 * barre — quand `headline` désigne le titre d'un écran ou d'un dialogue.
 *
 * ## Il a baissé deux fois, et pour la même raison
 *
 * Il portait d'abord `headlineSmallEmphasized`, soit 24 points de gras : un nom
 * de destination — « Chantrerie - Grandes Écoles » — y prenait deux lignes
 * pleines et le quart de ce qu'on voyait, avant d'avoir dit quoi que ce soit
 * d'utile. Il est passé à 22, ce qui a réglé le cas des deux lignes sans régler
 * le fond : sur un écran de 360 points, un titre de 22 points reste l'objet le
 * plus gros d'un volet dont on ne voit que la moitié haute, alors qu'il ne
 * répond à aucune question. Il **nomme** ce qu'on vient d'ouvrir — on le sait
 * déjà, on vient de le toucher.
 *
 * À 16 points appuyés il tient toujours son rang, parce qu'un titre se repère
 * autant à sa position et à sa graisse qu'à sa taille : il est seul sur sa
 * ligne, en haut, plus gras que tout ce qui suit. Ce qu'il rend, ce sont les
 * quatre points de hauteur que prend le prochain passage — la raison d'ouvrir
 * le volet.
 *
 * Le cadre ne bouge pas pour autant : les six volets partagent ce composant,
 * ils descendent donc ensemble.
 */
@Composable
internal fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
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
 *
 * Appuyé lui aussi, et pour la même raison qu'il est en `onSurfaceVariant` : il
 * doit se distinguer du contenu **sans** lui prendre la vedette. La graisse
 * sépare, la couleur retient — les deux ensemble donnent un intitulé qui se
 * repère au balayage sans jamais se lire avant ce qu'il annonce.
 */
@Composable
internal fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMediumEmphasized,
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
 *
 * Le contour a disparu. Il compensait une échelle de surfaces trop serrée : sur
 * l'ancienne palette, `surfaceContainerHigh` et la surface du volet ne se
 * distinguaient que d'un point de clarté, et sans trait le cartouche n'existait
 * pas. La nouvelle échelle descend assez bas pour que l'aplat se suffise —
 * et un cartouche sans contour se lit comme un bloc, là où le même cerné se lit
 * comme une boîte.
 */
@Composable
internal fun SheetCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
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
