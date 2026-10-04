package io.aule.android.feature.auth

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleNetworkEmblem
import io.aule.android.core.designsystem.component.AuleShape
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.model.OAuthProvider
import io.aule.android.core.designsystem.component.auleFieldColors
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.NETWORK_SEARCH_FROM
import io.aule.android.core.model.ProNetwork
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode
import io.aule.android.core.model.SIGNUP_PROFILES
import kotlinx.coroutines.CancellationException

/** Inscription professionnelle : présentation web, validations et brouillon natifs. */
@Composable
fun RegistrationScreen(
    viewModel: RegistrationViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // L'ouverture de l'onglet vit ici, et non dans l'étape qui l'a demandée :
    // l'étape est le contenu d'un `AnimatedContent`, qui la démonte et la
    // remonte au gré des transitions. Un effet accroché là-dedans peut être
    // annulé avant d'avoir lancé quoi que ce soit — ou rejoué, ce qui est pire.
    LaunchedEffect(state.oauthUrl) {
        val url = state.oauthUrl ?: return@LaunchedEffect
        if (openOAuthTab(context, url)) viewModel.consumeOAuthUrl() else viewModel.oauthBrowserMissing()
    }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { }
            viewModel.back(onClose)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    AuleTheme(night = false) {
        val colors = MaterialTheme.colorScheme
        val motion = MaterialTheme.motionScheme
        val reduceMotion = reduceMotionEnabled()
        val slide = motion.defaultSpatialSpec<IntOffset>()
        val fade = motion.defaultEffectsSpec<Float>()
        val resize = motion.defaultSpatialSpec<IntSize>()
        val slidePixels = with(LocalDensity.current) { RegistrationLayout.stepSlide.roundToPx() }
        Box(modifier.fillMaxSize().background(colors.surface)) {
            if (!state.isHydrated) {
                Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            } else {
                RegistrationChrome(
                    state = state,
                    onClose = onClose,
                    onBack = { viewModel.back(onClose) },
                    onContinue = viewModel::continueForward,
                ) {
                    AnimatedContent(
                        targetState = state.step,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            if (reduceMotion) {
                                (EnterTransition.None togetherWith ExitTransition.None).using(null)
                            } else {
                                val towards = if (targetState.ordinal >= initialState.ordinal) {
                                    AnimatedContentTransitionScope.SlideDirection.Start
                                } else AnimatedContentTransitionScope.SlideDirection.End
                                val enter = slideIntoContainer(towards, slide) { slidePixels } + fadeIn(fade)
                                val exit = slideOutOfContainer(towards, slide) { slidePixels } + fadeOut(fade)
                                (enter togetherWith exit).using(SizeTransform(clip = true) { _, _ -> resize })
                            }
                        },
                        label = "register-step",
                    ) { step ->
                        when (step) {
                            RegistrationStep.WELCOME -> RegistrationWelcome(onSignIn = onClose)
                            RegistrationStep.PROFILE -> ProfilesStep(state, viewModel::toggleProfile)
                            RegistrationStep.NETWORK -> NetworkStep(state, viewModel::setNetworkQuery, viewModel::selectNetwork)
                            RegistrationStep.IDENTITY -> IdentityStep(state, viewModel::setFullName, viewModel::setEmployeeId)
                            RegistrationStep.TRANSPORT_MODE -> TransportStep(state, viewModel::setTransportMode)
                            RegistrationStep.ACCOUNT -> AccountStep(state, viewModel)
                            RegistrationStep.CONFIRMATION -> ConfirmationStep(state, viewModel::resendConfirmation) { viewModel.finish(onClose) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Le titre d'une étape et sa phrase d'explication.
 *
 * `headlineSmallEmphasized` : le slot appuyé de Material 3 Expressive. Même
 * taille, même interligne, même boîte que `headlineSmall` — donc rien ne bouge
 * dans la mise en page — mais la graisse monte d'un cran, jusqu'au `SemiBold`
 * de l'échelle appuyée. Le titre portait jusqu'ici `titleMedium` en gras : seize points, la
 * taille du texte courant, ce qui donnait un formulaire sans tête. Un écran qui
 * pose une question doit d'abord se lire comme une question.
 *
 * Pas `titleLargeEmphasized`, qui porte le rôle `DATA` et ses chiffres à chasse
 * fixe : on n'écrit pas un intitulé avec les chiffres d'un tableau d'affichage.
 */
@Composable
private fun StepHeader(title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
    Spacer(modifier = Modifier.height(AuleSpacing.sm))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(AuleSpacing.xl))
}

@Composable
internal fun ProfilesStep(
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
        SIGNUP_PROFILES.forEachIndexed { index, profile ->
            ChoiceCard(
                glyph = profile.glyph,
                label = profile.label(),
                description = profile.description(),
                selected = profile in state.draft.profiles,
                onClick = { onToggle(profile) },
                modifier = Modifier.auleEnter(index = index),
                multiSelect = true,
                pastille = registrationProfileShape(profile),
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

/**
 * L'étape du réseau.
 *
 * ## Le champ de recherche qui cherchait dans une liste d'un
 *
 * Il était posé là par symétrie avec l'onboarding web, qui a des dizaines de
 * réseaux. Ici, il demandait « lequel ? » au-dessus d'une réponse unique, et
 * repoussait cette réponse d'une hauteur de champ vers le bas de l'écran. Il
 * ne revient qu'au-delà de [NETWORK_SEARCH_FROM] entrées — c'est le catalogue
 * qui décide, pas la main, et le jour où il s'ouvre personne n'aura à y penser.
 *
 * ## Le vide, et ce qu'on met dedans
 *
 * Une seule carte de choix suivie du bouton laissait les deux tiers bas de
 * l'écran nus. Ce vide était lu comme un chargement : *il en manque, elles
 * arrivent*. La note de fin dit ce qu'il en est réellement — la liste est
 * courte parce qu'elle est courte — et donne à la page un bas.
 */
@Composable
private fun NetworkStep(
    state: RegistrationUiState,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    val networks = state.networks
    Column {
        StepHeader(
            title = stringResource(R.string.register_network_title),
            subtitle = stringResource(R.string.register_network_subtitle),
        )
        if (state.networkSearchable) {
            NetworkSearch(
                query = state.networkQuery,
                results = networks.size,
                onQuery = onQuery,
            )
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
        }
        if (networks.isEmpty()) {
            NetworkEmpty()
            return@Column
        }
        networks.forEachIndexed { index, network ->
            if (index > 0) Spacer(modifier = Modifier.height(AuleSpacing.sm))
            ChoiceCard(
                label = network.name,
                description = network.territory,
                selected = state.draft.networkKey == network.key,
                onClick = { onSelect(network.key) },
                modifier = Modifier.auleEnter(index = index),
                emblem = {
                    AuleNetworkEmblem(logo = network.logo(), initial = network.initial)
                },
            )
        }
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        NetworkAbsent()
    }
}

/**
 * Le champ de recherche des réseaux.
 *
 * Un **texte d'invite** et non un libellé flottant : le libellé de Material
 * monte au-dessus de la saisie et réserve sa place même à vide, ce qui coûte
 * douze points à un champ qui ne pose qu'une question de trois mots. La loupe
 * dit déjà de quel champ il s'agit.
 *
 * Le compte de résultats est une **région vive** : c'est la seule façon pour
 * TalkBack d'annoncer qu'une frappe vient de faire fondre la liste — les cartes
 * qui disparaissent plus bas ne se lisent que si on y retourne.
 */
@Composable
private fun NetworkSearch(
    query: String,
    results: Int,
    onQuery: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.register_network_search)) },
        leadingIcon = {
            Icon(
                imageVector = AuleGlyph.SEARCH.asImageVector(),
                contentDescription = null,
            )
        },
        trailingIcon = if (query.isEmpty()) {
            null
        } else {
            {
                IconButton(onClick = { onQuery("") }) {
                    Icon(
                        imageVector = AuleGlyph.CLOSE.asImageVector(),
                        contentDescription = stringResource(R.string.register_network_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = auleFieldColors(),
    )
    if (query.isNotEmpty()) {
        Text(
            text = pluralStringResource(R.plurals.register_network_results, results, results),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(top = AuleSpacing.sm)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * La recherche n'a rien ramené.
 *
 * Une phrase centrée seule au milieu du blanc se lisait comme une panne. Le
 * jeton devant elle en fait un **état** : quelque chose a été cherché, et n'a
 * pas été trouvé. Et la première ligne dit quoi faire ensuite, parce qu'un
 * écran vide qui ne propose rien n'a plus qu'à être quitté.
 */
@Composable
private fun NetworkEmpty() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(AuleControl.avatar),
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurfaceVariant,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AuleGlyph.SEARCH.asImageVector(),
                    contentDescription = null,
                )
            }
        }
        Text(
            text = stringResource(R.string.register_network_empty_title),
            style = MaterialTheme.typography.bodyLargeEmphasized,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AuleSpacing.md),
        )
        Text(
            text = stringResource(R.string.register_network_empty),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AuleSpacing.xs),
        )
    }
}

/**
 * La note de fin d'étape : « et si mon réseau n'y est pas ? ».
 *
 * Sans bord ni aplat de carte — ce qui a un cadre est ce dans quoi on écrit
 * (voir l'entête du fichier). Un filet vertical suffit à la détacher de la
 * liste sans lui donner l'air d'un cinquième choix qu'on pourrait cocher.
 */
@Composable
private fun NetworkAbsent() {
    val colors = MaterialTheme.colorScheme
    // `IntrinsicSize.Min` plutôt qu'une hauteur écrite : le filet suit alors le
    // texte, y compris quand l'appareil l'agrandit. Une barre chiffrée à la
    // main déborderait au premier cran de « texte plus grand ».
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(AuleStroke.emphasis)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colors.outlineVariant),
        )
        Column(modifier = Modifier.padding(start = AuleSpacing.md)) {
            Text(
                text = stringResource(R.string.register_network_absent_title),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.register_network_absent_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = AuleSpacing.xs),
            )
        }
    }
}

/**
 * Le logo d'un réseau, quand on l'a.
 *
 * `null` n'est pas un oubli : le catalogue s'ouvre plus vite qu'on n'obtient
 * les fichiers des exploitants, et [AuleNetworkEmblem] rend l'initiale en
 * attendant. Rien d'autre dans l'écran n'a besoin de connaître la différence.
 */
@Composable
private fun ProNetwork.logo(): Painter? {
    val drawable = NETWORK_LOGOS[key] ?: return null
    return painterResource(drawable)
}

@Composable
internal fun IdentityStep(
    state: RegistrationUiState,
    onFullName: (String) -> Unit,
    onEmployeeId: (String) -> Unit,
) {
    val required = stringResource(R.string.auth_required)
    Column {
        StepHeader(
            title = stringResource(R.string.register_identity_title),
            subtitle = stringResource(R.string.register_identity_subtitle),
        )
        AuleFormField(
            label = stringResource(R.string.register_full_name),
            value = state.draft.fullName,
            onValueChange = onFullName,
            fieldModifier = Modifier.testTag("registration-name").semantics { contentType = ContentType.PersonFullName },
            required = true,
            requiredLabel = required,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        AuleFormField(
            label = stringResource(R.string.register_employee_id),
            value = state.draft.employeeId,
            fieldModifier = Modifier.testTag("registration-employee"),
            onValueChange = onEmployeeId,
            required = true,
            requiredLabel = required,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

/**
 * Le choix du matériel conduit.
 *
 * C'est la seule liste du parcours dont les pastilles portent la forme
 * expressive [AuleShape.modeAvatar] : neuf lobes doux, la silhouette que le
 * conducteur retrouvera sur « Autour de vous » quand il ouvrira la carte. Elle
 * est réservée aux **modes de transport**, et trois lignes sont exactement le
 * genre de liste ponctuelle pour laquelle son coût de découpage se paie sans
 * qu'on le voie.
 */
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
        ProfessionalTransportMode.entries.forEachIndexed { index, mode ->
            ChoiceCard(
                glyph = mode.glyph,
                label = mode.label(),
                description = mode.description(),
                selected = state.draft.transportMode == mode,
                onClick = { onSelect(mode) },
                modifier = Modifier.auleEnter(index = index),
                pastille = registrationModeShape(mode),
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
    val required = stringResource(R.string.auth_required)
    var termsFailed by remember { mutableStateOf(false) }
    Column {
        StepHeader(
            title = stringResource(R.string.register_account_title),
            subtitle = stringResource(R.string.register_account_subtitle),
        )
        AuleFormField(
            label = stringResource(R.string.auth_email_label),
            value = state.draft.email,
            onValueChange = viewModel::setEmail,
            fieldModifier = Modifier.semantics { contentType = ContentType.EmailAddress },
            required = true,
            requiredLabel = required,
            placeholder = stringResource(R.string.auth_email_placeholder),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        AuleFormField(
            label = stringResource(R.string.auth_password),
            value = state.password,
            onValueChange = viewModel::setPassword,
            fieldModifier = Modifier.semantics { contentType = ContentType.Password },
            required = true,
            requiredLabel = required,
            // Pas de consigne sous le champ : la jauge la porte déjà, et la
            // remplace par le mot qui juge la saisie dès le premier caractère.
            // Écrite aux deux endroits, elle s'affichait deux fois de suite.
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
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        PasswordMeter(score = passwordScore(state.password))
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        AuleFormField(
            label = stringResource(R.string.register_confirm_password),
            value = state.confirmPassword,
            onValueChange = viewModel::setConfirmPassword,
            fieldModifier = Modifier.semantics { contentType = ContentType.Password },
            required = true,
            requiredLabel = required,
            error = if (state.passwordMismatch) {
                stringResource(R.string.register_password_mismatch)
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
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        OrSeparator()
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        GoogleSignUpButton(
            enabled = !state.isSubmitting,
            onClick = { viewModel.startOAuthSignUp(OAuthProvider.GOOGLE) },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_google_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
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
            state.missingTerms -> stringResource(R.string.register_error_terms)
            state.browserMissing -> stringResource(R.string.register_error_no_browser)
            state.failure != null -> state.failure.message()
            else -> null
        }
        if (error != null) {
            Spacer(modifier = Modifier.height(AuleSpacing.lg))
            AuleBanner(message = error, tone = AuleTone.ALERT)
        }
    }
}

/**
 * La solidité du mot de passe, en trois crans **et en toutes lettres**.
 *
 * Trois barres colorées seules posent deux problèmes. Le premier est
 * d'accessibilité : la couleur y porte toute l'information, donc l'échelle
 * n'existe pas pour qui la distingue mal, ni pour TalkBack — trois `Box` sans
 * texte ne se lisent pas. Le second est plus simple : personne ne sait combien
 * de barres font un bon mot de passe. Le mot les nomme, et le cran vide reprend
 * la consigne qui existait déjà dans les ressources sans être affichée nulle
 * part.
 *
 * Les crans passent de trois à six points de haut et prennent des bouts ronds.
 * Un filet de trois points, dans une cabine en plein soleil, n'est plus un
 * indicateur : c'est une rayure.
 */
@Composable
private fun PasswordMeter(score: Int) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val strength = when (score) {
        0 -> null
        1 -> colors.error
        2 -> colors.tertiary
        else -> colors.primary
    }
    val label = stringResource(
        when (score) {
            0 -> R.string.register_password_hint
            1 -> R.string.register_password_weak
            2 -> R.string.register_password_fair
            else -> R.string.register_password_strong
        },
    )
    val track = colors.surfaceContainerHighest
    // Sans mot de passe il n'y a pas de couleur de solidité : le cran allumé
    // vaut alors le cran éteint, et aucun ne s'allume de toute façon.
    val lit = strength ?: track
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            repeat(METER_STEPS) { index ->
                val color by animateColorAsState(
                    targetValue = if (score >= index + 1) lit else track,
                    animationSpec = motion.defaultEffectsSpec<Color>(),
                    label = "meter-color",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(METER_HEIGHT)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = strength ?: colors.onSurfaceVariant,
        )
    }
}

/**
 * L'acceptation des conditions.
 *
 * Le lien était un `Text` simplement `clickable` : ni ondulation, ni rôle de
 * bouton, et surtout une cible de la hauteur d'une ligne de onze points. Sur
 * l'écran qui verrouille toute l'inscription, c'est le pire endroit pour un
 * appui qui rate. Le `TextButton` rend les trois — et sa marge intérieure est
 * ramenée à un cran pour que le lien reste dans l'alignement de la phrase qui
 * l'introduit.
 */
/**
 * L'acceptation des conditions : une case, et une phrase qui en contient le lien.
 *
 * Le lien vivait dans un `TextButton` sous la phrase — donc sur deux lignes, en
 * corps de libellé, ce qui donnait au texte légal le poids d'une action. Le web
 * l'écrit dans la phrase, à l'encre d'accent, en douze points (`signup-form.tsx`),
 * et c'est ce que fait cette version : le lien reste un mot, la case reste la
 * commande.
 *
 * Ce n'est pas un `LinkAnnotation.Url` mais un `Clickable` : l'ouverture passe
 * par l'écran, qui sait dire — bandeau à l'appui — qu'aucun navigateur n'a
 * répondu. Un lien d'URL, lui, échouerait en silence.
 *
 * La case et le texte gardent deux cibles distinctes. Fusionner les deux —
 * cocher en touchant la phrase — rendrait le lien inatteignable, puisque le
 * même doigt au même endroit ferait alors deux choses.
 */
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
    val sentence = stringResource(R.string.register_terms_line, link)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(modifier = Modifier.width(AuleSpacing.xs))
        Text(
            text = buildAnnotatedString {
                append(sentence)
                val start = sentence.indexOf(link)
                if (start >= 0) {
                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "terms",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium,
                                ),
                            ),
                            linkInteractionListener = { onOpenTerms() },
                        ),
                        start = start,
                        end = start + link.length,
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = openTerms },
        )
    }
}

/**
 * L'autre façon d'entrer : le compte Google, et rien à retenir.
 *
 * ## Pourquoi il est ici, sous les champs et non en tête
 *
 * L'usage voudrait le bouton d'un fournisseur en tête de formulaire. Il vient
 * après, et à un endroit précis : **juste au-dessus de la case des conditions**.
 * Cette case vaut pour les deux façons d'entrer — un consentement ne dépend pas
 * du fournisseur d'identité — et en tête, l'appui aurait affiché un refus dont
 * la cause serait deux hauteurs d'écran plus bas, hors de vue. Collés, le bouton
 * et la case se lisent d'un seul regard.
 *
 * Le libellé et le logo sont ceux de Google, qui ne se retouchent pas ; le
 * bouton qui les porte est celui d'Aule — contour et surface du thème. C'est ce
 * que les règles de marque autorisent, et la seule répartition qui évite un
 * rectangle blanc étranger au milieu d'un écran sombre.
 */
@Composable
private fun GoogleSignUpButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.height),
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(AuleStroke.hairline, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google),
            // Le logo ne nomme rien que le libellé ne dise déjà : le décrire
            // ferait entendre « Google » deux fois de suite à TalkBack.
            contentDescription = null,
            modifier = Modifier.size(AuleControl.icon),
        )
        Spacer(modifier = Modifier.width(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_google),
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
    }
}

/**
 * Le trait qui sépare les deux façons d'entrer.
 *
 * Le mot est retiré à TalkBack : annoncé seul entre deux contrôles, « ou »
 * n'apprend rien — le bouton qui suit se nomme lui-même.
 */
@Composable
private fun OrSeparator() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineVariant)
        Text(
            text = stringResource(R.string.register_oauth_separator),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = AuleSpacing.md)
                .clearAndSetSemantics { },
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.outlineVariant)
    }
}

@Composable
private fun ConfirmationStep(
    state: RegistrationUiState,
    onResend: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = CircleShape
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
    val recapNetworkValue = state.selectedNetwork?.name.orEmpty()
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
        if (recapNetworkValue.isNotEmpty()) add(recapNetwork to recapNetworkValue)
        if (recapModeValue != null) add(recapMode to recapModeValue)
        add(recapName to state.draft.fullName)
        add(recapEmployee to state.draft.employeeId)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(MEDALLION_SIZE).auleEnter(),
            shape = registrationIllustrationShape(0),
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(AuleGlyph.CHECK.asImageVector(filled = true), null, Modifier.size(MEDALLION_GLYPH))
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        Text(
            text = stringResource(R.string.register_confirm_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_confirm_body),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        // Le récapitulatif était une carte `surface` **dans** une carte
        // `surface` : deux blancs identiques que seule une ombre de six points
        // séparait. Un cran de conteneur au-dessus, et le tableau existe sans
        // qu'on ait à lui dessiner un contour.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLow,
            contentColor = colors.onSurface,
        ) {
            Column(modifier = Modifier.padding(horizontal = AuleSpacing.lg)) {
                recap.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = AuleStroke.hairline,
                            color = colors.outlineVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AuleSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMediumEmphasized,
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
                Spacer(modifier = Modifier.width(AuleSpacing.sm))
                Text(text = stringResource(R.string.register_resending))
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
                .defaultMinSize(minHeight = AuleControl.height)
                .auleShadow(AuleElevation.FLOATING, shape, AuleShadowTint.ACCENT),
            shape = shape,
            colors = auleAccentButtonColors(),
        ) {
            Text(
                text = stringResource(R.string.register_sign_in),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** Cartes de choix web : surface douce, forme métier et coche réservée à la sélection. */
@Composable
private fun ChoiceCard(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: AuleGlyph? = null,
    emblem: (@Composable () -> Unit)? = null,
    multiSelect: Boolean = false,
    pastille: Shape = CircleShape,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val view = LocalView.current
    val shape = MaterialTheme.shapes.medium
    val effects = motion.defaultEffectsSpec<Color>()

    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        animationSpec = effects,
        label = "choice-container",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) colors.primary else Color.Transparent,
        animationSpec = effects,
        label = "choice-edge",
    )
    val jetonFill by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainerHighest,
        animationSpec = effects,
        label = "choice-jeton",
    )
    val jetonInk by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        animationSpec = effects,
        label = "choice-jeton-ink",
    )

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
        modifier = modifier
            .fillMaxWidth()
            .then(selectModifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(
            defaultElevation = AuleElevation.NONE.height(AuleTheme.night),
        ),
        border = BorderStroke(
            width = if (selected) AuleStroke.emphasis else AuleStroke.hairline,
            color = edge,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AuleSpacing.md, vertical = RegistrationLayout.choiceVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emblem != null) {
                emblem()
            } else if (glyph != null) {
                Surface(
                    modifier = Modifier.size(RegistrationLayout.choiceIcon),
                    shape = pastille,
                    color = jetonFill,
                    contentColor = jetonInk,
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = AuleSpacing.md),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMediumEmphasized.copy(fontSize = RegistrationLayout.choiceLabelSize),
                    color = colors.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = RegistrationLayout.choiceDescriptionSize),
                    color = colors.onSurfaceVariant,
                )
            }
            Box(Modifier.padding(start = AuleSpacing.sm).size(AuleSpacing.xl), contentAlignment = Alignment.Center) {
                if (selected) {
                    Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                        Icon(AuleGlyph.CHECK.asImageVector(filled = true), null,
                            Modifier.size(AuleSpacing.xl).padding(AuleSpacing.xs))
                    }
                }
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


/** Même raison, pour l'échelle de solidité du mot de passe. */
private val METER_HEIGHT = 6.dp

/**
 * Le médaillon de fin de parcours.
 *
 * Assez grand pour être ce qu'on voit avant de lire le titre — c'est son seul
 * travail — sans devenir une illustration qui repousserait le récapitulatif
 * sous la ligne de flottaison.
 */
private val MEDALLION_SIZE = 92.dp

/**
 * La coche du médaillon.
 *
 * La grille d'icône ordinaire, posée au centre d'un disque quatre fois plus
 * large, se lirait comme un bouton oublié là.
 */
private val MEDALLION_GLYPH = 40.dp


/** Les trois crans de solidité que [passwordScore] sait rendre. */
private const val METER_STEPS = 3


/**
 * Les logos des réseaux, par clé.
 *
 * Vide tant qu'aucun fichier n'est arrivé : un emblème sans logo rend
 * l'initiale du réseau, ce qui est une identité honnête, là où un dessin
 * approximatif serait un faux. Une entrée ici suffit à basculer le réseau
 * concerné sur sa vraie marque.
 */
private val NETWORK_LOGOS: Map<String, Int> = emptyMap()
