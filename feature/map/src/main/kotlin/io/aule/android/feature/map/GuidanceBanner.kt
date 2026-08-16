package io.aule.android.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
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
    val tokens = AuleTheme.tokens
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuleRadius.lg))
            .background(if (alert) tokens.alert.color else tokens.surface.color)
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = spoken
            },
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        val titleColor = if (alert) tokens.onAccent.color else tokens.onSurface.color
        val detailColor = if (alert) tokens.onAccent.color.copy(alpha = 0.85f) else tokens.onSurfaceMuted.color
        BasicText(
            text = title,
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold).copy(color = titleColor),
        )
        if (detail != null) {
            BasicText(
                text = detail,
                style = auleTextStyle(AuleRole.BODY).copy(color = detailColor),
            )
        }
    }
}
