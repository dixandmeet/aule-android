package io.aule.android.feature.map

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.JourneyLeg
import io.aule.android.core.model.LegMode
import java.text.DecimalFormatSymbols

/**
 * Le détail du trajet : la liste des tronçons, et où l'on en est.
 *
 * S'ouvre pendant le guidage, donc en conduite. Ce qu'on y cherche n'est pas
 * l'itinéraire complet — on l'a choisi tout à l'heure — mais **où l'on en est
 * dedans** : ce qui est fait, ce qu'on fait, ce qui vient.
 *
 * ## Trois états, et aucun porté par la seule couleur
 *
 * L'étape en cours prenait l'aplat de marque et rien d'autre : un daltonien,
 * un écran en plein soleil ou TalkBack n'en savaient rien. Chaque état se dit
 * maintenant aussi en toutes lettres dans la description de la rangée, et
 * l'étape franchie porte une coche — un signe de forme, pas de teinte.
 */
@Composable
internal fun TripSheet(
    state: NavigationUiState,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetBody(modifier = modifier) {
        SheetTitle(stringResource(R.string.nav_trip))
        SheetCard(modifier = Modifier.fillMaxWidth()) {
            state.plan.legs.forEachIndexed { index, leg ->
                TripLegRow(
                    leg = leg,
                    current = index == state.progress.legIndex,
                    done = index < state.progress.legIndex,
                    rank = index,
                )
                if (index < state.plan.legs.lastIndex) {
                    SheetRowDivider()
                }
            }
        }
        // La seule sortie du guidage. Elle reste en bas, seule, à distance des
        // rangées : un doigt qui dérape sur une liste ne doit pas arrêter le
        // guidage en cours de route.
        FilledTonalButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.nav_stop))
        }
    }
}

/**
 * Un tronçon : ce qu'on y fait, et sur quelle longueur.
 *
 * Le compte se dit en arrêts dans un véhicule et en mètres à pied ou au
 * volant — c'est l'unité dans laquelle on suit le trajet des yeux, pas celle
 * qui se calcule le plus facilement.
 */
@Composable
private fun TripLegRow(leg: JourneyLeg, current: Boolean, done: Boolean, rank: Int) {
    val colors = MaterialTheme.colorScheme
    val measure = leg.measureText()
    val stateLabel = when {
        current -> stringResource(R.string.trip_leg_current)
        done -> stringResource(R.string.trip_leg_done)
        else -> null
    }
    val spoken = listOfNotNull(stateLabel, leg.title, leg.line, measure).joinToString(", ")

    AuleCappedFontScale {
        ListItem(
            modifier = Modifier
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .auleEnter(index = rank)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            leadingContent = {
                val line = leg.line
                if (line != null) {
                    LineBadge(
                        line = line,
                        colorHex = leg.lineColor,
                        contentDescription = stringResource(R.string.line_badge, line),
                    )
                } else {
                    // Marcher et conduire sont génériques : le dessin vient du
                    // Material Symbol, pas de la famille métier d'Aule. Sans
                    // rien à cette place, le titre d'un tronçon à pied
                    // démarrait plus à gauche que celui d'un tronçon en ligne,
                    // et la colonne se brisait au milieu de la liste.
                    Icon(
                        imageVector = leg.mode.icon(),
                        contentDescription = null,
                        tint = if (current) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
            },
            headlineContent = {
                Text(
                    text = leg.title,
                    // L'étape en cours s'appuie : sur une liste où trois états
                    // coexistent, la graisse dit « c'est ici » avant que la
                    // couleur n'ait été interprétée.
                    style = if (current) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = measure?.let { text ->
                {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            trailingContent = if (!done) {
                null
            } else {
                {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                // Transparent : la couleur vient du cartouche qui la porte.
                //
                // L'étape en cours prend l'aplat de marque **plein**, et non le
                // conteneur pastel qu'elle portait. Le pastel se voyait, mais il
                // ne pesait pas : posé au milieu de rangées grises, il se lisait
                // comme une surbrillance de tableur. Le teal profond se lit comme
                // une position — celle où l'on est.
                containerColor = if (current) colors.primary else Color.Transparent,
                headlineColor = if (current) colors.onPrimary else colors.onSurface,
                leadingIconColor = if (current) colors.onPrimary else colors.onSurfaceVariant,
                supportingColor = if (current) {
                    colors.onPrimary
                } else {
                    colors.onSurfaceVariant
                },
            ),
        )
    }
}

/**
 * Le dessin d'un tronçon qu'on fait par ses propres moyens.
 *
 * [LegMode.TRANSIT] n'y figure pas : un tronçon en ligne porte son badge, qui
 * dit à la fois le mode et le numéro — un pictogramme de bus à côté du badge
 * « C6 » redirait la même chose en moins précis.
 */
private fun LegMode.icon(): ImageVector = when (this) {
    LegMode.CAR -> Icons.Outlined.DirectionsCar
    else -> Icons.AutoMirrored.Outlined.DirectionsWalk
}

/** Combien il en reste à faire, dans l'unité du tronçon. */
@Composable
private fun JourneyLeg.measureText(): String? = when {
    mode == LegMode.TRANSIT -> stopCount?.let { count ->
        if (count == 1) {
            stringResource(R.string.nav_stops_one)
        } else {
            stringResource(R.string.nav_stops_many, count)
        }
    }
    else -> GeoMath.formatDistance(
        distanceMeters,
        DecimalFormatSymbols.getInstance().decimalSeparator,
    )
}
