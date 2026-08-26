package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.TransitLine

/**
 * La fiche d'une ligne : tous ses arrêts, par sens.
 *
 * ## Ce qui la distingue de `LineDepartureSheet`
 *
 * Celle-là est la fiche d'une ligne **à un arrêt** — ce qui passe à Commerce sur
 * la C6, et dans combien de minutes. Celle-ci est la fiche de la ligne
 * **entière** — par où elle passe, de bout en bout, sans horaire. Deux questions
 * différentes, deux volets, et deux noms qui ne se confondent pas.
 *
 * ## Le retour ramène à l'inventaire
 *
 * Elle se pose **par-dessus** le volet des lignes plutôt qu'à sa place : la ligne
 * reste mise en avant sur la carte, et le retour ramène à la liste d'où l'on
 * vient. C'est le même geste que le détail d'un trajet pendant le guidage — un
 * cran de plus dans la même chose, pas une autre chose.
 *
 * Port de `Native/Aule/Features/Lines/LineDetailSheet.swift`.
 */
@Composable
internal fun LineStopsSheet(
    line: TransitLine,
    state: LineStopsUiState,
    onBack: () -> Unit,
    onSelectDirection: (Int) -> Unit,
    onRetry: () -> Unit,
    onFocusStop: (LineJourneyStop) -> Unit,
    onReleaseStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    SheetBody(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            IconButton(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onBack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.line_stops_back),
                )
            }
            LineBadge(
                line = line.name,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.network_lines_badge, line.name),
            )
            SheetTitle(
                text = state.selected?.terminus
                    ?: line.headsigns.firstOrNull()
                    ?: stringResource(R.string.line_stops_title),
                modifier = Modifier.weight(1f),
            )
        }

        // Le sélecteur n'apparaît que s'il y a un choix : une ligne à sens unique
        // n'a pas besoin d'un contrôle qui ne sélectionne rien.
        if (state.hasChoice) {
            AuleConnectedButtonGroup(
                options = state.dessertes,
                selected = state.selected,
                label = { desserte -> desserte.terminus },
                onSelect = { desserte -> onSelectDirection(desserte.directionId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            state.isLoading -> AuleLoadingState(
                label = stringResource(R.string.line_stops_loading),
            )
            state.failure != null -> LineStopsFailureState(
                failure = state.failure,
                onRetry = onRetry,
            )
            else -> {
                val stops = state.selected?.stops.orEmpty()
                // Il ne s'affiche que la caméra **posée sur un arrêt** : hors
                // de là, il n'y a pas de « retour », il y a la vue courante.
                if (state.focusedStop != null) {
                    FilledTonalButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onReleaseStop()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomOutMap,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.line_stops_release))
                    }
                }
                Text(
                    text = stringResource(R.string.line_stops_count, stops.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SheetCard(modifier = Modifier.fillMaxWidth()) {
                    stops.forEachIndexed { index, stop ->
                        if (index > 0) SheetRowDivider()
                        LineStopRow(
                            stop = stop,
                            connections = state.connectionsAt(stop.name, excluding = line.name),
                            isFirst = index == 0,
                            isLast = index == stops.lastIndex,
                            isFocused = stop.id == state.focusedStopId,
                            onFocus = if (stop.coordinate != null) {
                                {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    onFocusStop(stop)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un arrêt de la desserte, et le voyage qu'il propose.
 *
 * Les deux extrémités portent un glyphe plein, le reste un point creux : c'est
 * ce qui fait lire la colonne comme un **trajet** plutôt que comme une liste. Le
 * même vocabulaire que le plan de ligne d'un véhicule.
 *
 * ## Le toucher emmène la carte
 *
 * Une desserte est une suite de noms, et un nom ne dit pas *où*. « Souillarderie
 * », « Halvêque », « Pin Sec » se lisent sans qu'aucun ne se situe — sauf pour
 * qui connaît déjà la ligne, c'est-à-dire pour qui n'avait pas besoin de la
 * liste. Toucher un rang pose la caméra sur l'arrêt : le nom devient un endroit,
 * et parcourir la desserte devient un survol du parcours.
 *
 * Le vol dure ce que dure une entrée de caméra, et il **part du tracé entier**
 * qu'on tenait à l'écran — c'est ce trajet-là, du plan large jusqu'au trottoir,
 * qui fait comprendre où l'arrêt tombe sur la ligne. Le poser d'un coup ne
 * dirait que le trottoir.
 *
 * ## Un rang sans position ne se touche pas
 *
 * Le référentiel n'en donne pas toujours une. Un rang cliquable qui ne ferait
 * rien serait pire que muet : il apprendrait qu'on ne peut pas compter sur le
 * geste. [onFocus] vaut `null` là, et le rang redevient du texte.
 *
 * ## Ce qu'on peut prendre d'autre
 *
 * Une desserte répond « par où passe cette ligne ». Elle ne répondait pas « et
 * ensuite ? » — or c'est la question qu'on se pose en la parcourant : à quel
 * arrêt descendre pour rejoindre le reste du réseau. Les badges le disent d'un
 * coup d'œil, sans quitter la liste ni ouvrir quinze fiches.
 *
 * Ils **arrivent après** le nom : chaque arrêt est une requête, et le volet ne
 * se fait pas attendre pour un complément. Un arrêt sans correspondance connue
 * — pas encore lue, ou réellement aucune — n'écrit rien : une phrase « aucune
 * correspondance » sur douze rangs sur quinze ferait de l'absence le sujet.
 */
@Composable
private fun LineStopRow(
    stop: LineJourneyStop,
    connections: List<ServingLine>,
    isFirst: Boolean,
    isLast: Boolean,
    isFocused: Boolean,
    onFocus: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val terminal = isFirst || isLast
    val showOnMap = stringResource(R.string.line_stops_show_on_map)
    val connectionsLabel = if (connections.isEmpty()) {
        null
    } else {
        stringResource(
            R.string.line_stops_connections,
            connections.joinToString(", ") { it.line },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // L'arrêt sous la caméra se **teinte**, il ne s'encadre pas : un
            // cartouche dans un cartouche redirait la boîte, alors que l'aplat
            // dit seulement « c'est celui-ci que vous regardez ».
            .background(if (isFocused) colors.primary.copy(alpha = AuleAlpha.TINT) else Color.Transparent)
            .let { base ->
                if (onFocus == null) {
                    base
                } else {
                    base
                        .clickable(onClickLabel = showOnMap, onClick = onFocus)
                        .defaultMinSize(minHeight = AuleTouch.minimum)
                }
            }
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Icon(
            imageVector = AuleGlyph.PIN.asImageVector(filled = terminal || isFocused),
            contentDescription = null,
            tint = if (terminal || isFocused) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.size(STOP_GLYPH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stop.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (connectionsLabel != null) {
                // Une seule annonce pour toute la rangée : badge par badge,
                // TalkBack dirait « Ligne 12, Ligne 92 » sans jamais dire de
                // quoi il s'agit.
                ServingStrip(
                    lines = connections,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = connectionsLabel
                    },
                )
            }
        }
    }
}

/**
 * Pourquoi la fiche est vide, et le geste qui va avec.
 *
 * Trois cas, trois phrases : se reconnecter, réessayer, ou accepter que le
 * référentiel ne connaisse pas cette ligne. Un seul message enverrait deux
 * personnes sur trois au mauvais geste.
 */
@Composable
private fun LineStopsFailureState(
    failure: LineStopsFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.auleEnter(index = 0),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        AuleEmptyState(
            title = stringResource(failure.titleRes()),
            detail = stringResource(failure.detailRes()),
            icon = AuleGlyph.ROUTE.asImageVector(),
        )
        // Pas de « Réessayer » sur une ligne que le référentiel ne connaît pas :
        // le bouton promettrait qu'insister peut changer la réponse.
        if (failure == LineStopsFailure.NETWORK) {
            TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.line_stops_retry))
            }
        }
    }
}

private fun LineStopsFailure.titleRes(): Int = when (this) {
    LineStopsFailure.NOT_SIGNED_IN -> R.string.line_stops_signed_out_title
    LineStopsFailure.UNKNOWN_LINE -> R.string.line_stops_unknown_title
    LineStopsFailure.NETWORK -> R.string.line_stops_error_title
}

private fun LineStopsFailure.detailRes(): Int = when (this) {
    LineStopsFailure.NOT_SIGNED_IN -> R.string.line_stops_signed_out_detail
    LineStopsFailure.UNKNOWN_LINE -> R.string.line_stops_unknown_detail
    LineStopsFailure.NETWORK -> R.string.line_stops_error_detail
}

/** Le point d'un arrêt, contre un texte de 14 — sous la grille d'icône de 24. */
private val STOP_GLYPH = 18.dp
