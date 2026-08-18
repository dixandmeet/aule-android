package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.DepartureRow
import io.aule.android.core.model.ServingLine
import io.aule.android.core.model.StopDepartures
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.repository.StopRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Le panneau d'un arrêt : ce qui passe, et quand.
 *
 * L'horloge du panneau bat toutes les dix secondes. Sans elle, « 3 min »
 * resterait « 3 min » pendant dix minutes.
 *
 * ## Ce qu'on lit en deux secondes
 *
 * L'information vitale de cette fiche est **l'attente du prochain passage** —
 * pas le nom de l'arrêt, qu'on vient de toucher, ni l'itinéraire, qu'on
 * demandera peut-être. Elle est donc écrite au rôle `DATA`, en chiffres
 * tabulaires : un compteur qui rétrécit d'un caractère en passant de « 10 min »
 * à « 9 min » fait danser toute la colonne.
 *
 * « Y aller » voyage pour cette raison dans l'en-tête plutôt que sur sa propre
 * rangée : pleine largeur, le bouton coûtait une rangée entière — donc un
 * passage de moins — dans le cran partiel du volet, qui est le seul cran qu'on
 * regarde sans lâcher le volant.
 */
@Composable
internal fun StopDetailSheet(
    stop: TransitStop,
    repository: StopRepository,
    dispatchers: AuleDispatchers,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(stop.id) { StopDetailModel(repository, dispatchers) }
    DisposableEffect(model) {
        model.load(stop.departuresKey)
        onDispose { model.close() }
    }

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(stop.id) {
        while (isActive) {
            delay(STOP_TICK_MS)
            now = Instant.now()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        StopIdentity(stop = stop, onRoute = onRoute)

        when {
            model.isLoading && model.departures == null -> {
                AuleLoadingState(label = stringResource(R.string.stop_loading))
            }
            model.departures != null -> {
                DeparturesSection(model.departures!!, now)
            }
            // Le message de l'exception ne sort pas d'ici : « Failed to connect
            // to /10.0.2.2:8080 » est une trace, pas une phrase. La panne se dit
            // en français, et le détail technique reste dans le journal.
            model.error != null -> {
                AuleEmptyState(
                    title = stringResource(R.string.stop_unavailable_title),
                    detail = stringResource(R.string.stop_unavailable_detail),
                )
            }
        }

        if (model.servingLines.isNotEmpty()) {
            ServingSection(model.servingLines)
        }
    }
}

/**
 * Qui est cet arrêt, et la seule action qu'on puisse faire dessus.
 *
 * Le nom passe en `headlineSmall` : `titleLarge` porte les chiffres tabulaires
 * du rôle `DATA`, qui n'ont rien à faire sur un nom de lieu — « Gare Nord 2 »
 * s'écrivait avec des chiffres de tableau d'affichage.
 *
 * La rangée coule (`FlowRow`) : à 200 % de taille de police, le bouton passe
 * sous les métadonnées au lieu de les écraser.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopIdentity(stop: TransitStop, onRoute: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetTitle(stop.departuresKey)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            TransportBadge(
                mode = stop.mode,
                label = stop.mode.label(),
                tint = stop.mode.markerColor(AuleTheme.night).color,
            )
            if (stop.isWheelchairAccessible) {
                Text(
                    text = stringResource(R.string.stop_accessible),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            stop.code?.let { code ->
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Button(
                onClick = onRoute,
                modifier = Modifier.defaultMinSize(minHeight = AuleTouch.minimum),
                colors = auleAccentButtonColors(),
                // Les crans d'un bouton à icône sont ceux de Material : ni la
                // taille de l'icône ni son écart au texte ne se décident ici.
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = AuleGlyph.ROUTE.asImageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.route_go))
            }
        }
    }
}

@Composable
private fun DeparturesSection(departures: StopDepartures, now: Instant) {
    val rows = departures.grouped(from = now)
    if (rows.isEmpty()) {
        AuleEmptyState(
            title = departures.outcome.title(),
            detail = departures.outcome.detail(),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.stop_next_departures))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                DepartureRowItem(row)
                if (index < rows.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
    }
}

/**
 * Un passage : quelle ligne, où elle va, dans combien de temps.
 *
 * Lue d'un trait par TalkBack. Séparés, le badge, la destination, le point
 * temps réel et l'attente feraient quatre arrêts de curseur pour une phrase
 * qu'on dit d'un souffle — « ligne C6, vers Beaujoire, temps réel, 3 min ».
 *
 * Le facteur de police est plafonné comme ailleurs sur les rangées denses : à
 * 200 %, la destination et l'attente se disputeraient une largeur qu'aucune des
 * deux n'a, et c'est l'attente qui perdrait.
 */
@Composable
private fun DepartureRowItem(row: DepartureRow) {
    val colors = MaterialTheme.colorScheme
    val feedLabel = stringResource(
        if (row.isRealtime) R.string.stop_realtime else R.string.stop_scheduled,
    )
    val wait = row.nextWait?.label()
    val following = row.followingWaits.joinToString(WAIT_SEPARATOR)
    val spoken = buildString {
        append(stringResource(R.string.line_badge, row.line))
        append(", ")
        append(stringResource(R.string.nearby_towards, row.destination))
        append(", ")
        append(feedLabel)
        if (wait != null) {
            append(", ")
            append(wait)
        }
        if (following.isNotEmpty()) {
            append(", ")
            append(stringResource(R.string.stop_following_waits, following))
        }
    }

    AuleCappedFontScale {
        ListItem(
            modifier = Modifier
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            leadingContent = {
                LineBadge(
                    line = row.line,
                    colorHex = row.lineColor,
                    contentDescription = stringResource(R.string.line_badge, row.line),
                )
            },
            headlineContent = {
                Text(
                    text = row.destination,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RealtimeDot(
                        isLive = row.isRealtime,
                        liveDescription = stringResource(R.string.stop_realtime),
                        scheduledDescription = stringResource(R.string.stop_scheduled),
                    )
                    Text(
                        text = feedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            trailingContent = if (wait == null) {
                null
            } else {
                {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = wait,
                            // `titleLarge`, donc le rôle `DATA` : chiffres à
                            // chasse fixe. C'est le seul endroit de la fiche où
                            // un chiffre change tout seul sous les yeux.
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            color = if (row.isRealtime) realtimeInk() else colors.onSurface,
                        )
                        if (following.isNotEmpty()) {
                            Text(
                                text = following,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            // Transparent : la couleur vient du cartouche qui la porte.
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/**
 * Les lignes desservies, et où elles vont.
 *
 * Elles restent vraies quand les passages ne le sont plus : la nuit, un arrêt
 * n'annonce rien mais dessert toujours les mêmes lignes.
 */
@Composable
private fun ServingSection(lines: List<ServingLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        SheetSectionLabel(stringResource(R.string.stop_serving_lines))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            lines.forEachIndexed { index, line ->
                ListItem(
                    modifier = Modifier
                        .defaultMinSize(minHeight = AuleTouch.minimum)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${line.line}, ${line.direction}"
                        },
                    leadingContent = {
                        LineBadge(
                            line = line.line,
                            colorHex = line.lineColor,
                            contentDescription = stringResource(R.string.line_badge, line.line),
                        )
                    },
                    headlineContent = {
                        Text(
                            text = line.direction,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (index < lines.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
    }
}

/** Assez pour que « 3 min » devienne « 2 min » sans qu'on l'ait vu vieillir. */
private const val STOP_TICK_MS = 10_000L

/** Ce qui sépare deux attentes d'une même ligne, à l'œil comme à l'oreille. */
private const val WAIT_SEPARATOR = " · "
