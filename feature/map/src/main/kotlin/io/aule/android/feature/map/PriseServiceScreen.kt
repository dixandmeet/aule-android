package io.aule.android.feature.map

import android.Manifest
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.location.LocationAuthorization
import io.aule.android.core.location.LocationProvider
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.DriverServiceFailureKind
import io.aule.android.core.model.ServiceDirection
import io.aule.android.core.model.ServiceLine
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException

/**
 * L'assistant de prise de service, posé par-dessus la carte.
 *
 * Six étapes, comme Flutter. L'heure, le train et le véhicule restent
 * facultatifs ; le GPS, non. La carte n'est pas démontée pendant ce temps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriseServiceScreen(
    viewModel: PriseServiceViewModel,
    location: LocationProvider,
    onClose: () -> Unit,
    onStarted: (ActiveDriverService) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authorization by location.authorization.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        location.markPermissionRequested()
        location.refreshAuthorization()
    }

    LaunchedEffect(state.started) {
        state.started?.let(onStarted)
    }
    LaunchedEffect(authorization) {
        if (authorization == LocationAuthorization.GRANTED) {
            viewModel.setGpsReady(true)
        } else if (state.gpsReady) {
            viewModel.setGpsReady(false)
        }
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            if (viewModel.back()) onClose()
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        AuleAmbientBackground(modifier = modifier.fillMaxSize()) {
            // La gouttière appartient au contenu, pas à l'écran : posée ici,
            // elle enfermait la barre d'application dans une carte flottante
            // large de deux marges — là où Material la veut bord à bord.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.service_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(
                                    R.string.service_step,
                                    state.step.index + 1,
                                    PriseServiceStep.entries.size,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (viewModel.back()) onClose() }) {
                            Icon(
                                imageVector = AuleGlyph.BACK.asImageVector(),
                                contentDescription = stringResource(R.string.service_back),
                            )
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    // Transparente, comme la relève : le fond d'ambiance passe
                    // dessous d'un bout à l'autre au lieu d'être coupé net par
                    // un bandeau blanc.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = colors.onSurface,
                        navigationIconContentColor = colors.onSurface,
                    ),
                )
                StepMarks(
                    current = state.step.index,
                    total = PriseServiceStep.entries.size,
                    modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                )
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AuleSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                ) {
                    when (state.step) {
                        PriseServiceStep.LINE -> LineStep(
                            state = state,
                            onSearch = viewModel::setSearch,
                            onPick = viewModel::pickLine,
                            onRetry = viewModel::loadLines,
                        )
                        PriseServiceStep.DIRECTION -> DirectionStep(
                            line = state.selectedLine,
                            selectedKey = state.selectedDirectionKey,
                            onPick = viewModel::pickDirection,
                        )
                        PriseServiceStep.TIME -> TimeStep(
                            departure = state.scheduledDeparture,
                            onPick = viewModel::setTimeOfDay,
                        )
                        PriseServiceStep.TRAIN -> {
                            StepIntro(R.string.service_train_title, R.string.service_train_detail)
                            OutlinedTextField(
                                value = state.trainNumber,
                                onValueChange = viewModel::setTrainNumber,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.service_train_field)) },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Next,
                                ),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        PriseServiceStep.VEHICLE -> {
                            StepIntro(
                                R.string.service_vehicle_title,
                                R.string.service_vehicle_detail,
                            )
                            OutlinedTextField(
                                value = state.vehicleId,
                                onValueChange = viewModel::setVehicleId,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.service_vehicle_field)) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                        PriseServiceStep.GPS -> GpsStep(
                            ready = state.gpsReady,
                            authorization = authorization,
                            onEnable = {
                                when (authorization) {
                                    LocationAuthorization.UNKNOWN ->
                                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                                    LocationAuthorization.GRANTED -> viewModel.setGpsReady(true)
                                    else -> location.openSettings()
                                }
                            },
                        )
                    }
                    if (state.step >= PriseServiceStep.TIME) {
                        val error = state.startFailure
                        if (error != null) {
                            AuleBanner(message = error.label(), tone = AuleTone.ALERT)
                        }
                        Button(
                            onClick = viewModel::continueOrStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = AuleControl.height),
                            enabled = state.canContinue && !state.isStarting,
                            colors = auleAccentButtonColors(),
                        ) {
                            if (state.isStarting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(AuleControl.icon),
                                    color = AuleTheme.tokens.onAccent.color,
                                    strokeWidth = AuleStroke.glyph,
                                )
                            } else {
                                Text(
                                    stringResource(
                                        if (state.step.isLast) {
                                            R.string.service_start
                                        } else {
                                            R.string.service_continue
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AuleSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun StepIntro(title: Int, detail: Int) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.onSurface,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(detail),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
}

@Composable
private fun StepMarks(current: Int, total: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STEP_MARK_GAP),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(STEP_MARK_HEIGHT)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) colors.primary else colors.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun LineStep(
    state: PriseServiceUiState,
    onSearch: (String) -> Unit,
    onPick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    StepIntro(R.string.service_line_title, R.string.service_line_detail)
    when {
        state.isLoadingLines -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AuleSpacing.xxl),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = colors.primary,
                    strokeWidth = AuleStroke.glyph,
                )
            }
        }
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
            OutlinedTextField(
                value = state.search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.service_line_search)) },
                leadingIcon = {
                    Icon(
                        imageVector = AuleGlyph.SEARCH.asImageVector(),
                        contentDescription = null,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            if (state.filteredLines.isEmpty()) {
                Text(
                    text = if (state.search.isBlank()) {
                        stringResource(R.string.service_lines_empty)
                    } else {
                        stringResource(R.string.service_lines_none, state.search)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            state.filteredLines.forEach { line ->
                LineChoice(
                    line = line,
                    selected = line.id == state.selectedLineId,
                    onClick = { onPick(line.id) },
                )
            }
        }
    }
}

@Composable
private fun LineChoice(
    line: ServiceLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = line.label
                this.selected = selected
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            LineBadge(
                line = line.label,
                colorHex = line.colorHex,
                contentDescription = stringResource(R.string.line_badge, line.label),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = line.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (selected) {
                Icon(
                    imageVector = AuleGlyph.CHECK.asImageVector(),
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun DirectionStep(
    line: ServiceLine?,
    selectedKey: String?,
    onPick: (String) -> Unit,
) {
    StepIntro(R.string.service_direction_title, R.string.service_direction_detail)
    line?.directions?.forEach { direction ->
        DirectionChoice(
            direction = direction,
            selected = direction.key == selectedKey,
            onClick = { onPick(direction.key) },
        )
    }
}

@Composable
private fun DirectionChoice(
    direction: ServiceDirection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val label = if (direction.terminus.isBlank()) {
        stringResource(R.string.service_direction_other)
    } else {
        stringResource(R.string.service_direction, direction.terminus)
    }
    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colors.primaryContainer else colors.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
            )
            if (selected) {
                Icon(
                    imageVector = AuleGlyph.CHECK.asImageVector(),
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeStep(
    departure: java.time.Instant?,
    onPick: (Int, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = LocalView.current
    var showPicker by remember { mutableStateOf(false) }
    StepIntro(R.string.service_time_title, R.string.service_time_detail)
    val label = if (departure == null) {
        stringResource(R.string.service_time_choose)
    } else {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(departure)
    }
    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showPicker = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = if (departure == null) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                if (departure != null) {
                    Text(
                        text = stringResource(R.string.service_time_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (departure != null) {
                Text(
                    text = stringResource(R.string.service_time_change),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
            }
        }
    }
    Text(
        text = stringResource(
            if (departure == null) R.string.service_time_missing else R.string.service_time_hint,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
    )
    if (showPicker) {
        val zone = ZoneId.systemDefault()
        val base = departure?.atZone(zone) ?: ZonedDateTime.now(zone)
        val pickerState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        TimePickerDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.service_time_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPick(pickerState.hour, pickerState.minute)
                        showPicker = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            TimePicker(state = pickerState)
        }
    }
}

@Composable
private fun GpsStep(
    ready: Boolean,
    authorization: LocationAuthorization,
    onEnable: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    StepIntro(R.string.service_gps_title, R.string.service_gps_detail)
    val label = stringResource(
        if (ready) R.string.service_gps_on else R.string.service_gps_off,
    )
    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onEnable()
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = label
                this.selected = ready
            },
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (ready) colors.primaryContainer else colors.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleTouch.minimum)
                .padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Icon(
                imageVector = if (ready) {
                    AuleGlyph.CHECK.asImageVector()
                } else {
                    AuleGlyph.PIN.asImageVector()
                },
                contentDescription = null,
                tint = if (ready) colors.primary else colors.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (!ready) {
        val notice = when (authorization) {
            LocationAuthorization.DENIED -> R.string.service_gps_denied
            LocationAuthorization.SERVICES_DISABLED -> R.string.service_gps_disabled
            LocationAuthorization.REDUCED_ACCURACY -> R.string.service_gps_reduced
            else -> null
        }
        if (notice != null) {
            AuleBanner(message = stringResource(notice), tone = AuleTone.ALERT)
        }
    }
}

@Composable
private fun DriverServiceFailureKind.label(): String = stringResource(
    when (this) {
        DriverServiceFailureKind.NOT_SIGNED_IN -> R.string.service_error_session
        DriverServiceFailureKind.NO_DRIVER -> R.string.service_error_driver
        DriverServiceFailureKind.ALREADY_ON_SERVICE -> R.string.service_error_already
        DriverServiceFailureKind.NOT_CONFIGURED -> R.string.service_error_config
        DriverServiceFailureKind.NETWORK -> R.string.service_error_network
        DriverServiceFailureKind.LINES_EMPTY -> R.string.service_error_lines
        DriverServiceFailureKind.REJECTED -> R.string.service_error_rejected
        DriverServiceFailureKind.UNKNOWN -> R.string.service_error_unknown
    },
)

private val STEP_MARK_HEIGHT = 4.dp
private val STEP_MARK_GAP = 6.dp

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
