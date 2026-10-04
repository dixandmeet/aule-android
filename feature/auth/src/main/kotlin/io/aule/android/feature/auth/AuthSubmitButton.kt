package io.aule.android.feature.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.foundation.AuleLayout
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.foundation.AuleHaptic
import io.aule.android.core.designsystem.foundation.rememberAuleHaptics
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleSpacing

internal enum class AuthButtonPhase { READY, CONNECTING, CHECKING, GRANTED }

@Composable
internal fun AuthSubmitButton(phase: AuthButtonPhase, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reduced = reduceMotionEnabled()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberAuleHaptics()
    val label = stringResource(when (phase) {
        AuthButtonPhase.READY -> R.string.auth_submit
        AuthButtonPhase.CONNECTING -> R.string.auth_submitting
        AuthButtonPhase.CHECKING -> R.string.auth_verifying
        AuthButtonPhase.GRANTED -> R.string.auth_granted
    })
    val elevation by animateDpAsState(
        targetValue = if (pressed) AuleElevation.floating else AuleElevation.none,
        animationSpec = if (reduced) snap() else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "auth-button-light",
    )
    Button(
        onClick = { haptics.play(AuleHaptic.ACTION); onClick() },
        enabled = phase == AuthButtonPhase.READY,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary,
            disabledContentColor = colors.onPrimary,
        ),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = AuleSpacing.xl, vertical = AuleSpacing.md),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = AuleLayout.button)
            .testTag("auth-submit").aulePress(interaction)
            .graphicsLayer {
                shadowElevation = elevation.toPx()
                shape = CircleShape
                ambientShadowColor = colors.primary
                spotShadowColor = colors.primary
            }
            .semantics { stateDescription = label; liveRegion = LiveRegionMode.Polite },
    ) {
        if (phase == AuthButtonPhase.CONNECTING || phase == AuthButtonPhase.CHECKING) {
            CircularProgressIndicator(Modifier.size(AuleLayout.glyph), color = colors.onPrimary, strokeWidth = AuleStroke.glyph)
            Spacer(Modifier.width(AuleSpacing.sm))
        } else if (phase == AuthButtonPhase.GRANTED) {
            Icon(AuleGlyph.CHECK.asImageVector(), contentDescription = null)
            Spacer(Modifier.width(AuleSpacing.sm))
        }
        Text(text = label, style = MaterialTheme.typography.labelLargeEmphasized)
    }
}
