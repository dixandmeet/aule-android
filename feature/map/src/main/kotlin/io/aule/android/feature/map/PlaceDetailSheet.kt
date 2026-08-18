package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.TransportBadge
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.Place
import io.aule.android.core.model.shortLabel

/**
 * Le panneau d'une adresse : ce qu'on a nommé, et rien d'autre.
 *
 * Une adresse n'a ni desserte ni horaires — la seule chose qu'on puisse en
 * faire, c'est y aller. Le bouton garde donc toute la largeur : contrairement
 * au volet d'un arrêt, il ne prend la place de rien.
 *
 * Le badge de mode n'apparaît que sur un lieu que le géocodeur a reconnu comme
 * un arrêt. C'est ce qui distingue « Commerce », la station, de « rue du
 * Commerce », la voie — deux résultats de recherche que le nom seul confond.
 */
@Composable
internal fun PlaceDetailSheet(
    place: Place,
    onRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            // La même gouttière que les autres volets : ils partagent un cadre,
            // et une marge qui change en passant d'un contenu à l'autre fait
            // glisser le texte sous les yeux.
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
            SheetTitle(place.shortLabel())
            place.stopMode?.let { mode ->
                TransportBadge(
                    mode = mode,
                    label = mode.label(),
                    tint = mode.markerColor(AuleTheme.night).color,
                )
            }
            // L'adresse complète sous le nom court : deux « Rue de Strasbourg »
            // ne se distinguent que par leur commune.
            Text(
                text = place.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onRoute,
            modifier = Modifier.fillMaxWidth(),
            colors = auleAccentButtonColors(),
        ) {
            Text(stringResource(R.string.route_go))
        }
    }
}
