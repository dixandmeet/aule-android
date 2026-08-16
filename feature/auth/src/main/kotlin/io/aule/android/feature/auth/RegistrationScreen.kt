package io.aule.android.feature.auth

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandMark
import io.aule.android.core.designsystem.component.AuleBusyIndicator
import io.aule.android.core.designsystem.component.AuleButton
import io.aule.android.core.designsystem.component.AuleButtonProminence
import io.aule.android.core.designsystem.component.AuleCard
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleIcon
import io.aule.android.core.designsystem.component.AuleIconButton
import io.aule.android.core.designsystem.component.AuleTextField
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
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
        val tokens = AuleTheme.tokens
        AuleAmbientBackground(modifier = modifier) {
            if (!state.isHydrated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    AuleBusyIndicator(color = tokens.accent.color)
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
                    AuleCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
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
    val tokens = AuleTheme.tokens
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        AuleButton(
            title = stringResource(
                when {
                    state.step == RegistrationStep.ACCOUNT && state.isSubmitting ->
                        R.string.register_creating
                    state.step == RegistrationStep.ACCOUNT -> R.string.register_create
                    else -> R.string.register_continue
                },
            ),
            onClick = viewModel::continueForward,
            enabled = state.canContinue && !state.isSubmitting,
            loading = state.isSubmitting,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AuleIcon(
                glyph = AuleGlyph.LOCK,
                tint = tokens.onSurfaceMuted.color,
                size = AuleSpacing.md,
            )
            BasicText(
                text = stringResource(R.string.register_saved),
                style = auleTextStyle(AuleRole.KICKER)
                    .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
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
    val tokens = AuleTheme.tokens
    val fraction = when {
        state.step == RegistrationStep.CONFIRMATION -> 1f
        state.actionSteps.size <= 1 -> 0f
        else -> state.actionIndex / (state.actionSteps.size - 1).toFloat()
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_HEIGHT)
                .background(tokens.hairline.color),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(tokens.accent.color),
            )
        }
        if (state.step != RegistrationStep.CONFIRMATION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = AuleTouch.minimum)
                        .clip(RoundedCornerShape(AuleRadius.sm))
                        .clickable(onClick = onBack)
                        .padding(horizontal = AuleSpacing.xs)
                        .semantics { role = Role.Button },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuleIcon(
                        glyph = AuleGlyph.BACK,
                        tint = tokens.accentOnSurface.color,
                        size = AuleSpacing.lg,
                    )
                    BasicText(
                        text = stringResource(R.string.register_back),
                        style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                            .copy(color = tokens.accentOnSurface.color),
                        modifier = Modifier.padding(start = AuleSpacing.xs),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                BasicText(
                    text = stringResource(
                        R.string.register_step,
                        state.actionIndex + 1,
                        state.actionSteps.size,
                    ),
                    style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                        .copy(color = tokens.onSurfaceMuted.color),
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
    val tokens = AuleTheme.tokens
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
        BasicText(
            text = stringResource(R.string.register_welcome_title),
            style = auleTextStyle(AuleRole.HERO, FontWeight.Bold)
                .copy(color = tokens.onSurface.color, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        BasicText(
            text = stringResource(R.string.register_welcome_subtitle),
            style = auleTextStyle(AuleRole.BODY)
                .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        AuleButton(title = stringResource(R.string.register_start), onClick = onStart)
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        BasicText(
            text = stringResource(R.string.register_hint),
            style = auleTextStyle(AuleRole.KICKER)
                .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        AuleButton(
            title = stringResource(R.string.register_already),
            onClick = onSignIn,
            prominence = AuleButtonProminence.PLAIN,
        )
    }
}

@Composable
private fun WelcomeIcon(glyph: AuleGlyph) {
    val tokens = AuleTheme.tokens
    Box(
        modifier = Modifier
            .size(AuleControl.avatar)
            .clip(CircleShape)
            .background(tokens.accent.color.copy(alpha = AuleAlpha.TINT))
            .border(
                AuleStroke.hairline,
                tokens.accent.color.copy(alpha = AuleAlpha.OUTLINE),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AuleIcon(glyph = glyph, tint = tokens.accentOnSurface.color)
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    val tokens = AuleTheme.tokens
    BasicText(
        text = title,
        style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
            .copy(color = tokens.onSurface.color, textAlign = TextAlign.Center),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(AuleSpacing.sm))
    BasicText(
        text = subtitle,
        style = auleTextStyle(AuleRole.BODY)
            .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(AuleSpacing.lg))
}

@Composable
private fun ProfilesStep(
    state: RegistrationUiState,
    onToggle: (ProfessionalProfile) -> Unit,
) {
    val tokens = AuleTheme.tokens
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
            )
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
        }
        if (exclusive.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = AuleSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                AuleIcon(
                    glyph = AuleGlyph.SHIELD,
                    tint = tokens.onSurfaceMuted.color,
                    size = AuleSpacing.md,
                )
                BasicText(
                    text = stringResource(
                        R.string.register_exclusive_hint,
                        exclusive.first().label(),
                    ),
                    style = auleTextStyle(AuleRole.KICKER)
                        .copy(color = tokens.onSurfaceMuted.color),
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
    Column {
        StepHeader(
            title = stringResource(R.string.register_network_title),
            subtitle = stringResource(R.string.register_network_subtitle),
        )
        AuleTextField(
            label = stringResource(R.string.register_network_search),
            value = state.networkQuery,
            onValueChange = onQuery,
            leading = AuleGlyph.SEARCH,
            imeAction = ImeAction.Done,
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
            BasicText(
                text = stringResource(R.string.register_network_empty),
                style = auleTextStyle(AuleRole.BODY)
                    .copy(
                        color = AuleTheme.tokens.onSurfaceMuted.color,
                        textAlign = TextAlign.Center,
                    ),
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
        AuleTextField(
            label = stringResource(R.string.register_full_name),
            value = state.draft.fullName,
            onValueChange = viewModel::setFullName,
            leading = AuleGlyph.PERSON,
            contentType = ContentType.PersonFullName,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        AuleTextField(
            label = stringResource(R.string.register_employee_id),
            value = state.draft.employeeId,
            onValueChange = viewModel::setEmployeeId,
            leading = AuleGlyph.TICKET,
            imeAction = ImeAction.Done,
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
    val tokens = AuleTheme.tokens
    val context = LocalContext.current
    var termsFailed by remember { mutableStateOf(false) }
    val score = passwordScore(state.password)
    Column {
        StepHeader(
            title = stringResource(R.string.register_account_title),
            subtitle = stringResource(R.string.register_account_subtitle),
        )
        AuleTextField(
            label = stringResource(R.string.auth_email),
            value = state.draft.email,
            onValueChange = viewModel::setEmail,
            leading = AuleGlyph.MAIL,
            keyboardType = KeyboardType.Email,
            contentType = ContentType.EmailAddress,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        AuleTextField(
            label = stringResource(R.string.auth_password),
            value = state.password,
            onValueChange = viewModel::setPassword,
            leading = AuleGlyph.LOCK,
            keyboardType = KeyboardType.Password,
            contentType = ContentType.Password,
            imeAction = ImeAction.Next,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = {
                AuleIconButton(
                    glyph = if (state.showPassword) AuleGlyph.EYE_OFF else AuleGlyph.EYE,
                    contentDescription = stringResource(
                        if (state.showPassword) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                    onClick = viewModel::toggleShowPassword,
                    tint = tokens.onSurfaceMuted.color,
                )
            },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            repeat(3) { index ->
                val filled = score >= index + 1
                val color = when {
                    !filled -> tokens.hairline.color
                    score == 1 -> tokens.alert.color
                    score == 2 -> tokens.delay.color
                    else -> tokens.accent.color
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(PROGRESS_HEIGHT)
                        .clip(RoundedCornerShape(AuleRadius.sm))
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        AuleTextField(
            label = stringResource(R.string.register_confirm_password),
            value = state.confirmPassword,
            onValueChange = viewModel::setConfirmPassword,
            leading = AuleGlyph.LOCK,
            error = if (state.passwordMismatch) {
                stringResource(R.string.register_password_mismatch)
            } else {
                null
            },
            keyboardType = KeyboardType.Password,
            contentType = ContentType.Password,
            imeAction = ImeAction.Done,
            visualTransformation = if (state.showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
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
    val tokens = AuleTheme.tokens
    val view = LocalView.current
    val accept = stringResource(R.string.register_terms_accept)
    val link = stringResource(R.string.register_terms_link)
    val openTerms = stringResource(R.string.register_terms_open)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(AuleTouch.minimum)
                .clip(RoundedCornerShape(AuleRadius.sm))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onToggle()
                }
                .semantics {
                    role = Role.Checkbox
                    contentDescription = "$accept $link"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(AuleControl.check)
                    .clip(RoundedCornerShape(AuleRadius.sm))
                    .background(if (accepted) tokens.accent.color else Color.Transparent)
                    .border(
                        AuleStroke.hairline,
                        if (accepted) tokens.accent.color else tokens.onSurfaceMuted.color,
                        RoundedCornerShape(AuleRadius.sm),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (accepted) {
                    AuleIcon(
                        glyph = AuleGlyph.CHECK,
                        tint = tokens.onAccent.color,
                        size = AuleSpacing.md,
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(top = AuleSpacing.sm)) {
            BasicText(
                text = accept,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.onSurfaceMuted.color),
            )
            BasicText(
                text = link,
                style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                    .copy(color = tokens.accentOnSurface.color),
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
    val tokens = AuleTheme.tokens
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
        Box(
            modifier = Modifier
                .size(AuleControl.avatar)
                .clip(CircleShape)
                .background(tokens.accent.color.copy(alpha = AuleAlpha.TINT))
                .border(
                    AuleStroke.hairline,
                    tokens.accent.color.copy(alpha = AuleAlpha.OUTLINE),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AuleIcon(glyph = AuleGlyph.CHECK, tint = tokens.accentOnSurface.color)
        }
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        BasicText(
            text = stringResource(R.string.register_confirm_title),
            style = auleTextStyle(AuleRole.TITLE, FontWeight.Bold)
                .copy(color = tokens.onSurface.color, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        BasicText(
            text = stringResource(R.string.register_confirm_body),
            style = auleTextStyle(AuleRole.BODY)
                .copy(color = tokens.onSurfaceMuted.color, textAlign = TextAlign.Center),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        AuleCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = AuleElevation.RESTING,
            shape = RoundedCornerShape(AuleRadius.lg),
            contentPadding = PaddingValues(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm),
        ) {
            recap.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AuleSpacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(
                        text = label,
                        style = auleTextStyle(AuleRole.KICKER)
                            .copy(color = tokens.onSurfaceMuted.color),
                        modifier = Modifier.weight(1f),
                    )
                    BasicText(
                        text = value,
                        style = auleTextStyle(AuleRole.KICKER, FontWeight.SemiBold)
                            .copy(color = tokens.onSurface.color, textAlign = TextAlign.End),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        AuleButton(
            title = stringResource(
                if (state.isResending) R.string.register_resending else R.string.register_resend,
            ),
            onClick = onResend,
            enabled = !state.isResending,
            loading = state.isResending,
            prominence = AuleButtonProminence.PLAIN,
        )
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
        AuleButton(title = stringResource(R.string.register_sign_in), onClick = onFinish)
    }
}

@Composable
private fun ChoiceCard(
    glyph: AuleGlyph,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = AuleTheme.tokens
    val shape = RoundedCornerShape(AuleRadius.md)
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) tokens.accent.color.copy(alpha = AuleAlpha.TINT) else tokens.surfaceSolid.color,
            )
            .border(
                if (selected) AuleStroke.emphasis else AuleStroke.hairline,
                if (selected) tokens.accent.color else tokens.hairline.color,
                shape,
            )
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(AuleSpacing.md)
            .semantics { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AuleControl.avatar)
                .clip(RoundedCornerShape(AuleRadius.sm))
                .background(tokens.accent.color.copy(alpha = AuleAlpha.TINT)),
            contentAlignment = Alignment.Center,
        ) {
            AuleIcon(glyph = glyph, tint = tokens.accentOnSurface.color, filled = selected)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = AuleSpacing.md),
        ) {
            BasicText(
                text = label,
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = tokens.onSurface.color),
            )
            BasicText(
                text = description,
                style = auleTextStyle(AuleRole.KICKER)
                    .copy(color = tokens.onSurfaceMuted.color),
            )
        }
        if (selected) {
            AuleIcon(glyph = AuleGlyph.CHECK, tint = tokens.accentOnSurface.color)
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
