package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBusyIndicator
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIcon
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.component.AuleTextField
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverTarget
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import io.aule.android.core.model.StopDeparture
import io.aule.android.core.model.Wait
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.awaitCancellation

/**
 * L'assistant de relève, posé par-dessus la carte.
 *
 * Ligne, véhicule, candidat : un écran plein. Dès que la relève est
 * engagée, le panneau se réduit : la carte montre le collègue, et le
 * choix d'arrêt reste un volet.
 */
@Composable
fun HandoverScreen(
    viewModel: HandoverViewModel,
    onClose: () -> Unit,
    onStarted: (ActiveDriverService) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val overlay = state.step == HandoverStep.STOP ||
        state.step == HandoverStep.ALERTS ||
        state.step == HandoverStep.CONFIRM

    LaunchedEffect(state.started) {
        state.started?.let(onStarted)
    }
    LaunchedEffect(overlay, state.handover?.id) {
        if (!overlay || state.handover?.id == null) return@LaunchedEffect
        viewModel.startTracking()
        try {
            awaitCancellation()
        } finally {
            viewModel.stopTracking()
        }
    }

    BackHandler {
        if (viewModel.back()) {
            viewModel.dismiss()
            onClose()
        }
    }

    AuleTheme {
        Box(modifier) {
            when (state.step) {
                HandoverStep.CONFIRM -> TrackingPane(
                    state = state,
                    onBack = {
                        if (viewModel.back()) {
                            viewModel.dismiss()
                            onClose()
                        }
                    },
                    onClose = {
                        viewModel.dismiss()
                        onClose()
                    },
                    onConfirm = viewModel::confirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                HandoverStep.ALERTS -> AlertsPane(
                    state = state,
                    onBack = {
                        if (viewModel.back()) {
                            viewModel.dismiss()
                            onClose()
                        }
                    },
                    onClose = {
                        viewModel.dismiss()
                        onClose()
                    },
                    onPrefs = viewModel::updateAlertPrefs,
                    onStart = viewModel::beginTracking,
                    modifier = Modifier.fillMaxWidth(),
                )
                HandoverStep.STOP -> LiveStopPane(
                    state = state,
                    onBack = {
                        if (viewModel.back()) {
                            viewModel.dismiss()
                            onClose()
                        }
                    },
                    onClose = {
                        viewModel.dismiss()
                        onClose()
                    },
                    onPick = viewModel::pickLiveStop,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> WizardPane(
                    state = state,
                    viewModel = viewModel,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WizardPane(
    state: HandoverUiState,
    viewModel: HandoverViewModel,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val tokens = AuleTheme.tokens
    AuleAmbientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = AuleSpacing.lg),
        ) {
            HandoverHeader(
                title = stringResource(state.step.title()),
                onBack = {
                    if (viewModel.back()) {
                        viewModel.dismiss()
                        onClose()
                    }
                },
                onClose = {
                    viewModel.dismiss()
                    onClose()
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                val error = state.failure
                if (error != null) {
                    AuleBanner(message = error.label(), tone = AuleTone.ALERT)
                }
                when (state.step) {
                    HandoverStep.RESUME -> ResumeStep(
                        state = state,
                        onResume = viewModel::resumePending,
                        onDiscard = viewModel::discardPending,
                    )
                    HandoverStep.LINE -> LineStep(
                        state = state,
                        onSearch = viewModel::setSearch,
                        onPick = viewModel::pickLine,
                        onRetry = viewModel::loadLines,
                    )
                    HandoverStep.VEHICLE -> VehicleStep(
                        state = state,
                        onQuery = viewModel::setQuery,
                        onSearch = viewModel::searchVehicle,
                        onChangeLine = { viewModel.back() },
                    )
                    HandoverStep.CANDIDATES -> CandidatesStep(
                        state = state,
                        onPick = viewModel::engage,
                    )
                    HandoverStep.DIRECTION -> DirectionStep(
                        state = state,
                        onPick = viewModel::pickDirection,
                    )
                    HandoverStep.FALLBACK_STOP -> FallbackStopStep(
                        state = state,
                        onSearch = viewModel::setFallbackStopSearch,
                        onPick = viewModel::pickFallbackStop,
                    )
                    HandoverStep.FALLBACK_TIME -> FallbackTimeStep(
                        state = state,
                        onChangeStop = { viewModel.back() },
                        onStart = viewModel::startFallback,
                    )
                    HandoverStep.STOP, HandoverStep.ALERTS, HandoverStep.CONFIRM -> Unit
                }
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
            }
        }
    }
}

@Composable
private fun TrackingPane(
    state: HandoverUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .auleShadow(AuleElevation.OVERLAY, shape)
            .clip(shape)
            .background(tokens.surfaceSolid.color)
            .imePadding()
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        HandoverHeader(
            title = stringResource(state.step.title()),
            onBack = onBack,
            onClose = onClose,
        )
        val error = state.failure
        if (error != null) {
            AuleBanner(message = trackingError(state), tone = AuleTone.ALERT)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            ConfirmStep(
                state = state,
                onConfirm = onConfirm,
                onCancel = onBack,
            )
        }
    }
}

@Composable
private fun LiveStopPane(
    state: HandoverUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPick: (LineJourneyStop) -> Unit,
    modifier: Modifier,
) {
    val tokens = AuleTheme.tokens
    val clock = rememberPassageClock()
    val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .auleShadow(AuleElevation.OVERLAY, shape)
            .clip(shape)
            .background(tokens.surfaceSolid.color)
            .imePadding()
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        HandoverHeader(
            title = stringResource(state.step.title()),
            onBack = onBack,
            onClose = onClose,
        )
        val error = state.failure
        if (error != null) {
            AuleBanner(message = trackingError(state), tone = AuleTone.ALERT)
        }
        BasicText(
            text = stringResource(R.string.handover_live_stop_detail),
            style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = LIVE_STOP_LIST_MAX_HEIGHT)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            if (state.isBusy && state.liveStops.isEmpty()) {
                AuleBusyIndicator(color = tokens.accent.color)
            }
            state.visibleLiveStops.forEach { stop ->
                val distance = stop.coordinate?.let { coordinate ->
                    state.fallbackAround?.let { GeoMath.formatDistance(GeoMath.distance(it, coordinate)) }
                }
                val passages = state.neighbourPassages[stop.name].orEmpty()
                val first = passages.firstOrNull()?.let { clock.format(it) }
                val others = passages.drop(1).take(2).joinToString(" · ") { clock.format(it) }
                val passage = first?.let { time ->
                    val head = stringResource(R.string.handover_passage, time)
                    if (others.isEmpty()) head
                    else "$head ${stringResource(R.string.handover_passage_then, others)}"
                }
                ChoiceRow(
                    label = stop.name,
                    detail = listOfNotNull(distance, passage).joinToString(" · ").ifEmpty { null },
                    selected = stop.id == state.selectedLiveStop?.id,
                    enabled = !state.isBusy,
                    onClick = { onPick(stop) },
                )
            }
        }
    }
}

@Composable
private fun AlertsPane(
    state: HandoverUiState,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onPrefs: (HandoverAlertPrefs) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier,
) {
    val tokens = AuleTheme.tokens
    val prefs = state.alertPrefs
    val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .auleShadow(AuleElevation.OVERLAY, shape)
            .clip(shape)
            .background(tokens.surfaceSolid.color)
            .imePadding()
            .padding(horizontal = AuleSpacing.lg)
            .padding(bottom = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        HandoverHeader(
            title = stringResource(state.step.title()),
            onBack = onBack,
            onClose = onClose,
        )
        val error = state.failure
        if (error != null) {
            AuleBanner(message = trackingError(state), tone = AuleTone.ALERT)
        }
        BasicText(
            text = stringResource(R.string.handover_alerts_detail),
            style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
        )
        AlertToggle(
            label = stringResource(R.string.handover_alerts_stops, prefs.stopsBefore ?: 2),
            checked = prefs.stopsBefore != null,
            onChecked = { on ->
                onPrefs(prefs.withStopsBefore(if (on) prefs.stopsBefore ?: 2 else null))
            },
            trailing = {
                val value = prefs.stopsBefore
                if (value != null) {
                    AlertStepper(
                        value = value,
                        min = HandoverAlertPrefs.STOPS_MIN,
                        max = HandoverAlertPrefs.STOPS_MAX,
                        onChange = { onPrefs(prefs.withStopsBefore(it)) },
                    )
                }
            },
        )
        AlertToggle(
            label = stringResource(R.string.handover_alerts_minutes, prefs.minutesBefore ?: 5),
            checked = prefs.minutesBefore != null,
            onChecked = { on ->
                onPrefs(prefs.withMinutesBefore(if (on) prefs.minutesBefore ?: 5 else null))
            },
            trailing = {
                val value = prefs.minutesBefore
                if (value != null) {
                    AlertStepper(
                        value = value,
                        min = HandoverAlertPrefs.MINUTES_MIN,
                        max = HandoverAlertPrefs.MINUTES_MAX,
                        onChange = { onPrefs(prefs.withMinutesBefore(it)) },
                    )
                }
            },
        )
        AlertToggle(
            label = stringResource(R.string.handover_alerts_arrival),
            checked = prefs.onArrival,
            onChecked = { on -> onPrefs(prefs.copy(onArrival = on)) },
        )
        AlertToggle(
            label = stringResource(R.string.handover_alerts_vibration),
            checked = prefs.vibration,
            onChecked = { on -> onPrefs(prefs.copy(vibration = on)) },
        )
        AlertToggle(
            label = stringResource(R.string.handover_alerts_sound),
            checked = prefs.sound,
            onChecked = { on -> onPrefs(prefs.copy(sound = on)) },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        AuleButton(
            title = stringResource(R.string.handover_alerts_start),
            onClick = onStart,
        )
    }
}

@Composable
private fun AlertToggle(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val shape = RoundedCornerShape(AuleRadius.sm)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(shape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onChecked(!checked)
                }
                .padding(vertical = AuleSpacing.xs)
                .semantics {
                    role = Role.Checkbox
                    this.selected = checked
                    contentDescription = label
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(ALERT_CHECKBOX_SIZE)
                    .clip(RoundedCornerShape(AuleRadius.sm))
                    .background(
                        if (checked) tokens.accent.color.copy(alpha = AuleAlpha.TINT) else tokens.surface.color,
                    )
                    .border(
                        AuleStroke.hairline,
                        if (checked) tokens.accent.color else tokens.hairline.color,
                        RoundedCornerShape(AuleRadius.sm),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    AuleIcon(
                        glyph = AuleGlyph.CHECK,
                        tint = tokens.accent.color,
                        size = ALERT_CHECK_ICON,
                    )
                }
            }
            BasicText(
                text = label,
                style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurface.color),
                modifier = Modifier.weight(1f),
            )
        }
        trailing()
    }
}

@Composable
private fun AlertStepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val tokens = AuleTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        AuleIconButton(
            glyph = AuleGlyph.BACK,
            contentDescription = stringResource(R.string.handover_alerts_decrease),
            onClick = { onChange(value - 1) },
            enabled = value > min,
            tint = tokens.onSurface.color,
        )
        BasicText(
            text = value.toString(),
            style = auleTextStyle(AuleRole.BODY, FontWeight.Bold)
                .copy(color = tokens.onSurface.color),
        )
        AuleIconButton(
            glyph = AuleGlyph.CHEVRON,
            contentDescription = stringResource(R.string.handover_alerts_increase),
            onClick = { onChange(value + 1) },
            enabled = value < max,
            tint = tokens.onSurface.color,
        )
    }
}

@Composable
private fun HandoverHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm, bottom = AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuleIconButton(
            glyph = AuleGlyph.BACK,
            contentDescription = stringResource(R.string.handover_back),
            onClick = onBack,
        )
        Column(modifier = Modifier.padding(start = AuleSpacing.sm).weight(1f)) {
            BasicText(
                text = title,
                style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
                    .copy(color = tokens.onSurface.color),
                modifier = Modifier.semantics { heading() },
            )
        }
        AuleIconButton(
            glyph = AuleGlyph.CLOSE,
            contentDescription = stringResource(R.string.sheet_dismiss),
            onClick = onClose,
        )
    }
}

@Composable
private fun trackingError(state: HandoverUiState): String {
    if (state.failure == HandoverFailureKind.CLOSED) {
        return stringResource(
            if (state.abortedReason == "outgoing_service_closed") {
                R.string.handover_aborted_closed
            } else {
                R.string.handover_aborted
            },
        )
    }
    return state.failure?.label() ?: ""
}

@Composable
private fun ResumeStep(
    state: HandoverUiState,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    val pending = state.pending
    if (pending == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            AuleBusyIndicator(color = AuleTheme.tokens.accent.color)
            BasicText(
                text = stringResource(R.string.handover_resume_checking),
                style = auleTextStyle(AuleRole.BODY).copy(color = AuleTheme.tokens.onSurfaceMuted.color),
            )
        }
        return
    }
    val target = pending.target
    BasicText(
        text = stringResource(R.string.handover_resume_detail),
        style = auleTextStyle(AuleRole.BODY).copy(color = AuleTheme.tokens.onSurfaceMuted.color),
    )
    TargetCard(target = target, lineLabel = state.selectedLine?.label ?: target.lineId)
    AuleButton(
        title = stringResource(R.string.handover_resume_action),
        onClick = onResume,
        enabled = !state.isBusy,
        loading = state.isBusy,
    )
    AuleButton(
        title = stringResource(R.string.handover_resume_cancel),
        onClick = onDiscard,
        prominence = AuleButtonProminence.PLAIN,
        enabled = !state.isBusy,
    )
}

@Composable
private fun LineStep(
    state: HandoverUiState,
    onSearch: (String) -> Unit,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    BasicText(
        text = stringResource(R.string.handover_line_detail),
        style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
    )
    when {
        state.isLoadingLines -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AuleSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                AuleBusyIndicator(color = tokens.accent.color)
            }
        }
        state.loadFailure != null -> Column(
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            AuleEmptyState(
                title = stringResource(R.string.service_lines_error_title),
                detail = state.loadFailure.label(),
            )
            AuleButton(
                title = stringResource(R.string.issue_retry),
                onClick = onRetry,
                prominence = AuleButtonProminence.TINTED,
            )
        }
        else -> {
            AuleTextField(
                label = stringResource(R.string.service_line_search),
                value = state.search,
                onValueChange = onSearch,
                leading = AuleGlyph.SEARCH,
                imeAction = ImeAction.Search,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.isSearchingLines) {
                if (state.filteredLines.isEmpty()) {
                    BasicText(
                        text = stringResource(R.string.service_lines_none, state.search.trim()),
                        style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
                    )
                } else {
                    state.filteredLines.forEach { line ->
                        HandoverLineChoice(
                            line = line,
                            selected = line.id == state.selectedLineId,
                            live = line.id in state.activeLineIds,
                            onClick = { onPick(line.id) },
                        )
                    }
                }
            } else {
                // Sans recherche : seulement les lignes qui roulent et les
                // récentes — le champ dit déjà comment trouver le reste.
                if (state.activeLines.isNotEmpty()) {
                    LineSectionLabel(
                        text = stringResource(R.string.handover_lines_active),
                        accent = true,
                    )
                    state.activeLines.forEach { line ->
                        HandoverLineChoice(
                            line = line,
                            selected = line.id == state.selectedLineId,
                            live = true,
                            onClick = { onPick(line.id) },
                        )
                    }
                }
                if (state.recentLines.isNotEmpty()) {
                    LineSectionLabel(
                        text = stringResource(R.string.handover_lines_recent),
                        accent = false,
                    )
                    state.recentLines.forEach { line ->
                        HandoverLineChoice(
                            line = line,
                            selected = line.id == state.selectedLineId,
                            live = false,
                            onClick = { onPick(line.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineSectionLabel(
    text: String,
    accent: Boolean,
) {
    val tokens = AuleTheme.tokens
    val color = if (accent) tokens.accent.color else tokens.onSurfaceMuted.color
    BasicText(
        text = text.uppercase(),
        style = auleTextStyle(AuleRole.KICKER).copy(
            color = color,
            fontWeight = FontWeight.Bold,
        ),
        modifier = Modifier
            .padding(top = AuleSpacing.sm)
            .semantics { heading() },
    )
}

@Composable
private fun HandoverLineChoice(
    line: ServiceLine,
    selected: Boolean,
    live: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val shape = RoundedCornerShape(AuleRadius.md)
    val background = when {
        selected -> tokens.accent.color.copy(alpha = AuleAlpha.TINT)
        live -> tokens.accent.color.copy(alpha = AuleAlpha.TINT * 0.55f)
        else -> tokens.surface.color
    }
    val border = when {
        selected || live -> tokens.accent.color
        else -> tokens.hairline.color
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(background)
            .border(AuleStroke.hairline, border, shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(AuleSpacing.md)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = line.label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        LineBadge(
            line = line.label,
            colorHex = line.colorHex,
            contentDescription = stringResource(R.string.line_badge, line.label),
        )
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = line.label,
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
            )
            if (line.description.isNotBlank()) {
                BasicText(
                    text = line.description,
                    style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                    maxLines = 2,
                )
            }
        }
        if (selected) {
            AuleIcon(glyph = AuleGlyph.CHECK, tint = tokens.accent.color)
        }
    }
}

@Composable
private fun VehicleStep(
    state: HandoverUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onChangeLine: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val line = state.selectedLine
    if (line != null) {
        val view = LocalView.current
        val changeLine = stringResource(R.string.handover_change_line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuleRadius.md))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onChangeLine()
                }
                .padding(vertical = AuleSpacing.sm)
                .semantics {
                    role = Role.Button
                    contentDescription = changeLine
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            LineBadge(
                line = line.label,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.line_badge, line.label),
            )
            BasicText(
                text = stringResource(R.string.handover_change_line),
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        }
    }
    BasicText(
        text = stringResource(R.string.handover_vehicle_detail),
        style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
    )
    AuleTextField(
        label = stringResource(R.string.handover_vehicle_field),
        value = state.query,
        onValueChange = onQuery,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Search,
        onImeAction = onSearch,
        modifier = Modifier.fillMaxWidth(),
    )
    AuleButton(
        title = stringResource(R.string.handover_search),
        onClick = onSearch,
        enabled = state.canSearch,
        loading = state.isBusy,
    )
}

@Composable
private fun CandidatesStep(
    state: HandoverUiState,
    onPick: (HandoverTarget) -> Unit,
) {
    val tokens = AuleTheme.tokens
    if (state.candidates.size > 1) {
        BasicText(
            text = stringResource(R.string.handover_candidates_many),
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
    }
    state.candidates.forEach { target ->
        TargetCard(
            target = target,
            lineLabel = state.selectedLine?.label ?: target.lineId,
            action = stringResource(R.string.handover_engage),
            enabled = !state.isBusy,
            onAction = { onPick(target) },
        )
    }
}

@Composable
private fun FallbackNotice(state: HandoverUiState) {
    val query = state.query.trim().ifEmpty { "—" }
    val line = state.selectedLine?.label ?: state.selectedLineId.orEmpty()
    AuleBanner(
        message = stringResource(R.string.handover_fallback_notice, query, line),
        tone = AuleTone.NEUTRAL,
    )
}

@Composable
private fun DirectionStep(
    state: HandoverUiState,
    onPick: (ServiceDirection) -> Unit,
) {
    FallbackNotice(state)
    state.selectedLine?.directions?.forEach { direction ->
        val label = if (direction.terminus.isBlank()) {
            stringResource(R.string.service_direction_other)
        } else {
            stringResource(R.string.service_direction, direction.terminus)
        }
        ChoiceRow(
            label = label,
            selected = direction.key == state.fallbackDirectionKey,
            enabled = !state.isBusy,
            onClick = { onPick(direction) },
        )
    }
}

@Composable
private fun FallbackStopStep(
    state: HandoverUiState,
    onSearch: (String) -> Unit,
    onPick: (LineJourneyStop) -> Unit,
) {
    FallbackNotice(state)
    AuleTextField(
        label = stringResource(R.string.handover_fallback_stop_search),
        value = state.fallbackStopSearch,
        onValueChange = onSearch,
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Search,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.isBusy) {
        AuleBusyIndicator(color = AuleTheme.tokens.accent.color)
    }
    state.visibleFallbackStops.forEach { stop ->
        val distance = stop.coordinate?.let { coordinate ->
            state.fallbackAround?.let { GeoMath.formatDistance(GeoMath.distance(it, coordinate)) }
        }
        ChoiceRow(
            label = stop.name,
            detail = distance,
            selected = false,
            enabled = !state.isBusy,
            onClick = { onPick(stop) },
        )
    }
}

@Composable
private fun FallbackTimeStep(
    state: HandoverUiState,
    onChangeStop: () -> Unit,
    onStart: (StopDeparture) -> Unit,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val stopName = state.fallbackStop?.name.orEmpty()
    val change = stringResource(R.string.handover_fallback_change_stop)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuleRadius.md))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChangeStop()
            }
            .padding(vertical = AuleSpacing.sm)
            .semantics {
                role = Role.Button
                contentDescription = "$stopName. $change"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        AuleIcon(glyph = AuleGlyph.PIN, tint = tokens.accent.color)
        BasicText(
            text = stopName,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.weight(1f),
        )
        BasicText(
            text = change,
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
    }
    if (state.alreadyOnService) {
        AuleBanner(
            message = stringResource(R.string.handover_already_on_service_hint),
            tone = AuleTone.ALERT,
        )
    }
    if (state.isLoadingPassages) {
        AuleBusyIndicator(color = tokens.accent.color)
    } else if (state.fallbackPassages.isEmpty()) {
        AuleEmptyState(
            title = stringResource(R.string.handover_fallback_no_passages),
            detail = null,
        )
    } else {
        BasicText(
            text = stringResource(R.string.handover_fallback_passages),
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
        if (state.fallbackShowsAllDirections) {
            AuleBanner(
                message = stringResource(R.string.handover_fallback_terminus_hint),
                tone = AuleTone.NEUTRAL,
            )
        }
        BasicText(
            text = stringResource(R.string.handover_fallback_pick_passage),
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
        val clock = rememberPassageClock()
        state.fallbackPassages.forEach { passage ->
            val wait = passage.waitMinutes(java.time.Instant.now()).let { minutes ->
                if (minutes == 0) Wait.Approaching else Wait.Minutes(minutes)
            }
            ChoiceRow(
                label = "${clock.format(passage.expectedAt)}  ·  ${passage.destination}",
                detail = wait.label(),
                selected = state.fallbackTime == passage.expectedAt,
                enabled = !state.isBusy && !state.alreadyOnService,
                onClick = { onStart(passage) },
            )
        }
    }
}

@Composable
private fun rememberPassageClock(): java.time.format.DateTimeFormatter {
    val zone = java.time.ZoneId.systemDefault()
    return remember(zone) {
        java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(
                if (selected) tokens.accent.color.copy(alpha = AuleAlpha.TINT) else tokens.surface.color,
            )
            .border(
                AuleStroke.hairline,
                if (selected) tokens.accent.color else tokens.hairline.color,
                shape,
            )
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(AuleSpacing.md)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = if (detail == null) label else "$label. $detail"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = label,
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
            )
            if (detail != null) {
                BasicText(
                    text = detail,
                    style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                )
            }
        }
        AuleIcon(glyph = AuleGlyph.CHEVRON, tint = tokens.onSurfaceMuted.color)
    }
}

@Composable
private fun ConfirmStep(
    state: HandoverUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val target = state.target ?: return
    val tokens = AuleTheme.tokens
    val clock = rememberPassageClock()
    val progress = state.progress
    val arrived = progress?.arrived == true
    TargetCard(target = target, lineLabel = state.selectedLine?.label ?: target.lineId)
    if (state.abortedReason == null && state.failure != HandoverFailureKind.CLOSED) {
        if (progress == null) {
            BasicText(
                text = stringResource(R.string.handover_waiting_fix),
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        } else {
            val unknown = stringResource(R.string.handover_metric_unknown)
            TrackingCell(
                label = stringResource(R.string.handover_metric_relief),
                value = state.selectedLiveStop?.name ?: unknown,
                strong = true,
            )
            val planned = state.handover?.reliefPlannedAt
            if (planned != null) {
                TrackingCell(
                    label = stringResource(R.string.handover_metric_planned),
                    value = clock.format(planned),
                )
            }
            val eta = progress.estimatedAt?.let(clock::format)
            val delay = progress.delayMinutes()
            TrackingCell(
                label = stringResource(R.string.handover_metric_eta),
                value = when {
                    eta == null -> unknown
                    delay == null || delay == 0 -> eta
                    delay > 0 -> stringResource(R.string.handover_eta_late, eta, delay)
                    else -> stringResource(R.string.handover_eta_early, eta, delay)
                },
                strong = true,
            )
            TrackingCell(
                label = stringResource(R.string.handover_metric_stops),
                value = when {
                    progress.stopsRemaining == null -> unknown
                    progress.passed -> stringResource(R.string.handover_metric_passed)
                    else -> progress.stopsRemaining.toString()
                },
            )
            TrackingCell(
                label = stringResource(R.string.handover_metric_distance),
                value = progress.metersRemaining?.let { GeoMath.formatDistance(it) } ?: unknown,
            )
            val leaveBy = state.leaveBy
            if (leaveBy != null) {
                val overdue = leaveBy.isBefore(java.time.Instant.now())
                TrackingCell(
                    label = stringResource(R.string.handover_metric_leave),
                    value = clock.format(leaveBy),
                    valueColor = if (overdue) tokens.alert.color else tokens.accent.color,
                    strong = true,
                )
            }
            if (!progress.reliable) {
                AuleBanner(
                    message = if (!progress.fresh) {
                        val age = progress.fixAgeSeconds
                        val ago = if (age >= 60) {
                            stringResource(R.string.handover_ago_minutes, age / 60)
                        } else {
                            stringResource(R.string.handover_ago_seconds, age)
                        }
                        stringResource(R.string.handover_stale_alerts, ago)
                    } else {
                        stringResource(R.string.handover_path_unmatched)
                    },
                    tone = AuleTone.NEUTRAL,
                )
            }
        }
    }
    if (state.alreadyOnService) {
        AuleBanner(
            message = stringResource(R.string.handover_already_on_service_hint),
            tone = AuleTone.ALERT,
        )
    }
    val canConfirm = !state.isBusy &&
        state.abortedReason == null &&
        state.failure != HandoverFailureKind.CLOSED &&
        !state.alreadyOnService
    AuleButton(
        title = stringResource(
            if (arrived) R.string.handover_confirm_action else R.string.handover_confirm_action_waiting,
        ),
        onClick = onConfirm,
        prominence = if (arrived) AuleButtonProminence.FILLED else AuleButtonProminence.TINTED,
        enabled = canConfirm,
        loading = state.isBusy,
    )
    AuleButton(
        title = stringResource(R.string.handover_confirm_cancel),
        onClick = onCancel,
        prominence = AuleButtonProminence.PLAIN,
        enabled = !state.isBusy,
    )
}

@Composable
private fun TrackingCell(
    label: String,
    value: String,
    strong: Boolean = false,
    valueColor: Color? = null,
) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
        BasicText(
            text = value,
            style = auleTextStyle(
                if (strong) AuleRole.BODY else AuleRole.KICKER,
                if (strong) FontWeight.Bold else FontWeight.SemiBold,
            ).copy(color = valueColor ?: tokens.onSurface.color),
        )
    }
}

@Composable
private fun TargetCard(
    target: HandoverTarget,
    lineLabel: String,
    action: String? = null,
    enabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    val direction = target.terminus?.let { stringResource(R.string.service_direction, it) }
        ?: stringResource(R.string.service_direction_other)
    val vehicle = target.vehicleLabel()
    val colleague = target.driverDisplay ?: stringResource(R.string.handover_colleague)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface.color)
            .border(AuleStroke.hairline, tokens.hairline.color, shape)
            .padding(AuleSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        BasicText(
            text = stringResource(R.string.handover_target_title, lineLabel, vehicle),
            style = auleTextStyle(AuleRole.BODY, FontWeight.Bold).copy(color = tokens.onSurface.color),
        )
        BasicText(
            text = direction,
            style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            AuleIcon(glyph = AuleGlyph.PERSON, tint = tokens.onSurfaceMuted.color)
            BasicText(
                text = colleague,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        }
        val age = target.positionAgeSeconds
        if (age != null) {
            BasicText(
                text = if (age < 15) {
                    stringResource(R.string.handover_fix_fresh)
                } else {
                    stringResource(R.string.handover_fix_age, age)
                },
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
        }
        if (action != null && onAction != null) {
            AuleButton(
                title = action,
                onClick = onAction,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun HandoverTarget.vehicleLabel(): String {
    val vehicle = vehicleId?.let { stringResource(R.string.handover_vehicle, it) }
    val train = trainNumber
    return when {
        vehicle != null && train != null -> stringResource(R.string.handover_vehicle_train, vehicle, train)
        vehicle != null -> vehicle
        train != null -> train
        else -> stringResource(R.string.handover_vehicle_unknown)
    }
}

private fun HandoverStep.title(): Int = when (this) {
    HandoverStep.RESUME -> R.string.handover_resume_title
    HandoverStep.LINE -> R.string.handover_title
    HandoverStep.VEHICLE -> R.string.handover_vehicle_title
    HandoverStep.CANDIDATES -> R.string.handover_candidates_title
    HandoverStep.DIRECTION -> R.string.handover_direction_title
    HandoverStep.FALLBACK_STOP -> R.string.handover_fallback_stop_title
    HandoverStep.FALLBACK_TIME -> R.string.handover_fallback_time_title
    HandoverStep.STOP -> R.string.handover_live_stop_title
    HandoverStep.ALERTS -> R.string.handover_alerts_title
    HandoverStep.CONFIRM -> R.string.handover_confirm_title
}

@Composable
private fun HandoverFailureKind.label(): String = stringResource(
    when (this) {
        HandoverFailureKind.NOT_SIGNED_IN -> R.string.service_error_session
        HandoverFailureKind.NO_DRIVER -> R.string.handover_error_driver
        HandoverFailureKind.NOT_CONFIGURED -> R.string.handover_error_config
        HandoverFailureKind.NETWORK -> R.string.handover_error_network
        HandoverFailureKind.LINES_EMPTY -> R.string.service_error_lines
        HandoverFailureKind.TARGET_NOT_ACTIVE -> R.string.handover_error_target
        HandoverFailureKind.CANNOT_RELIEVE_SELF -> R.string.handover_error_self
        HandoverFailureKind.OTHER_NETWORK -> R.string.handover_error_network_other
        HandoverFailureKind.ALREADY_RELIEVING -> R.string.handover_error_already
        HandoverFailureKind.ALREADY_BEING_RELIEVED -> R.string.handover_error_busy
        HandoverFailureKind.ALREADY_ON_SERVICE -> R.string.handover_error_on_service
        HandoverFailureKind.CLOSED -> R.string.handover_error_closed
        HandoverFailureKind.NOT_FOUND -> R.string.handover_error_missing
        HandoverFailureKind.ALREADY_COMPLETED -> R.string.handover_error_done
        HandoverFailureKind.NOT_A_PARTY -> R.string.handover_error_party
        HandoverFailureKind.REJECTED -> R.string.handover_error_rejected
        HandoverFailureKind.JOURNEY_UNAVAILABLE -> R.string.handover_error_journey
        HandoverFailureKind.UNKNOWN -> R.string.handover_error_unknown
    },
)

private val LIVE_STOP_LIST_MAX_HEIGHT = 320.dp
private val ALERT_CHECKBOX_SIZE = 24.dp
private val ALERT_CHECK_ICON = 16.dp
