package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.shortLabel
import kotlinx.coroutines.launch

/**
 * La barre de recherche de la carte.
 *
 * Un seul champ pour deux familles : les arrêts du catalogue, et les adresses
 * du géocodeur. Elles arrivent à leur rythme, jamais fondues — c'est la
 * leçon du web, et elle vaut mot pour mot ici.
 *
 * Un tap ouvre la recherche en plein écran, style contained de Material 3 :
 * un conteneur rempli, sans séparateur, le champ gardant sa forme arrondie.
 * La flèche de retour remplace le menu dès que la vue s'ouvre. Le geste de
 * retour du système fait la même chose.
 *
 * Un nom d'arrêt n'est pas un mot de la langue : la correction automatique
 * réécrirait « Bouffay » et « Ranzay ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapSearchBar(
    search: MapSearchState,
    onQueryChange: (String) -> Unit,
    onActivate: () -> Unit,
    onCancel: () -> Unit,
    onSelectStop: (StopSearchHit) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onOpenMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val hint = stringResource(R.string.search_hint)
    val cancelLabel = stringResource(R.string.search_cancel)
    val clearLabel = stringResource(R.string.search_clear)
    val scope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState(search.query)
    val latestQuery = rememberUpdatedState(search.query)
    val latestOnQueryChange = rememberUpdatedState(onQueryChange)
    val latestOnActivate = rememberUpdatedState(onActivate)
    val latestOnCancel = rememberUpdatedState(onCancel)

    LaunchedEffect(search.query) {
        if (search.query != textFieldState.text.toString()) {
            textFieldState.setTextAndPlaceCursorAtEnd(search.query)
        }
    }
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect { text ->
            if (text != latestQuery.value) latestOnQueryChange.value(text)
        }
    }
    LaunchedEffect(search.isActive) {
        if (search.isActive) {
            if (searchBarState.currentValue != SearchBarValue.Expanded) {
                searchBarState.animateToExpanded()
            }
        } else if (searchBarState.currentValue != SearchBarValue.Collapsed) {
            searchBarState.animateToCollapsed()
        }
    }
    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.currentValue }.collect { value ->
            when (value) {
                SearchBarValue.Expanded -> latestOnActivate.value()
                SearchBarValue.Collapsed -> latestOnCancel.value()
            }
        }
    }

    val inputFieldColors = SearchBarDefaults.inputFieldColors()

    // Repliée, la barre est posée sur la carte : c'est le verre d'Aule, le seul
    // endroit où il tienne. Déployée, elle prend l'écran et redevient une
    // surface pleine — laisser voir la carte derrière une liste de résultats
    // ne la rend pas plus lisible.
    val collapsedColors = SearchBarDefaults.colors(
        containerColor = AuleTheme.tokens.surface.color,
        inputFieldColors = inputFieldColors,
    )
    val expandedColors = SearchBarDefaults.colors(
        containerColor = colors.surfaceContainerLow,
        dividerColor = Color.Transparent,
        inputFieldColors = inputFieldColors,
    )
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { keyboard?.hide() },
                placeholder = { Text(hint) },
                colors = inputFieldColors,
                leadingIcon = {
                    if (expanded) {
                        IconButton(
                            onClick = { scope.launch { searchBarState.animateToCollapsed() } },
                        ) {
                            Icon(
                                imageVector = AuleGlyph.BACK.asImageVector(),
                                contentDescription = cancelLabel,
                                tint = colors.onSurface,
                            )
                        }
                    } else {
                        IconButton(onClick = { onOpenMenu?.invoke() }) {
                            Icon(
                                imageVector = AuleGlyph.MENU.asImageVector(),
                                contentDescription = null,
                                tint = colors.onSurface,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(
                            onClick = { textFieldState.setTextAndPlaceCursorAtEnd("") },
                        ) {
                            Icon(
                                imageVector = AuleGlyph.CLOSE.asImageVector(),
                                contentDescription = clearLabel,
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = collapsedColors,
        modifier = modifier.fillMaxWidth(),
    )
    ExpandedFullScreenSearchBar(
        state = searchBarState,
        inputField = inputField,
        colors = expandedColors,
    ) {
        SearchResults(
            search = search,
            onSelectStop = onSelectStop,
            onSelectPlace = onSelectPlace,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SearchResults(
    search: MapSearchState,
    onSelectStop: (StopSearchHit) -> Unit,
    onSelectPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        if (search.query.trim().isEmpty()) return

        if (search.isEmpty) {
            AuleEmptyState(
                title = stringResource(R.string.search_empty_title),
                detail = stringResource(R.string.search_empty_detail, search.query.trim()),
            )
            return
        }

        if (search.stops.isNotEmpty()) {
            SearchSection(title = stringResource(R.string.search_section_stops)) {
                search.stops.forEach { hit ->
                    val subtitle = hit.sublabel()
                    SearchRow(
                        title = hit.label,
                        subtitle = subtitle,
                        contentDescription = "${hit.label}, $subtitle",
                        clickLabel = stringResource(R.string.search_stop_hint),
                        onClick = {
                            keyboard?.hide()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelectStop(hit)
                        },
                    )
                }
            }
        }

        if (search.places.isNotEmpty() || search.isGeocoding) {
            SearchSection(title = stringResource(R.string.search_section_places)) {
                if (search.isGeocoding && search.places.isEmpty()) {
                    AuleLoadingState(
                        label = stringResource(R.string.search_geocoding),
                    )
                }
                search.places.forEach { place ->
                    val short = place.shortLabel()
                    SearchRow(
                        title = short,
                        subtitle = place.label,
                        contentDescription = place.label,
                        clickLabel = stringResource(R.string.search_place_hint),
                        onClick = {
                            keyboard?.hide()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelectPlace(place)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // 16 dp, comme la gouttière d'un `ListItem` Material : à 12 dp,
            // l'intitulé de section était décalé de quatre points à gauche des
            // résultats qu'il annonce.
            modifier = Modifier
                .padding(horizontal = AuleSpacing.lg)
                .semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    contentDescription: String,
    clickLabel: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1) },
        supportingContent = { Text(subtitle, maxLines = 2) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
internal fun StopSearchHit.sublabel(): String {
    val mode = mode.stationLabel()
    return if (quays > 1) stringResource(R.string.search_quays, mode, quays) else mode
}

@Composable
internal fun io.aule.android.core.model.TransportMode.stationLabel(): String = when (this) {
    io.aule.android.core.model.TransportMode.BUS -> stringResource(R.string.search_mode_bus)
    io.aule.android.core.model.TransportMode.TRAM -> stringResource(R.string.search_mode_tram)
    io.aule.android.core.model.TransportMode.BOAT -> stringResource(R.string.search_mode_boat)
}
