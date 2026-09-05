package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.model.NetworkLinesDigest
import io.aule.android.core.model.TransitLine
import io.aule.android.core.model.TransitLineFamily

/**
 * « Lignes du réseau » — l'inventaire complet, hors ligne.
 *
 * ## Ce qu'il répond, et que rien d'autre ne répondait
 *
 * Le catalogue d'arrêts dit ce qui est **desservi**, la flotte dit ce qui
 * **roule**. Ni l'un ni l'autre ne dit ce qui **existe**. Ce volet lit l'index
 * embarqué : 138 lignes, sans réseau et sans requête, dans un tunnel comme en
 * zone blanche.
 *
 * ## Il allume les tracés, et les éteint en partant
 *
 * Les tracés décrivent ce qui existe, pas ce qui se passe. Peints en permanence,
 * ils recouvriraient le territoire d'un lacis au milieu duquel les véhicules et
 * l'arrêt qu'on vient de toucher deviennent difficiles à distinguer. Ils vivent
 * donc le temps de ce volet — voir `TransitLinesLayer`.
 *
 * ## Toucher un rang met la ligne en avant
 *
 * Halo, trait plein, réseau assourdi autour. Il n'y a **rien à charger** : la
 * ligne est déjà dans les tuiles, ce qui change est un filtre. Retoucher le même
 * rang éteint la mise en avant.
 *
 * Port de `Native/Aule/Features/Lines/NetworkLinesSheet.swift`.
 */
@Composable
internal fun NetworkLinesSheet(
    digest: NetworkLinesDigest,
    query: String,
    focused: String?,
    onQuery: (String) -> Unit,
    onFocus: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var selectedFamily by rememberSaveable { mutableStateOf<TransitLineFamily?>(null) }

    // Si la recherche fait disparaître la famille sélectionnée, on retombe sur « Toutes »
    val availableFamilies = remember(digest) { digest.sections.map { it.family } }
    LaunchedEffect(availableFamilies) {
        if (selectedFamily != null && selectedFamily !in availableFamilies) {
            selectedFamily = null
        }
    }

    val visibleSections = remember(digest, selectedFamily) {
        if (selectedFamily == null) {
            digest.sections
        } else {
            digest.sections.filter { it.family == selectedFamily }
        }
    }

    val totalVisibleCount = remember(visibleSections) {
        visibleSections.sumOf { it.lines.size }
    }

    SheetBody(modifier = modifier) {
        SheetHeading(
            title = stringResource(R.string.network_lines_title),
            subtitle = if (digest.isEmpty) {
                null
            } else {
                lineCountLabel(totalVisibleCount)
            },
        )

        SheetSearchField(
            query = query,
            onQuery = onQuery,
            placeholder = stringResource(R.string.network_lines_search),
        )

        if (digest.isEmpty) {
            AuleEmptyState(
                title = stringResource(R.string.network_lines_empty_title),
                detail = stringResource(R.string.network_lines_empty_detail),
                icon = Icons.Outlined.Search,
            )
            return@SheetBody
        }

        // Puces de filtrage par famille si plusieurs familles sont présentes
        if (availableFamilies.size > 1) {
            FamilyFilterRow(
                families = availableFamilies,
                selected = selectedFamily,
                onSelect = { selectedFamily = it },
            )
        }

        visibleSections.forEachIndexed { index, section ->
            Column(
                modifier = Modifier.auleEnter(index = index),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                FamilySectionHeader(
                    family = section.family,
                    count = section.lines.size,
                )
                SheetCard(modifier = Modifier.fillMaxWidth()) {
                    section.lines.forEachIndexed { row, line ->
                        if (row > 0) SheetRowDivider()
                        NetworkLineRow(
                            line = line,
                            shown = line.match == focused,
                            onOpen = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onOpen(line.name)
                            },
                            onToggleShown = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onFocus(line.name)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Puces de filtrage rapide par famille de ligne.
 */
@Composable
private fun FamilyFilterRow(
    families: List<TransitLineFamily>,
    selected: TransitLineFamily?,
    onSelect: (TransitLineFamily?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.network_lines_filter_all)) },
        )
        families.forEach { family ->
            FilterChip(
                selected = selected == family,
                onClick = { onSelect(if (selected == family) null else family) },
                label = { Text(stringResource(family.labelRes())) },
            )
        }
    }
}

/**
 * L'en-tête d'une famille de lignes, avec son libellé et son décompte.
 */
@Composable
private fun FamilySectionHeader(
    family: TransitLineFamily,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SheetSectionLabel(stringResource(family.labelRes()))
        Text(
            text = lineCountLabel(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Un rang de ligne : le badge, les terminus, et l'œil qui la montre.
 *
 * ## Deux gestes, parce qu'il y a deux intentions
 *
 * Toucher le rang **ouvre la fiche** — par où passe-t-elle, arrêt par arrêt.
 * Toucher l'œil **la montre sur la carte**, sans rien ouvrir. Les fondre en un
 * seul geste coûterait le second : les vingt-neuf cars interurbains n'ont pas de
 * desserte publiée, et voir leur tracé est la seule chose qu'on puisse en faire.
 *
 * Le badge porte la couleur GTFS ; sans couleur connue il reste gris, ce qui dit
 * « on ne sait pas » plutôt que d'inventer une teinte.
 *
 * Les terminus sont reliés par un séparateur bidirectionnel « ↔ » pour
 * distinguer sans ambiguïté les deux extrémités du trajet des branches (« / »).
 */
@Composable
private fun NetworkLineRow(
    line: TransitLine,
    shown: Boolean,
    onOpen: () -> Unit,
    onToggleShown: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val showLabel = stringResource(R.string.network_lines_show, line.name)

    AuleCappedFontScale {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .background(
                    if (shown) colors.primary.copy(alpha = AuleAlpha.TINT) else Color.Transparent,
                )
                .clickable(onClick = onOpen)
                .padding(
                    start = AuleSpacing.lg,
                    end = AuleSpacing.sm,
                    top = AuleSpacing.sm,
                    bottom = AuleSpacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            LineBadge(
                line = line.name,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.network_lines_badge, line.name),
            )
            Column(modifier = Modifier.weight(1f)) {
                val destinations = line.headsigns.joinToString(HEADSIGN_SEPARATOR) { it }
                if (destinations.isNotBlank()) {
                    Text(
                        text = destinations,
                        style = if (shown) {
                            MaterialTheme.typography.bodyMediumEmphasized
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    // Une ligne sans terminus annoncé garde son rang : elle existe,
                    // et c'est tout ce que cet inventaire promet.
                    Text(
                        text = stringResource(R.string.network_lines_no_headsign),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            // L'œil est au repos un symbole discret sans barre de négation,
            // et s'allume en pastille pleine sur la ligne projetée à la carte.
            FilledIconToggleButton(
                checked = shown,
                onCheckedChange = { onToggleShown() },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant.copy(alpha = AuleAlpha.DISABLED),
                    checkedContainerColor = colors.primary,
                    checkedContentColor = colors.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = showLabel,
                    modifier = Modifier.size(AuleControl.icon),
                )
            }
        }
    }
}

/**
 * Le décompte, accordé.
 *
 * Le volet écrivait « 1 lignes » dès qu'une recherche ne laissait qu'un résultat
 * — c'est-à-dire au moment précis où l'on a trouvé ce qu'on cherchait, sous le
 * titre et sous la famille, deux fois de suite. Une faute d'accord n'est pas une
 * coquille dans un texte qui compte : c'est le seul mot que le compte produit.
 */
@Composable
private fun lineCountLabel(count: Int): String = if (count == 1) {
    stringResource(R.string.network_lines_count_one)
} else {
    stringResource(R.string.network_lines_count_many, count)
}

/** L'intitulé d'une famille — la part qui est à nous (ADR-011). */
private fun TransitLineFamily.labelRes(): Int = when (this) {
    TransitLineFamily.TRAM -> R.string.network_family_tram
    TransitLineFamily.NAVIBUS -> R.string.network_family_navibus
    TransitLineFamily.CHRONOBUS -> R.string.network_family_chronobus
    TransitLineFamily.EXPRESS -> R.string.network_family_express
    TransitLineFamily.BUS -> R.string.network_family_bus
    TransitLineFamily.INTERURBAN -> R.string.network_family_interurban
}

/**
 * Ce qui joint deux terminus : une flèche bidirectionnelle qui exprime clairement
 * les deux extrémités de la ligne, sans se confondre avec les séparateurs de branches (« / »).
 */
private const val HEADSIGN_SEPARATOR = " ↔ "

