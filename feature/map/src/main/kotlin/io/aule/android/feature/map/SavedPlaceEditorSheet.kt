package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.MIN_PLACE_QUERY_LENGTH
import io.aule.android.core.model.Place
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceIcon
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.contextLabel
import io.aule.android.core.model.repository.PlaceSearchRepository
import io.aule.android.core.model.shortLabel
import io.aule.android.core.model.shortPlaceName

/**
 * Ce que l'éditeur ouvre : un emplacement à remplir, ou un favori à retoucher.
 *
 * Deux cas et pas trois : « ajouter un lieu personnalisé » est un
 * [Fill][SavedPlaceTarget.Fill] sur [SavedPlaceSlot.CUSTOM]. Les distinguer
 * aurait donné deux chemins vers le même formulaire.
 */
internal sealed interface SavedPlaceTarget {
    data class Fill(val slot: SavedPlaceSlot) : SavedPlaceTarget
    data class Edit(val place: SavedPlace) : SavedPlaceTarget
}

/**
 * Enregistrer ou modifier une adresse.
 *
 * ## Ce que le formulaire demande, et dans quel ordre
 *
 * L'adresse d'abord, le reste ensuite — c'est l'ordre de ce qu'on est venu
 * faire. Un formulaire qui ouvre sur « Nom » demande d'inventer un intitulé
 * avant de savoir de quoi ; ici on cherche « Bel Air », on le trouve, et le nom
 * se propose tout seul à partir de l'adresse. Le champ reste modifiable : c'est
 * une proposition, pas une contrainte.
 *
 * ## Domicile et Travail n'ont pas de nom à saisir
 *
 * Leur nom **est** leur emplacement, et il vit dans les ressources (ADR-011).
 * Leur offrir un champ « Nom » laisserait enregistrer un « Domicile » appelé
 * « Maison », qui s'afficherait « Maison » en français et « Maison » en
 * anglais — c'est-à-dire une traduction perdue pour rien.
 *
 * ## Rien ne s'enregistre sans adresse
 *
 * « Enregistrer » reste éteint tant qu'aucun lieu n'est choisi. Un favori sans
 * coordonnées est un raccourci qui ne mène nulle part, et le laisser créer
 * reviendrait à poser le bouton qui ment qu'on cherche justement à éviter.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SavedPlaceEditorSheet(
    target: SavedPlaceTarget,
    catalog: List<TransitStop>,
    repository: PlaceSearchRepository,
    dispatchers: AuleDispatchers,
    logger: AuleLogger,
    onSave: (SavedPlaceEdit) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current

    val editing = (target as? SavedPlaceTarget.Edit)?.place
    val slot = editing?.slot ?: (target as SavedPlaceTarget.Fill).slot

    // Le brouillon vit ici et non dans un modèle d'écran : il ne survit pas au
    // volet, et rien d'autre ne le lit. Le remonter au ViewModel aurait fait
    // recomposer la carte à chaque lettre du nom.
    var chosen by remember(target) {
        mutableStateOf(editing?.let { Place(it.label, it.coordinate, it.stopMode) })
    }
    var name by remember(target) { mutableStateOf(editing?.name.orEmpty()) }
    var icon by remember(target) {
        mutableStateOf(editing?.icon ?: SavedPlaceIcon.forSlot(slot))
    }
    var confirmingDelete by remember(target) { mutableStateOf(false) }

    val picker = remember(repository, dispatchers) {
        PlacePickerModel(repository, dispatchers, logger)
    }
    DisposableEffect(picker) { onDispose { picker.close() } }

    AuleTheme {
        ModalBottomSheet(
            onDismissRequest = onClose,
            modifier = modifier,
            sheetState = sheetState,
        ) {
            SheetBody {
                // ⚠️ **Le titre nomme ce qu'on renseigne, pas ce qu'on fait.**
                //
                // Touchant « Domicile », on ouvrait « Nouvelle adresse » : plus
                // rien à l'écran ne disait que le lieu choisi deviendrait le
                // domicile. Le seul instant où l'on pouvait s'en assurer était
                // après avoir enregistré. Un emplacement nommé porte donc son
                // nom ici aussi.
                SheetTitle(
                    stringResource(
                        when {
                            slot != SavedPlaceSlot.CUSTOM -> slot.labelRes()
                            editing == null -> R.string.saved_place_editor_new
                            else -> R.string.saved_place_editor_edit
                        },
                    ),
                )

                val place = chosen
                if (place == null) {
                    SheetSectionLabel(stringResource(R.string.saved_place_address))
                    SheetSearchField(
                        query = picker.query,
                        onQuery = { picker.search(catalog, it) },
                        placeholder = stringResource(R.string.saved_place_address_hint),
                    )
                    PickerResults(
                        picker = picker,
                        onPick = { picked ->
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            chosen = picked
                            // Le nom se propose à partir de l'adresse, et
                            // seulement s'il est vide : réécrire par-dessus une
                            // saisie effacerait ce que quelqu'un vient de taper.
                            if (name.isBlank() && slot == SavedPlaceSlot.CUSTOM) {
                                name = shortPlaceName(picked.label)
                            }
                            picker.reset()
                        },
                    )
                } else {
                    ChosenAddress(
                        place = place,
                        onChange = {
                            chosen = null
                            picker.reset()
                        },
                    )

                    if (slot == SavedPlaceSlot.CUSTOM) {
                        AuleFormField(
                            label = stringResource(R.string.saved_place_name),
                            value = name,
                            onValueChange = { name = it },
                            placeholder = stringResource(R.string.saved_place_name_hint),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SheetSectionLabel(stringResource(R.string.saved_place_symbol))
                    SymbolPicker(selected = icon, onSelect = { icon = it })

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editing != null) {
                            TextButton(
                                onClick = { confirmingDelete = true },
                                modifier = Modifier.defaultMinSize(
                                    minHeight = AuleTouch.minimum,
                                ),
                            ) {
                                Text(stringResource(R.string.saved_place_delete))
                            }
                        }
                        Box(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onSave(
                                    SavedPlaceEdit(
                                        id = editing?.id,
                                        name = name,
                                        slot = slot,
                                        icon = icon,
                                        place = place,
                                    ),
                                )
                                onClose()
                            },
                            colors = auleAccentButtonColors(),
                            modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                        ) {
                            Text(stringResource(R.string.saved_place_save))
                        }
                    }
                }
            }
        }
    }

    if (confirmingDelete && editing != null) {
        SavedPlaceDeleteDialog(
            name = editing.displayName(),
            onConfirm = {
                confirmingDelete = false
                onDelete(editing.id)
                onClose()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * L'adresse retenue, et de quoi en changer.
 *
 * Le bouton dit « Changer » plutôt que de porter une croix : effacer laisserait
 * le formulaire dans un état où « Enregistrer » s'éteint sans qu'on ait rien
 * demandé, alors qu'on voulait seulement corriger le numéro de rue.
 */
@Composable
private fun ChosenAddress(place: Place, onChange: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = place.contextLabel()
    SheetCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AuleGlyph.PIN.asImageVector(),
                contentDescription = null,
                modifier = Modifier.size(AuleControl.icon),
                tint = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
            ) {
                Text(
                    text = place.shortLabel(),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (context.isNotEmpty()) {
                    Text(
                        text = context,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(
                onClick = onChange,
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
            ) {
                Text(stringResource(R.string.saved_place_address_change))
            }
        }
    }
}

/**
 * Ce que la recherche de l'éditeur propose.
 *
 * Arrêts d'abord, adresses ensuite — le même ordre que la recherche de la
 * carte, parce que c'est le même geste et que deux ordres différents pour deux
 * champs qui se ressemblent obligeraient à relire à chaque fois.
 */
@Composable
private fun PickerResults(picker: PlacePickerModel, onPick: (Place) -> Unit) {
    val query = picker.query.trim()
    if (query.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        if (picker.stops.isNotEmpty()) {
            SheetCard(modifier = Modifier.fillMaxWidth()) {
                picker.stops.forEachIndexed { index, hit ->
                    if (index > 0) SheetRowDivider()
                    PickerRow(
                        title = hit.label,
                        detail = hit.sublabel(),
                        onClick = {
                            onPick(
                                Place(
                                    label = hit.label,
                                    coordinate = hit.coordinate,
                                    // Le mode fait de ce favori un arrêt du
                                    // réseau, dont on pourra demander les
                                    // passages. Le perdre en ferait une adresse.
                                    stopMode = hit.mode,
                                ),
                            )
                        },
                    )
                }
            }
        }

        if (picker.isGeocoding && picker.places.isEmpty()) {
            AuleLoadingState(label = stringResource(R.string.search_geocoding))
        }

        if (picker.places.isNotEmpty()) {
            SheetCard(modifier = Modifier.fillMaxWidth()) {
                picker.places.forEachIndexed { index, place ->
                    if (index > 0) SheetRowDivider()
                    PickerRow(
                        title = place.shortLabel(),
                        detail = place.contextLabel().ifEmpty {
                            stringResource(R.string.search_place_generic)
                        },
                        onClick = { onPick(place) },
                    )
                }
            }
        }

        if (picker.isEmpty) {
            AuleEmptyState(
                title = stringResource(
                    if (query.length < MIN_PLACE_QUERY_LENGTH) {
                        R.string.search_short_title
                    } else {
                        R.string.search_empty_title
                    },
                ),
                detail = if (query.length < MIN_PLACE_QUERY_LENGTH) {
                    stringResource(R.string.search_short_detail, query, MIN_PLACE_QUERY_LENGTH)
                } else {
                    stringResource(R.string.search_empty_detail, query)
                },
                icon = AuleGlyph.SEARCH.asImageVector(),
            )
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .selectable(selected = false, onClick = onClick)
            .padding(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Le choix de l'icône.
 *
 * Une grille qu'on embrasse d'un regard, et non une liste déroulante : dix
 * dessins tiennent en deux rangées, et reconnaître une salle de sport à son
 * haltère est plus rapide que de lire « Sport » dans un menu.
 *
 * Chaque case s'annonce comme un bouton radio — c'est un choix exclusif — et
 * porte le nom de son intention, faute de quoi TalkBack lirait dix cases
 * identiques.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolPicker(selected: SavedPlaceIcon, onSelect: (SavedPlaceIcon) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        SavedPlaceIcon.entries.forEach { candidate ->
            val chosen = candidate == selected
            val label = stringResource(candidate.labelRes())
            Surface(
                modifier = Modifier
                    .size(AuleControl.avatar)
                    .selectable(
                        selected = chosen,
                        role = Role.RadioButton,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(candidate)
                        },
                    )
                    .semantics { contentDescription = label },
                shape = MaterialTheme.shapes.medium,
                color = if (chosen) colors.secondaryContainer else colors.surfaceContainerHigh,
                contentColor = if (chosen) colors.onSecondaryContainer else colors.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = candidate.asImageVector(filled = chosen),
                        contentDescription = null,
                        modifier = Modifier.size(AuleControl.icon),
                    )
                }
            }
        }
    }
}

/** Le nom d'une icône — une phrase, donc une ressource (ADR-011). */
internal fun SavedPlaceIcon.labelRes(): Int = when (this) {
    SavedPlaceIcon.HOME -> R.string.saved_place_symbol_home
    SavedPlaceIcon.WORK -> R.string.saved_place_symbol_work
    SavedPlaceIcon.SCHOOL -> R.string.saved_place_symbol_school
    SavedPlaceIcon.GYM -> R.string.saved_place_symbol_gym
    SavedPlaceIcon.FAMILY -> R.string.saved_place_symbol_family
    SavedPlaceIcon.SHOPPING -> R.string.saved_place_symbol_shopping
    SavedPlaceIcon.HEALTH -> R.string.saved_place_symbol_health
    SavedPlaceIcon.DEPOT -> R.string.saved_place_symbol_depot
    SavedPlaceIcon.STAR -> R.string.saved_place_symbol_star
    SavedPlaceIcon.PIN -> R.string.saved_place_symbol_pin
}

/**
 * Le nom qu'on affiche pour ce favori.
 *
 * Un emplacement nommé porte le nom de son emplacement — traduit —, un lieu
 * personnalisé le sien, et à défaut le nom court de son adresse. Cette dernière
 * bretelle n'est pas théorique : une entrée venue d'un autre appareil peut avoir
 * perdu son emplacement à la fusion, et se retrouver sans nom.
 */
@Composable
internal fun SavedPlace.displayName(): String = when (slot) {
    SavedPlaceSlot.CUSTOM -> name.ifEmpty { shortPlaceName(label) }
    else -> stringResource(slot.labelRes())
}
