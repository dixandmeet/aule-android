package io.aule.android.feature.auth

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.AuleTypeface
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleNetworkBackdrop
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * « J'ai oublié mon mot de passe » — la demande, puis l'accusé.
 *
 * Deux temps, comme le web et comme l'iOS. Le second n'annonce **pas** « c'est
 * envoyé » mais « si un compte existe » : le serveur répond pareil pour une
 * adresse inconnue, et c'est ce qui empêche de découvrir qui est inscrit.
 * Promettre un e-mail qui n'arrivera jamais ferait attendre pour rien.
 *
 * L'écran emprunte tout son décor à [AuthScreen] — fond ambiant, carte, surface
 * de marque, bandeau. Il en est la suite, pas un ailleurs : changer de décor
 * pour deux champs donnerait l'impression d'avoir quitté l'application au
 * moment précis où l'on doute déjà de son compte.
 *
 * @param initialEmail ce que la connexion avait déjà dans son champ. Qui clique
 *   « mot de passe oublié » vient d'essayer de se connecter : le retaper serait
 *   une punition.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialEmail: String = "",
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf(initialEmail) }
    var emailError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val emailFocus = remember { FocusRequester() }
    val emailRequired = stringResource(R.string.auth_email_required)
    val emailInvalid = stringResource(R.string.auth_email_invalid)
    val submitting = state.isSubmitting
    val sentTo = state.recoverySentTo

    fun submit() {
        val trimmed = email.trim()
        emailError = when {
            trimmed.isEmpty() -> emailRequired
            '@' !in trimmed -> emailInvalid
            else -> null
        }
        if (emailError != null) {
            emailFocus.requestFocus()
            return
        }
        keyboard?.hide()
        focus.clearFocus()
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        viewModel.sendPasswordRecovery(trimmed)
    }

    RecoveryScaffold(modifier = modifier) { imeVisible ->
        if (sentTo == null) {
            RecoveryHeading(
                title = stringResource(R.string.recovery_title),
                subtitle = stringResource(R.string.recovery_subtitle),
                compact = imeVisible,
                modifier = Modifier.auleEnter(index = 0),
            )

            Spacer(modifier = Modifier.height(if (imeVisible) AuleSpacing.md else AuleSpacing.xl))

            RecoveryCard(
                imeVisible = imeVisible,
                modifier = Modifier.auleEnter(index = 1),
            ) {
                AuleFormField(
                    label = stringResource(R.string.auth_email_label),
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                        viewModel.clearFailure()
                    },
                    fieldModifier = Modifier.focusRequester(emailFocus),
                    enabled = !submitting,
                    required = true,
                    requiredLabel = stringResource(R.string.auth_required),
                    placeholder = stringResource(R.string.auth_email_placeholder),
                    error = emailError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (!submitting) submit() }),
                )

                RecoveryFailure(state.failure)

                Spacer(
                    modifier = Modifier.height(
                        if (imeVisible) AuleSpacing.md else AuleSpacing.xl,
                    ),
                )
                RecoveryAction(
                    label = stringResource(R.string.recovery_submit),
                    busyLabel = stringResource(R.string.recovery_submitting),
                    submitting = submitting,
                    onSubmit = { if (!submitting) submit() },
                )
            }
        } else {
            RecoverySentContent(
                address = sentTo,
                compact = imeVisible,
                submitting = submitting,
                failure = state.failure,
                onResend = { if (!submitting) viewModel.sendPasswordRecovery(sentTo) },
            )
        }

        if (!imeVisible) {
            Spacer(modifier = Modifier.height(AuleSpacing.md))
            TextButton(
                onClick = {
                    viewModel.clearRecovery()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .auleEnter(index = 3),
            ) {
                Text(
                    text = stringResource(R.string.recovery_back),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/**
 * L'accusé d'envoi. Le pictogramme et le titre disent ce qui vient de se passer ;
 * la phrase, elle, ne dépasse pas ce que le serveur a réellement affirmé.
 */
@Composable
private fun RecoverySentContent(
    address: String,
    compact: Boolean,
    submitting: Boolean,
    failure: io.aule.android.core.model.AuthFailureKind?,
    onResend: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Tout à gauche, y compris le pictogramme : c'est la forme de l'accusé
    // d'envoi du web (`signup-form.tsx`, écran « Vérifiez vos e-mails »), et
    // c'est la seule qui s'aligne sur le titre juste en dessous.
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!compact) {
            Icon(
                imageVector = Icons.Outlined.MarkEmailRead,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier
                    .size(RECOVERY_MARK)
                    .auleEnter(index = 0),
            )
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
        }
        RecoveryHeading(
            title = stringResource(R.string.recovery_sent_title),
            subtitle = stringResource(R.string.recovery_sent_body, address),
            compact = compact,
            modifier = Modifier.auleEnter(index = 1),
        )
        if (!compact) {
            Spacer(modifier = Modifier.height(AuleSpacing.md))
            Text(
                text = stringResource(R.string.recovery_sent_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.auleEnter(index = 2),
            )
        }

        if (failure != null) {
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
            AuleBanner(message = failure.message(), tone = AuleTone.ALERT)
        }

        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        // Le renvoi est une action **secondaire** : le lien est déjà parti, et
        // le proposer en surface de marque inviterait à le redemander avant même
        // d'avoir regardé sa boîte — au prix d'un plafond de cadence GoTrue.
        TextButton(
            onClick = onResend,
            enabled = !submitting,
            modifier = Modifier
                .fillMaxWidth()
                .auleEnter(index = 3),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    strokeWidth = AuleStroke.glyph,
                )
                Spacer(modifier = Modifier.height(AuleSpacing.sm))
            }
            Text(
                text = stringResource(
                    if (submitting) R.string.recovery_submitting else R.string.recovery_resend,
                ),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/**
 * Le nouveau mot de passe, derrière un lien de réinitialisation.
 *
 * Cet écran n'existe **que** derrière [AuthUiState.isResettingPassword] : la
 * session ouverte par le lien ne donne accès à rien d'autre. On y arrive donc
 * sans l'avoir demandé — c'est le lien qui a amené ici — d'où le titre, qui
 * rappelle ce qui vient de se passer.
 *
 * Les deux règles s'affichent en clair et se cochent à la frappe, plutôt que de
 * n'apparaître qu'en rouge après un refus : ce qu'on demande d'un mot de passe
 * se dit avant qu'il soit tapé, pas après.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatePasswordScreen(
    viewModel: AuthViewModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var obscure by remember { mutableStateOf(true) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val submitting = state.isSubmitting

    val longEnough = password.length >= MIN_PASSWORD_LENGTH
    val matches = password.isNotEmpty() && password == confirmation
    val canSubmit = longEnough && matches && !submitting

    fun submit() {
        if (!canSubmit) return
        keyboard?.hide()
        focus.clearFocus()
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        viewModel.updatePassword(password)
    }

    RecoveryScaffold(modifier = modifier) { imeVisible ->
        if (!imeVisible) {
            Icon(
                imageVector = Icons.Outlined.LockReset,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(RECOVERY_MARK)
                    .auleEnter(index = 0),
            )
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
        }

        RecoveryHeading(
            title = stringResource(R.string.recovery_new_title),
            subtitle = stringResource(R.string.recovery_new_subtitle),
            compact = imeVisible,
            modifier = Modifier.auleEnter(index = 1),
        )

        Spacer(modifier = Modifier.height(if (imeVisible) AuleSpacing.md else AuleSpacing.xl))

        RecoveryCard(
            imeVisible = imeVisible,
            modifier = Modifier.auleEnter(index = 2),
        ) {
            AuleFormField(
                label = stringResource(R.string.recovery_new_password),
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearFailure()
                },
                enabled = !submitting,
                required = true,
                requiredLabel = stringResource(R.string.auth_required),
                placeholder = stringResource(R.string.recovery_new_password_hint),
                trailingIcon = {
                    IconButton(onClick = { obscure = !obscure }, enabled = !submitting) {
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) },
                ),
                visualTransformation = if (obscure) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )

            Spacer(modifier = Modifier.height(AuleSpacing.lg))

            AuleFormField(
                label = stringResource(R.string.recovery_confirmation),
                value = confirmation,
                onValueChange = {
                    confirmation = it
                    viewModel.clearFailure()
                },
                enabled = !submitting,
                required = true,
                requiredLabel = stringResource(R.string.auth_required),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                visualTransformation = if (obscure) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )

            Spacer(modifier = Modifier.height(AuleSpacing.lg))
            RecoveryRule(
                text = stringResource(R.string.recovery_rule_length),
                met = longEnough,
            )
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            RecoveryRule(
                text = stringResource(R.string.recovery_rule_match),
                met = matches,
            )

            RecoveryFailure(state.failure)

            Spacer(
                modifier = Modifier.height(if (imeVisible) AuleSpacing.md else AuleSpacing.xl),
            )
            RecoveryAction(
                label = stringResource(R.string.recovery_save),
                busyLabel = stringResource(R.string.recovery_saving),
                submitting = submitting,
                enabled = canSubmit,
                onSubmit = ::submit,
            )
        }

        if (!imeVisible) {
            Spacer(modifier = Modifier.height(AuleSpacing.md))
            // Quitter ici **ferme la session** : celle du lien ne sert qu'à cet
            // écran, et la laisser ouverte donnerait un compte à demi identifié
            // dont plus personne ne saurait quoi faire.
            TextButton(
                onClick = onCancel,
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .auleEnter(index = 3),
            ) {
                Text(
                    text = stringResource(R.string.recovery_cancel),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/**
 * Le décor commun aux deux temps : fond ambiant, colonne centrée, largeur
 * bornée, respiration qui se resserre quand le clavier monte.
 *
 * C'est celui de [AuthScreen], à la marque près — elle n'est pas reprise ici.
 * Ces écrans arrivent **après** la première image : rappeler le logo à chaque
 * étape ferait reculer d'un cran quelqu'un qui avance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecoveryScaffold(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(imeVisible: Boolean) -> Unit,
) {
    AuleTheme(night = true, typeface = AuleTypeface.BRAND) {
        val imeVisible = WindowInsets.isImeVisible
        AuleNetworkBackdrop(
            modifier = modifier,
            contentAlignment = if (imeVisible) Alignment.TopCenter else Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding(),
                contentAlignment = if (imeVisible) Alignment.TopCenter else Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = RECOVERY_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = AuleSpacing.xl,
                            vertical = if (imeVisible) AuleSpacing.md else AuleSpacing.xxl,
                        ),
                ) {
                    content(imeVisible)
                }
            }
        }
    }
}

/** Le titre et sa phrase, hors de la carte — voir `AuthHeading`. */
@Composable
private fun RecoveryHeading(
    title: String,
    subtitle: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = if (compact) {
                MaterialTheme.typography.titleLargeEmphasized
            } else {
                MaterialTheme.typography.headlineMediumEmphasized
            },
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (!compact) {
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le bloc de saisie — ce qui reste de la carte qui vivait ici.
 *
 * La connexion a perdu la sienne en passant à la charte du web ; celui-ci la
 * garderait qu'on verrait la couture au premier tap sur « Mot de passe oublié
 * ? ». Ne reste que ce que la carte faisait vraiment : tenir les champs
 * ensemble et respirer moins quand le clavier monte.
 */
@Composable
private fun RecoveryCard(
    imeVisible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (imeVisible) AuleSpacing.xs else AuleSpacing.sm),
        content = content,
    )
}

/**
 * Une règle de mot de passe, cochée ou non.
 *
 * La pastille est décorative : son état est déjà dans la phrase pour qui écoute
 * l'écran, et l'annoncer deux fois ferait lire « coché coché ».
 */
@Composable
private fun RecoveryRule(text: String, met: Boolean) {
    val colors = MaterialTheme.colorScheme
    val glyph: ImageVector = if (met) {
        Icons.Filled.CheckCircle
    } else {
        Icons.Outlined.RadioButtonUnchecked
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        Icon(
            imageVector = glyph,
            contentDescription = null,
            tint = if (met) colors.primary else colors.outline,
            modifier = Modifier
                .size(RULE_GLYPH)
                .clearAndSetSemantics { },
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (met) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/**
 * Le bandeau de refus, dans la carte.
 *
 * Il apparaît et disparaît en fondu plutôt qu'en poussant la colonne d'un coup :
 * sous les doigts, un bouton qui descend de quarante points au moment où on le
 * vise se fait manquer.
 */
@Composable
private fun RecoveryFailure(failure: io.aule.android.core.model.AuthFailureKind?) {
    AnimatedVisibility(
        visible = failure != null,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column {
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
            AuleBanner(
                message = failure?.message().orEmpty(),
                tone = AuleTone.ALERT,
            )
        }
    }
}

/** L'action principale : la seule surface de marque de l'écran — voir `AuthSubmit`. */
@Composable
private fun RecoveryAction(
    label: String,
    busyLabel: String,
    submitting: Boolean,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
) {
    val actionable = enabled && !submitting
    AuleBrandSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                if (!actionable) disabled()
            },
        // Le cran des champs, et non celui des volets : depuis le passage à la
        // charte du web, l'action et la boîte de saisie partagent un rayon —
        // deux arrondis voisins sur un même écran se lisent comme une erreur.
        shape = MaterialTheme.shapes.small,
        onClick = if (actionable) onSubmit else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height)
                .padding(horizontal = AuleSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(
                AuleSpacing.md,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = AuleTheme.tokens.onAccent.color,
                    strokeWidth = AuleStroke.glyph,
                )
            }
            Text(
                text = if (submitting) busyLabel else label,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

/** La même borne que la carte de connexion : au-delà, les champs s'étirent en bandes. */
private val RECOVERY_MAX_WIDTH = 420.dp

/**
 * Le pictogramme de tête. Il est seul en haut d'un écran centré : à la taille
 * d'une icône de barre, il se lirait comme une puce oubliée.
 */
private val RECOVERY_MARK = 44.dp

/** La pastille d'une règle, contre un texte de 12 — sous la grille de 24. */
private val RULE_GLYPH = 18.dp

/**
 * Le minimum que GoTrue applique côté serveur, et que l'écran annonce avant
 * d'envoyer. Le refuser ici évite un aller-retour pour une règle connue.
 */
private const val MIN_PASSWORD_LENGTH = 8
