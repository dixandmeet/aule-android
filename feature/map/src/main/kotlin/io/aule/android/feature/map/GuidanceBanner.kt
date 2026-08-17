package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * Le bandeau haut : « que dois-je faire maintenant ? », et rien d'autre.
 *
 * Un seul à la fois. GPS perdu et sortie de tracé passent devant la
 * manœuvre : sans position fiable, une consigne à « 80 m » peut être à
 * trois cents.
 *
 * Pas de « Recalcul » : ce jalon n'en fait pas, et un bandeau qui l'annonce
 * mentirait.
 */
@Composable
internal fun GuidanceBanner(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    val title: String
    val detail: String?
    val alert = state.signalLost || state.offRoute
    when {
        state.signalLost -> {
            title = stringResource(R.string.nav_gps_lost)
            detail = stringResource(R.string.nav_gps_lost_detail)
        }
        state.offRoute -> {
            title = stringResource(R.string.nav_off_route)
            detail = stringResource(R.string.nav_off_route_detail)
        }
        else -> {
            val action = state.action ?: return
            title = listOfNotNull(action.leadText(), action.titleText()).joinToString(" · ")
            detail = action.detailText()
        }
    }
    val spoken = listOfNotNull(title, detail).joinToString(". ")
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        shape = MaterialTheme.shapes.medium,
        color = if (alert) colors.error else colors.surfaceContainerHigh,
        contentColor = if (alert) colors.onError else colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = AuleSpacing.lg,
                vertical = AuleSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alert) colors.onError else colors.onSurfaceVariant,
                )
            }
        }
    }
}
