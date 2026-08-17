package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.DriverServiceFailureKind

/**
 * Terminer le service — deux gestes, et le second n'est pas une politesse.
 *
 * Solder un service écrit une heure de fin en base. Un doigt posé au
 * mauvais endroit, au volant, n'est pas rattrapable. La confirmation reste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndServiceHost(
    ending: Boolean,
    failure: DriverServiceFailureKind?,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AuleTheme {
        ModalBottomSheet(
            onDismissRequest = { if (!ending) onClose() },
            modifier = modifier,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.xl)
                    .padding(bottom = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                Text(
                    text = stringResource(R.string.service_end_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(
                        if (confirming) R.string.service_end_confirm else R.string.service_end_detail,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Button(
                    onClick = { if (confirming) onConfirm() else confirming = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    if (ending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuleControl.icon),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            strokeWidth = AuleStroke.glyph,
                        )
                    } else {
                        Text(
                            stringResource(
                                if (confirming) {
                                    R.string.service_end_confirm_action
                                } else {
                                    R.string.service_end_action
                                },
                            ),
                        )
                    }
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ending,
                ) {
                    Text(stringResource(R.string.sheet_dismiss))
                }
                Spacer(Modifier.height(AuleSpacing.sm))
            }
        }
    }
}
