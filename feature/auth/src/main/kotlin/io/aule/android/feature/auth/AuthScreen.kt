package io.aule.android.feature.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.foundation.AuleHaptic
import io.aule.android.core.designsystem.foundation.auleBottomSystemPadding
import io.aule.android.core.designsystem.foundation.rememberAuleHaptics
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEntryFinished: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreenContent(
        state = state,
        onSignIn = viewModel::signIn,
        onClearFailure = viewModel::clearFailure,
        onRetryBiometric = viewModel::retryBiometricUnlock,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
        onEntryFinished = onEntryFinished,
        modifier = modifier,
    )
}

/** Le contenu conserve la saisie durant la vérification des habilitations. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuthScreenContent(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onClearFailure: () -> Unit,
    onRetryBiometric: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onEntryFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Le mot de passe reste uniquement en mémoire, jamais dans l'état Android sauvegardé.
    var password by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var invalidAttempt by remember { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val haptics = rememberAuleHaptics()
    val emailRequired = stringResource(R.string.auth_email_required)
    val emailInvalid = stringResource(R.string.auth_email_invalid)
    val passwordRequired = stringResource(R.string.auth_password_required)
    val phase = authButtonPhase(state)
    val enabled = phase == AuthButtonPhase.READY

    fun submit() {
        if (!enabled) return
        val trimmed = email.trim()
        emailError = when {
            trimmed.isEmpty() -> emailRequired
            '@' !in trimmed -> emailInvalid
            else -> null
        }
        passwordError = if (password.isEmpty()) passwordRequired else null
        when {
            emailError != null -> {
                invalidAttempt++
                haptics.play(AuleHaptic.ERROR)
                emailFocus.requestFocus()
            }
            passwordError != null -> {
                invalidAttempt++
                haptics.play(AuleHaptic.ERROR)
                passwordFocus.requestFocus()
            }
            else -> {
                keyboard?.hide()
                focus.clearFocus()
                onSignIn(trimmed, password)
            }
        }
    }

    AuleTheme(night = false) {
        val colors = MaterialTheme.colorScheme
        val reduced = reduceMotionEnabled()
        val imeVisible = WindowInsets.isImeVisible
        val exit = remember { Animatable(0f) }
        val currentOnFinished by rememberUpdatedState(onEntryFinished)
        LaunchedEffect(phase) {
            if (phase == AuthButtonPhase.GRANTED) {
                haptics.play(AuleHaptic.SUCCESS)
                if (!reduced) exit.animateTo(1f, tween(620))
                currentOnFinished()
            } else exit.snapTo(0f)
        }
        AuthWebChrome(modifier = modifier, quiet = imeVisible || !enabled) {
            Column(Modifier.graphicsLayer {
                scaleX = 1f - exit.value * 0.025f
                scaleY = 1f - exit.value * 0.025f
                alpha = 1f - exit.value
            }) {
                Column(Modifier.authEnter(600)) {
                    Text(
                        text = stringResource(R.string.auth_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(AuleSpacing.xs))
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(AuleSpacing.lg))
                AnimatedVisibility(visible = state.failure != null) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        state.failure?.let { failure ->
                            AuleBanner(message = failure.message(state.failureNetworkReason), tone = AuleTone.ALERT)
                        }
                        Spacer(Modifier.height(AuleSpacing.lg))
                    }
                }
                if (state.biometricInvalidatedNotice) {
                    AuleBanner(stringResource(R.string.auth_biometric_invalidated), tone = AuleTone.NEUTRAL)
                    Spacer(Modifier.height(AuleSpacing.lg))
                }
                AuthFieldMotion(emailError, invalidAttempt) { motion ->
                    AuthWebField(
                        label = stringResource(R.string.auth_email_label),
                        value = email,
                        onValueChange = { email = it; emailError = null; onClearFailure() },
                        modifier = motion.authEnter(700),
                        fieldModifier = Modifier.authFieldFocus().focusRequester(emailFocus).testTag("auth-email"),
                        enabled = enabled,
                        required = true,
                        requiredLabel = stringResource(R.string.auth_required),
                        error = emailError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                    )
                }
                Spacer(Modifier.height(AuleSpacing.lg))
                AuthFieldMotion(passwordError, invalidAttempt) { motion ->
                    AuthWebField(
                        label = stringResource(R.string.auth_password),
                        value = password,
                        onValueChange = { password = it; passwordError = null; onClearFailure() },
                        modifier = motion.authEnter(780),
                        fieldModifier = Modifier.authFieldFocus().focusRequester(passwordFocus).testTag("auth-password"),
                        enabled = enabled,
                        required = true,
                        requiredLabel = stringResource(R.string.auth_required),
                        error = passwordError,
                        trailingIcon = {
                            IconButton(
                                onClick = { obscure = !obscure; haptics.play(AuleHaptic.SELECTION) },
                                enabled = enabled,
                                modifier = Modifier.testTag("auth-password-visibility"),
                            ) {
                                Crossfade(
                                    targetState = obscure,
                                    animationSpec = if (reduced) snap() else MaterialTheme.motionScheme.fastEffectsSpec(),
                                    label = "password-visibility",
                                ) { hidden ->
                                    Icon(
                                        imageVector = if (hidden) AuleGlyph.EYE.asImageVector() else AuleGlyph.EYE_OFF.asImageVector(),
                                        contentDescription = stringResource(if (hidden) R.string.auth_show_password else R.string.auth_hide_password),
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }
                Box(Modifier.fillMaxWidth().authEnter(1000)) {
                    TextButton(
                        onClick = { onForgotPassword(email.trim()) },
                        enabled = enabled,
                        modifier = Modifier.align(Alignment.CenterEnd).testTag("auth-forgot-password"),
                    ) { Text(stringResource(R.string.auth_forgot_password), style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.height(AuleSpacing.sm))
                Box(Modifier.authEnter(850)) { AuthSubmitButton(phase = phase, onClick = ::submit) }
                if (state.canRetryBiometric) {
                    Spacer(Modifier.height(AuleSpacing.sm))
                    TextButton(
                        onClick = onRetryBiometric,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AuleTouch.minimum).authEnter(1000),
                    ) {
                        Icon(AuleGlyph.FINGERPRINT.asImageVector(), contentDescription = null)
                        Spacer(Modifier.width(AuleSpacing.sm))
                        Text(stringResource(R.string.auth_biometric_retry), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(AuleSpacing.sm))
                AuthDivider(stringResource(R.string.auth_no_account))
                Spacer(Modifier.height(AuleSpacing.md))
                FilledTonalButton(onClick = onCreateAccount, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AuleTouch.minimum).testTag("auth-create-account")) {
                    Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.labelLargeEmphasized)
                }

                Spacer(Modifier.height(AuleSpacing.lg))
                AuthLegalNote()
            }
        }
    }
}

@Composable
private fun Modifier.authFieldFocus(): Modifier {
    var focused by remember { mutableStateOf(false) }
    val reduced = reduceMotionEnabled()
    val amount by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = if (reduced) snap() else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "auth-field-focus",
    )
    val shape = MaterialTheme.shapes.small
    return onFocusChanged { focused = it.isFocused }.graphicsLayer {
        shadowElevation = amount * AuleElevation.floating.toPx()
        this.shape = shape
    }
}

@Composable
private fun AuthLegalNote(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = legalNotice(
            template = stringResource(R.string.auth_legal),
            terms = stringResource(R.string.auth_legal_terms),
            privacy = stringResource(R.string.auth_legal_privacy),
            linkColor = colors.onSurface,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

internal val COLUMN_MAX_WIDTH = 420.dp
