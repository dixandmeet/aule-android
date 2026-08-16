package io.aule.android.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleShadowTint
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.auleTextStyle
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleRole
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Le champ de saisie Aule : libellé flottant, glyphe de tête, erreur sous le
 * champ.
 *
 * Le libellé monte au lieu de disparaître. Un texte d'invite qui s'efface à la
 * première frappe laisse un champ rempli dont plus rien ne dit ce qu'il
 * contient — invisible tant qu'on regarde un formulaire vide, gênant dès
 * qu'on le relit.
 *
 * L'erreur se pose **sous** le champ et non à sa place : la valeur fautive
 * reste à l'écran, c'est elle qu'on vient corriger.
 */
@Composable
fun AuleTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leading: AuleGlyph? = null,
    trailing: @Composable (() -> Unit)? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    contentType: ContentType? = null,
    imeAction: ImeAction = ImeAction.Done,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onImeAction: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val tokens = AuleTheme.tokens
    var focused by remember { mutableStateOf(false) }
    val lifted = focused || value.isNotEmpty() || error != null
    val lift by animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = tween(AuleMotion.POP_MS),
        label = "field-label",
    )
    val emphasis by animateFloatAsState(
        targetValue = if (focused || error != null) 1f else 0f,
        animationSpec = tween(AuleMotion.POP_MS),
        label = "field-emphasis",
    )
    val shape = RoundedCornerShape(AuleRadius.lg)
    val accent = if (error != null) tokens.alert.color else tokens.accentOnSurface.color
    val borderColor = if (error != null) {
        tokens.alert.color
    } else {
        lerp(tokens.hairline.color, accent, emphasis)
    }
    val glyphColor = when {
        error != null -> tokens.alert.color
        focused -> tokens.accentOnSurface.color
        else -> tokens.onSurfaceMuted.color
    }
    val fill = if (AuleTheme.night) {
        tokens.onSurface.color.copy(alpha = if (focused) 0.07f else 0.05f)
    } else {
        tokens.surfaceSolid.color.copy(alpha = if (focused) 0.92f else 0.72f)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuleControl.field)
                .auleShadow(
                    if (focused && error == null) AuleElevation.RESTING else AuleElevation.NONE,
                    shape,
                    AuleShadowTint.ACCENT,
                )
                .clip(shape)
                .background(fill)
                .border(
                    width = if (focused || error != null) AuleStroke.emphasis else AuleStroke.hairline,
                    color = borderColor,
                    shape = shape,
                )
                .clickable(
                    enabled = enabled,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { focusRequester.requestFocus() }
                .padding(start = if (leading == null) AuleSpacing.lg else AuleSpacing.md)
                .semantics(mergeDescendants = true) {
                    if (contentType != null) this.contentType = contentType
                    contentDescription = label
                    if (error != null) error(error)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(
                    modifier = Modifier
                        .size(AuleControl.icon)
                        .drawBehind { drawAuleGlyph(leading, glyphColor) },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(AuleControl.field)
                    .padding(start = if (leading == null) 0.dp else AuleSpacing.md)
                    .padding(end = AuleSpacing.xs),
            ) {
                BasicText(
                    text = label,
                    style = auleTextStyle(if (lift > 0.5f) AuleRole.KICKER else AuleRole.BODY)
                        .copy(
                            color = lerp(tokens.onSurfaceMuted.color, accent, emphasis),
                        ),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer { translationY = -lift * LABEL_LIFT.toPx() },
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = auleTextStyle(AuleRole.BODY).copy(color = tokens.onSurface.color),
                    cursorBrush = SolidColor(tokens.accentOnSurface.color),
                    keyboardOptions = KeyboardOptions(
                        capitalization = capitalization,
                        keyboardType = keyboardType,
                        imeAction = imeAction,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onImeAction() },
                        onDone = { onImeAction() },
                        onGo = { onImeAction() },
                    ),
                    visualTransformation = visualTransformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(bottom = AuleSpacing.md)
                        .graphicsLayer { alpha = 0.35f + lift * 0.65f }
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            trailing?.invoke()
        }
        if (error != null) {
            BasicText(
                text = error,
                style = auleTextStyle(AuleRole.KICKER).copy(color = tokens.alert.color),
                modifier = Modifier.padding(start = AuleSpacing.sm),
            )
        }
    }
}

/** De quoi loger le libellé au-dessus de la saisie sans qu'ils se touchent. */
private val LABEL_LIFT = 12.dp
