package io.aule.android.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.DriverReport
import io.aule.android.core.model.DriverReportException
import io.aule.android.core.model.DriverReportFailureKind
import io.aule.android.core.model.DriverReportType
import io.aule.android.core.model.DriverReportUrgency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * « Signaler un événement » — dix types, trois urgences, et le moins de
 * gestes possible.
 *
 * Un type suffit : au volant, exiger une phrase reviendrait à ne rien
 * recevoir. Rien ne se referme sans confirmation d'envoi — un signalement
 * qu'on croit parti et qui n'est pas parti est pire que pas de bouton.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportSheetHost(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        ReportSheet(onSubmit = onSubmit, onClose = onClose)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReportSheet(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf<DriverReportType?>(null) }
    var urgency by remember { mutableStateOf(DriverReportUrgency.MEDIUM) }
    var message by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<DriverReportFailureKind?>(null) }
    val title = stringResource(
        if (sent) R.string.report_sent_title else R.string.report_title,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        TopAppBar(
            title = { Text(title) },
            actions = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.sheet_dismiss),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )

        Column(
            modifier = Modifier.padding(horizontal = AuleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            if (sent) {
                Text(
                    text = stringResource(R.string.report_sent_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = stringResource(R.string.report_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                ) {
                    DriverReportType.entries.forEach { candidate ->
                        val chosen = type == candidate
                        FilterChip(
                            selected = chosen,
                            onClick = { if (!sending) type = candidate },
                            label = { Text(candidate.label()) },
                            enabled = !sending,
                            leadingIcon = if (chosen) selectedChipIcon else null,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.report_urgency),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                    DriverReportUrgency.entries.forEach { candidate ->
                        val chosen = urgency == candidate
                        FilterChip(
                            selected = chosen,
                            onClick = { if (!sending) urgency = candidate },
                            label = { Text(candidate.label()) },
                            enabled = !sending,
                            leadingIcon = if (chosen) selectedChipIcon else null,
                        )
                    }
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !sending,
                    label = { Text(stringResource(R.string.report_message)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    singleLine = true,
                )
                val error = failure
                if (error != null) {
                    AuleBanner(message = error.label(), tone = AuleTone.ALERT)
                }
                Button(
                    onClick = {
                        val chosen = type ?: return@Button
                        if (sending) return@Button
                        sending = true
                        failure = null
                        scope.launch {
                            try {
                                onSubmit(
                                    DriverReport(
                                        type = chosen,
                                        urgency = urgency,
                                        message = message,
                                    ),
                                )
                                sent = true
                            } catch (cancelled: CancellationException) {
                                sending = false
                                throw cancelled
                            } catch (thrown: DriverReportException) {
                                sending = false
                                failure = thrown.kind
                            } catch (_: Throwable) {
                                sending = false
                                failure = DriverReportFailureKind.UNKNOWN
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = type != null,
                    colors = auleAccentButtonColors(),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(AuleControl.icon),
                            strokeWidth = AuleStroke.glyph,
                            color = AuleTheme.tokens.onAccent.color,
                        )
                    } else {
                        Text(stringResource(R.string.report_send))
                    }
                }
            }
        }
    }
}

/**
 * La coche du chip retenu.
 *
 * Material la prévoit, Compose ne la pose pas tout seul. Sans elle, un chip
 * sélectionné ne se distingue que par sa teinte — et un daltonien deutan ne
 * voit aucune différence entre « Normal » et « Urgent » sur une pastille verte
 * de 12 % d'opacité.
 */
private val selectedChipIcon: @Composable () -> Unit = {
    Icon(
        imageVector = AuleGlyph.CHECK.asImageVector(),
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize),
    )
}

@Composable
private fun DriverReportType.label(): String = stringResource(
    when (this) {
        DriverReportType.TRAFFIC -> R.string.report_type_traffic
        DriverReportType.DELAY -> R.string.report_type_delay
        DriverReportType.DETOUR -> R.string.report_type_detour
        DriverReportType.CROWDED -> R.string.report_type_crowded
        DriverReportType.STOP_SKIPPED -> R.string.report_type_stop_skipped
        DriverReportType.BREAKDOWN -> R.string.report_type_breakdown
        DriverReportType.ACCIDENT -> R.string.report_type_accident
        DriverReportType.PASSENGER_ILLNESS -> R.string.report_type_passenger_illness
        DriverReportType.INCIVILITY -> R.string.report_type_incivility
        DriverReportType.OTHER -> R.string.report_type_other
    },
)

@Composable
private fun DriverReportUrgency.label(): String = stringResource(
    when (this) {
        DriverReportUrgency.LOW -> R.string.report_urgency_low
        DriverReportUrgency.MEDIUM -> R.string.report_urgency_medium
        DriverReportUrgency.HIGH -> R.string.report_urgency_high
    },
)

@Composable
private fun DriverReportFailureKind.label(): String = stringResource(
    when (this) {
        DriverReportFailureKind.NOT_SIGNED_IN -> R.string.report_error_session
        DriverReportFailureKind.NO_DRIVER -> R.string.report_error_driver
        DriverReportFailureKind.NOT_CONFIGURED -> R.string.report_error_config
        DriverReportFailureKind.NETWORK -> R.string.report_error_network
        DriverReportFailureKind.REJECTED -> R.string.report_error_rejected
        DriverReportFailureKind.UNKNOWN -> R.string.report_error_unknown
    },
)
