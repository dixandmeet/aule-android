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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.Place
import io.aule.android.core.model.shortLabel

/**
 * Le panneau d'une adresse : ce qu'on a nommé, et rien d'autre.
 *
 * Une adresse n'a ni desserte ni horaires — la seule chose qu'on puisse en
 * faire, c'est y aller.
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
            .padding(horizontal = AuleSpacing.xl)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        Text(
            text = place.shortLabel(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = place.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onRoute,
            modifier = Modifier.fillMaxWidth(),
            colors = auleAccentButtonColors(),
        ) {
            Text(stringResource(R.string.route_go))
        }
    }
}
