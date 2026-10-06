package io.aule.android.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import io.aule.android.core.designsystem.token.AuleSpacing

/** Champs remplis de /login ; le libellé reste annoncé lorsque la saisie remplace le repère. */
@Composable
internal fun AuthWebField(
    label: String, value: String, onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier, fieldModifier: Modifier = Modifier,
    enabled: Boolean, required: Boolean, requiredLabel: String?,
    error: String?, keyboardOptions: KeyboardOptions, keyboardActions: KeyboardActions,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        TextField(value, onValueChange, modifier = fieldModifier.fillMaxWidth().semantics {
            contentDescription = if (required && requiredLabel != null) "$label, $requiredLabel" else label
            if (error != null) this.error(error)
        }, enabled = enabled, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            trailingIcon = trailingIcon, isError = error != null,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            visualTransformation = visualTransformation, shape = MaterialTheme.shapes.small,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainer, unfocusedContainerColor = colors.surfaceContainer,
                disabledContainerColor = colors.surfaceContainer, errorContainerColor = colors.errorContainer,
                focusedIndicatorColor = colors.primary, unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent, errorIndicatorColor = colors.error,
            ))
        if (error != null) Text(error, color = colors.error, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = AuleSpacing.xs))
    }
}
