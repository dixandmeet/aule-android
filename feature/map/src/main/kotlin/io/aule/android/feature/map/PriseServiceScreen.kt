package io.aule.android.feature.map

import android.Manifest
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
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
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
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
import java.time.format.DateTimeFormatter
import android.view.HapticFeedbackConstants

/**
 * L'assistant de prise de service, posé par-dessus la carte.
 *
 * Six étapes, comme Flutter. L'heure, le train et le véhicule restent
 * facultatifs ; le GPS, non. La carte n'est pas démontée pendant ce temps.
 */
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

    BackHandler {
        if (viewModel.back()) onClose()
    }

    AuleTheme {
        val tokens = AuleTheme.tokens
        AuleAmbientBackground(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = AuleSpacing.lg),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AuleSpacing.sm, bottom = AuleSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuleIconButton(
                        glyph = AuleGlyph.BACK,
                        contentDescription = stringResource(R.string.service_back),
                        onClick = { if (viewModel.back()) onClose() },
                    )
                    Column(modifier = Modifier.padding(start = AuleSpacing.sm)) {
                        BasicText(
                            text = stringResource(R.string.service_title),
                            style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
                                .copy(color = tokens.onSurface.color),
                            modifier = Modifier.semantics { heading() },
                        )
                        BasicText(
                            text = stringResource(
                                R.string.service_step,
                                state.step.index + 1,
                                PriseServiceStep.entries.size,
                            ),
                            style = auleTextStyle(AuleRole.KICKER)
                                .copy(color = tokens.onSurfaceMuted.color),
                        )
                    }
                }
                StepMarks(
                    current = state.step.index,
                    total = PriseServiceStep.entries.size,
                )
                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
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
                            AuleTextField(
                                label = stringResource(R.string.service_train_field),
                                value = state.trainNumber,
                                onValueChange = viewModel::setTrainNumber,
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PriseServiceStep.VEHICLE -> {
                            StepIntro(R.string.service_vehicle_title, R.string.service_vehicle_detail)
                            AuleTextField(
                                label = stringResource(R.string.service_vehicle_field),
                                value = state.vehicleId,
                                onValueChange = viewModel::setVehicleId,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.fillMaxWidth(),
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
                        AuleButton(
                            title = stringResource(
                                if (state.step.isLast) R.string.service_start else R.string.service_continue,
                            ),
                            onClick = viewModel::continueOrStart,
                            enabled = state.canContinue,
                            loading = state.isStarting,
                        )
                    }
                    Spacer(modifier = Modifier.height(AuleSpacing.lg))
                }
            }
        }
    }
}

@Composable
private fun StepIntro(title: Int, detail: Int) {
    val tokens = AuleTheme.tokens
    BasicText(
        text = stringResource(title),
        style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold).copy(color = tokens.onSurface.color),
        modifier = Modifier.semantics { heading() },
    )
    BasicText(
        text = stringResource(detail),
        style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
    )
}

@Composable
private fun StepMarks(current: Int, total: Int) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STEP_MARK_GAP),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(STEP_MARK_HEIGHT)
                    .clip(RoundedCornerShape(AuleRadius.pill))
                    .background(
                        if (index <= current) tokens.accent.color else tokens.hairline.color,
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
    val tokens = AuleTheme.tokens
    StepIntro(R.string.service_line_title, R.string.service_line_detail)
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
            if (state.filteredLines.isEmpty()) {
                BasicText(
                    text = if (state.search.isBlank()) {
                        stringResource(R.string.service_lines_empty)
                    } else {
                        stringResource(R.string.service_lines_none, state.search)
                    },
                    style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
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
            BasicText(
                text = line.description,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                maxLines = 2,
            )
        }
        if (selected) {
            AuleIcon(glyph = AuleGlyph.CHECK, tint = tokens.accent.color)
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
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val label = if (direction.terminus.isBlank()) {
        stringResource(R.string.service_direction_other)
    } else {
        stringResource(R.string.service_direction, direction.terminus)
    }
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
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(AuleSpacing.md)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        if (selected) {
            AuleIcon(glyph = AuleGlyph.CHECK, tint = tokens.accent.color)
        }
    }
}

@Composable
private fun TimeStep(
    departure: java.time.Instant?,
    onPick: (Int, Int) -> Unit,
) {
    val tokens = AuleTheme.tokens
    val context = LocalContext.current
    val view = LocalView.current
    StepIntro(R.string.service_time_title, R.string.service_time_detail)
    val label = if (departure == null) {
        stringResource(R.string.service_time_choose)
    } else {
        DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(departure)
    }
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(tokens.surface.color)
            .border(
                AuleStroke.hairline,
                if (departure == null) tokens.hairline.color else tokens.accent.color,
                shape,
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val zone = ZoneId.systemDefault()
                val base = departure?.atZone(zone) ?: java.time.ZonedDateTime.now(zone)
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(hour, minute) },
                    base.hour,
                    base.minute,
                    DateFormat.is24HourFormat(context),
                ).show()
            }
            .padding(AuleSpacing.md)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = label,
                style = auleTextStyle(
                    if (departure == null) AuleRole.BODY else AuleRole.TITLE,
                    FontWeight.Bold,
                ).copy(color = tokens.onSurface.color),
            )
            if (departure != null) {
                BasicText(
                    text = stringResource(R.string.service_time_edited),
                    style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
                )
            }
        }
        if (departure != null) {
            BasicText(
                text = stringResource(R.string.service_time_change),
                style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                    .copy(color = tokens.accentOnSurface.color),
            )
        }
    }
    BasicText(
        text = stringResource(
            if (departure == null) R.string.service_time_missing else R.string.service_time_hint,
        ),
        style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
    )
}

@Composable
private fun GpsStep(
    ready: Boolean,
    authorization: LocationAuthorization,
    onEnable: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    StepIntro(R.string.service_gps_title, R.string.service_gps_detail)
    val label = stringResource(
        if (ready) R.string.service_gps_on else R.string.service_gps_off,
    )
    val shape = RoundedCornerShape(AuleRadius.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .clip(shape)
            .background(
                if (ready) tokens.accent.color.copy(alpha = AuleAlpha.TINT) else tokens.surface.color,
            )
            .border(
                AuleStroke.hairline,
                if (ready) tokens.accent.color else tokens.hairline.color,
                shape,
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onEnable()
            }
            .padding(AuleSpacing.md)
            .semantics {
                role = Role.Button
                selected = ready
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        AuleIcon(
            glyph = if (ready) AuleGlyph.CHECK else AuleGlyph.PIN,
            tint = if (ready) tokens.accent.color else tokens.onSurface.color,
        )
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                .copy(color = tokens.onSurface.color),
            modifier = Modifier.weight(1f),
        )
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
