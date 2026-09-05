package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleConnectedButtonGroup
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.designsystem.token.parseLineColor
import io.aule.android.core.model.LineStopMarker
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
    onSelectBranch: (String) -> Unit,
    onRetry: () -> Unit,
    onFocusStop: (LineStopMarker) -> Unit,
    onReleaseStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val lineColor = parseLineColor(line.colorHex).color

    SheetBody(modifier = modifier) {
        LineStopsHeader(
            line = line,
            // Le sens ne s'écrit **qu'une fois**. Dès qu'il y a un choix, c'est le
            // segment allumé qui le porte, en grand et en couleur ; le répéter en
            // gris trois centimètres plus haut, mot pour mot, n'apprend rien et
            // fait relire la même phrase pour vérifier qu'elle dit bien la même
            // chose. Sans choix, il n'y a pas de segment, et la mention revient.
            direction = if (state.hasChoice) {
                null
            } else {
                state.selected?.terminus ?: line.headsigns.firstOrNull()
            },
            onBack = onBack,
        )

        // Le sélecteur n'apparaît que s'il y a un choix. Le libellé préfixe par « Vers »
        // pour indiquer sans ambiguïté la direction sélectionnée.
        if (state.hasChoice) {
            AuleConnectedButtonGroup(
                options = state.directions,
                // Le sens affiché, et non le parcours : sur une ligne à branches
                // ils diffèrent, et comparer l'un à l'autre n'allumerait aucun
                // segment.
                selected = state.directions.firstOrNull {
                    it.directionId == state.selected?.directionId
                },
                label = { desserte -> stringResource(R.string.line_stops_direction, desserte.terminus) },
                onSelect = { desserte -> onSelectDirection(desserte.directionId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Le sens a son groupe de boutons, la branche a son menu — et le menu ne
        // paraît que pour les lignes qui se scindent. Voir [BranchPicker].
        if (state.hasBranches) {
            BranchPicker(
                branches = state.branches,
                selected = state.selected,
                onSelect = onSelectBranch,
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
                // ⚠️ **Les marqueurs, et non les rangs bruts du parcours.** C'est
                // ce qui garantit que la liste et la carte montrent la même
                // desserte : elles lisent la même construction, au même moment.
                // Voir [LineStopsUiState.markers].
                val stops = state.markers

                // Bouton de recentrage quand un arrêt précis est regardé à la caméra
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

                if (stops.isNotEmpty()) {
                    JourneySummaryBar(
                        origin = stops.first().name,
                        // ⚠️ **Le dernier arrêt, et non le terminus annoncé.**
                        // Celui-ci nomme le sens — « François Mitterrand / Jamet »
                        // —, et sur une branche il désigne un bout que le parcours
                        // n'atteint pas : la desserte Babinière s'arrête à
                        // Commerce, et le récap promettait Mitterrand. Vu à
                        // l'écran le 05/09/2026.
                        destination = stops.last().name,
                        stopCount = stops.size,
                        // Le menu des branches écrit déjà les deux bouts, à un
                        // centimètre au-dessus. Le récap n'en garde alors que ce
                        // qu'il est seul à dire : le compte.
                        showTerminals = !state.hasBranches,
                    )

                    SheetCard(modifier = Modifier.fillMaxWidth()) {
                        stops.forEachIndexed { index, stop ->
                            LineStopRow(
                                stop = stop,
                                connections = state.connectionsAt(stop.name, excluding = line.name),
                                lineColor = lineColor,
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
}

/**
 * Le choix de la branche, quand le sens affiché se scinde.
 *
 * ## Un menu, et non des segments
 *
 * Le sélecteur de sens juste au-dessus est un groupe connecté, et c'est le bon
 * contrôle pour lui : deux choix, des libellés courts, une cible qu'on vise sans
 * regarder. Il ne convient pas ici. Les libellés d'une branche sont **deux noms
 * de terminus** — « Beaujoire → Commerce » —, qu'aucune barre segmentée ne tient
 * sur un écran de 360 points. C'est l'argument d'iOS, où le même choix est un
 * `Menu` (`LineDetailSheet.branchPicker`).
 *
 * ## Ce qui nomme une branche
 *
 * **Ses deux bouts, jamais son terminus annoncé.** Les quatre dessertes de la
 * ligne 1 portent deux girouettes pour quatre trajets : « Beaujoire /
 * Babinière » nomme la paire. Un menu bâti dessus proposerait deux fois la même
 * entrée, et l'on ne saurait pas laquelle on regarde.
 *
 * ## Il n'apparaît que s'il choisit
 *
 * Huit lignes du réseau sur cent trente-huit se scindent. Pour les autres, rien
 * ne s'intercale entre le sens et la liste — même règle que le groupe de
 * boutons, qui se tait sur une ligne à sens unique.
 */
@Composable
private fun BranchPicker(
    branches: List<LineDesserte>,
    selected: LineDesserte?,
    onSelect: (String) -> Unit,
) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(R.string.line_stops_branch, selected?.label.orEmpty())

    Box {
        TextButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                expanded = true
            },
            modifier = Modifier
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .semantics { contentDescription = label },
        ) {
            Icon(
                imageVector = AuleGlyph.ROUTE.asImageVector(),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = selected?.label.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(AuleSpacing.xs))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = branch.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        expanded = false
                        onSelect(branch.id)
                    },
                    // La coche marque l'entrée affichée : un menu qui ne dit pas
                    // où l'on est fait rouvrir pour vérifier.
                    leadingIcon = if (branch.id == selected?.id) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(AuleControl.icon),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * L'en-tête de la fiche de ligne : identification de la ligne et du sens.
 */
@Composable
private fun LineStopsHeader(
    line: TransitLine,
    direction: String?,
    onBack: () -> Unit,
) {
    val view = LocalView.current
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
        val mode = line.mode
        if (mode != null) {
            TransportBadge(
                mode = mode,
                label = mode.label(),
                tint = mode.markerColor(AuleTheme.night).color,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            Text(
                text = stringResource(R.string.network_lines_badge, line.name),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (direction != null) {
                Text(
                    text = stringResource(R.string.line_stops_direction, direction),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Le récapitulatif du parcours affiché : Origine → Terminus et total d'arrêts.
 */
@Composable
private fun JourneySummaryBar(
    origin: String,
    destination: String,
    stopCount: Int,
    modifier: Modifier = Modifier,
    showTerminals: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AuleSpacing.xs),
        horizontalArrangement = if (showTerminals) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.End
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showTerminals) {
            Text(
                text = stringResource(R.string.line_stops_terminals, origin, destination),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(AuleSpacing.sm))
        }
        Text(
            text = if (stopCount == 1) {
                stringResource(R.string.line_stops_count_one)
            } else {
                stringResource(R.string.line_stops_count_many, stopCount)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Un arrêt de la ligne sur le tracé continu.
 */
@Composable
private fun LineStopRow(
    stop: LineStopMarker,
    connections: List<ServingLine>,
    lineColor: Color,
    isFirst: Boolean,
    isLast: Boolean,
    isFocused: Boolean,
    onFocus: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val terminal = isFirst || isLast
    val showOnMap = stringResource(R.string.line_stops_show_on_map)

    // ## Le nœud se pose en face du **nom**, pas au milieu de la rangée
    //
    // Une rangée qui porte ses correspondances est deux fois plus haute que son
    // nom : le nom en tête, la bande de badges dessous. Un nœud centré sur la
    // rangée descend alors entre les deux, et la colonne de pastilles se met à
    // désigner l'entre-deux plutôt que les arrêts — décalée d'un demi-cran ici,
    // pas du tout là où l'arrêt n'a pas de correspondance, donc irrégulière du
    // haut en bas du plan. Vu sur la C1, où les trois quarts des arrêts en ont.
    //
    // La rangée mesure donc la hauteur de son nom et la rend au rail. La lambda
    // n'est lue qu'au dessin : une mesure qui change repeint le rail, elle ne
    // recompose pas la rangée.
    val density = LocalDensity.current
    var nameHeightPx by remember { mutableIntStateOf(0) }
    val railTopPx = with(density) { ROW_PADDING.toPx() }
    val connectionsLabel = if (connections.isEmpty()) {
        null
    } else {
        stringResource(
            R.string.line_stops_connections,
            connections.joinToString(", ") { it.line },
        )
    }
    val a11yDescription = buildString {
        append(stop.name)
        when {
            isFirst -> {
                append(", ")
                append(stringResource(R.string.route_from))
            }
            isLast -> {
                append(", ")
                append(stringResource(R.string.route_to))
            }
        }
        if (connectionsLabel != null) {
            append(", ")
            append(connectionsLabel)
        }
    }

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(
                    if (isFocused) lineColor.copy(alpha = AuleAlpha.TINT) else Color.Transparent,
                )
                .let { base ->
                    if (onFocus == null) {
                        base
                    } else {
                        base
                            .clickable(onClickLabel = showOnMap, onClick = onFocus)
                            .defaultMinSize(minHeight = AuleTouch.minimum)
                    }
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = a11yDescription
                },
            // ⚠️ **En haut, et non centré.** Le plancher tactile étire une rangée
            // sans correspondance de 36 à 48 points ; centrée, la colonne du nom
            // encaissait la différence en glissant de six points vers le bas,
            // pendant que le nœud, lui, restait sur la mesure ci-dessus. Deux
            // arrêts sur trois retombaient juste, les autres étaient décalés —
            // le pire des cas, celui qu'on ne voit qu'en regardant longtemps.
            // Ancrée en haut, la position du nom ne dépend plus de la hauteur de
            // sa rangée, et le calcul du nœud redevient exact partout.
            verticalAlignment = Alignment.Top,
        ) {
            LineStopRail(
                lineColor = lineColor,
                hasConnections = connections.isNotEmpty(),
                isFirst = isFirst,
                isLast = isLast,
                isFocused = isFocused,
                nodeCenterY = {
                    nameHeightPx.takeIf { it > 0 }?.let { railTopPx + it / 2f }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .width(RAIL_WIDTH),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        top = ROW_PADDING,
                        bottom = ROW_PADDING,
                        end = AuleSpacing.lg,
                    ),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stop.name,
                    modifier = Modifier.onSizeChanged { nameHeightPx = it.height },
                    style = if (terminal) {
                        MaterialTheme.typography.titleSmallEmphasized
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connections.isNotEmpty()) {
                    ServingStrip(
                        lines = connections,
                    )
                }
            }
        }
    }
}

/**
 * Le rail vertical continu et la pastille de l'arrêt.
 *
 * Le tracé est peint dans la couleur GTFS de la ligne. Les terminus portent un
 * nœud distinctif, les arrêts en correspondance un anneau de transfert, et
 * l'arrêt focalisé un halo lumineux.
 *
 * @param nodeCenterY où poser le nœud, en pixels depuis le haut du rail. C'est
 *   la rangée qui le sait — elle seule connaît la hauteur de son nom. `null`
 *   avant la première mesure : le rail retombe alors sur son milieu, qui est
 *   juste tant qu'aucune correspondance n'allonge la rangée.
 */
@Composable
private fun LineStopRail(
    lineColor: Color,
    hasConnections: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    isFocused: Boolean,
    nodeCenterY: () -> Float?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val surfaceColor = colors.surfaceContainerHigh

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = (nodeCenterY() ?: (size.height / 2f)).coerceIn(0f, size.height)
        val stroke = RAIL_STROKE.toPx()

        // Le tracé vertical de la ligne
        if (!isFirst) {
            drawLine(
                color = lineColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, centerY),
                strokeWidth = stroke,
            )
        }
        if (!isLast) {
            drawLine(
                color = lineColor,
                start = Offset(centerX, centerY),
                end = Offset(centerX, size.height),
                strokeWidth = stroke,
            )
        }

        val center = Offset(centerX, centerY)

        // Halo lumineux quand l'arrêt est sous la caméra
        if (isFocused) {
            drawCircle(
                color = lineColor.copy(alpha = AuleAlpha.TINT),
                radius = DOT_HALO.toPx(),
                center = center,
            )
        }

        // Silhouette du nœud de station
        when {
            isFirst || isLast -> {
                drawCircle(
                    color = lineColor,
                    radius = DOT_TERMINUS.toPx(),
                    center = center,
                )
                drawCircle(
                    color = surfaceColor,
                    radius = DOT_TERMINUS_INNER.toPx(),
                    center = center,
                )
            }
            hasConnections -> {
                drawCircle(
                    color = surfaceColor,
                    radius = DOT_TRANSFER.toPx(),
                    center = center,
                )
                drawCircle(
                    color = lineColor,
                    radius = DOT_TRANSFER.toPx(),
                    center = center,
                    style = Stroke(width = stroke),
                )
            }
            else -> {
                drawCircle(
                    color = lineColor,
                    radius = DOT_REGULAR.toPx(),
                    center = center,
                )
            }
        }
    }
}

/**
 * Pourquoi la fiche est vide, et le geste qui va avec.
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

/** La respiration d'une rangée, et l'origine dont le rail déduit son nœud. */
private val ROW_PADDING = AuleSpacing.sm

private val RAIL_WIDTH = 40.dp
private val RAIL_STROKE = 3.dp
private val DOT_TERMINUS = 7.dp
private val DOT_TERMINUS_INNER = 3.dp
private val DOT_TRANSFER = 5.5.dp
private val DOT_REGULAR = 4.dp
private val DOT_HALO = 14.dp

