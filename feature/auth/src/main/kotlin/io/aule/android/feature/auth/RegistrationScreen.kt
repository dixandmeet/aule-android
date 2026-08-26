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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import io.aule.android.core.designsystem.AuleTypeface
import io.aule.android.core.designsystem.component.AuleFormField
import io.aule.android.core.designsystem.component.AuleNetworkBackdrop
import io.aule.android.core.designsystem.component.AuleWordmark
import io.aule.android.core.designsystem.component.auleFieldColors
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleBanner
import io.aule.android.core.designsystem.component.AuleBrandSurface
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.AuleShape
import io.aule.android.core.designsystem.component.AuleTone
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.component.auleAccentButtonColors
import io.aule.android.core.designsystem.reduceMotionEnabled
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
 *
 * ## Ce qu'un parcours en plusieurs étapes doit dire, et qu'il ne disait pas
 *
 * Un assistant a une question que l'écran ne pose jamais mais que l'utilisateur
 * se pose à chaque page : *où j'en suis, et combien il en reste*. La barre de
 * progression qui vivait ici y répondait mal, pour deux raisons dont aucune ne
 * se voyait en revue de code :
 *
 * - son fond (`outlineVariant`) est un filet à huit centièmes d'opacité. Un
 *   trait de séparation, pas un rail : la part **non parcourue** de la barre
 *   était donc invisible, et une barre sans fond n'est plus une proportion,
 *   c'est un trait qui grandit ;
 * - collée au bord haut de la carte, elle passait sous un arrondi de vingt-huit
 *   points, qui lui mangeait les deux extrémités.
 *
 * Elle est remplacée par [StepRail] — une pastille par étape, celle du moment
 * plus large que les autres. La proportion se **compte** au lieu de s'estimer,
 * ce qui est exactement ce dont on a besoin quand il reste quatre écrans à
 * remplir debout dans un dépôt.
 *
 * ## Le mouvement
 *
 * Deux gestes, et deux seulement. Le rail redistribue ses pastilles sur un
 * ressort spatial : c'est le changement d'étape qui **pousse** la pastille
 * active, on ne le lit pas, on le voit arriver. Et le contenu de l'étape glisse
 * dans le sens de la lecture — il part quand on avance, il revient quand on
 * recule — ce qui donne au bouton « Retour » une conséquence visible et non un
 * simple changement de page.
 *
 * Le glissement fait un quart de largeur, pas une largeur entière. Une page qui
 * traverse tout l'écran raconte un déplacement ; un quart raconte une
 * succession, et se termine avant que le doigt ait quitté le verre.
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

    AuleTheme(night = true, typeface = AuleTypeface.BRAND) {
        val colors = MaterialTheme.colorScheme
        val motion = MaterialTheme.motionScheme
        val reduceMotion = reduceMotionEnabled()

        // Les trois régimes sont lus **ici** et non dans `transitionSpec` : ce
        // dernier n'est pas un contexte composable — Compose l'évalue dans un
        // `remember` — et n'a donc pas accès au thème.
        val slide = motion.defaultSpatialSpec<IntOffset>()
        val fade = motion.defaultEffectsSpec<Float>()
        val resize = motion.defaultSpatialSpec<IntSize>()

        // Le tracé passe au régime discret : derrière quatre cartes de choix
        // et deux champs, le motif qui pose la connexion ne pose plus rien, il
        // encombre.
        AuleNetworkBackdrop(modifier = modifier, quiet = true) {
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
                return@AuleNetworkBackdrop
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
                        .widthIn(max = COLUMN_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AuleSpacing.xl, vertical = AuleSpacing.xl),
                ) {
                    // Plus de carte : comme sur le web, ce qui a un cadre est ce
                    // dans quoi on écrit, et rien d'autre. La carte cernait
                    // aussi le rail, le bouton et l'air entre les deux — trois
                    // choses qui n'ont jamais eu besoin d'un bord.
                    if (state.step != RegistrationStep.WELCOME) {
                        RegistrationHeader(state = state, onBack = { viewModel.back(onClose) })
                    }
                    AnimatedContent(
                        targetState = state.step,
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            if (reduceMotion) {
                                // L'appareil a demandé moins de mouvement :
                                // l'étape est simplement là. Pas de version
                                // atténuée — un glissement discret reste un
                                // glissement.
                                (EnterTransition.None togetherWith ExitTransition.None)
                                    .using(sizeTransform = null)
                            } else {
                                // L'ordre de déclaration de RegistrationStep
                                // **est** l'ordre du parcours : le rang de
                                // l'énumération dit donc le sens de la
                                // marche, y compris quand l'étape « mode de
                                // transport » saute.
                                // `Start` / `End` et non `Left` / `Right` :
                                // le sens de la marche est celui de la
                                // lecture, et il s'inverse avec elle.
                                val towards = if (targetState.ordinal >= initialState.ordinal) {
                                    AnimatedContentTransitionScope.SlideDirection.Start
                                } else {
                                    AnimatedContentTransitionScope.SlideDirection.End
                                }
                                val enter =
                                    slideIntoContainer(towards, slide) { it / SLIDE_FRACTION } +
                                        fadeIn(fade)
                                val exit =
                                    slideOutOfContainer(towards, slide) { it / SLIDE_FRACTION } +
                                        fadeOut(fade)
                                (enter togetherWith exit)
                                    .using(SizeTransform(clip = true) { _, _ -> resize })
                            }
                        },
                        label = "register-step",
                    ) { step ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (step) {
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

/**
 * Le pied d'une étape à valider : l'action principale, puis la promesse.
 *
 * L'action porte une **ombre teintée** — la seule de l'écran. C'est ce
 * qu'[AuleShadowTint.ACCENT] existe pour faire : une lueur de marque qui désigne
 * l'action au lieu de l'éloigner du fond. Elle disparaît quand le bouton est
 * inactif, sinon elle promettrait un appui que le formulaire refuse.
 *
 * Pendant l'envoi, la roue s'accompagne de « Création… ». Une roue seule dit
 * qu'il se passe quelque chose ; elle ne dit pas quoi, et un compte qui se crée
 * mérite qu'on le nomme — c'est la seule action de tout le parcours qui parte
 * sur le réseau.
 */
@Composable
private fun WithFooter(
    state: RegistrationUiState,
    viewModel: RegistrationViewModel,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val enabled = state.canContinue && !state.isSubmitting
    val glow = Modifier.auleShadow(AuleElevation.FLOATING, shape, AuleShadowTint.ACCENT)
    Column {
        content()
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Button(
            onClick = viewModel::continueForward,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height)
                .then(if (enabled) glow else Modifier),
            enabled = enabled,
            shape = shape,
            colors = auleAccentButtonColors(),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    color = AuleTheme.tokens.onAccent.color,
                    strokeWidth = AuleStroke.glyph,
                )
                Spacer(modifier = Modifier.width(AuleSpacing.sm))
                Text(
                    text = stringResource(R.string.register_creating),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            } else {
                Text(
                    text = stringResource(
                        when {
                            state.step == RegistrationStep.ACCOUNT -> R.string.register_create
                            else -> R.string.register_continue
                        },
                    ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier.padding(start = AuleSpacing.xs),
            )
        }
    }
}

/**
 * L'en-tête de l'assistant : le rail, puis le retour et le compte d'étapes.
 *
 * Le rail passe **en premier**, avant le bouton de retour : c'est la réponse à
 * la question qu'on se pose en arrivant sur l'écran, et le compte écrit juste
 * en dessous se lit alors comme sa légende plutôt que comme une information
 * séparée.
 *
 * Ce compte est une **région vivante**. TalkBack annonce le contenu de la
 * nouvelle étape quand elle arrive, mais rien ne lui dit qu'on a changé de
 * rang : sans cette ligne, l'utilisateur qui n'a pas l'écran sous les yeux
 * traverse six formulaires sans savoir combien il en reste.
 */
@Composable
private fun RegistrationHeader(
    state: RegistrationUiState,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val finished = state.step == RegistrationStep.CONFIRMATION
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AuleSpacing.xl, top = AuleSpacing.xl, end = AuleSpacing.xl),
    ) {
        StepRail(
            stepCount = state.actionSteps.size,
            // Sur la confirmation, l'index d'action retombe à zéro : le parcours
            // est fini, pas revenu à son début. On pousse donc l'index au-delà du
            // dernier cran, ce qui allume tout le rail sans en élargir aucun.
            activeIndex = if (finished) state.actionSteps.size else state.actionIndex,
        )
        if (!finished) {
            Spacer(modifier = Modifier.height(AuleSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(horizontal = AuleSpacing.xs),
                ) {
                    Icon(
                        imageVector = AuleGlyph.BACK.asImageVector(),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(AuleSpacing.lg),
                    )
                    Text(
                        text = stringResource(R.string.register_back),
                        style = MaterialTheme.typography.labelLargeEmphasized,
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
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * Le rail d'étapes : une pastille par écran, la courante élargie.
 *
 * ## Pourquoi des pastilles et non une barre
 *
 * Une barre continue donne une **proportion** — « à peu près à la moitié » — là
 * où un assistant demande un **compte** : « encore trois ». Le parcours fait
 * quatre ou cinq étapes selon qu'on conduit ou non ; à cette échelle les crans
 * se comptent d'un coup d'œil, ce qu'aucune barre ne permet.
 *
 * ## Ce qui bouge, et sur quel régime
 *
 * La **largeur** de la pastille active est animée sur un ressort spatial : à
 * chaque étape, le rail se redistribue, et le mouvement part de là où il en
 * était si l'on enchaîne deux appuis. Une durée fixe rejouerait sa courbe depuis
 * le début et donnerait ce décalage d'un demi-battement qu'on sent sans savoir
 * le nommer. La **couleur**, elle, passe par un ressort d'effets : animée sur un
 * ressort spatial elle dépasserait sa cible, se ferait plafonner, et
 * scintillerait.
 *
 * La pastille active porte en plus l'ombre teintée. Sur huit points de haut
 * c'est une lueur, pas une ombre — et c'est précisément ce qu'on veut : le
 * repère du moment doit briller un peu.
 *
 * ## Le fond des pastilles à venir
 *
 * `surfaceContainerHighest` et non `outlineVariant`. Ce dernier est le filet de
 * séparation de la charte, à huit centièmes d'opacité : il disparaît en plein
 * soleil, et un rail dont on ne voit pas les crans restants ne compte plus rien.
 * L'échelle de conteneurs, elle, descend jusqu'à un gris franc.
 */
@Composable
private fun StepRail(
    stepCount: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val glow = Modifier.auleShadow(AuleElevation.RESTING, CircleShape, AuleShadowTint.ACCENT)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        repeat(stepCount) { index ->
            val active = index == activeIndex
            val reached = index <= activeIndex
            val share by animateFloatAsState(
                targetValue = if (active) RAIL_ACTIVE_SHARE else 1f,
                animationSpec = motion.defaultSpatialSpec<Float>(),
                label = "rail-share",
            )
            val color by animateColorAsState(
                targetValue = if (reached) colors.primary else colors.surfaceContainerHighest,
                animationSpec = motion.defaultEffectsSpec<Color>(),
                label = "rail-color",
            )
            Box(
                modifier = Modifier
                    // Un ressort expressif dépasse sa cible aux deux bouts : la
                    // pastille qu'on quitte passe donc *sous* sa part d'arrivée,
                    // et `weight` refuse une part nulle ou négative.
                    .weight(share.coerceAtLeast(RAIL_MIN_SHARE))
                    .height(RAIL_HEIGHT)
                    .then(if (active) glow else Modifier)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * L'accueil de l'inscription : la marque, ce qu'on y fait, et par où on entre.
 *
 * Il suit la page d'inscription du web (`SpacePro/app/(auth)/inscription`) :
 * marque en tête, titre à gauche, la phrase qui dit à qui l'espace s'adresse,
 * l'action, puis l'autre chemin pour ceux qui ont déjà un compte.
 *
 * Les trois pastilles restent, et elles ne sont pas un décor : elles nomment
 * les trois métiers que l'inscription accepte, ce que la phrase suivante met
 * quatre lignes à dire. Elles s'alignent maintenant à gauche avec le reste —
 * réparties sur toute la largeur, elles faisaient une frise, c'est-à-dire une
 * décoration.
 */
@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
    onSignIn: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Column {
        AuleWordmark(
            name = stringResource(R.string.auth_brand),
            kicker = stringResource(R.string.auth_workspace),
            contentDescription = stringResource(R.string.auth_logo),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xxl))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Les trois pastilles arrivent l'une après l'autre. C'est le premier
            // écran de l'application pour qui s'inscrit : trois ronds qui se
            // posent ensemble sont un bloc, les mêmes décalés de quarante
            // millisecondes se déroulent, et le regard suit le déroulé.
            WelcomeIcon(AuleGlyph.BUS, index = 0)
            WelcomeIcon(AuleGlyph.TICKET, index = 1)
            WelcomeIcon(AuleGlyph.SHIELD, index = 2)
        }
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Text(
            text = stringResource(R.string.register_welcome_title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.xl))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleControl.height)
                .auleShadow(AuleElevation.FLOATING, shape, AuleShadowTint.ACCENT),
            shape = shape,
            colors = auleAccentButtonColors(),
        ) {
            Text(
                text = stringResource(R.string.register_start),
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
        }
        Spacer(modifier = Modifier.height(AuleSpacing.md))
        Text(
            text = stringResource(R.string.register_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.register_have_account),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            TextButton(onClick = onSignIn) {
                Text(
                    text = stringResource(R.string.register_already),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = colors.primary,
                )
            }
        }
    }
}

/**
 * Une pastille d'accueil : un des trois mondes de l'application.
 *
 * Le contour clair n'est pas une décoration : posée sur la carte blanche, une
 * pastille de conteneur primaire sans bord flotte sans se poser. Le filet lui
 * donne un bord, et l'ensemble se lit comme trois objets et non comme trois
 * taches.
 */
@Composable
private fun WelcomeIcon(glyph: AuleGlyph, index: Int) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .size(AuleControl.avatar)
            .auleEnter(index = index),
        shape = CircleShape,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        border = BorderStroke(AuleStroke.hairline, colors.outlineVariant),
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
        SIGNUP_PROFILES.forEachIndexed { index, profile ->
            ChoiceCard(
                glyph = profile.glyph,
                label = profile.label(),
                description = profile.description(),
                selected = profile in state.draft.profiles,
                onClick = { onToggle(profile) },
                modifier = Modifier.auleEnter(index = index),
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
            shape = MaterialTheme.shapes.small,
            colors = auleFieldColors(),
        )
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        if (state.showsNaolib) {
            ChoiceCard(
                glyph = AuleGlyph.PIN,
                label = stringResource(R.string.register_network_naolib),
                description = stringResource(R.string.register_network_naolib_desc),
                selected = state.draft.networkKey == "naolib",
                onClick = onSelectNaolib,
                modifier = Modifier.auleEnter(),
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
    val required = stringResource(R.string.auth_required)
    Column {
        StepHeader(
            title = stringResource(R.string.register_identity_title),
            subtitle = stringResource(R.string.register_identity_subtitle),
        )
        AuleFormField(
            label = stringResource(R.string.register_full_name),
            value = state.draft.fullName,
            onValueChange = viewModel::setFullName,
            fieldModifier = Modifier.semantics { contentType = ContentType.PersonFullName },
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
            onValueChange = viewModel::setEmployeeId,
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
    val pastille = AuleShape.modeAvatar()
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
                pastille = pastille,
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

@Composable
private fun ConfirmationStep(
    state: RegistrationUiState,
    onResend: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
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
    // Aligné à gauche comme les quatre étapes qui précèdent : l'écran de fin
    // n'est pas un autre écran, c'est le dernier de la même colonne.
    Column {
        // La seule surface de marque de l'écran, à l'endroit où le parcours
        // s'achève. Le dégradé, le reflet et l'ombre teintée valent ici ce qu'un
        // aplat ne dirait pas : le compte existe. Une pastille de conteneur
        // primaire de cinquante-deux points — celle des trois mondes de
        // l'accueil — annonçait la fin du parcours du même ton qu'une icône
        // décorative.
        AuleBrandSurface(
            modifier = Modifier.size(MEDALLION_SIZE),
            shape = CircleShape,
            elevation = AuleElevation.LIFTED,
        ) {
            Icon(
                imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(MEDALLION_GLYPH),
            )
        }
        Spacer(modifier = Modifier.height(AuleSpacing.lg))
        Text(
            text = stringResource(R.string.register_confirm_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        Text(
            text = stringResource(R.string.register_confirm_body),
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
            color = colors.surfaceContainerHigh,
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

/**
 * Une carte de choix : un métier, un réseau, un matériel.
 *
 * ## Ce que la sélection doit produire
 *
 * L'ancienne version ne changeait qu'un aplat : conteneur primaire pour le
 * choix retenu, conteneur neutre pour les autres. Deux clairs voisins, à
 * comparer l'un à l'autre pour savoir lequel est coché — soit exactement le
 * geste qu'on ne fait pas quand on remplit un formulaire debout. La sélection
 * porte maintenant trois marques qui se lisent chacune seule :
 *
 * - l'aplat, qui passe au conteneur primaire ;
 * - le **contour**, qui passe du filet au trait appuyé, à la couleur d'encre de
 *   la marque ;
 * - l'**ombre teintée**, qui décolle la rangée retenue de celles qui restent au
 *   ras de la carte.
 *
 * Les deux couleurs sont animées sur le ressort d'effets : la carte prend sa
 * teinte pendant que le doigt se relève, au lieu de basculer d'un coup après
 * lui. C'est le seul endroit du parcours où un mouvement accompagne un appui, et
 * c'est là qu'il compte.
 *
 * ## L'élévation, qui vient d'ici et pas du composant
 *
 * `CardDefaults.cardElevation` est neutralisée : Material poserait une ombre
 * noire, et une ombre noire sous une carte teintée la salit. L'ombre de la
 * rangée retenue passe donc par [auleShadow] et prend la couleur de la marque.
 *
 * @param pastille la forme du jeton de tête. Ronde par défaut ; les modes de
 *   transport prennent la silhouette expressive du kit.
 */
@Composable
private fun ChoiceCard(
    glyph: AuleGlyph,
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    pastille: Shape = CircleShape,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val view = LocalView.current
    val shape = MaterialTheme.shapes.medium
    val effects = motion.defaultEffectsSpec<Color>()

    val container by animateColorAsState(
        // `surface` posait une carte blanche dans la carte blanche du
        // formulaire : le choix non retenu disparaissait. Un cran de conteneur
        // au-dessus le fait exister sans lui donner l'air d'être coché.
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = effects,
        label = "choice-container",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant,
        animationSpec = effects,
        label = "choice-edge",
    )
    val jetonFill by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.primaryContainer,
        animationSpec = effects,
        label = "choice-jeton",
    )
    val jetonInk by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onPrimaryContainer,
        animationSpec = effects,
        label = "choice-jeton-ink",
    )
    val glow = Modifier.auleShadow(AuleElevation.RESTING, shape, AuleShadowTint.ACCENT)

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
            .then(if (selected) glow else Modifier)
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
            modifier = Modifier.padding(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(AuleControl.avatar),
                shape = pastille,
                color = jetonFill,
                contentColor = jetonInk,
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
                // Seize points en demi-gras plutôt que quatorze en gras : à
                // encombrement égal, c'est la taille qui porte, pas la graisse.
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = colors.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
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
                    imageVector = AuleGlyph.CHECK.asImageVector(filled = true),
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

/**
 * La hauteur d'un cran du rail.
 *
 * Le filet de quatre points de Material se lit assis, à l'ombre. Debout, gants
 * aux mains, pare-brise plein sud, il faut le double pour que le rail reste un
 * repère et non une bordure.
 */
private val RAIL_HEIGHT = 8.dp

/** Même raison, pour l'échelle de solidité du mot de passe. */
private val METER_HEIGHT = 6.dp

/**
 * Le médaillon de fin de parcours.
 *
 * Assez grand pour être ce qu'on voit avant de lire le titre — c'est son seul
 * travail — sans devenir une illustration qui repousserait le récapitulatif
 * sous la ligne de flottaison.
 */
private val MEDALLION_SIZE = 96.dp

/**
 * La coche du médaillon.
 *
 * La grille d'icône ordinaire, posée au centre d'un disque quatre fois plus
 * large, se lirait comme un bouton oublié là.
 */
private val MEDALLION_GLYPH = 44.dp

/**
 * La part de largeur que prend la pastille de l'étape en cours.
 *
 * Deux fois et demie ses voisines : au-delà, les crans restants s'écrasent et le
 * rail cesse de se compter ; en deçà, on ne voit plus lequel est le nôtre.
 */
private const val RAIL_ACTIVE_SHARE = 2.5f

/** Le plancher qui protège `weight` du dépassement du ressort. */
private const val RAIL_MIN_SHARE = 0.6f

/** Les trois crans de solidité que [passwordScore] sait rendre. */
private const val METER_STEPS = 3

/** Le quart de largeur dont l'étape entrante glisse. */
private const val SLIDE_FRACTION = 4

