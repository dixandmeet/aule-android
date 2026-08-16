package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
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
    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        BasicText(
            text = place.shortLabel(),
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.semantics { heading() },
        )
        BasicText(
            text = place.label,
            style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
        )
        AuleButton(
            title = stringResource(R.string.route_go),
            onClick = onRoute,
            prominence = AuleButtonProminence.FILLED,
        )
    }
}
