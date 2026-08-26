package io.aule.android.feature.auth

import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.AuleTypeface
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleNetworkBackdrop
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.AuleWordmark
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * L'écran de connexion e-mail + mot de passe, dans la charte publique d'Aule.
 *
 * C'est la première image du produit, et la seule qui n'affiche aucune donnée :
 * ni tournée, ni horaire, rien qu'on doive lire en vingt secondes. Elle a donc
 * un seul travail, et il est le même sur les trois surfaces — dire qu'on est
 * chez Aule avant qu'un mot ait été lu.
 *
 * Ce que cet écran porte du web (`SpacePro/app/(auth)/login`, lui-même porté du
 * dashboard, tous deux alignés sur aule.fr) :
 *
 * - l'**ambiance de nuit, toujours**. Le web ne suit pas le thème du système
 *   sur ses écrans d'accueil : la maison est sombre, et une porte d'entrée qui
 *   change de couleur selon l'heure du visiteur n'est plus une identité. Le
 *   reste de l'application, lui, garde le choix d'apparence ;
 * - la **police d'affichage**, Space Grotesk, posée sur toute la coquille et
 *   pas seulement sur le titre ;
 * - le **fond de réseau**, qui remplace le lavis d'ambiance ordinaire ;
 * - la **mise en page en colonne alignée à gauche** : marque, titre, l'autre
 *   chemin, puis le formulaire. Plus de carte.
 *
 * ## Pourquoi la carte disparaît
 *
 * Elle répondait à une vraie question — détacher le formulaire du fond — mais
 * elle en posait une autre : un écran qui n'a qu'un bloc n'a pas besoin de le
 * cerner. Le web ne cerne rien ; il remplit les **champs** (`bg-card`) et laisse
 * le reste à même le fond. Le résultat tient en une phrase : ce qui a un cadre
 * est ce dans quoi on écrit, et rien d'autre. La carte, elle, cadrait aussi le
 * bouton, le lien d'oubli et l'air entre les deux.
 *
 * ## L'alignement à gauche
 *
 * Le web aligne tout à gauche, et ce n'est pas un goût : un formulaire se lit
 * en colonne, et un titre centré au-dessus de libellés alignés à gauche crée
 * deux axes de lecture pour un seul écran. Le centrage tenait tant que l'écran
 * n'était qu'une marque et deux champs sans libellé ; avec des libellés posés
 * au-dessus des champs, il ne tient plus.
 *
 * ## L'ordre d'arrivée
 *
 * Quatre blocs entrent en cascade, et l'ordre **est** la hiérarchie : la marque,
 * le titre et son issue, le formulaire, la mention légale. Les ressorts
 * d'entrée s'interrompent et repartent d'où ils sont — ce que ne fait pas un
 * fondu de durée fixe, qu'il faut attendre.
 *
 * ## Clavier ouvert
 *
 * La marque et la mention légale se retirent, les respirations se resserrent,
 * le titre passe au cran le plus compact : sur un S21, clavier déployé, il ne
 * reste que la moitié haute de l'écran, et un titre hors champ vaut un titre
 * absent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
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

    AuleTheme(night = true, typeface = AuleTypeface.BRAND) {
        val imeVisible = WindowInsets.isImeVisible
        val submitting = state.isSubmitting
        val required = stringResource(R.string.auth_required)

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
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = AuleSpacing.xl,
                            vertical = if (imeVisible) AuleSpacing.md else AuleSpacing.xxl,
                        ),
                ) {
                    if (!imeVisible) {
                        AuleWordmark(
                            name = stringResource(R.string.auth_brand),
                            kicker = stringResource(R.string.auth_workspace),
                            contentDescription = stringResource(R.string.auth_logo),
                            modifier = Modifier.auleEnter(index = 0),
                        )
                        Spacer(modifier = Modifier.height(AuleSpacing.xxl))
                    }

                    AuthHeading(
                        compact = imeVisible,
                        submitting = submitting,
                        onCreateAccount = onCreateAccount,
                        modifier = Modifier.auleEnter(index = 1),
                    )

                    Spacer(
                        modifier = Modifier.height(
                            if (imeVisible) AuleSpacing.lg else AuleSpacing.xl,
                        ),
                    )

                    Column(modifier = Modifier.auleEnter(index = 2)) {
                        val failure = state.failure
                        if (failure != null) {
                            AuleBanner(message = failure.message(), tone = AuleTone.ALERT)
                            Spacer(modifier = Modifier.height(AuleSpacing.lg))
                        }

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
                            requiredLabel = required,
                            placeholder = stringResource(R.string.auth_email_placeholder),
                            error = emailError,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focus.moveFocus(FocusDirection.Down) },
                            ),
                        )

                        Spacer(modifier = Modifier.height(AuleSpacing.lg))

                        AuleFormField(
                            label = stringResource(R.string.auth_password),
                            value = password,
                            onValueChange = {
                                password = it
                                passwordError = null
                                viewModel.clearFailure()
                            },
                            fieldModifier = Modifier.focusRequester(passwordFocus),
                            enabled = !submitting,
                            required = true,
                            requiredLabel = required,
                            error = passwordError,
                            trailingIcon = {
                                RevealToggle(
                                    obscure = obscure,
                                    enabled = !submitting,
                                    onToggle = { obscure = !obscure },
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { if (!submitting) submit() },
                            ),
                            visualTransformation = if (obscure) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                        )

                        // À droite, sous le champ qu'elle concerne, et au-dessus
                        // de l'action : c'est l'ordre du web, et c'est le seul
                        // qui place l'issue au moment où l'on découvre qu'on en
                        // a besoin — juste après avoir buté sur le mot de passe.
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { onForgotPassword(email.trim()) },
                                enabled = !submitting,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            ) {
                                Text(
                                    text = stringResource(R.string.auth_forgot_password),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AuleSpacing.sm))
                        AuthSubmit(
                            submitting = submitting,
                            onSubmit = { if (!submitting) submit() },
                        )
                    }

                    if (!imeVisible) {
                        Spacer(modifier = Modifier.height(AuleSpacing.xl))
                        AuthLegalNote(modifier = Modifier.auleEnter(index = 3))
                    }
                }
            }
        }
    }
}

/**
 * L'en-tête : ce que l'écran est, puis l'autre chemin qu'on peut prendre.
 *
 * Le web pose la création de compte **sous le titre**, en une phrase, et non en
 * pied d'écran. C'est mieux placé qu'il n'y paraît : celui qui n'a pas de compte
 * doit l'apprendre avant d'avoir rempli deux champs pour rien, pas après.
 *
 * @param compact vrai quand le clavier occupe l'écran. Le titre descend alors au
 *   cran le plus court et l'autre chemin disparaît : ce qui reste doit tenir
 *   au-dessus du premier champ.
 */
@Composable
private fun AuthHeading(
    compact: Boolean,
    submitting: Boolean,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.auth_title),
            style = if (compact) {
                MaterialTheme.typography.titleLargeEmphasized
            } else {
                MaterialTheme.typography.headlineMediumEmphasized
            },
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (!compact) {
            Spacer(modifier = Modifier.height(AuleSpacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.auth_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                // Un bouton et non un lien dans la phrase : au pouce, une cible
                // de quarante-huit points vaut mieux qu'un mot souligné, et la
                // charte du dépôt tient ce plancher partout ailleurs.
                TextButton(onClick = onCreateAccount, enabled = !submitting) {
                    Text(
                        text = stringResource(R.string.auth_create_account),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/**
 * La bascule d'affichage du mot de passe.
 *
 * Elle vit dans le champ, comme sur le web, et elle porte son propre libellé
 * accessible — c'est le seul contrôle de l'écran dont l'icône change de sens
 * selon l'état, et donc le seul dont le nom doit changer avec elle.
 */
@Composable
private fun RevealToggle(
    obscure: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle, enabled = enabled) {
        Icon(
            imageVector = if (obscure) {
                AuleGlyph.EYE.asImageVector()
            } else {
                AuleGlyph.EYE_OFF.asImageVector()
            },
            contentDescription = stringResource(
                if (obscure) R.string.auth_show_password else R.string.auth_hide_password,
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * L'action principale, et la seule surface de marque de l'écran.
 *
 * Le web pose ici un aplat de `primary`. On garde la surface de marque, qui est
 * l'équivalent Aule du même bouton : les deux mêmes tons en diagonale, le reflet
 * sur le tiers haut, et l'ombre **teintée** — une lueur d'accent qui désigne
 * l'action au lieu de la salir de noir. C'est le même teal, éclairé.
 *
 * ## Pendant l'envoi
 *
 * La roue dit que ça tourne, le mot dit quoi. Et la surface **cesse d'être
 * actionnable** : `AuleBrandSurface` n'a pas de paramètre `enabled`, mais son
 * `onClick` est nullable, et c'est la même chose — sans lui, la surface ne pose
 * plus de zone cliquable du tout. Un `onClick` conservé pendant l'envoi
 * laisserait à TalkBack une action « activer » sur une action déjà partie.
 *
 * Le rôle, lui, est écrit à la main : il venait de `Button`, qui le pose sur sa
 * surface ; `Surface` seule ne le pose pas, et un lecteur d'écran annoncerait
 * un texte là où il y a une commande.
 */
@Composable
private fun AuthSubmit(
    submitting: Boolean,
    onSubmit: () -> Unit,
) {
    AuleBrandSurface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                if (submitting) disabled()
            },
        shape = MaterialTheme.shapes.small,
        onClick = if (submitting) null else onSubmit,
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
                text = stringResource(
                    if (submitting) R.string.auth_submitting else R.string.auth_submit,
                ),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
    }
}

/**
 * La mention légale, en pied d'écran.
 *
 * Elle remplace le « connexion sécurisée » qui vivait ici. Les deux tiennent la
 * même place et n'ont pas la même valeur : l'un rassurait sans rien engager,
 * l'autre est ce que le web affiche parce qu'il le doit — se connecter, c'est
 * accepter des conditions, et il faut pouvoir les lire avant.
 *
 * Les deux textes s'ouvrent dans le navigateur : ils vivent sur `aule.fr`, ils
 * changent sans l'application, et une copie embarquée serait périmée à la
 * première mise à jour des conditions.
 */
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

/**
 * Au-delà, les champs s'étirent en bandes et l'œil perd le début de la ligne.
 * La colonne cesse donc de grandir bien avant le bord d'une tablette — c'est la
 * `max-w-[25rem]` du web, à quelques points près.
 *
 * L'inscription la partage : les deux écrans sont la même colonne, et deux
 * largeurs voisines se verraient au passage de l'un à l'autre.
 */
internal val COLUMN_MAX_WIDTH = 420.dp
