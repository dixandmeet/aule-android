package io.aule.android.feature.map

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.model.DriverServiceFailureKind
import kotlinx.coroutines.CancellationException

/**
 * Terminer le service — deux gestes, et le second n'est pas une politesse.
 *
 * Solder un service écrit une heure de fin en base. Un doigt posé au
 * mauvais endroit, au volant, n'est pas rattrapable. La confirmation reste.
 */
@Composable
fun EndServiceHost(
    ending: Boolean,
    failure: DriverServiceFailureKind?,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            if (!ending) onClose()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
    val dismiss = stringResource(R.string.sheet_dismiss)
    var confirming by remember { mutableStateOf(false) }
    AuleTheme {
        val tokens = AuleTheme.tokens
        val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)
        Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.onSurface.color.copy(alpha = AuleAlpha.OUTLINE))
                .clickable(enabled = !ending, onClick = onClose)
                .semantics { contentDescription = dismiss },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .auleShadow(AuleElevation.OVERLAY, shape)
                .clip(shape)
                .background(tokens.surfaceSolid.color)
                .padding(AuleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            BasicText(
                text = stringResource(R.string.service_end_title),
                style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
                    .copy(color = tokens.onSurface.color),
                modifier = Modifier.semantics { heading() },
            )
            BasicText(
                text = stringResource(
                    if (confirming) R.string.service_end_confirm else R.string.service_end_detail,
                ),
                style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
            )
            if (failure != null) {
                AuleBanner(
                    message = stringResource(
                        when (failure) {
                            DriverServiceFailureKind.NETWORK -> R.string.service_error_network
                            DriverServiceFailureKind.NOT_SIGNED_IN -> R.string.service_error_session
                            else -> R.string.service_end_error
                        },
                    ),
                    tone = AuleTone.ALERT,
                )
            }
            AuleButton(
                title = stringResource(
                    if (confirming) R.string.service_end_confirm_action else R.string.service_end_action,
                ),
                onClick = {
                    if (confirming) onConfirm() else confirming = true
                },
                prominence = AuleButtonProminence.DANGER,
                enabled = !ending,
                loading = ending,
            )
            AuleButton(
                title = stringResource(R.string.sheet_dismiss),
                onClick = onClose,
                prominence = AuleButtonProminence.PLAIN,
                enabled = !ending,
            )
        }
        }
    }
}
