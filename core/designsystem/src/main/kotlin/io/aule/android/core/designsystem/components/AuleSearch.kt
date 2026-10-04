package io.aule.android.core.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.foundation.AuleLayout
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * La capsule de recherche posée sur la carte : « Où allez-vous ? ».
 *
 * C'est un **bouton** — il ouvre la recherche — qui a la géométrie du champ qui la remplace :
 * même hauteur, même capsule, même loupe à la même place. Le passage de l'un à l'autre se lit
 * alors comme une continuité et non comme un changement d'écran. Elle est en verre, parce
 * qu'elle est la seule chose posée en permanence sur la carte, et que la ville doit se deviner
 * dessous.
 *
 * @param trailing ce que la capsule porte à sa droite — l'avatar du compte, comme le veut
 *   l'usage des cartes sur Android : le compte est là où l'on va quand on le cherche, sans
 *   coûter un bouton de plus sur la carte.
 */
@Composable
fun AuleSearchBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    AuleGlassCard(
        modifier = modifier.semantics { role = Role.Button },
        shape = CircleShape,
        onClick = onClick,
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AuleLayout.searchBar)
                .padding(start = AuleSpacing.lg, end = AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(AuleLayout.icon),
                tint = colors.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke(this)
        }
    }
}

/**
 * Recherche commune aux deux produits. Le filtre compact conserve 48 dp au minimum ;
 * une grande police augmente naturellement sa hauteur sans couper les caractères.
 * Le modèle garde la requête, le champ conserve la sélection et la composition du clavier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    requestFocus: Boolean = false,
    compact: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    imeAction: ImeAction = ImeAction.Search,
) {
    val colors = MaterialTheme.colorScheme
    val field = rememberTextFieldState(value)
    val latestValue = rememberUpdatedState(value)
    val latestOnValueChange = rememberUpdatedState(onValueChange)
    val focus = remember { FocusRequester() }
    LaunchedEffect(value) {
        if (value != field.text.toString()) field.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { text ->
            if (text != latestValue.value) latestOnValueChange.value(text)
        }
    }
    LaunchedEffect(requestFocus) { if (requestFocus) focus.requestFocus() }
    TextField(
        state = field,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) AuleLayout.touch else AuleLayout.searchBar)
            .then(if (requestFocus) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = textStyle,
        shape = CircleShape,
        placeholder = { Text(text = placeholder, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(AuleLayout.icon),
            )
        },
        trailingIcon = if (value.isEmpty()) null else {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = clearLabel,
                        modifier = Modifier.size(AuleLayout.icon),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        onKeyboardAction = onSearch?.let { action -> KeyboardActionHandler { action() } },
        contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
            top = AuleSpacing.xs,
            bottom = AuleSpacing.xs,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerHigh,
            unfocusedContainerColor = colors.surfaceContainerHigh,
            disabledContainerColor = colors.surfaceContainerHigh,
            cursorColor = colors.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}
