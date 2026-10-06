package io.aule.android.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import io.aule.android.core.designsystem.foundation.auleBottomSystemPadding
import io.aule.android.core.designsystem.foundation.auleBottomSystemInset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.R as DesignR

/** Composition de /onboarding : scène inverse, carte claire et actions toujours accessibles. */
@Composable
internal fun RegistrationChrome(
    state: RegistrationUiState,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var footerHeight by remember { mutableIntStateOf(0) }
    val hasActions = state.step in state.actionSteps
    val welcome = state.step == RegistrationStep.WELCOME
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val confirmationInset = auleBottomSystemInset
    val scroll = rememberScrollState()
    val sceneVisible by remember(density) { derivedStateOf { scroll.value < with(density) { RegistrationLayout.bannerHeight.toPx() } } }
    val animateScene = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !keyboardVisible && sceneVisible
    // Un changement d'étape repart du titre, sans perdre les valeurs du brouillon.
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface).imePadding()) {
        val wide = maxWidth >= RegistrationLayout.wideBreakpoint
        val round = MaterialTheme.shapes.extraLarge
        val square = CornerSize(0.dp)
        val cardShape = if (hasActions || welcome) round.copy(bottomStart = square, bottomEnd = square) else round
        val footerShape = round.copy(topStart = square, topEnd = square)
        val bodyBottom = if (hasActions || welcome) with(density) { footerHeight.toDp() } else confirmationInset
        val minCardHeight = (maxHeight - bodyBottom - (if (wide) 0.dp else RegistrationLayout.bannerHeight - RegistrationLayout.overlap)).coerceAtLeast(0.dp)
        val card: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = bodyBottom).verticalScroll(scroll)
                        .padding(bottom = if (hasActions || welcome) 0.dp else RegistrationLayout.pageBottom),
                ) {
                    if (!wide) RegistrationScene(state, animateScene)
                    Surface(
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(horizontal = AuleSpacing.lg)
                            .offset(y = if (wide) 0.dp else -RegistrationLayout.overlap)
                            .widthIn(max = RegistrationLayout.cardWidth).fillMaxWidth().heightIn(min = minCardHeight)
                            .auleShadow(AuleElevation.FLOATING, cardShape, AuleShadowTint.NEUTRAL).authEnter(120),
                        shape = cardShape,
                        color = colors.surfaceContainerLowest,
                    ) {
                        Column(
                            Modifier.padding(horizontal = RegistrationLayout.cardPadding)
                                .padding(top = RegistrationLayout.cardTop, bottom = RegistrationLayout.cardBottom),
                        ) {
                            if (hasActions) RegistrationProgress(state)
                            content()
                        }
                    }
                }
                if (welcome) {
                    Surface(Modifier.align(Alignment.BottomCenter).padding(horizontal = AuleSpacing.lg)
                        .widthIn(max = RegistrationLayout.cardWidth).fillMaxWidth().onSizeChanged { footerHeight = it.height },
                        shape = footerShape, color = colors.surfaceContainerLowest) {
                        RegistrationWelcomeActions(onContinue, onClose,
                            Modifier.auleBottomSystemPadding().padding(horizontal = RegistrationLayout.cardPadding, vertical = AuleSpacing.md))
                    }
                } else if (hasActions) {
                    RegistrationFooter(
                        state, onBack, onContinue,
                        Modifier.align(Alignment.BottomCenter)
                            .padding(horizontal = AuleSpacing.lg)
                            .widthIn(max = RegistrationLayout.cardWidth)
                            .onSizeChanged { footerHeight = it.height },
                    )
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { RegistrationScene(state, animateScene, wide = true) }
                Box(Modifier.weight(1f).fillMaxHeight().padding(vertical = AuleSpacing.xl), contentAlignment = Alignment.TopCenter) { card() }
            }
        } else card()
    }
}

@Composable
private fun RegistrationScene(state: RegistrationUiState, animate: Boolean, wide: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val inverse = colors.inverseSurface
    Box(
        Modifier.fillMaxWidth()
            .then(if (wide) Modifier.fillMaxHeight() else Modifier.heightIn(min = RegistrationLayout.bannerHeight))
            .clip(MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)))
            .background(inverse),
    ) {
        RegistrationNetwork(Modifier.matchParentSize(), wide, animate)
        Column(
            Modifier.fillMaxWidth().then(if (wide) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = RegistrationLayout.scenePadding).statusBarsPadding().padding(top = AuleSpacing.lg, bottom = RegistrationLayout.sceneBottom),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                Image(painterResource(DesignR.drawable.aule_logo_blanc), stringResource(R.string.auth_logo), Modifier.size(RegistrationLayout.logoSize))
                Text(
                    buildAnnotatedString {
                        append("Aule ")
                        withStyle(SpanStyle(color = colors.inversePrimary)) { append("Pro") }
                    },
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = colors.inverseOnSurface,
                )
            }
            if (wide) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(RegistrationLayout.sceneGap))
            Text(
                stringResource(R.string.register_scene_title),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = colors.inverseOnSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.register_scene_emphasis),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = colors.inversePrimary,
            )
            if (wide) {
                Spacer(Modifier.height(AuleSpacing.xl))
                Text(stringResource(R.string.register_welcome_subtitle), color = colors.inverseOnSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RegistrationProgress(state: RegistrationUiState) {
    val colors = MaterialTheme.colorScheme
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
        Text(
            stringResource(R.string.register_step, state.actionIndex + 1, state.actionSteps.size).uppercase(),
            color = colors.primary, style = MaterialTheme.typography.labelSmall.copy(fontSize = RegistrationLayout.stepCountSize),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(AuleGlyph.LOCK.asImageVector(), null, Modifier.size(AuleSpacing.md), colors.onSurfaceVariant)
            Spacer(Modifier.width(AuleSpacing.xs))
            Text(stringResource(R.string.register_saved), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(AuleSpacing.sm))
    LinearWavyProgressIndicator(
        progress = { (state.actionIndex + 0.5f) / state.actionSteps.size },
        modifier = Modifier.fillMaxWidth().testTag("registration-progress"),
        color = colors.primary, trackColor = colors.surfaceContainerHighest,
        amplitude = { 1f }, wavelength = RegistrationLayout.waveLength, gapSize = RegistrationLayout.waveGap,
        waveSpeed = if (reduceMotionEnabled()) 0.dp else RegistrationLayout.waveSpeed,
    )
    Spacer(Modifier.height(AuleSpacing.xl))
}

@Composable
internal fun RegistrationFooter(
    state: RegistrationUiState,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val white = colors.surfaceContainerLowest
    Surface(
        modifier.fillMaxWidth().drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, white), -AuleSpacing.xl.toPx(), 0f),
                topLeft = androidx.compose.ui.geometry.Offset(0f, -AuleSpacing.xl.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width, AuleSpacing.xl.toPx()))
        },
        color = white,
        shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)),
    ) {
        Column(Modifier.auleBottomSystemPadding().padding(horizontal = RegistrationLayout.cardPadding, vertical = AuleSpacing.md)) {
            if (state.step == RegistrationStep.ACCOUNT && !state.canContinue && !state.isSubmitting) {
                val blocker = when {
                    !state.emailValid -> R.string.register_need_valid_email
                    !state.passwordLengthValid -> R.string.register_password_hint
                    state.passwordMismatch -> R.string.register_password_mismatch
                    else -> R.string.register_error_terms
                }
                Surface(shape = MaterialTheme.shapes.small, color = colors.secondaryContainer) {
                    Text(stringResource(blocker), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer, modifier = Modifier.padding(AuleSpacing.sm))
                }
                Spacer(Modifier.height(AuleSpacing.sm))
            }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm), verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = !state.isSubmitting, contentPadding = PaddingValues(horizontal = AuleSpacing.sm)) {
                Icon(AuleGlyph.BACK.asImageVector(), null, Modifier.size(AuleSpacing.lg))
                Spacer(Modifier.width(AuleSpacing.xs))
                Text(stringResource(R.string.register_back), style = MaterialTheme.typography.labelLargeEmphasized)
            }
            Button(
                onClick = onContinue,
                enabled = state.canContinue && !state.isSubmitting,
                modifier = Modifier.weight(1f).heightIn(min = RegistrationLayout.buttonHeight).testTag("registration-continue"),
                shape = CircleShape,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(AuleSpacing.lg), color = colors.primary, strokeWidth = AuleStroke.glyph)
                    Spacer(Modifier.width(AuleSpacing.sm))
                    Text(stringResource(R.string.register_creating), style = MaterialTheme.typography.labelLargeEmphasized)
                } else {
                    Text(stringResource(if (state.step == RegistrationStep.ACCOUNT) R.string.register_create else R.string.register_continue), style = MaterialTheme.typography.labelLargeEmphasized)
                    Spacer(Modifier.width(AuleSpacing.sm))
                    Icon(AuleGlyph.CHEVRON.asImageVector(), null, Modifier.size(AuleSpacing.lg))
                }
            }
        }
        }
    }
}

/** Mesures du panneau mobile web, indépendantes des composants de formulaire partagés. */
internal object RegistrationLayout {
    internal val wideBreakpoint = 1024.dp
    internal val cardWidth = 520.dp
    internal val bannerHeight = 260.dp
    internal val cardPadding = 22.dp
    internal val cardTop = 28.dp
    internal val cardBottom = 30.dp
    internal val overlap = 32.dp
    internal val scenePadding = 20.dp
    internal val sceneBottom = 52.dp
    internal val sceneGap = 66.dp
    internal val logoSize = 34.dp
    internal val pageBottom = 40.dp
    internal val buttonHeight = 56.dp
    internal val stepSlide = 18.dp
    internal val waveSpeed = 22.dp
    internal val waveLength = 22.dp
    internal val waveGap = 7.dp
    internal val choiceVertical = 13.dp
    val stepCountSize = 12.5.sp
    val choiceLabelSize = 15.sp
    val choiceDescriptionSize = 13.sp
    internal val requirementHeight = 40.dp
    internal val choiceIcon = 46.dp
    internal val welcomeIcon = 58.dp
    internal val illustrationDash = 6.dp
}
