package io.aule.android.feature.auth

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.*
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.model.ProfessionalProfile
import io.aule.android.core.model.ProfessionalTransportMode

@Composable
internal fun RegistrationWelcome(onSignIn: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onSignIn, Modifier.align(Alignment.Start), contentPadding = PaddingValues(0.dp)) {
            Icon(AuleGlyph.BACK.asImageVector(), null, Modifier.size(AuleSpacing.lg), colors.onSurfaceVariant)
            Spacer(Modifier.width(AuleSpacing.sm))
            Text(stringResource(R.string.register_return_signup), color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(AuleSpacing.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(AuleGlyph.BUS, AuleGlyph.TICKET, AuleGlyph.SHIELD).forEachIndexed { index, glyph ->
                if (index > 0) {
                    val track = colors.surfaceContainerHighest
                    Canvas(Modifier.width(RegistrationLayout.logoSize).height(AuleSpacing.xs)) {
                        drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                            strokeWidth = AuleStroke.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(RegistrationLayout.illustrationDash.toPx(), RegistrationLayout.illustrationDash.toPx())))
                    }
                }
                Surface(
                    modifier = Modifier.size(RegistrationLayout.welcomeIcon).auleEnter(index),
                    shape = registrationIllustrationShape(index), color = colors.primaryContainer,
                    contentColor = colors.primary,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(glyph.asImageVector(), null) }
                }
            }
        }
        Spacer(Modifier.height(AuleSpacing.xl))
        Text(stringResource(R.string.register_welcome_title), style = MaterialTheme.typography.headlineMediumEmphasized,
            color = colors.onSurface, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(AuleSpacing.sm))
        Text(stringResource(R.string.register_welcome_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(AuleSpacing.xl))
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
            Column(Modifier.padding(AuleSpacing.sm), verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs)) {
                WelcomeRequirement(AuleGlyph.HEADING, stringResource(R.string.register_need_time))
                WelcomeRequirement(AuleGlyph.PERSON, stringResource(R.string.register_need_identity))
                WelcomeRequirement(AuleGlyph.MAIL, stringResource(R.string.register_need_email))
            }
        }
    }
}

@Composable
internal fun RegistrationWelcomeActions(onStart: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var browserFailed by remember { mutableStateOf(false) }
    val haveAccount = stringResource(R.string.register_have_account)
    val signIn = stringResource(R.string.register_already)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onStart,
            Modifier.fillMaxWidth().heightIn(min = RegistrationLayout.buttonHeight)
                .auleShadow(AuleElevation.FLOATING, CircleShape, AuleShadowTint.ACCENT), shape = CircleShape) {
            Text(stringResource(R.string.register_start), style = MaterialTheme.typography.labelLargeEmphasized)
            Spacer(Modifier.width(AuleSpacing.sm))
            Icon(AuleGlyph.CHEVRON.asImageVector(), null, Modifier.size(AuleSpacing.lg))
        }
        Spacer(Modifier.height(AuleSpacing.lg))
        TextButton(onSignIn, contentPadding = PaddingValues(horizontal = AuleSpacing.xs)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(haveAccount); append(" ") }
                append(signIn)
            }, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        TextButton(onClick = {
            browserFailed = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, TRAVELER_SIGNUP_URL.toUri())) }.isFailure
        }) {
            Text(stringResource(R.string.register_traveler_link), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        if (browserFailed) AuleBanner(stringResource(R.string.register_error_no_browser), tone = AuleTone.ALERT)
    }
}

@Composable
private fun WelcomeRequirement(glyph: AuleGlyph, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = RegistrationLayout.requirementHeight), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.extraSmall, color = colors.surfaceContainerLowest) {
            Icon(glyph.asImageVector(), null, Modifier.size(AuleSpacing.xxl).padding(AuleSpacing.sm), colors.primary)
        }
        Spacer(Modifier.width(AuleSpacing.md))
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurface, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun registrationIllustrationShape(index: Int): Shape = when (index) {
    0 -> MaterialShapes.Cookie7Sided.toShape()
    1 -> MaterialShapes.Clover4Leaf.toShape()
    2 -> MaterialShapes.Sunny.toShape()
    else -> MaterialShapes.Clover8Leaf.toShape()
}

@Composable
internal fun registrationProfileShape(profile: ProfessionalProfile): Shape = registrationIllustrationShape(
    when (profile) {
        ProfessionalProfile.CONDUCTEUR -> 0
        ProfessionalProfile.CONTROLEUR -> 1
        ProfessionalProfile.INTERVENTION -> 2
        else -> 3
    },
)

@Composable
internal fun registrationModeShape(mode: ProfessionalTransportMode): Shape = registrationIllustrationShape(mode.ordinal)

private const val TRAVELER_SIGNUP_URL = "https://www.aule.fr/signup"
