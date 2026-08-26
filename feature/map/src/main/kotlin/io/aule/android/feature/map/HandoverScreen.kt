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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.bleedHorizontal
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleEmptyState
import io.aule.android.core.designsystem.component.AuleGlassSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleLoadingState
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.LineBadge
import io.aule.android.core.designsystem.component.RealtimeDot
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.component.delayInk
import io.aule.android.core.designsystem.component.realtimeInk
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.model.ActiveDriverService
import io.aule.android.core.model.HandoverAlertPrefs
import io.aule.android.core.model.HandoverFailureKind
import io.aule.android.core.model.HandoverProgress
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
                // Tonal, et non l'aplat de marque. L'écran d'arrivée porte déjà
                // sa surface de marque — le cartouche de réussite — et deux
                // teals empilés à trois centimètres l'un de l'autre ne
                // désignent plus rien. « Fermer » n'est de toute façon pas une
                // action : c'est la sortie d'un écran dont le travail est fait.
                HandoverPrimaryAction(
                    label = stringResource(R.string.handover_done_close),
                    onClick = onFinish,
                    tonal = true,
                    modifier = Modifier
                        // La colonne porte déjà `safeDrawingPadding` : y ajouter
                        // `navigationBarsPadding` décollait le bouton d'une
                        // seconde barre système qui n'existe pas.
                        .padding(horizontal = AuleSpacing.lg)
                        .padding(bottom = AuleSpacing.lg),
                )
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
    // Du verre, et non un aplat de conteneur. Ces trois panneaux ne prennent
    // pas l'écran : ils se posent dessus, et sous eux la carte continue de
    // montrer où est le collègue. Un aplat opaque coupait cette carte en deux
    // sans rien gagner en lisibilité, et surtout sans ombre — le panneau était
    // collé au fond au lieu de flotter au-dessus.
    AuleGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = AuleElevation.LIFTED,
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
    AuleGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = AuleElevation.LIFTED,
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
                // La liste est triée par proximité, donc elle se réordonne
                // pendant que le collègue roule : sans clef, la cascade
                // d'entrée suivrait la place et non l'arrêt.
                state.visibleLiveStops.forEachIndexed { index, stop ->
                    key(stop.id) {
                        val distance = stop.coordinate?.let { coordinate ->
                            state.fallbackAround?.let {
                                GeoMath.formatDistance(GeoMath.distance(it, coordinate))
                            }
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
                            detail = listOfNotNull(distance, passage)
                                .joinToString(" · ")
                                .ifEmpty { null },
                            selected = stop.id == state.selectedLiveStop?.id,
                            enabled = !state.isBusy,
                            onClick = { onPick(stop) },
                            index = index,
                        )
                    }
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
    AuleGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = AuleElevation.LIFTED,
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
                index = 0,
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
                index = 1,
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
                index = 2,
                onChecked = { on -> onPrefs(prefs.copy(onArrival = on)) },
            )
            AlertToggle(
                label = stringResource(R.string.handover_alerts_vibration),
                checked = prefs.vibration,
                index = 3,
                onChecked = { on -> onPrefs(prefs.copy(vibration = on)) },
            )
            AlertToggle(
                label = stringResource(R.string.handover_alerts_sound),
                checked = prefs.sound,
                index = 4,
                onChecked = { on -> onPrefs(prefs.copy(sound = on)) },
            )
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            HandoverPrimaryAction(
                label = stringResource(R.string.handover_alerts_start),
                onClick = onStart,
            )
            }
        }
    }
}

/**
 * Une alerte, et le seuil qui la déclenche.
 *
 * Une case cochée et une case décochée ne se distinguaient que par la case
 * elle-même, à l'extrême gauche de la rangée. Le libellé passe donc au slot
 * **appuyé** quand l'alerte est active, et à l'encre secondaire quand elle ne
 * l'est pas : le slot appuyé a exactement les mêmes métriques que l'ordinaire,
 * donc la rangée ne bouge pas d'un point pendant qu'on coche — c'est toute la
 * raison d'être de ces slots. Ce qu'on lit, c'est la liste des alertes qu'on
 * recevra, en gras, au milieu de celles qu'on ne recevra pas.
 */
@Composable
private fun AlertToggle(
    label: String,
    checked: Boolean,
    index: Int,
    onChecked: (Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = if (checked) {
                    MaterialTheme.typography.bodyLargeEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (checked) colors.onSurface else colors.onSurfaceVariant,
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
            .auleEnter(index = index)
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
        // Le seuil est le seul chiffre de la rangée, et c'est celui qu'on vient
        // régler : il prend le slot `DATA` appuyé, dont les chiffres sont à
        // chasse fixe. « 2 » et « 10 » y occupent la même largeur, donc les
        // deux flèches ne se déplacent pas sous le doigt d'un appui à l'autre —
        // ce que faisait l'ancien `bodyMedium` en gras.
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLargeEmphasized,
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
            // Le slot appuyé, à taille égale. La relève est une suite de
            // questions — quelle ligne, quel véhicule, quel arrêt, quel
            // passage — et la question de l'étape est la seule chose qu'un
            // conducteur doit lire en arrivant sur l'écran. Au poids ordinaire
            // elle pesait le même poids que les rangées qu'elle annonce.
            //
            // Pas un slot plus grand : la barre Material a une hauteur fixe, et
            // un titre de 24 sp qui passe à deux lignes s'y ferait couper.
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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

/**
 * La fin de la relève — la seule surface de marque de tout le parcours.
 *
 * `AuleBrandSurface` se réserve à « l'élément qui doit être vu en premier », et
 * il n'y en a qu'un par écran. Sur les huit écrans de la relève, c'est ici
 * qu'il tombe juste : partout ailleurs, l'élément à voir en premier est un
 * **choix à faire**, et le teal appartient alors au bouton qui le porte. Ici il
 * n'y a plus de choix — le service est repris, l'écran ne fait que le dire — et
 * la surface de marque peut être l'annonce elle-même sans entrer en
 * concurrence avec quoi que ce soit. C'est aussi le seul instant du parcours
 * qui mérite d'être fêté : quatre écrans de questions viennent d'aboutir.
 *
 * L'échec, lui, garde un aplat d'erreur. Une relève annulée qu'on annoncerait
 * sur la couleur de la marque serait un contresens, et le conducteur qui
 * regarde de loin lirait « c'est bon ».
 */
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
    val detail = if (success) {
        val stop = state.selectedLiveStop?.name ?: state.handover?.reliefStopName
        val line = state.started.lineLabel
        when {
            !stop.isNullOrBlank() && line.isNotBlank() ->
                stringResource(R.string.handover_done_detail_stop, stop, line)
            line.isNotBlank() -> stringResource(R.string.handover_done_detail, line)
            else -> null
        }
    } else {
        null
    }
    if (success) {
        AuleBrandSurface(
            modifier = Modifier
                .fillMaxWidth()
                .auleEnter(),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = AuleElevation.LIFTED,
        ) {
            Column(
                modifier = Modifier.padding(AuleSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                // Sans `tint` : la surface de marque pose déjà sa couleur de
                // contenu, et l'icône en hérite. La forcer ici, c'est se
                // retrouver avec une coche qui ne suit plus l'ambiance.
                Icon(
                    imageVector = Icons.Outlined.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(DONE_GLYPH),
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .auleEnter(),
            shape = MaterialTheme.shapes.large,
            color = colors.errorContainer,
            contentColor = colors.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(AuleSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = AuleGlyph.FLAG.asImageVector(),
                    contentDescription = null,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                )
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
        modifier = Modifier.auleEnter(index = 0),
    )
    TargetCard(
        target = target,
        lineLabel = state.selectedLine?.label ?: target.lineId,
        modifier = Modifier.auleEnter(index = 1),
    )
    HandoverPrimaryAction(
        label = stringResource(R.string.handover_resume_action),
        onClick = onResume,
        enabled = !state.isBusy,
        busy = state.isBusy,
        modifier = Modifier.auleEnter(index = 2),
    )
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
            HandoverPrimaryAction(
                label = stringResource(R.string.issue_retry),
                onClick = onRetry,
                tonal = true,
            )
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
                        state.filteredLines.forEachIndexed { index, line ->
                            // La cascade se règle sur l'identité de la ligne,
                            // pas sur son rang : sans clef, chaque caractère
                            // tapé décale les rangées d'un cran et l'entrée se
                            // rejoue sur des lignes qui n'ont pas bougé.
                            key(line.id) {
                                HandoverLineChoice(
                                    line = line,
                                    selected = line.id == state.selectedLineId,
                                    onClick = { onPick(line.id) },
                                    index = index,
                                )
                            }
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
                        // Les lignes en service entrent et sortent au fil des
                        // instantanés de flotte : même clef, même raison.
                        state.activeLines.forEachIndexed { index, line ->
                            key(line.id) {
                                HandoverLineChoice(
                                    line = line,
                                    selected = line.id == state.selectedLineId,
                                    onClick = { onPick(line.id) },
                                    index = index,
                                )
                            }
                        }
                    }
                }
                if (state.recentLines.isNotEmpty()) {
                    LineSectionLabel(
                        text = stringResource(R.string.handover_lines_recent),
                        accent = false,
                    )
                    Column(modifier = Modifier.bleedHorizontal(AuleSpacing.lg)) {
                        state.recentLines.forEachIndexed { index, line ->
                            key(line.id) {
                                HandoverLineChoice(
                                    line = line,
                                    selected = line.id == state.selectedLineId,
                                    onClick = { onPick(line.id) },
                                    index = index,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'intitulé d'un groupe de lignes.
 *
 * Deux groupes, deux natures : « en service maintenant » est un **fait mesuré**
 * — ces véhicules roulent, la position vient d'arriver — là où « relevées
 * récemment » n'est qu'un souvenir de l'appareil. D'où le point de temps réel,
 * qui ne se pose que sur le premier, et l'encre d'accent qui va avec : le
 * conducteur pressé doit pouvoir viser ce groupe sans lire son titre.
 *
 * Même slot appuyé que les intitulés de section des volets de la carte
 * (`SheetSectionLabel`) : le titre se repère au balayage sans jamais se lire
 * avant les lignes qu'il annonce.
 */
@Composable
private fun LineSectionLabel(
    text: String,
    accent: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(top = AuleSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accent) {
            RealtimeDot(
                isLive = true,
                liveDescription = stringResource(R.string.stop_realtime),
                scheduledDescription = stringResource(R.string.stop_scheduled),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = if (accent) {
                colors.primary
            } else {
                colors.onSurfaceVariant
            },
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * Une ligne du réseau, telle qu'on la choisit.
 *
 * La ligne retenue prend l'**aplat de marque plein**, comme l'étape en cours
 * d'un itinéraire ou l'arrêt recommandé d'« autour de vous ». Elle portait le
 * conteneur pastel `secondaryContainer` — qui, dans cette palette, est le vert
 * du temps réel : un choix de conducteur y était donc annoncé avec la couleur
 * qui dit « donnée mesurée », et il fallait rattraper sa faiblesse en mettant
 * le libellé en gras. Le gras reste, mais il ne rattrape plus rien : il
 * accompagne un aplat qui se voit.
 *
 * Toutes les encres viennent de `ListItemDefaults` et non des `Text` : sur le
 * teal profond, un libellé resté sur `onSurface` et une description restée sur
 * `onSurfaceVariant` écriraient en gris sombre sur du sombre. Le badge de
 * ligne, lui, garde la couleur du réseau — c'est l'identité de la ligne, pas
 * un état de sélection.
 */
@Composable
private fun HandoverLineChoice(
    line: ServiceLine,
    selected: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val description = line.description
    ListItem(
        headlineContent = {
            // `titleMedium` appuyé plutôt que `bodyLarge` : mêmes 16 sp sur le
            // même interligne — la rangée ne change pas de hauteur en se
            // sélectionnant — mais la ligne retenue se lit en gras au milieu
            // des autres.
            Text(
                text = line.label,
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            )
        },
        supportingContent = if (description.isNotBlank()) {
            {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
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
                    imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
                    contentDescription = null,
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.primary else Color.Transparent,
            headlineColor = if (selected) colors.onPrimary else colors.onSurface,
            supportingColor = if (selected) {
                colors.onPrimary.copy(alpha = AuleAlpha.VEIL)
            } else {
                colors.onSurfaceVariant
            },
            trailingIconColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = index)
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
                // La ligne est acquise : elle se lit comme un fait posé, pas
                // comme une option restante.
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.titleMediumEmphasized,
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
                .auleEnter(index = 0)
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
        modifier = Modifier.auleEnter(index = 1),
    )
    OutlinedTextField(
        value = state.query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = 2)
            .defaultMinSize(minHeight = AuleControl.field),
        label = { Text(stringResource(R.string.handover_vehicle_field)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        // Le cran médian de l'échelle — 22 dp — et non plus le plus petit. À
        // 10 dp, le champ avait des coins de menu contextuel sous un bouton en
        // gélule : deux objets dessinés par deux mains différentes, à trois
        // centimètres d'écart. C'est aussi le cran des champs de la prise de
        // service, l'assistant jumeau de celui-ci.
        shape = MaterialTheme.shapes.medium,
    )
    HandoverPrimaryAction(
        label = stringResource(R.string.handover_search),
        onClick = onSearch,
        enabled = state.canSearch && !state.isBusy,
        busy = state.isBusy,
        modifier = Modifier.auleEnter(index = 3),
    )
}

@Composable
private fun CandidatesStep(
    state: HandoverUiState,
    onPick: (HandoverTarget) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (state.candidates.size > 1) {
        // L'avertissement porte sur un choix qu'on peut rater — un service
        // oublié de la veille. `bodyMedium` et non `labelSmall` : à 11 sp, un
        // texte qu'il faut absolument lire est un texte qu'on ne lit pas.
        Text(
            text = stringResource(R.string.handover_candidates_many),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
    state.candidates.forEachIndexed { index, target ->
        TargetCard(
            target = target,
            lineLabel = state.selectedLine?.label ?: target.lineId,
            action = stringResource(R.string.handover_engage),
            enabled = !state.isBusy,
            onAction = { onPick(target) },
            modifier = Modifier.auleEnter(index = index),
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
        state.selectedLine?.directions?.forEachIndexed { index, direction ->
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
                index = index,
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
        // Filtrée au clavier, et triée par proximité : la clef est l'arrêt.
        state.visibleFallbackStops.forEachIndexed { index, stop ->
            key(stop.id) {
                val distance = stop.coordinate?.let { coordinate ->
                    state.fallbackAround?.let {
                        GeoMath.formatDistance(GeoMath.distance(it, coordinate))
                    }
                }
                ChoiceRow(
                    label = stop.name,
                    detail = distance,
                    selected = false,
                    enabled = !state.isBusy,
                    onClick = { onPick(stop) },
                    index = index,
                )
            }
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
            // L'arrêt choisi est le sujet de l'écran : il se lit avant la
            // liste des passages qu'il commande.
            Text(
                text = stopName,
                style = MaterialTheme.typography.titleMediumEmphasized,
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
        SheetSectionLabel(text = stringResource(R.string.handover_fallback_passages))
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
            // Les passages se rafraîchissent, et celui de tête part : la clef
            // est l'heure du passage, pas sa place dans la liste.
            state.fallbackPassages.forEachIndexed { index, passage ->
                key(passage.expectedAt, passage.destination) {
                    val wait = passage.waitMinutes(java.time.Instant.now()).let { minutes ->
                        if (minutes == 0) Wait.Approaching else Wait.Minutes(minutes)
                    }
                    // L'heure sort du libellé. Collée à la destination par un
                    // point médian, elle se lisait au même poids qu'elle — or
                    // c'est le seul mot de la rangée qu'un conducteur compare
                    // d'une ligne à l'autre. En tête et à chasse fixe, les
                    // heures s'alignent en colonne et la liste se parcourt d'un
                    // seul mouvement de l'œil.
                    ChoiceRow(
                        lead = clock.format(passage.expectedAt),
                        label = passage.destination,
                        detail = wait.label(),
                        selected = state.fallbackTime == passage.expectedAt,
                        enabled = !state.isBusy && !state.alreadyOnService,
                        onClick = { onStart(passage) },
                        index = index,
                    )
                }
            }
        }
    }
}

/**
 * Une rangée de choix : un sens, un arrêt, un passage.
 *
 * Les quatre listes de la relève partagent cette rangée, et c'est voulu — on y
 * répond quatre fois à la même question, « lequel ? », et quatre dessins
 * différents feraient croire à quatre questions différentes.
 *
 * La rangée retenue prend l'**aplat de marque plein**, comme partout ailleurs
 * dans le produit depuis que le pastel a été retiré des états sélectionnés. Le
 * conteneur `secondaryContainer` qu'elle portait est, dans cette palette, le
 * vert du temps réel : un arrêt choisi s'y annonçait avec la couleur qui dit
 * « donnée mesurée », et l'heure du passage y restait écrite en `primary` —
 * du teal sur du vert pâle, pour désigner un choix.
 *
 * Toutes les encres passent donc par `ListItemDefaults` : posées sur les
 * `Text`, elles resteraient sur les rôles de surface claire et tomberaient à
 * un rapport de deux pour un sur l'aplat de marque.
 *
 * @param lead ce qui doit se lire avant le libellé, quand la rangée en a un :
 *   l'heure d'un passage. Il s'aligne en colonne d'une rangée à l'autre grâce
 *   aux chiffres à chasse fixe du slot `DATA`, ce qui rend la liste
 *   comparable au lieu d'être seulement lisible.
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    detail: String? = null,
    lead: String? = null,
    index: Int = 0,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            )
        },
        supportingContent = detail?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        leadingContent = lead?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = AuleGlyph.CHEVRON.asImageVector(),
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) colors.primary else Color.Transparent,
            headlineColor = if (selected) colors.onPrimary else colors.onSurface,
            supportingColor = if (selected) {
                colors.onPrimary.copy(alpha = AuleAlpha.VEIL)
            } else {
                colors.onSurfaceVariant
            },
            leadingIconColor = if (selected) colors.onPrimary else colors.onSurface,
            trailingIconColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = index)
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .semantics {
                this.selected = selected
                // L'énoncé suit l'ordre de lecture : l'heure, puis la
                // destination, puis l'attente. TalkBack lit la rangée comme un
                // conducteur la regarde.
                contentDescription = listOfNotNull(lead, label, detail).joinToString(". ")
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
        val unknown = stringResource(R.string.handover_metric_unknown)
        if (progress == null) {
            // Pas encore de point GPS. C'est un état de la relève comme un
            // autre, et il mérite le même cartouche que les trois autres :
            // écrit en petite phrase grise, il donnait un écran qui n'a l'air
            // de rien faire — alors qu'il attend, et qu'il le sait.
            //
            // Sans valeur, et non avec le tiret des mesures manquantes : un
            // cadratin de 28 sp posé au centre de l'écran est ce que l'œil
            // trouve en premier, et il n'apprend rien. Le cartouche se réduit
            // alors à ce qu'il a vraiment à dire — l'état, et ce qu'on attend.
            ReliefHero(
                relief = ReliefState.WAITING,
                value = null,
                caption = stringResource(R.string.handover_waiting_fix),
                modifier = Modifier.auleEnter(),
            )
        } else {
            val eta = progress.estimatedAt?.let(clock::format)
            val delay = progress.delayMinutes()
            // L'heure d'arrivée quitte la liste des mesures pour devenir le
            // sujet de l'écran. C'est le chiffre qu'un conducteur relit toutes
            // les vingt secondes ; aligné à droite d'un intitulé, au même corps
            // que la distance et le nombre d'arrêts, il fallait le chercher.
            ReliefHero(
                relief = progress.reliefState(),
                value = when {
                    eta == null -> unknown
                    delay == null || delay == 0 -> eta
                    delay > 0 -> stringResource(R.string.handover_eta_late, eta, delay)
                    else -> stringResource(R.string.handover_eta_early, eta, delay)
                },
                caption = stringResource(R.string.handover_metric_eta),
                valueColor = when {
                    delay == null || delay == 0 -> null
                    delay > 0 -> delayInk()
                    else -> realtimeInk()
                },
                modifier = Modifier.auleEnter(),
            )
            val planned = state.handover?.reliefPlannedAt
            val leaveBy = state.leaveBy
            val overdue = leaveBy != null && leaveBy.isBefore(java.time.Instant.now())
            // Les intitulés sont sortis de la liste : `stringResource` est un
            // appel composable, et le glisser dans les lambdas de `listOfNotNull`
            // rendrait illisible ce qui n'est qu'une énumération de mesures.
            val reliefLabel = stringResource(R.string.handover_metric_relief)
            val plannedLabel = stringResource(R.string.handover_metric_planned)
            val stopsLabel = stringResource(R.string.handover_metric_stops)
            val distanceLabel = stringResource(R.string.handover_metric_distance)
            val leaveLabel = stringResource(R.string.handover_metric_leave)
            val passedLabel = stringResource(R.string.handover_metric_passed)
            val metrics = listOfNotNull(
                TrackingMetric(
                    label = reliefLabel,
                    value = state.selectedLiveStop?.name ?: unknown,
                    strong = true,
                ),
                planned?.let { TrackingMetric(label = plannedLabel, value = clock.format(it)) },
                TrackingMetric(
                    label = stopsLabel,
                    value = when {
                        progress.stopsRemaining == null -> unknown
                        progress.passed -> passedLabel
                        else -> progress.stopsRemaining.toString()
                    },
                ),
                TrackingMetric(
                    label = distanceLabel,
                    value = progress.metersRemaining?.let { GeoMath.formatDistance(it) } ?: unknown,
                ),
                leaveBy?.let {
                    TrackingMetric(
                        label = leaveLabel,
                        value = clock.format(it),
                        ink = if (overdue) colors.error else colors.onSurface,
                        strong = true,
                    )
                },
            )
            // Un cartouche, et non cinq rangées posées sur le fond du panneau.
            // Les mesures se lisent ensemble — « quel arrêt, quand, à quelle
            // distance » est une seule question — et un bloc unique les range
            // sous le chiffre qui les résume au lieu de les mettre à son
            // niveau. C'est le cartouche des volets de la carte, à l'identique.
            SheetCard(modifier = Modifier.fillMaxWidth()) {
                metrics.forEachIndexed { index, metric ->
                    if (index > 0) SheetRowDivider()
                    TrackingCell(metric = metric, index = index)
                }
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
    // Le bouton **s'allume** à l'arrivée : tonal tant que le véhicule roule,
    // aplat de marque quand il est là. C'est la seule surface de marque de cet
    // écran, et c'est pourquoi le cartouche d'état n'en est pas une — deux
    // teals à trois centimètres l'un de l'autre, et plus rien ne désigne
    // l'action. Le cartouche, lui, porte sa lueur d'accent au même instant :
    // les deux s'allument ensemble, ce qui se lit comme un seul événement.
    HandoverPrimaryAction(
        label = confirmTitle,
        onClick = onConfirm,
        enabled = canConfirm,
        busy = state.isBusy,
        tonal = !arrived,
    )
    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
    ) {
        Text(stringResource(R.string.handover_confirm_cancel))
    }
}

/**
 * Une mesure du suivi : ce qu'on mesure, et la mesure.
 *
 * [ink] ne sert qu'aux valeurs qui **changent de sens** selon leur contenu —
 * l'heure de départ conseillée passe au rouge quand elle est dépassée. Une
 * couleur posée là pour décorer ferait perdre celle-là.
 */
private data class TrackingMetric(
    val label: String,
    val value: String,
    val ink: Color? = null,
    val strong: Boolean = false,
)

/**
 * Une rangée du cartouche de suivi.
 *
 * L'intitulé et la valeur se sont écartés d'un cran chacun : l'intitulé quitte
 * `labelSmall` — 11 sp, ce qu'on met sur une mention légale, pas sur ce qu'un
 * conducteur lit debout — et la valeur passe aux slots appuyés. La graisse
 * n'est plus posée à la main : `bodyMediumEmphasized` et
 * `titleMediumEmphasized` ont exactement les métriques de leurs homologues
 * ordinaires, donc la rangée garde sa hauteur, et le dépôt garde une seule
 * façon de rendre un texte plus présent.
 */
@Composable
private fun TrackingCell(metric: TrackingMetric, index: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .auleEnter(index = index)
            .defaultMinSize(minHeight = AuleTouch.minimum)
            .padding(horizontal = AuleSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
    ) {
        Text(
            text = metric.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Text(
            text = metric.value,
            style = if (metric.strong) {
                MaterialTheme.typography.titleMediumEmphasized
            } else {
                MaterialTheme.typography.bodyMediumEmphasized
            },
            color = metric.ink ?: colors.onSurface,
        )
    }
}

/**
 * Le véhicule qu'on relève : ligne, engin, sens, collègue, fraîcheur du point.
 *
 * Le cartouche des volets de la carte, et non plus une `Card` posée sur la
 * couleur de surface. L'ancienne prenait `surface` — exactement le fond sur
 * lequel elle était posée — et ne se distinguait donc que par ses coins : une
 * carte invisible, dont on ne voyait que le contour. `surfaceContainerHigh`
 * la fait exister.
 *
 * Il n'y a pas d'ombre, et c'est délibéré : ce cartouche vit à l'intérieur
 * d'un panneau qui en porte déjà une, et une carte qui flotte au-dessus d'une
 * carte n'ajoute pas de la profondeur, elle en retire.
 *
 * L'âge du point change de nature selon sa valeur : une position à jour est un
 * **fait mesuré**, et prend donc l'encre du temps réel et son point qui pulse ;
 * une position vieille de trois minutes n'est plus qu'une information, en gris.
 * C'est la même distinction que sur les passages de la carte, et elle décide
 * ici de la confiance qu'on accorde à tout le reste de l'écran.
 */
@Composable
private fun TargetCard(
    target: HandoverTarget,
    lineLabel: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    enabled: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val direction = target.terminus?.let { stringResource(R.string.service_direction, it) }
        ?: stringResource(R.string.service_direction_other)
    val vehicle = target.vehicleLabel()
    val colleague = target.driverDisplay ?: stringResource(R.string.handover_colleague)
    val age = target.positionAgeSeconds
    val fresh = age != null && age < FIX_FRESH_SECONDS
    SheetCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(AuleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            // « Ligne 1 · Véhicule 342 » identifie la relève à lui seul : c'est
            // la phrase qu'un conducteur relit pour vérifier qu'il ne s'est pas
            // trompé de véhicule, et elle passe donc au titre appuyé.
            Text(
                text = stringResource(R.string.handover_target_title, lineLabel, vehicle),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
            Text(
                text = direction,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            ) {
                Icon(
                    imageVector = AuleGlyph.PERSON.asImageVector(),
                    contentDescription = null,
                    modifier = Modifier.size(AuleControl.icon),
                    tint = colors.onSurfaceVariant,
                )
                Text(
                    text = colleague,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            if (age != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                ) {
                    RealtimeDot(
                        isLive = fresh,
                        liveDescription = stringResource(R.string.stop_realtime),
                        scheduledDescription = stringResource(R.string.stop_scheduled),
                    )
                    Text(
                        text = if (fresh) {
                            stringResource(R.string.handover_fix_fresh)
                        } else {
                            stringResource(R.string.handover_fix_age, age)
                        },
                        // Gris et ordinaire dans les deux cas, comme sur les
                        // volets de la carte : la phrase dit déjà si le point
                        // est frais, et la redire en vert n'ajoutait qu'un
                        // troisième canal au même fait.
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (action != null && onAction != null) {
                HandoverPrimaryAction(
                    label = action,
                    onClick = onAction,
                    enabled = enabled,
                )
            }
        }
    }
}

/**
 * L'état d'une approche, tel qu'un conducteur le nomme.
 *
 * Le suivi affichait quatre situations très différentes dans exactement la même
 * grille de mesures : un véhicule à six arrêts, un véhicule qui entre en
 * station, un véhicule arrivé, un véhicule qu'on a manqué. Seuls les chiffres
 * changeaient — et un chiffre qui change n'est pas un état qui change. Il
 * fallait lire trois lignes pour savoir laquelle des quatre on vivait.
 *
 * Ces quatre valeurs ne calculent rien : elles **nomment** ce que
 * [io.aule.android.core.model.HandoverProgress] mesure déjà, et que l'écran ne
 * montrait pas.
 */
private enum class ReliefState { WAITING, APPROACHING, ARRIVED, PASSED }

/**
 * L'état courant, lu dans la mesure d'approche.
 *
 * L'ordre des branches n'est pas indifférent. `arrived` passe **avant**
 * `passed` parce que c'est déjà l'ordre du bouton de confirmation : un véhicule
 * qui a dépassé l'index de l'arrêt tout en restant à quelques mètres est un
 * véhicule qu'on relève, et l'écran ne peut pas dire « manqué » pendant que le
 * bouton dit « prendre le service ».
 */
private fun HandoverProgress.reliefState(): ReliefState = when {
    arrived -> ReliefState.ARRIVED
    passed -> ReliefState.PASSED
    approaching -> ReliefState.APPROACHING
    else -> ReliefState.WAITING
}

/**
 * La couleur d'un état — la seule chose qui les distingue à un mètre.
 *
 * Aucune n'est un rôle de hiérarchie Material, et c'est le point : « le
 * véhicule approche » est un fait de transport, pas un niveau d'importance.
 * L'approche prend donc l'encre du temps réel, celle des passages mesurés de
 * la carte ; l'arrivée prend l'accent de la marque, la même couleur que le
 * bouton qui s'allume au même instant ; le véhicule manqué prend l'erreur,
 * parce qu'il y a bien quelque chose à corriger. L'attente ne prend rien :
 * c'est l'état où il n'y a rien à faire.
 */
@Composable
private fun ReliefState.ink(): Color = when (this) {
    ReliefState.WAITING -> MaterialTheme.colorScheme.onSurfaceVariant
    ReliefState.APPROACHING -> realtimeInk()
    ReliefState.ARRIVED -> MaterialTheme.colorScheme.primary
    ReliefState.PASSED -> MaterialTheme.colorScheme.error
}

@Composable
private fun ReliefState.label(): String = stringResource(
    when (this) {
        ReliefState.WAITING -> R.string.handover_state_waiting
        ReliefState.APPROACHING -> R.string.handover_state_approaching
        ReliefState.ARRIVED -> R.string.handover_state_arrived
        ReliefState.PASSED -> R.string.handover_state_passed
    },
)

/**
 * Le symbole d'un état.
 *
 * Pris directement dans les Material Symbols, sans passer par [AuleGlyph] :
 * cette énumération est un pont pour les écrans anciens, et un usage neuf doit
 * prendre le symbole. Aucun des quatre n'a d'équivalent métier — une horloge,
 * une flèche de proximité, une coche cerclée et un signe d'alerte sont
 * exactement ce que Material dessine pour ces quatre sens.
 */
private fun ReliefState.glyph(): ImageVector = when (this) {
    ReliefState.WAITING -> Icons.Outlined.Schedule
    ReliefState.APPROACHING -> Icons.Outlined.NearMe
    ReliefState.ARRIVED -> Icons.Outlined.TaskAlt
    ReliefState.PASSED -> Icons.Outlined.ErrorOutline
}

/**
 * Le cartouche du suivi : où en est le véhicule, et à quelle heure il arrive.
 *
 * C'est l'élément que l'écran veut faire voir en premier, et il s'y prend de
 * trois façons qui ne se marchent pas dessus :
 *
 * - le **corps**. L'heure prend `headlineMedium` appuyé — 28 sp, gras, chiffres
 *   à chasse fixe : la minute change sans faire danser la ligne, et le chiffre
 *   se lit de biais, à bout de bras, gant au doigt ;
 * - la **couleur**, qui ne touche que l'état — le trait du contour, le symbole,
 *   le mot. Elle ne touche pas la valeur, et c'est délibéré : l'heure garde
 *   l'ambre du retard et le vert de l'avance, deux informations qu'un
 *   cartouche entièrement coloré aurait mangées ;
 * - la **lueur**, à l'arrivée seulement. `AuleShadowTint.ACCENT` n'éloigne pas
 *   une surface du fond, il la désigne — c'est sa raison d'être. Au moment où
 *   le bouton passe à l'aplat de marque, le cartouche s'allume de la même
 *   couleur : deux objets, un seul événement.
 *
 * Le fond reste neutre. Poser ces 28 sp sur un aplat sémantique aurait été plus
 * spectaculaire et moins lisible : `surfaceContainerHighest` tient le contraste
 * en plein soleil comme en cabine de nuit, ce qu'un conteneur d'erreur ou de
 * temps réel ne garantit pas sous une valeur qui, elle aussi, est colorée.
 *
 * @param value l'heure, quand il y en a une. `null` tant qu'aucun point n'est
 *   arrivé : le cartouche n'affiche alors ni chiffre ni tiret, parce qu'un
 *   cadratin de 28 sp est ce que l'œil trouve en premier sur l'écran et qu'il
 *   n'apprend rien. L'état et sa légende suffisent à dire qu'on attend.
 */
@Composable
private fun ReliefHero(
    relief: ReliefState,
    value: String?,
    caption: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val ink = relief.ink()
    val label = relief.label()
    val spoken = if (value == null) "$label. $caption" else "$label. $caption $value"
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .auleShadow(
                level = if (relief == ReliefState.ARRIVED) {
                    AuleElevation.FLOATING
                } else {
                    AuleElevation.NONE
                },
                shape = shape,
                tint = AuleShadowTint.ACCENT,
            )
            // Fusionné pour TalkBack : « en approche, arrivée estimée 16:42 »
            // est une phrase. Les trois textes lus séparément obligeaient à
            // reconstruire l'information dans sa tête, au volant.
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = shape,
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        border = BorderStroke(AuleStroke.emphasis, ink),
    ) {
        Column(
            modifier = Modifier.padding(AuleSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = relief.glyph(),
                    contentDescription = null,
                    modifier = Modifier.size(AuleControl.icon),
                    tint = ink,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = ink,
                )
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = valueColor ?: colors.onSurface,
                )
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * L'action principale d'une étape.
 *
 * Le même bouton revenait sept fois dans ce fichier, avec sept fois les mêmes
 * quatre lignes de roue de chargement — et sept occasions de diverger d'un
 * pixel ou d'une couleur. Il est ici une fois.
 *
 * [tonal] n'est pas un goût mais une hiérarchie : l'aplat de marque désigne
 * l'action qu'on **peut** faire maintenant, le tonal celle qu'on peut faire
 * quand même. Le bouton de confirmation passe de l'un à l'autre à l'arrivée du
 * véhicule, et c'est ce basculement qui dit au conducteur que le moment est
 * venu — bien avant qu'il ait lu l'intitulé.
 *
 * Le libellé prend `labelLarge` appuyé : même boîte, plus de présence. Un
 * bouton de 52 dp de haut portant un texte au poids d'un paragraphe, c'est le
 * genre de détail qui fait dire d'une application qu'elle est fade.
 *
 * La roue suit l'**aplat réel** du bouton, et c'est ce qui manquait. Aux sept
 * endroits d'où elle vient, un bouton occupé est aussi un bouton éteint — on
 * ne relance pas une prise de service en cours — et Material remplace alors son
 * aplat par un gris à douze pour cent. La roue, elle, restait à l'encre de
 * l'accent : du blanc sur du blanc cassé. Le conducteur appuyait, le bouton
 * pâlissait, et plus rien ne tournait dedans.
 */
@Composable
private fun HandoverPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    tonal: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shaped = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = AuleControl.height)
    val body: @Composable () -> Unit = {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(AuleControl.icon),
                color = when {
                    !enabled -> colors.onSurface.copy(alpha = AuleAlpha.DISABLED)
                    tonal -> colors.onSecondaryContainer
                    else -> AuleTheme.tokens.onAccent.color
                },
                strokeWidth = AuleStroke.glyph,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = shaped, enabled = enabled) { body() }
    } else {
        Button(
            onClick = onClick,
            modifier = shaped,
            enabled = enabled,
            colors = auleAccentButtonColors(),
        ) {
            body()
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

/**
 * La coche de l'écran d'arrivée.
 *
 * Plus grande que la grille d'icône : ce n'est pas une commande, c'est le seul
 * instant du parcours où l'application n'a rien à demander. À 24 dp la coche
 * ressemblait à la puce d'une liste ; à 40 elle est ce qu'on voit avant de lire.
 */
private val DONE_GLYPH = 40.dp

/**
 * En deçà de quoi la position du collègue est « à jour ».
 *
 * Le seuil vivait en clair dans le cartouche. Il décide maintenant de deux
 * choses — le libellé et la couleur — et deux endroits qui divergeraient
 * donneraient une position à la fois verte et périmée.
 */
private const val FIX_FRESH_SECONDS = 15
