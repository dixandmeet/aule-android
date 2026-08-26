package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
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
    SheetBody(modifier = modifier) {
        SheetHeading(
            title = stringResource(R.string.network_lines_title),
            subtitle = if (digest.isEmpty) {
                null
            } else {
                stringResource(R.string.network_lines_count, digest.count)
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

        digest.sections.forEachIndexed { index, section ->
            Column(
                modifier = Modifier.auleEnter(index = index),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                SheetSectionLabel(stringResource(section.family.labelRes()))
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
 * Les terminus sont **joints** et non listés : sur une ligne à branches il y en
 * a quatre, et quatre lignes de texte par rang feraient de l'inventaire un pavé.
 * Ce qu'on cherche en parcourant, c'est de reconnaître un nom au passage.
 */
@Composable
private fun NetworkLineRow(
    line: TransitLine,
    shown: Boolean,
    onOpen: () -> Unit,
    onToggleShown: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = AuleSpacing.lg, top = AuleSpacing.xs, bottom = AuleSpacing.xs),
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
                    style = MaterialTheme.typography.bodyMedium,
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
        // L'œil est **toujours là**, allumé ou éteint : n'apparaître qu'une fois
        // la ligne montrée en ferait un témoin, pas une commande, et il n'y
        // aurait plus aucun moyen de l'éteindre.
        //
        // C'est une **bascule**, et non un bouton dont le libellé change. La
        // différence se voit deux fois. À l'œil : la ligne peinte sur la carte
        // porte une pastille pleine, qui se repère en parcourant cent trente-huit
        // rangs — un simple changement de teinte sur un glyphe de dix-huit points
        // se perdait dans la liste. À l'oreille : TalkBack annonce « coché », là
        // où deux libellés concurrents obligeaient à écouter la phrase entière
        // pour savoir dans quel état on se trouvait.
        FilledIconToggleButton(
            checked = shown,
            onCheckedChange = { onToggleShown() },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                // Éteint, la pastille n'existe pas : cent trente-huit aplats
                // dans une liste feraient un damier, pas un inventaire.
                containerColor = Color.Transparent,
                contentColor = colors.onSurfaceVariant,
                checkedContainerColor = colors.primary,
                checkedContentColor = colors.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (shown) {
                    Icons.Outlined.Visibility
                } else {
                    Icons.Outlined.VisibilityOff
                },
                contentDescription = stringResource(R.string.network_lines_show, line.name),
                modifier = Modifier.size(AuleControl.icon),
            )
        }
    }
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
 * Ce qui joint deux terminus. Une barre verticale entourée d'espaces plutôt
 * qu'une virgule : les noms de terminus en contiennent déjà.
 */
private const val HEADSIGN_SEPARATOR = " · "
