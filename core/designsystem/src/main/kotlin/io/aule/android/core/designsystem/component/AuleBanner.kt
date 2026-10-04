package io.aule.android.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.aule.android.core.designsystem.components.AuleNotice
import io.aule.android.core.designsystem.components.AuleNoticeLevel

/** API historique Pro : information persistante ou échec récupérable. */
enum class AuleTone { NEUTRAL, ALERT }

/** Le bandeau Pro conserve son contrat et utilise le feedback commun à Voyageur. */
@Composable
fun AuleBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: AuleTone = AuleTone.NEUTRAL,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AuleNotice(
        level = if (tone == AuleTone.ALERT) AuleNoticeLevel.Disruption else AuleNoticeLevel.Information,
        title = message,
        modifier = modifier,
        action = action,
        onAction = onAction,
    )
}
