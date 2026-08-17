package io.aule.android.feature.auth

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleAmbientBackground
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandMark
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * L'écran de connexion e-mail + mot de passe.
 *
 * Il n'invente rien : fond ambiant, marque, carte, champs, bandeau et bouton
 * viennent tous du design system. Ce qui reste ici est ce qui n'appartient
 * qu'à la connexion — l'ordre des champs, la validation, et ce que fait la
 * touche « Suiv. ».
 *
 * Clavier ouvert, la marque se replie et les respirations se resserrent : sur
 * un S21 en paysage il ne reste que la moitié haute de l'écran, et un titre
 * hors champ vaut un titre absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val emailRequired = stringResource(R.string.auth_email_required)
    val emailInvalid = stringResource(R.string.auth_email_invalid)
    val passwordRequired = stringResource(R.string.auth_password_required)
    val reduceMotion = reduceMotionEnabled()
    var introReady by remember { mutableStateOf(reduceMotion) }
    val intro by animateFloatAsState(
        targetValue = if (introReady) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else AuleMotion.CAMERA_ENTRY_MS),
        label = "auth-intro",
    )

    LaunchedEffect(Unit) { introReady = true }

    fun submit() {
        val trimmed = email.trim()
        emailError = when {
            trimmed.isEmpty() -> emailRequired
            '@' !in trimmed -> emailInvalid
            else -> null
        }
        passwordError = if (password.isEmpty()) passwordRequired else null
        when {
            emailError != null -> emailFocus.requestFocus()
            passwordError != null -> passwordFocus.requestFocus()
            else -> {
                keyboard?.hide()
                focus.clearFocus()
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.signIn(trimmed, password)
            }
        }
    }

    AuleTheme {
        val colors = MaterialTheme.colorScheme
        val imeVisible = WindowInsets.isImeVisible
        val breath = if (imeVisible) AuleSpacing.md else AuleSpacing.xl
        AuleAmbientBackground(modifier = modifier) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
                contentAlignment = if (imeVisible) Alignment.TopCenter else Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = CARD_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = AuleSpacing.xl,
                            vertical = if (imeVisible) AuleSpacing.md else AuleSpacing.xxl,
                        )
                        .graphicsLayer {
                            alpha = intro
                            translationY = if (imeVisible) 0f else (1f - intro) * INTRO_RISE.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!imeVisible) {
                        AuleBrandMark(contentDescription = stringResource(R.string.auth_logo))
                        Spacer(modifier = Modifier.height(AuleSpacing.xl))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = AuleElevation.OVERLAY.height(AuleTheme.night),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = AuleSpacing.xl,
                                top = breath,
                                end = AuleSpacing.xl,
                                bottom = breath,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.auth_title),
                                style = if (imeVisible) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.headlineMedium
                                },
                                fontWeight = FontWeight.Bold,
                                color = colors.onSurface,
                                modifier = Modifier.semantics { heading() },
                            )
                            if (!imeVisible) {
                                Spacer(modifier = Modifier.height(AuleSpacing.sm))
                                Text(
                                    text = stringResource(R.string.auth_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                                Text(
                                    text = stringResource(R.string.auth_network_note),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }

                            Spacer(modifier = Modifier.height(breath))
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    emailError = null
                                    viewModel.clearFailure()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(emailFocus),
                                enabled = !state.isSubmitting,
                                label = { Text(stringResource(R.string.auth_email)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AuleGlyph.MAIL.asImageVector(),
                                        contentDescription = null,
                                    )
                                },
                                isError = emailError != null,
                                supportingText = emailError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focus.moveFocus(FocusDirection.Down) },
                                ),
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )
                            Spacer(modifier = Modifier.height(AuleSpacing.md))
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    passwordError = null
                                    viewModel.clearFailure()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(passwordFocus),
                                enabled = !state.isSubmitting,
                                label = { Text(stringResource(R.string.auth_password)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = AuleGlyph.LOCK.asImageVector(),
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { obscure = !obscure },
                                        enabled = !state.isSubmitting,
                                    ) {
                                        Icon(
                                            imageVector = if (obscure) {
                                                AuleGlyph.EYE.asImageVector()
                                            } else {
                                                AuleGlyph.EYE_OFF.asImageVector()
                                            },
                                            contentDescription = stringResource(
                                                if (obscure) {
                                                    R.string.auth_show_password
                                                } else {
                                                    R.string.auth_hide_password
                                                },
                                            ),
                                            tint = colors.onSurfaceVariant,
                                        )
                                    }
                                },
                                isError = passwordError != null,
                                supportingText = passwordError?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (!state.isSubmitting) submit() },
                                ),
                                visualTransformation = if (obscure) {
                                    PasswordVisualTransformation()
                                } else {
                                    VisualTransformation.None
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                            )

                            val failure = state.failure
                            if (failure != null) {
                                Spacer(modifier = Modifier.height(AuleSpacing.lg))
                                AuleBanner(message = failure.message(), tone = AuleTone.ALERT)
                            }

                            Spacer(
                                modifier = Modifier.height(
                                    if (imeVisible) AuleSpacing.md else AuleSpacing.xl,
                                ),
                            )
                            Button(
                                onClick = { if (!state.isSubmitting) submit() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = AuleControl.height),
                                enabled = !state.isSubmitting,
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
                                        stringResource(
                                            if (state.isSubmitting) {
                                                R.string.auth_submitting
                                            } else {
                                                R.string.auth_submit
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }

                    if (!imeVisible) {
                        Spacer(modifier = Modifier.height(AuleSpacing.md))
                        TextButton(
                            onClick = onCreateAccount,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.auth_create_account))
                        }
                        Spacer(modifier = Modifier.height(AuleSpacing.xl))
                        val secure = colors.onSurfaceVariant
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = AuleSpacing.lg),
                        ) {
                            Icon(
                                imageVector = AuleGlyph.SHIELD.asImageVector(),
                                contentDescription = null,
                                tint = secure,
                                modifier = Modifier.size(AuleSpacing.lg),
                            )
                            Text(
                                text = stringResource(R.string.auth_secure),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = secure,
                                maxLines = 2,
                                modifier = Modifier.padding(start = AuleSpacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Au-delà, les champs s'étirent en bandes et l'œil perd le début de la ligne.
 * La carte cesse donc de grandir bien avant le bord d'une tablette.
 */
private val CARD_MAX_WIDTH = 420.dp

/** Le contenu monte de quelques points en apparaissant : il arrive, il ne surgit pas. */
private val INTRO_RISE = 18.dp
