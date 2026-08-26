package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceIcon
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.shortPlaceName

/**
 * Les raccourcis d'adresses, en tête de la recherche.
 *
 * ## Pourquoi une rangée, et pas une liste
 *
 * Une liste de cartes aurait poussé les destinations récentes et les arrêts
 * proches sous le clavier : trois listes verticales dans un volet qui en montre
 * déjà deux, et la première réponse se trouve à un défilement. La rangée tient
 * sur une ligne, se lit d'un regard, et laisse le reste du volet intact.
 *
 * C'est aussi la forme du geste qu'on cherche : **ouvrir, toucher Domicile,
 * partir**. Un raccourci qu'il faut chercher n'en est plus un.
 *
 * ## Domicile et Travail sont là avant d'exister
 *
 * Les deux emplacements nommés s'affichent même vides, en « À définir ». C'est
 * ce qui fait découvrir la fonction : un raccourci absent ne se remarque pas, et
 * personne ne va chercher dans un menu de quoi enregistrer une adresse dont
 * l'application ne lui a jamais parlé. Le doigt tombe sur « Domicile », l'écran
 * propose de le renseigner — la promesse et le moyen au même endroit.
 *
 * ⚠️ **Une pastille vide ne calcule pas d'itinéraire, elle en ouvre l'éditeur.**
 * Le contraire — un bouton qui ne fait rien parce que la donnée manque — est
 * exactement le « bouton qui ment » que le Flutter avait refusé de poser.
 */
@Composable
internal fun SavedPlacesStrip(
    places: List<SavedPlace>,
    onSelect: (SavedPlace) -> Unit,
    onFill: (SavedPlaceSlot) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val custom = places.filter { it.slot == SavedPlaceSlot.CUSTOM }

    BoxWithConstraints(modifier = modifier) {
    // ⚠️ **La largeur d'une pastille se mesure, elle ne s'écrit pas.**
    //
    // À largeur fixe, la deuxième pastille était coupée par le bord droit du
    // S21 et la troisième — « Ajouter » — tombait hors écran : la seule porte
    // vers la gestion des adresses n'était atteignable que par un défilement
    // que rien n'annonçait. Et la valeur qui aurait tenu sur cet écran-là aurait
    // cédé au premier réglage de texte agrandi.
    //
    // Une fraction de la largeur disponible garantit ce qui compte : **deux
    // pastilles entières, et un morceau de la troisième**. Ce morceau est
    // l'annonce du défilement — c'est lui qui dit qu'il y a une suite.
    val chipWidth = maxWidth * CHIP_WIDTH_FRACTION
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Domicile puis Travail, toujours, et toujours dans cet ordre : c'est
        // une position qu'on apprend au doigt, et une rangée qui se réordonne
        // selon ce qui est rempli ferait rater la cible à qui vise sans lire.
        listOf(SavedPlaceSlot.HOME, SavedPlaceSlot.WORK).forEachIndexed { index, slot ->
            val filled = places.firstOrNull { it.slot == slot }
            SavedPlaceChip(
                width = chipWidth,
                title = stringResource(slot.labelRes()),
                detail = filled?.let { shortPlaceName(it.label) },
                icon = (filled?.icon ?: SavedPlaceIcon.forSlot(slot)).asImageVector(),
                rank = index,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (filled != null) onSelect(filled) else onFill(slot)
                },
            )
        }

        custom.forEachIndexed { index, place ->
            SavedPlaceChip(
                width = chipWidth,
                title = place.name.ifEmpty { shortPlaceName(place.label) },
                detail = shortPlaceName(place.label),
                icon = place.icon.asImageVector(),
                rank = index + 2,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelect(place)
                },
            )
        }

        // Le dernier, jamais le premier : ce qui sert tous les jours passe
        // devant ce qui sert deux fois par an.
        ManageChip(
            hasPlaces = places.isNotEmpty(),
            rank = custom.size + 2,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onManage()
            },
        )
    }
    }
}

/**
 * Un raccourci.
 *
 * Deux lignes : ce qu'on a nommé, et où ça mène. La seconde est en gris et
 * n'est **pas** décorative — deux « Crèche » n'existent pas, mais « Travail »
 * et « Dépôt » peuvent désigner le même endroit, et le sous-titre est ce qui
 * permet de s'en apercevoir sans ouvrir.
 *
 * [detail] absent : l'emplacement attend son adresse, et la pastille le dit.
 * Elle garde alors la même taille — une rangée dont les pastilles changent de
 * largeur selon ce qui est rempli se relit à chaque fois.
 */
@Composable
private fun SavedPlaceChip(
    width: Dp,
    title: String,
    detail: String?,
    icon: ImageVector,
    rank: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val unset = stringResource(R.string.saved_place_unset)
    val hint = stringResource(
        if (detail == null) R.string.saved_place_set_hint else R.string.saved_place_go_hint,
    )
    val label = if (detail == null) {
        stringResource(R.string.saved_place_unset_a11y, title)
    } else {
        stringResource(R.string.saved_place_a11y, title, detail)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .auleEnter(index = rank)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                onClick(label = hint, action = null)
            },
        colors = CardDefaults.cardColors(
            // Une pastille vide se distingue par sa surface, pas par un trait :
            // le contour se serait ajouté aux quatre bords déjà dessinés par la
            // rangée, et la ligne serait devenue une grille.
            containerColor = if (detail == null) {
                colors.surfaceContainer
            } else {
                colors.surfaceContainerHigh
            },
            contentColor = colors.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavedPlaceAvatar(icon = icon, muted = detail == null)
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail ?: unset,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * La porte d'entrée de la gestion.
 *
 * Elle change de dessin selon qu'il y a quelque chose à gérer : un « + » quand
 * la liste est nue, les réglages quand elle ne l'est plus. Deux pastilles — une
 * pour ajouter, une pour gérer — auraient occupé la fin de la rangée avec deux
 * chemins vers le même écran.
 */
@Composable
private fun ManageChip(hasPlaces: Boolean, rank: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(
        if (hasPlaces) R.string.saved_places_manage_a11y else R.string.saved_places_add,
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .defaultMinSize(minWidth = AuleTouch.minimum, minHeight = AuleTouch.minimum)
            .auleEnter(index = rank)
            .semantics(mergeDescendants = true) { contentDescription = label },
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainer,
            contentColor = colors.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hasPlaces) Icons.Outlined.Tune else Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(AuleControl.icon),
            )
            Text(
                text = stringResource(
                    if (hasPlaces) R.string.saved_places_manage else R.string.saved_places_add,
                ),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * La pastille d'icône d'un favori.
 *
 * Même dessin que [ModeAvatar], teinte en moins : un favori n'appartient à
 * aucun mode de transport, et lui donner la couleur du tram ferait croire à un
 * lien avec le réseau. Elle prend donc la teinte secondaire du thème — présente,
 * mais qui ne prétend rien.
 */
@Composable
internal fun SavedPlaceAvatar(
    icon: ImageVector,
    muted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(AuleControl.avatarBadge),
        shape = MaterialTheme.shapes.small,
        color = if (muted) Color.Transparent else colors.secondaryContainer,
        contentColor = if (muted) colors.onSurfaceVariant else colors.onSecondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AuleControl.check),
            )
        }
    }
}

/** Le nom d'un emplacement — une phrase, donc une ressource (ADR-011). */
internal fun SavedPlaceSlot.labelRes(): Int = when (this) {
    SavedPlaceSlot.HOME -> R.string.saved_place_home
    SavedPlaceSlot.WORK -> R.string.saved_place_work
    // Un lieu personnalisé porte le nom qu'on lui a donné : il n'y a pas de mot
    // d'emplacement à afficher, et « Personnalisé » n'apprendrait rien.
    SavedPlaceSlot.CUSTOM -> R.string.saved_places_section
}

/**
 * La part de la largeur qu'occupe une pastille.
 *
 * 42 % : deux pastilles entières, leur gouttière, et ce qui reste laisse voir la
 * troisième — assez pour qu'on sache qu'il y a une suite, pas assez pour qu'on
 * la prenne pour une pastille tronquée.
 *
 * Toutes de la même largeur, et c'est le point : deux lignes de texte coupées au
 * même endroit se balayent d'un regard, là où des pastilles réglées sur leur
 * contenu font une rangée en dents de scie où l'œil s'arrête à chaque bord.
 * « Domicile » et « 12 rue Paul Bellamy » tiennent ; « Salle de sport de la
 * Beaujoire » s'ellipse — le sous-titre n'est là que pour départager deux
 * raccourcis voisins.
 */
private const val CHIP_WIDTH_FRACTION = 0.42f
