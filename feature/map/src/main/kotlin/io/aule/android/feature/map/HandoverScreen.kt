package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.bleedHorizontal
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleControl
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
import kotlinx.coroutines.CancellationException
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

    LaunchedEffect(overlay, state.handover?.id) {
        if (!overlay || state.handover?.id == null) return@LaunchedEffect
        viewModel.startTracking()
        try {
            awaitCancellation()
        } finally {
            viewModel.stopTracking()
        }
    }

    fun finish() {
        val started = state.started
        viewModel.dismiss()
        if (started != null) onStarted(started)
        onClose()
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            if (state.step == HandoverStep.DONE) {
                finish()
            } else if (viewModel.back()) {
                viewModel.dismiss()
                onClose()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
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
                    onFinish = ::finish,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardPane(
    state: HandoverUiState,
    viewModel: HandoverViewModel,
    onClose: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier,
) {
    AuleAmbientBackground(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
        ) {
            val isRootStep = state.step == HandoverStep.RESUME ||
                state.step == HandoverStep.LINE ||
                state.step == HandoverStep.DONE
            val closeWizard: () -> Unit = {
                if (state.step == HandoverStep.DONE) onFinish()
                else {
                    viewModel.dismiss()
                    onClose()
                }
            }
            HandoverAppBar(
                title = stringResource(state.step.title()),
                onBack = if (isRootStep) {
                    null
                } else {
                    {
                        if (viewModel.back()) {
                            viewModel.dismiss()
                            onClose()
                        }
                    }
                },
                onClose = closeWizard,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                val error = state.failure
                if (error != null && state.step != HandoverStep.DONE) {
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
                    HandoverStep.DONE -> DoneStep(state = state)
                    HandoverStep.STOP, HandoverStep.ALERTS, HandoverStep.CONFIRM -> Unit
                }
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
            }
            if (state.step == HandoverStep.DONE) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AuleControl.height)
                        // La colonne porte déjà `safeDrawingPadding` : y ajouter
                        // `navigationBarsPadding` décollait le bouton d'une
                        // seconde barre système qui n'existe pas.
                        .padding(horizontal = AuleSpacing.lg)
                        .padding(bottom = AuleSpacing.lg),
                    colors = auleAccentButtonColors(),
                ) {
                    Text(stringResource(R.string.handover_done_close))
                }
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
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = shape,
        color = colors.surfaceContainer,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AuleSpacing.lg),
        ) {
            HandoverAppBar(
                title = stringResource(state.step.title()),
                onBack = onBack,
                onClose = onClose,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
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
    val colors = MaterialTheme.colorScheme
    val clock = rememberPassageClock()
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = shape,
        color = colors.surfaceContainer,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AuleSpacing.lg),
        ) {
            HandoverAppBar(
                title = stringResource(state.step.title()),
                onBack = onBack,
                onClose = onClose,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
            val error = state.failure
            if (error != null) {
                AuleBanner(message = trackingError(state), tone = AuleTone.ALERT)
            }
            Text(
                text = stringResource(R.string.handover_live_stop_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LIVE_STOP_LIST_MAX_HEIGHT)
                    .bleedHorizontal(AuleSpacing.lg)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.isBusy && state.liveStops.isEmpty()) {
                    AuleLoadingState(
                        label = stringResource(R.string.handover_live_stop_loading),
                        modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                    )
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
    val colors = MaterialTheme.colorScheme
    val prefs = state.alertPrefs
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = shape,
        color = colors.surfaceContainer,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AuleSpacing.lg),
        ) {
            HandoverAppBar(
                title = stringResource(state.step.title()),
                onBack = onBack,
                onClose = onClose,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
            val error = state.failure
            if (error != null) {
                AuleBanner(message = trackingError(state), tone = AuleTone.ALERT)
            }
            Text(
                text = stringResource(R.string.handover_alerts_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
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
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AuleControl.height),
                colors = auleAccentButtonColors(),
            ) {
                Text(stringResource(R.string.handover_alerts_start))
            }
            }
        }
    }
}

@Composable
private fun AlertToggle(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        },
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onChecked(it)
                },
            )
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .bleedHorizontal(AuleSpacing.lg)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChecked(!checked)
            },
    )
}

@Composable
private fun AlertStepper(
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onChange(value - 1) },
            enabled = value > min,
        ) {
            Icon(
                imageVector = AuleGlyph.BACK.asImageVector(),
                contentDescription = stringResource(R.string.handover_alerts_decrease),
                tint = colors.onSurface,
            )
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        IconButton(
            onClick = { onChange(value + 1) },
            enabled = value < max,
        ) {
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = stringResource(R.string.handover_alerts_increase),
                tint = colors.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandoverAppBar(
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (onBack == null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = AuleGlyph.CLOSE.asImageVector(),
                        contentDescription = stringResource(R.string.sheet_dismiss),
                    )
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = stringResource(R.string.handover_back),
                    )
                }
            }
        },
        actions = {
            if (onBack != null) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = AuleGlyph.CLOSE.asImageVector(),
                        contentDescription = stringResource(R.string.sheet_dismiss),
                    )
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = colors.onSurface,
            navigationIconContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface,
        ),
    )
}

@Composable
private fun HandoverSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = AuleGlyph.SEARCH.asImageVector(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = AuleGlyph.CLOSE.asImageVector(),
                        contentDescription = stringResource(R.string.search_clear),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHighest,
            unfocusedContainerColor = colors.surfaceContainerHighest,
            disabledContainerColor = colors.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
            focusedLeadingIconColor = colors.onSurfaceVariant,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.onSurfaceVariant,
            unfocusedTrailingIconColor = colors.onSurfaceVariant,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            cursorColor = colors.primary,
        ),
    )
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
private fun DoneStep(state: HandoverUiState) {
    val colors = MaterialTheme.colorScheme
    val success = state.started != null
    val message = when {
        success -> stringResource(R.string.handover_done_success)
        state.abortedReason == "outgoing_service_closed" ->
            stringResource(R.string.handover_aborted_closed)
        state.abortedReason != null -> stringResource(R.string.handover_aborted)
        else -> stringResource(R.string.handover_done_aborted)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = (if (success) AuleGlyph.CHECK else AuleGlyph.FLAG).asImageVector(),
            contentDescription = null,
            tint = if (success) colors.primary else colors.error,
        )
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            if (success) {
                val stop = state.selectedLiveStop?.name ?: state.handover?.reliefStopName
                val line = state.started.lineLabel
                val detail = when {
                    !stop.isNullOrBlank() && line.isNotBlank() ->
                        stringResource(R.string.handover_done_detail_stop, stop, line)
                    line.isNotBlank() -> stringResource(R.string.handover_done_detail, line)
                    else -> null
                }
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeStep(
    state: HandoverUiState,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pending = state.pending
    if (pending == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(AuleControl.icon),
                color = colors.primary,
                strokeWidth = AuleStroke.glyph,
            )
            Text(
                text = stringResource(R.string.handover_resume_checking),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        return
    }
    val target = pending.target
    Text(
        text = stringResource(R.string.handover_resume_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
    TargetCard(target = target, lineLabel = state.selectedLine?.label ?: target.lineId)
    Button(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.height),
        enabled = !state.isBusy,
        colors = auleAccentButtonColors(),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(AuleControl.icon),
                color = AuleTheme.tokens.onAccent.color,
                strokeWidth = AuleStroke.glyph,
            )
        } else {
            Text(stringResource(R.string.handover_resume_action))
        }
    }
    TextButton(
        onClick = onDiscard,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
    ) {
        Text(stringResource(R.string.handover_resume_cancel))
    }
}

@Composable
private fun LineStep(
    state: HandoverUiState,
    onSearch: (String) -> Unit,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.handover_line_detail),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
    )
    when {
        state.isLoadingLines -> AuleLoadingState(
            label = stringResource(R.string.handover_lines_loading),
        )
        state.loadFailure != null -> Column(
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            AuleEmptyState(
                title = stringResource(R.string.service_lines_error_title),
                detail = state.loadFailure.label(),
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AuleControl.height),
            ) {
                Text(stringResource(R.string.issue_retry))
            }
        }
        else -> {
            HandoverSearchField(
                value = state.search,
                onValueChange = onSearch,
                placeholder = stringResource(R.string.service_line_search),
            )
            if (state.isSearchingLines) {
                if (state.filteredLines.isEmpty()) {
                    AuleEmptyState(
                        title = stringResource(R.string.service_lines_none, state.search.trim()),
                        detail = null,
                        icon = AuleGlyph.SEARCH.asImageVector(),
                    )
                } else {
                    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
                        state.filteredLines.forEach { line ->
                            HandoverLineChoice(
                                line = line,
                                selected = line.id == state.selectedLineId,
                                onClick = { onPick(line.id) },
                            )
                        }
                    }
                }
            } else {
                // Sans recherche : seulement les lignes qui roulent et les
                // récentes — le champ dit déjà comment trouver le reste.
                if (state.activeLines.isEmpty() && state.recentLines.isEmpty()) {
                    AuleEmptyState(
                        title = stringResource(R.string.handover_lines_empty_title),
                        detail = stringResource(R.string.handover_lines_empty_detail),
                        icon = AuleGlyph.SEARCH.asImageVector(),
                    )
                }
                if (state.activeLines.isNotEmpty()) {
                    LineSectionLabel(
                        text = stringResource(R.string.handover_lines_active),
                        accent = true,
                    )
                    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
                        state.activeLines.forEach { line ->
                            HandoverLineChoice(
                                line = line,
                                selected = line.id == state.selectedLineId,
                                onClick = { onPick(line.id) },
                            )
                        }
                    }
                }
                if (state.recentLines.isNotEmpty()) {
                    LineSectionLabel(
                        text = stringResource(R.string.handover_lines_recent),
                        accent = false,
                    )
                    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
                        state.recentLines.forEach { line ->
                            HandoverLineChoice(
                                line = line,
                                selected = line.id == state.selectedLineId,
                                onClick = { onPick(line.id) },
                            )
                        }
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
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (accent) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier
            .padding(top = AuleSpacing.sm)
            .semantics { heading() },
    )
}

@Composable
private fun HandoverLineChoice(
    line: ServiceLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val description = line.description
    ListItem(
        headlineContent = {
            Text(
                text = line.label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        },
        supportingContent = if (description.isNotBlank()) {
            {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        } else {
            null
        },
        leadingContent = {
            LineBadge(
                line = line.label,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.line_badge, line.label),
            )
        },
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = AuleGlyph.CHECK.asImageVector(),
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .semantics {
                this.selected = selected
                contentDescription = line.label
            },
    )
}

@Composable
private fun VehicleStep(
    state: HandoverUiState,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onChangeLine: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val line = state.selectedLine
    if (line != null) {
        val view = LocalView.current
        val changeLine = stringResource(R.string.handover_change_line)
        ListItem(
            headlineContent = {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
            },
            supportingContent = {
                Text(
                    text = changeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            },
            leadingContent = {
                LineBadge(
                    line = line.label,
                    colorHex = line.colorHex,
                    contentDescription = stringResource(R.string.line_badge, line.label),
                )
            },
            trailingContent = {
                Icon(
                    imageVector = AuleGlyph.CHEVRON.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .bleedHorizontal(AuleSpacing.lg)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onChangeLine()
                }
                .semantics { contentDescription = changeLine },
        )
    }
    Text(
        text = stringResource(R.string.handover_vehicle_detail),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.field),
        label = { Text(stringResource(R.string.handover_vehicle_field)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        shape = MaterialTheme.shapes.extraSmall,
    )
    Button(
        onClick = onSearch,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.height),
        enabled = state.canSearch && !state.isBusy,
        colors = auleAccentButtonColors(),
    ) {
        if (state.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(AuleControl.icon),
                color = AuleTheme.tokens.onAccent.color,
                strokeWidth = AuleStroke.glyph,
            )
        } else {
            Text(stringResource(R.string.handover_search))
        }
    }
}

@Composable
private fun CandidatesStep(
    state: HandoverUiState,
    onPick: (HandoverTarget) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (state.candidates.size > 1) {
        Text(
            text = stringResource(R.string.handover_candidates_many),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
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
    val unknown = stringResource(R.string.value_unknown)
    val query = state.query.trim().ifEmpty { unknown }
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
    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
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
}

@Composable
private fun FallbackStopStep(
    state: HandoverUiState,
    onSearch: (String) -> Unit,
    onPick: (LineJourneyStop) -> Unit,
) {
    FallbackNotice(state)
    HandoverSearchField(
        value = state.fallbackStopSearch,
        onValueChange = onSearch,
        placeholder = stringResource(R.string.handover_fallback_stop_search),
    )
    if (state.isBusy) {
        AuleLoadingState(label = stringResource(R.string.handover_live_stop_loading))
    }
    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
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
}

@Composable
private fun FallbackTimeStep(
    state: HandoverUiState,
    onChangeStop: () -> Unit,
    onStart: (StopDeparture) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val stopName = state.fallbackStop?.name.orEmpty()
    val change = stringResource(R.string.handover_fallback_change_stop)
    ListItem(
        headlineContent = {
            Text(
                text = stopName,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = change,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = AuleGlyph.PIN.asImageVector(),
                contentDescription = null,
                tint = colors.primary,
            )
        },
        trailingContent = {
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .bleedHorizontal(AuleSpacing.lg)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onChangeStop()
            }
            .semantics { contentDescription = "$stopName. $change" },
    )
    if (state.alreadyOnService) {
        AuleBanner(
            message = stringResource(R.string.handover_already_on_service_hint),
            tone = AuleTone.ALERT,
        )
    }
    if (state.isLoadingPassages) {
        AuleLoadingState(label = stringResource(R.string.handover_live_stop_loading))
    } else if (state.fallbackPassages.isEmpty()) {
        AuleEmptyState(
            title = stringResource(R.string.handover_fallback_no_passages),
            detail = null,
        )
    } else {
        Text(
            text = stringResource(R.string.handover_fallback_passages),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        if (state.fallbackShowsAllDirections) {
            AuleBanner(
                message = stringResource(R.string.handover_fallback_terminus_hint),
                tone = AuleTone.NEUTRAL,
            )
        }
        Text(
            text = stringResource(R.string.handover_fallback_pick_passage),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        val clock = rememberPassageClock()
        Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
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
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
        },
        supportingContent = detail?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.secondaryContainer else Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .semantics {
                this.selected = selected
                contentDescription = if (detail == null) label else "$label. $detail"
            },
    )
}

@Composable
private fun ConfirmStep(
    state: HandoverUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val target = state.target ?: return
    val colors = MaterialTheme.colorScheme
    val clock = rememberPassageClock()
    val progress = state.progress
    val arrived = progress?.arrived == true
    TargetCard(target = target, lineLabel = state.selectedLine?.label ?: target.lineId)
    if (state.abortedReason == null && state.failure != HandoverFailureKind.CLOSED) {
        if (progress == null) {
            Text(
                text = stringResource(R.string.handover_waiting_fix),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
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
                valueColor = when {
                    delay == null || delay == 0 -> null
                    delay > 0 -> delayInk()
                    else -> realtimeInk()
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
                    valueColor = if (overdue) colors.error else colors.onSurface,
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
    val confirmTitle = stringResource(
        if (arrived) R.string.handover_confirm_action else R.string.handover_confirm_action_waiting,
    )
    val confirmModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = AuleControl.height)
    if (arrived) {
        Button(
            onClick = onConfirm,
            modifier = confirmModifier,
            enabled = canConfirm,
            colors = auleAccentButtonColors(),
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = AuleTheme.tokens.onAccent.color,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Text(confirmTitle)
            }
        }
    } else {
        FilledTonalButton(
            onClick = onConfirm,
            modifier = confirmModifier,
            enabled = canConfirm,
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = colors.onSecondaryContainer,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Text(confirmTitle)
            }
        }
    }
    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
    ) {
        Text(stringResource(R.string.handover_confirm_cancel))
    }
}

@Composable
private fun TrackingCell(
    label: String,
    value: String,
    strong: Boolean = false,
    valueColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (strong) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor ?: colors.onSurface,
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
    val colors = MaterialTheme.colorScheme
    val direction = target.terminus?.let { stringResource(R.string.service_direction, it) }
        ?: stringResource(R.string.service_direction_other)
    val vehicle = target.vehicleLabel()
    val colleague = target.driverDisplay ?: stringResource(R.string.handover_colleague)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(AuleSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            Text(
                text = stringResource(R.string.handover_target_title, lineLabel, vehicle),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Text(
                text = direction,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                Icon(
                    imageVector = AuleGlyph.PERSON.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    text = colleague,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            val age = target.positionAgeSeconds
            if (age != null) {
                Text(
                    text = if (age < 15) {
                        stringResource(R.string.handover_fix_fresh)
                    } else {
                        stringResource(R.string.handover_fix_age, age)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (action != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AuleControl.height),
                    enabled = enabled,
                    colors = auleAccentButtonColors(),
                ) {
                    Text(action)
                }
            }
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
    HandoverStep.DONE -> R.string.handover_done_title
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
