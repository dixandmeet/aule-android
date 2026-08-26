package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.SAVED_PLACES_LIMIT
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceIcon
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.shortPlaceName

/**
 * La gestion des adresses enregistrées.
 *
 * ## Ce qu'elle ajoute à la rangée de raccourcis
 *
 * La rangée sert à **partir** ; cet écran sert à **ranger**. C'est la même
 * séparation qu'entre toucher un arrêt sur la carte et ouvrir l'inventaire des
 * lignes : le geste courant reste à un doigt de la carte, et ce qu'on fait deux
 * fois par an vit derrière une porte.
 *
 * D'où l'inversion des gestes : ici, toucher une adresse l'**ouvre en
 * modification** au lieu de lancer l'itinéraire. Le contraire aurait fait
 * calculer un trajet à qui venait corriger un numéro de rue — et refermé du même
 * geste l'écran où il travaillait.
 *
 * ## Domicile et Travail y figurent, remplis ou non
 *
 * Même raison que dans la rangée : c'est ici qu'on vient les renseigner, et un
 * emplacement qui n'apparaît qu'une fois rempli ne se découvre jamais.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedPlacesSheet(
    places: List<SavedPlace>,
    onEdit: (SavedPlace) -> Unit,
    onFill: (SavedPlaceSlot) -> Unit,
    onDelete: (SavedPlace) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    val custom = places.filter { it.slot == SavedPlaceSlot.CUSTOM }
    val full = places.size >= SAVED_PLACES_LIMIT

    AuleTheme {
        ModalBottomSheet(
            onDismissRequest = onClose,
            modifier = modifier,
            sheetState = sheetState,
        ) {
            SheetBody {
                SheetTitle(stringResource(R.string.saved_places_title))

                SheetCard(modifier = Modifier.fillMaxWidth()) {
                    listOf(SavedPlaceSlot.HOME, SavedPlaceSlot.WORK).forEachIndexed { index, slot ->
                        if (index > 0) SheetRowDivider()
                        val filled = places.firstOrNull { it.slot == slot }
                        SavedPlaceRow(
                            title = stringResource(slot.labelRes()),
                            detail = filled?.let { shortPlaceName(it.label) },
                            icon = (filled?.icon ?: SavedPlaceIcon.forSlot(slot)),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (filled != null) onEdit(filled) else onFill(slot)
                            },
                            onDelete = filled?.let { { onDelete(it) } },
                        )
                    }
                }

                if (custom.isNotEmpty()) {
                    SheetCard(modifier = Modifier.fillMaxWidth()) {
                        custom.forEachIndexed { index, place ->
                            if (index > 0) SheetRowDivider()
                            SavedPlaceRow(
                                title = place.name.ifEmpty { shortPlaceName(place.label) },
                                detail = shortPlaceName(place.label),
                                icon = place.icon,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onEdit(place)
                                },
                                onDelete = { onDelete(place) },
                            )
                        }
                    }
                } else if (places.isEmpty()) {
                    AuleEmptyState(
                        title = stringResource(R.string.saved_places_empty_title),
                        detail = stringResource(R.string.saved_places_empty_detail),
                        icon = AuleGlyph.PIN.asImageVector(),
                    )
                }

                if (full) {
                    // Le plafond se dit avant qu'on ait rempli un formulaire
                    // pour rien. Un bouton simplement éteint ferait chercher
                    // pourquoi.
                    Text(
                        text = stringResource(R.string.saved_places_full),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onFill(SavedPlaceSlot.CUSTOM)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AuleTouch.minimum),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(AuleControl.icon),
                        )
                        Text(
                            text = stringResource(R.string.saved_places_add),
                            modifier = Modifier.padding(start = AuleSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une adresse dans la liste de gestion.
 *
 * La corbeille est une **cible séparée**, à côté de la rangée : la placer dans
 * un menu contextuel l'aurait cachée derrière un appui long que rien n'annonce,
 * et la poser sur la rangée entière aurait fait supprimer ce qu'on voulait
 * ouvrir. Elle n'existe pas pour un emplacement encore vide — il n'y a rien à
 * effacer.
 */
@Composable
private fun SavedPlaceRow(
    title: String,
    detail: String?,
    icon: SavedPlaceIcon,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val unset = stringResource(R.string.saved_place_unset)
    val editLabel = stringResource(R.string.saved_place_edit_a11y, title)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = editLabel }
                .padding(AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SavedPlaceAvatar(icon = icon.asImageVector(), muted = detail == null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail ?: unset,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .padding(end = AuleSpacing.sm)
                    .defaultMinSize(
                        minWidth = AuleTouch.minimum,
                        minHeight = AuleTouch.minimum,
                    ),
            ) {
                Icon(
                    imageVector = AuleGlyph.TRASH.asImageVector(),
                    contentDescription = stringResource(
                        R.string.saved_place_delete_a11y,
                        title,
                    ),
                    tint = colors.error,
                )
            }
        }
    }
}

/**
 * La confirmation d'une suppression.
 *
 * Elle dit **où** l'adresse disparaît — « sur cet appareil comme sur les
 * autres ». C'est ce qui distingue cette suppression d'un simple retrait de
 * liste : un favori effacé ici l'est aussi sur le téléphone resté à la maison,
 * et personne ne devrait l'apprendre en le constatant.
 *
 * Même forme que la déconnexion : action destructrice en teinte d'erreur,
 * annulation à gauche.
 */
@Composable
internal fun SavedPlaceDeleteDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AuleGlyph.TRASH.asImageVector(),
                contentDescription = null,
            )
        },
        iconContentColor = colors.error,
        title = {
            Text(
                text = stringResource(R.string.saved_place_delete_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = { Text(stringResource(R.string.saved_place_delete_detail, name)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.errorContainer,
                    contentColor = colors.onErrorContainer,
                ),
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
            ) {
                Text(stringResource(R.string.saved_place_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
            ) {
                Text(stringResource(R.string.saved_place_delete_cancel))
            }
        },
    )
}
