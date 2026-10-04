package io.aule.android.feature.auth

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.foundation.auleBottomSystemPadding
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleSpacing
import kotlinx.coroutines.delay

/** La carte et le bandeau de /login?mode=pro, sans charger une seconde carte avant connexion. */
@Composable
internal fun AuthWebChrome(modifier: Modifier = Modifier, quiet: Boolean, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.fillMaxSize().background(colors.surface).imePadding()) {
        val wide = maxWidth >= RegistrationLayout.wideBreakpoint
        val sceneVisible by remember { derivedStateOf { scroll.value == 0 } }
        val card: @Composable () -> Unit = {
            Surface(Modifier.padding(horizontal = AuleSpacing.lg).widthIn(max = RegistrationLayout.cardWidth).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLowest) {
                Column(Modifier.padding(horizontal = RegistrationLayout.cardPadding, vertical = RegistrationLayout.cardTop)) { content() }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { AuthWebShowcase(quiet, true) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll).padding(vertical = AuleSpacing.xl).auleBottomSystemPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally) { card() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).auleBottomSystemPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                AuthWebShowcase(quiet || !sceneVisible, false)
                Box(Modifier.offset(y = -RegistrationLayout.overlap)) { card() }
            }
        }
    }
}

@Composable
private fun AuthWebShowcase(quiet: Boolean, wide: Boolean) {
    val colors = MaterialTheme.colorScheme
    val reduced = reduceMotionEnabled()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var paused by rememberSaveable { mutableStateOf(false) }
    val titles = stringResource(R.string.auth_showcase_titles).split('|')
    val labels = stringResource(R.string.auth_showcase_labels).split('|')
    val details = stringResource(R.string.auth_showcase_details).split('|')
    val context = LocalContext.current
    val accessibility = remember(context) { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager }
    var spokenFeedback by remember(accessibility) { mutableStateOf(accessibility.isTouchExplorationEnabled) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { spokenFeedback = it }
        accessibility.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility.removeTouchExplorationStateChangeListener(listener) }
    }
    val running = !quiet && !paused && !reduced && !spokenFeedback && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(running, selected) {
        if (running) { delay(6500); selected = (selected + 1) % titles.size }
    }
    Column(Modifier.fillMaxWidth().then(if (wide) Modifier.fillMaxHeight() else Modifier)
        .clip(MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp)))
        .background(colors.inverseSurface).statusBarsPadding()
        .padding(horizontal = RegistrationLayout.scenePadding, vertical = AuleSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
            Image(painterResource(io.aule.android.core.designsystem.R.drawable.aule_logo_blanc), null, Modifier.size(RegistrationLayout.logoSize))
            Text(buildAnnotatedString { append("Aule"); withStyle(SpanStyle(color = colors.inversePrimary)) { append(" Pro") } },
                style = MaterialTheme.typography.titleLarge, color = colors.inverseOnSurface)
        }
        if (wide) Spacer(Modifier.weight(1f)) else Spacer(Modifier.height(AuleSpacing.md))
        AnimatedContent(selected, label = "auth-web-feature") { index ->
            Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
                Text(labels[index], style = MaterialTheme.typography.labelSmall, color = colors.inversePrimary,
                    modifier = Modifier.background(colors.primary, MaterialTheme.shapes.extraLarge).padding(horizontal = AuleSpacing.sm, vertical = AuleSpacing.xs))
                Text(titles[index], style = MaterialTheme.typography.titleLargeEmphasized, color = colors.inverseOnSurface,
                    modifier = Modifier.semantics { heading() })
                Text(details[index], style = MaterialTheme.typography.bodySmall, color = colors.inverseOnSurface,
                    modifier = Modifier.heightIn(min = SHOWCASE_DETAIL_HEIGHT))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
            titles.forEachIndexed { index, title ->
                TextButton(onClick = { selected = index; paused = true }, contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).semantics { contentDescription = title; this.selected = selected == index }) {
                    HorizontalDivider(color = if (index <= selected) colors.inversePrimary else colors.outline, thickness = SHOWCASE_RAIL_HEIGHT)
                }
            }
            IconButton(onClick = { paused = !paused }, modifier = Modifier.testTag("auth-showcase-pause")) {
                Icon(if (paused) AuleGlyph.PLAY.asImageVector() else Icons.Outlined.Pause,
                    stringResource(if (paused) R.string.auth_showcase_resume else R.string.auth_showcase_pause), tint = colors.inversePrimary)
            }
        }
        Spacer(Modifier.height(RegistrationLayout.overlap))
    }
}

@Composable
internal fun AuthDivider(label: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
        HorizontalDivider(Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}
private val SHOWCASE_DETAIL_HEIGHT = 48.dp
private val SHOWCASE_RAIL_HEIGHT = 3.dp
