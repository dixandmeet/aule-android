package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Le poids d'un bouton.
 *
 * [DANGER] est teinté et non plein : le rouge d'alerte sur un aplat plein
 * n'atteint pas le contraste demandé au texte, et une action qui supprime ou
 * déconnecte n'a de toute façon pas à être le point le plus lumineux de
 * l'écran — elle a à être reconnaissable.
 */
enum class AuleButtonProminence { FILLED, TINTED, PLAIN, DANGER }

/**
 * Le bouton Aule.
 *
 * La lueur d'accent sous le bouton plein fait partie du composant, et elle
 * s'éteint pendant l'envoi : une ombre qui appelle le doigt sous un bouton qui
 * ne répond plus est un mensonge de 300 ms.
 */
@Composable
fun AuleButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominence: AuleButtonProminence = AuleButtonProminence.FILLED,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val tokens = AuleTheme.tokens
    val interactive = enabled && !loading
    val shape = RoundedCornerShape(AuleRadius.md)
    val background = when (prominence) {
        AuleButtonProminence.FILLED ->
            tokens.accent.color.copy(alpha = if (interactive) 1f else AuleAlpha.DISABLED)
        AuleButtonProminence.TINTED ->
            tokens.accent.color.copy(alpha = if (interactive) AuleAlpha.TINT else AuleAlpha.TINT / 2f)
        AuleButtonProminence.PLAIN -> Color.Transparent
        AuleButtonProminence.DANGER ->
            tokens.alert.color.copy(alpha = if (interactive) AuleAlpha.TINT else AuleAlpha.TINT / 2f)
    }
    val foreground = when (prominence) {
        AuleButtonProminence.FILLED -> tokens.onAccent.color
        AuleButtonProminence.TINTED -> tokens.accentOnSurface.color
        AuleButtonProminence.PLAIN -> tokens.onSurfaceMuted.color
        AuleButtonProminence.DANGER -> tokens.alert.color
    }
    val elevation = if (prominence == AuleButtonProminence.FILLED && interactive) {
        AuleElevation.LIFTED
    } else {
        AuleElevation.NONE
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AuleControl.height)
            .auleShadow(elevation, shape, AuleShadowTint.ACCENT)
            .clip(shape)
            .background(background)
            .clickable(enabled = interactive, onClick = onClick)
            .padding(horizontal = AuleSpacing.lg, vertical = AuleSpacing.md)
            .semantics {
                role = Role.Button
                if (!interactive) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            AuleBusyIndicator(color = foreground)
        } else {
            BasicText(
                text = title,
                style = auleTextStyle(AuleRole.BODY, FontWeight.SemiBold)
                    .copy(color = foreground, textAlign = TextAlign.Center),
                maxLines = 2,
            )
        }
    }
}

@Composable
fun AuleBusyIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = reduceMotionEnabled()
    val angle = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "busy")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(AuleMotion.SPIN_MS, easing = LinearEasing)),
            label = "busy-angle",
        )
        animated
    }
    Canvas(
        modifier = modifier
            .size(AuleControl.icon)
            .graphicsLayer { rotationZ = angle }
            .semantics { hideFromAccessibility() },
    ) {
        val stroke = Stroke(width = AuleStroke.glyph.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            style = stroke,
        )
    }
}

/** Un chargement lisible et annoncé, commun aux volets et aux écrans. */
@Composable
fun AuleLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuleBusyIndicator(color = tokens.accentOnSurface.color)
        BasicText(
            text = label,
            style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
        )
    }
}

@Composable
fun AuleSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AuleSpacing.sm, bottom = AuleSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = HANDLE_WIDTH, height = HANDLE_HEIGHT)
                .clip(RoundedCornerShape(AuleRadius.pill))
                .background(AuleTheme.tokens.onSurfaceMuted.color.copy(alpha = AuleAlpha.SUBDUED)),
        )
    }
}

/**
 * Ce qu'on affiche quand il n'y a rien à afficher — et qui doit dire **pourquoi**.
 *
 * « Aucun passage » et « le fournisseur ne répond pas » sont deux absences
 * différentes. Les confondre fait dire à l'app qu'il n'y a pas de bus alors
 * qu'elle ne sait simplement pas.
 */
@Composable
fun AuleEmptyState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    val tokens = AuleTheme.tokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        BasicText(
            text = title,
            style = auleTextStyle(AuleRole.TITLE, FontWeight.SemiBold).copy(color = tokens.onSurface.color),
        )
        if (detail != null) {
            BasicText(
                text = detail,
                style = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurfaceMuted.color),
            )
        }
    }
}

/** La poignée : assez large pour se saisir, assez fine pour ne pas être un objet. */
private val HANDLE_WIDTH = 36.dp
private val HANDLE_HEIGHT = 5.dp
