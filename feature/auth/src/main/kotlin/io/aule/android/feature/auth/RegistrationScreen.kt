package io.aule.android.feature.auth

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandMark
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode
import io.aule.android.core.model.SIGNUP_PROFILES
import kotlinx.coroutines.CancellationException

/**
 * L'assistant d'inscription professionnelle.
 *
 * Port de `SAE/lib/screens/registration_screen.dart` : les phrases, l'ordre
 * des étapes et la persistance du brouillon (sans le mot de passe).
 */
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            viewModel.back(onClose)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        AuleAmbientBackground(modifier = modifier) {
            if (!state.isHydrated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(AuleControl.icon),
                        color = colors.primary,
                        strokeWidth = AuleStroke.glyph,
                    )
                }
                return@AuleAmbientBackground
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = CARD_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AuleSpacing.xl, vertical = AuleSpacing.xl),
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = AuleElevation.OVERLAY.height(AuleTheme.night),
                        ),
                    ) {
                        if (state.step != RegistrationStep.WELCOME) {
                            RegistrationProgress(state = state, onBack = { viewModel.back(onClose) })
                        }
                        Column(
                            modifier = Modifier.padding(AuleSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            when (state.step) {
                                RegistrationStep.WELCOME -> WelcomeStep(
                                    onStart = viewModel::continueForward,
                                    onSignIn = onClose,
                                )
                                RegistrationStep.PROFILE -> WithFooter(state, viewModel) {
                                    ProfilesStep(state = state, onToggle = viewModel::toggleProfile)
                                }
                                RegistrationStep.NETWORK -> WithFooter(state, viewModel) {
                                    NetworkStep(
                                        state = state,
                                        onQuery = viewModel::setNetworkQuery,
                                        onSelectNaolib = viewModel::selectNaolib,
                                    )
                                }
                                RegistrationStep.IDENTITY -> WithFooter(state, viewModel) {
                                    IdentityStep(state = state, viewModel = viewModel)
                                }
                                RegistrationStep.TRANSPORT_MODE -> WithFooter(state, viewModel) {
                                    TransportStep(state = state, onSelect = viewModel::setTransportMode)
                                }
                                RegistrationStep.ACCOUNT -> WithFooter(state, viewModel) {
                                    AccountStep(state = state, viewModel = viewModel)
                                }
                                RegistrationStep.CONFIRMATION -> ConfirmationStep(
                                    state = state,
                                    onResend = viewModel::resendConfirmation,
                                    onFinish = { viewModel.finish(onClose) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WithFooter(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Button(
            onClick = viewModel::continueForward,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height),
            enabled = state.canContinue && !state.isSubmitting,
            colors = auleAccentButtonColors(),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = AuleTheme.tokens.onAccent.color,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Text(
                    text = stringResource(
                        when {
                            state.step == RegistrationStep.ACCOUNT -> R.string.register_create
                            else -> R.string.register_continue
                        },
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = AuleGlyph.LOCK.asImageVector(),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(AuleSpacing.md),
            )
            Text(
                text = stringResource(R.string.register_saved),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = AuleSpacing.xs),
            )
        }
    }
}

@Composable
private fun RegistrationProgress(
    state: RegistrationUiState,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val fraction = when {
        state.step == RegistrationStep.CONFIRMATION -> 1f
        state.actionSteps.size <= 1 -> 0f
        else -> state.actionIndex / (state.actionSteps.size - 1).toFloat()
    }
    Column {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primary,
            trackColor = colors.outlineVariant,
        )
        if (state.step != RegistrationStep.CONFIRMATION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(AuleSpacing.lg),
                    )
                    Text(
                        text = stringResource(R.string.register_back),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                        modifier = Modifier.padding(start = AuleSpacing.xs),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(
                        R.string.register_step,
                        state.actionIndex + 1,
                        state.actionSteps.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    onSignIn: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AuleBrandMark(contentDescription = stringResource(R.string.auth_logo))
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WelcomeIcon(AuleGlyph.BUS)
            WelcomeIcon(AuleGlyph.TICKET)
            WelcomeIcon(AuleGlyph.SHIELD)
        }
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Text(
            text = stringResource(R.string.register_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height),
            colors = auleAccentButtonColors(),
        ) {
            Text(text = stringResource(R.string.register_start))
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        Text(
            text = stringResource(R.string.register_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        TextButton(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.register_already))
        }
    }
}

@Composable
private fun WelcomeIcon(glyph: AuleGlyph) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(AuleControl.avatar),
        shape = CircleShape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = glyph.asImageVector(),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(AuleSpacing.sm))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(AuleSpacing.lg))
}

@Composable
private fun ProfilesStep(
    state: RegistrationUiState,
    onToggle: (ProfessionalProfile) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val exclusive = state.draft.orderedProfiles.filter { !it.isCombinable }
    Column {
        StepHeader(
            title = stringResource(R.string.register_profiles_title),
            subtitle = stringResource(R.string.register_profiles_subtitle),
        )
        SIGNUP_PROFILES.forEach { profile ->
            ChoiceCard(
                glyph = profile.glyph,
                label = profile.label(),
                description = profile.description(),
                selected = profile in state.draft.profiles,
                onClick = { onToggle(profile) },
                multiSelect = true,
            )
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
        }
        if (exclusive.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = AuleSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = AuleGlyph.SHIELD.asImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(AuleSpacing.md),
                )
                Text(
                    text = stringResource(
                        R.string.register_exclusive_hint,
                        exclusive.first().label(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = AuleSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun NetworkStep(
    state: RegistrationUiState,
    onQuery: (String) -> Unit,
    onSelectNaolib: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        StepHeader(
            title = stringResource(R.string.register_network_title),
            subtitle = stringResource(R.string.register_network_subtitle),
        )
        OutlinedTextField(
            value = state.networkQuery,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.register_network_search)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.SEARCH.asImageVector(),
                    contentDescription = null,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        if (state.showsNaolib) {
            ChoiceCard(
                glyph = AuleGlyph.PIN,
                label = stringResource(R.string.register_network_naolib),
                description = stringResource(R.string.register_network_naolib_desc),
                selected = state.draft.networkKey == "naolib",
                onClick = onSelectNaolib,
            )
        } else {
            Text(
                text = stringResource(R.string.register_network_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AuleSpacing.lg),
            )
        }
    }
}

@Composable
private fun IdentityStep(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
) {
    Column {
        StepHeader(
            title = stringResource(R.string.register_identity_title),
            subtitle = stringResource(R.string.register_identity_subtitle),
        )
        OutlinedTextField(
            value = state.draft.fullName,
            onValueChange = viewModel::setFullName,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.PersonFullName },
            label = { Text(stringResource(R.string.register_full_name)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.PERSON.asImageVector(),
                    contentDescription = null,
                )
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        OutlinedTextField(
            value = state.draft.employeeId,
            onValueChange = viewModel::setEmployeeId,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.register_employee_id)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.TICKET.asImageVector(),
                    contentDescription = null,
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

@Composable
private fun TransportStep(
    state: RegistrationUiState,
    onSelect: (ProfessionalTransportMode) -> Unit,
) {
    Column {
        StepHeader(
            title = stringResource(R.string.register_transport_title),
            subtitle = stringResource(R.string.register_transport_subtitle),
        )
        ProfessionalTransportMode.entries.forEach { mode ->
            ChoiceCard(
                glyph = mode.glyph,
                label = mode.label(),
                description = mode.description(),
                selected = state.draft.transportMode == mode,
                onClick = { onSelect(mode) },
            )
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
        }
    }
}

@Composable
private fun AccountStep(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var termsFailed by remember { mutableStateOf(false) }
    val score = passwordScore(state.password)
    Column {
        StepHeader(
            title = stringResource(R.string.register_account_title),
            subtitle = stringResource(R.string.register_account_subtitle),
        )
        OutlinedTextField(
            value = state.draft.email,
            onValueChange = viewModel::setEmail,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
            label = { Text(stringResource(R.string.auth_email)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.MAIL.asImageVector(),
                    contentDescription = null,
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.auth_password)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.LOCK.asImageVector(),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                IconButton(onClick = viewModel::toggleShowPassword) {
                    Icon(
                        imageVector = if (state.showPassword) {
                            AuleGlyph.EYE_OFF.asImageVector()
                        } else {
                            AuleGlyph.EYE.asImageVector()
                        },
                        contentDescription = stringResource(
                            if (state.showPassword) {
                                R.string.auth_hide_password
                            } else {
                                R.string.auth_show_password
                            },
                        ),
                        tint = colors.onSurfaceVariant,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            repeat(3) { index ->
                val filled = score >= index + 1
                val color = when {
                    !filled -> colors.outlineVariant
                    score == 1 -> colors.error
                    score == 2 -> colors.tertiary
                    else -> colors.primary
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(PROGRESS_HEIGHT)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::setConfirmPassword,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            label = { Text(stringResource(R.string.register_confirm_password)) },
            leadingIcon = {
                Icon(
                    imageVector = AuleGlyph.LOCK.asImageVector(),
                    contentDescription = null,
                )
            },
            isError = state.passwordMismatch,
            supportingText = if (state.passwordMismatch) {
                { Text(stringResource(R.string.register_password_mismatch)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        TermsRow(
            accepted = state.draft.termsAccepted,
            onToggle = viewModel::toggleTerms,
            onOpenTerms = {
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, TERMS_URL.toUri()),
                    )
                }.isSuccess
                termsFailed = !opened
            },
        )
        if (termsFailed) {
            Spacer(modifier = Modifier.height(AuleSpacing.md))
            AuleBanner(message = stringResource(R.string.register_terms_failed), tone = AuleTone.ALERT)
        }
        val error = when {
            state.missingProfessionalData -> stringResource(R.string.register_error_incomplete)
            state.failure != null -> state.failure.message()
            else -> null
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
            AuleBanner(message = error, tone = AuleTone.ALERT)
        }
    }
}

@Composable
private fun TermsRow(
    accepted: Boolean,
    onToggle: () -> Unit,
    onOpenTerms: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val accept = stringResource(R.string.register_terms_accept)
    val link = stringResource(R.string.register_terms_link)
    val openTerms = stringResource(R.string.register_terms_open)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = accepted,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onToggle()
            },
            modifier = Modifier.semantics {
                contentDescription = "$accept $link"
            },
        )
        Column(modifier = Modifier.padding(top = AuleSpacing.sm)) {
            Text(
                text = accept,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                text = link,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
                modifier = Modifier
                    .clickable(onClick = onOpenTerms)
                    .semantics { contentDescription = openTerms },
            )
        }
    }
}

@Composable
private fun ConfirmationStep(
    state: RegistrationUiState,
    onResend: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val recapKind = stringResource(
        if (state.draft.profiles.size > 1) {
            R.string.register_recap_profiles
        } else {
            R.string.register_recap_profile
        },
    )
    val conductor = stringResource(R.string.register_profile_conducteur)
    val controller = stringResource(R.string.register_profile_controleur)
    val intervention = stringResource(R.string.register_profile_intervention)
    val supervisor = stringResource(R.string.register_profile_maitrise)
    val recapProfiles = state.draft.orderedProfiles.joinToString(" + ") { profile ->
        when (profile) {
            ProfessionalProfile.CONDUCTEUR -> conductor
            ProfessionalProfile.CONTROLEUR -> controller
            ProfessionalProfile.INTERVENTION -> intervention
            ProfessionalProfile.MAITRISE -> supervisor
            ProfessionalProfile.REGULATEUR, ProfessionalProfile.EXPLOITATION -> supervisor
        }
    }
    val recapNetwork = stringResource(R.string.register_recap_network)
    val recapNetworkValue = stringResource(R.string.register_network_naolib)
    val recapMode = stringResource(R.string.register_recap_mode)
    val bus = stringResource(R.string.register_mode_bus)
    val tram = stringResource(R.string.register_mode_tram)
    val bustram = stringResource(R.string.register_mode_bustram)
    val recapModeValue = when (state.draft.transportMode) {
        ProfessionalTransportMode.BUS -> bus
        ProfessionalTransportMode.TRAM -> tram
        ProfessionalTransportMode.BUSTRAM -> bustram
        null -> null
    }
    val recapName = stringResource(R.string.register_recap_name)
    val recapEmployee = stringResource(R.string.register_recap_employee)
    val recap = buildList {
        add(recapKind to recapProfiles)
        add(recapNetwork to recapNetworkValue)
        if (recapModeValue != null) add(recapMode to recapModeValue)
        add(recapName to state.draft.fullName)
        add(recapEmployee to state.draft.employeeId)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WelcomeIcon(AuleGlyph.CHECK)
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        Text(
            text = stringResource(R.string.register_confirm_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(
                defaultElevation = AuleElevation.RESTING.height(AuleTheme.night),
            ),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = AuleSpacing.lg,
                    vertical = AuleSpacing.sm,
                ),
            ) {
                recap.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AuleSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        TextButton(
            onClick = onResend,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isResending,
        ) {
            if (state.isResending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = colors.primary,
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Text(text = stringResource(R.string.register_resend))
            }
        }
        state.notice?.let { notice ->
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            AuleBanner(
                message = stringResource(
                    when (notice) {
                        RegistrationNotice.CONFIRMATION_SENT -> R.string.register_notice_sent
                        RegistrationNotice.RATE_LIMITED -> R.string.register_notice_rate
                        RegistrationNotice.RESEND_FAILED -> R.string.register_notice_failed
                    },
                ),
            )
        }
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height),
            colors = auleAccentButtonColors(),
        ) {
            Text(text = stringResource(R.string.register_sign_in))
        }
    }
}

@Composable
private fun ChoiceCard(
    glyph: AuleGlyph,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    multiSelect: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val view = LocalView.current
    val selectModifier = if (multiSelect) {
        Modifier.toggleable(
            value = selected,
            role = Role.Checkbox,
            onValueChange = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        )
    } else {
        Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            },
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(selectModifier),
        shape = MaterialTheme.shapes.medium,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = colors.primaryContainer)
        } else {
            // `surface` posait une carte blanche dans la carte blanche du
            // formulaire : le choix non retenu disparaissait.
            CardDefaults.cardColors(containerColor = colors.surfaceContainer)
        },
    ) {
        Row(
            modifier = Modifier.padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(AuleControl.avatar),
                shape = CircleShape,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = glyph.asImageVector(filled = selected),
                        contentDescription = null,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AuleSpacing.md),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (multiSelect) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                )
            } else if (selected) {
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
private fun ProfessionalProfile.label(): String = stringResource(
    when (this) {
        ProfessionalProfile.CONDUCTEUR -> R.string.register_profile_conducteur
        ProfessionalProfile.CONTROLEUR -> R.string.register_profile_controleur
        ProfessionalProfile.INTERVENTION -> R.string.register_profile_intervention
        ProfessionalProfile.MAITRISE -> R.string.register_profile_maitrise
        ProfessionalProfile.REGULATEUR, ProfessionalProfile.EXPLOITATION ->
            R.string.register_profile_maitrise
    },
)

@Composable
private fun ProfessionalProfile.description(): String = stringResource(
    when (this) {
        ProfessionalProfile.CONDUCTEUR -> R.string.register_profile_conducteur_desc
        ProfessionalProfile.CONTROLEUR -> R.string.register_profile_controleur_desc
        ProfessionalProfile.INTERVENTION -> R.string.register_profile_intervention_desc
        ProfessionalProfile.MAITRISE -> R.string.register_profile_maitrise_desc
        ProfessionalProfile.REGULATEUR, ProfessionalProfile.EXPLOITATION ->
            R.string.register_profile_maitrise_desc
    },
)

private val ProfessionalProfile.glyph: AuleGlyph
    get() = when (this) {
        ProfessionalProfile.CONDUCTEUR -> AuleGlyph.BUS
        ProfessionalProfile.CONTROLEUR -> AuleGlyph.TICKET
        ProfessionalProfile.INTERVENTION -> AuleGlyph.SHIELD
        ProfessionalProfile.MAITRISE -> AuleGlyph.PERSON
        ProfessionalProfile.REGULATEUR, ProfessionalProfile.EXPLOITATION -> AuleGlyph.PERSON
    }

@Composable
private fun ProfessionalTransportMode.label(): String = stringResource(
    when (this) {
        ProfessionalTransportMode.BUS -> R.string.register_mode_bus
        ProfessionalTransportMode.TRAM -> R.string.register_mode_tram
        ProfessionalTransportMode.BUSTRAM -> R.string.register_mode_bustram
    },
)

@Composable
private fun ProfessionalTransportMode.description(): String = stringResource(
    when (this) {
        ProfessionalTransportMode.BUS -> R.string.register_mode_bus_desc
        ProfessionalTransportMode.TRAM -> R.string.register_mode_tram_desc
        ProfessionalTransportMode.BUSTRAM -> R.string.register_mode_bustram_desc
    },
)

private val ProfessionalTransportMode.glyph: AuleGlyph
    get() = when (this) {
        ProfessionalTransportMode.BUS -> AuleGlyph.BUS
        ProfessionalTransportMode.TRAM -> AuleGlyph.TRAM
        ProfessionalTransportMode.BUSTRAM -> AuleGlyph.HEADING
    }

private val CARD_MAX_WIDTH = 520.dp
private val PROGRESS_HEIGHT = 3.dp
private const val TERMS_URL = "https://www.aule.fr/conditions"
