package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.Place
import io.aule.android.core.model.StopSearchHit
import io.aule.android.core.model.shortLabel
import kotlinx.coroutines.CancellationException

/**
 * La barre de recherche de la carte.
 *
 * Un seul champ pour deux familles : les arrêts du catalogue, et les adresses
 * du géocodeur. Elles arrivent à leur rythme, jamais fondues — c'est la
 * leçon du web, et elle vaut mot pour mot ici.
 *
 * Entrer dans la recherche prend l'écran : le clavier occupe la moitié basse.
 * Il faut donc une sortie visible, et c'est la flèche de retour qui remplace
 * la loupe dès que la saisie commence. Le geste de retour du système fait
 * la même chose.
 *
 * Un nom d'arrêt n'est pas un mot de la langue : la correction automatique
 * réécrirait « Bouffay » et « Ranzay ».
 */
@Composable
internal fun MapSearchBar(
    search: MapSearchState,
    onQueryChange: (String) -> Unit,
    onActivate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldDescription = stringResource(R.string.search_field)
    val hint = stringResource(R.string.search_hint)
    val cancelLabel = stringResource(R.string.search_cancel)
    val clearLabel = stringResource(R.string.search_clear)
    val active = search.isActive

    if (active) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { }
                keyboard?.hide()
                focusManager.clearFocus()
                onCancel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    val shape = RoundedCornerShape(AuleRadius.pill)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuleControl.height)
                .auleShadow(AuleElevation.FLOATING, shape)
                .clip(shape)
                .background(tokens.surfaceSolid.color)
                .border(
                    width = if (active) AuleStroke.emphasis else AuleStroke.hairline,
                    color = if (active) tokens.accentOnSurface.color else tokens.hairline.color,
                    shape = shape,
                )
                .padding(end = if (search.query.isNotEmpty()) 0.dp else AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuleIconButton(
                glyph = if (active) AuleGlyph.BACK else AuleGlyph.SEARCH,
                contentDescription = if (active) cancelLabel else fieldDescription,
                onClick = {
                    if (active) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                        onCancel()
                    } else {
                        focusRequester.requestFocus()
                    }
                },
            )
            BasicTextField(
                value = search.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onActivate() }
                    .semantics { contentDescription = fieldDescription },
                textStyle = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurface.color),
                singleLine = true,
                cursorBrush = SolidColor(tokens.accentOnSurface.color),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (search.query.isEmpty()) {
                            BasicText(
                                text = hint,
                                style = auleTextStyle(AuleRole.BODY)
                                    .copy(color = tokens.onSurfaceMuted.color),
                            )
                        }
                        inner()
                    }
                },
            )
            if (search.query.isNotEmpty()) {
                AuleIconButton(
                    glyph = AuleGlyph.CLOSE,
                    contentDescription = clearLabel,
                    onClick = { onQueryChange("") },
                    tint = tokens.onSurfaceMuted.color,
                )
            }
        }
    }
}

@Composable
internal fun SearchResults(
    search: MapSearchState,
    onSelectStop: (StopSearchHit) -> Unit,
    onSelectPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(AuleRadius.lg))
            .background(tokens.surfaceSolid.color)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.md)
            .padding(vertical = AuleSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
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
    val tokens = AuleTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        BasicText(
            text = title,
            style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                .copy(color = tokens.onSurfaceMuted.color),
            modifier = Modifier.semantics { heading() },
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
    val tokens = AuleTheme.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .padding(vertical = AuleSpacing.sm),
    ) {
        BasicText(
            text = title,
            style = auleTextStyle(AuleRole.BODY, FontWeight.Medium)
                .copy(color = tokens.onSurface.color),
            maxLines = 1,
        )
        BasicText(
            text = subtitle,
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            maxLines = 2,
        )
    }
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
