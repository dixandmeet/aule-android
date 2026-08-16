package io.aule.android.feature.map

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.component.AuleTextField
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleTouch
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
@Composable
internal fun ReportSheetHost(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            onClose()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)
    val dismiss = stringResource(R.string.sheet_dismiss)
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.onSurface.color.copy(alpha = AuleAlpha.OUTLINE))
                .clickable(onClick = onClose)
                .semantics { contentDescription = dismiss },
        )
        ReportSheet(
            onSubmit = onSubmit,
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .auleShadow(AuleElevation.OVERLAY, shape)
                .clip(shape)
                .background(tokens.surfaceSolid.color),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportSheet(
    onSubmit: suspend (DriverReport) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
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
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = title,
                style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
                    .copy(color = tokens.onSurface.color),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                maxLines = 1,
            )
            AuleIconButton(
                glyph = AuleGlyph.CLOSE,
                contentDescription = stringResource(R.string.sheet_dismiss),
                onClick = onClose,
                tint = tokens.onSurfaceMuted.color,
            )
        }

        if (sent) {
            BasicText(
                text = stringResource(R.string.report_sent_detail),
                style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
                maxLines = 2,
            )
        } else {
            BasicText(
                text = stringResource(R.string.report_hint),
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                maxLines = 1,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                DriverReportType.entries.forEach { candidate ->
                    ReportChip(
                        label = candidate.label(),
                        selected = type == candidate,
                        onClick = if (sending) null else ({ type = candidate }),
                    )
                }
            }
            BasicText(
                text = stringResource(R.string.report_urgency),
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                DriverReportUrgency.entries.forEach { candidate ->
                    ReportChip(
                        label = candidate.label(),
                        selected = urgency == candidate,
                        onClick = if (sending) null else ({ urgency = candidate }),
                    )
                }
            }
            AuleTextField(
                label = stringResource(R.string.report_message),
                value = message,
                onValueChange = { message = it },
                enabled = !sending,
                capitalization = KeyboardCapitalization.Sentences,
                modifier = Modifier.fillMaxWidth(),
            )
            val error = failure
            if (error != null) {
                AuleBanner(
                    message = error.label(),
                    tone = AuleTone.ALERT,
                )
            }
            AuleButton(
                title = stringResource(R.string.report_send),
                onClick = {
                    val chosen = type ?: return@AuleButton
                    if (sending) return@AuleButton
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
                enabled = type != null,
                loading = sending,
            )
        }
    }
}

@Composable
private fun ReportChip(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.sm)
    val fill = if (selected) {
        tokens.accent.color
    } else {
        tokens.onSurface.color.copy(alpha = AuleAlpha.TINT)
    }
    val ink = if (selected) tokens.onAccent.color else tokens.onSurface.color
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(fill)
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = auleTextStyle(
                AuleRole.BODY,
                if (selected) FontWeight.Bold else FontWeight.Medium,
            ).copy(color = ink),
            maxLines = 1,
        )
    }
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
