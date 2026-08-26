package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * Un champ de formulaire de la charte web : le libellé au-dessus, puis la boîte.
 *
 * Port de `SpacePro/components/ui/form-field.tsx`. La différence avec le champ
 * Material n'est pas cosmétique, et c'est elle qu'on vient chercher : Material
 * fait **flotter** son libellé, qui vit donc dans la boîte et rétrécit quand on
 * saisit ; le web le pose **au-dessus**, à taille fixe, et la boîte ne contient
 * que la saisie.
 *
 * Ce que le libellé posé apporte, et qu'un libellé flottant ne peut pas donner :
 *
 * - il ne bouge pas. Sur un formulaire d'inscription de six champs, six
 *   libellés qui montent et rétrécissent à mesure qu'on avance font un écran
 *   qui remue pendant qu'on écrit ;
 * - il tient sur deux lignes. « Identifiant professionnel ou matricule » ne
 *   rentre pas dans une encoche de bordure : flottant, il se coupe ; posé, il
 *   se replie ;
 * - il laisse le **repère de saisie** libre. Le web s'en sert pour montrer la
 *   forme attendue (`prenom.nom@operateur.fr`), ce qu'un champ à libellé
 *   flottant ne peut pas faire — le repère y est déjà pris par le libellé.
 *
 * L'astérisque des champs obligatoires suit le web, à l'encre d'erreur, et il
 * est **doublé pour les lecteurs d'écran** : une étoile rouge n'est pas une
 * information tant qu'elle n'est pas dite.
 *
 * @param required marque le champ obligatoire, sans rien contrôler : la
 *   validation reste à l'écran, c'est lui qui sait ce qu'il exige et quand.
 * @param hint la consigne sous le champ — la contrainte qu'on veut lire *avant*
 *   de se tromper. Elle s'efface quand une erreur prend sa place : deux lignes
 *   sous un champ, dont l'une dit ce qui ne va pas, l'autre ne se lit plus.
 */
@Composable
fun AuleFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    required: Boolean = false,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    requiredLabel: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    fieldModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = fieldLabel(label, required, SpanStyle(color = colors.error)),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            // L'étoile ne se prononce pas : ce que le lecteur d'écran doit
            // entendre est le mot, et il remplace ici la ligne entière.
            modifier = if (required && requiredLabel != null) {
                Modifier.semantics { contentDescription = "$label, $requiredLabel" }
            } else {
                Modifier
            },
        )
        Spacer(modifier = Modifier.height(AuleSpacing.sm))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier.fillMaxWidth(),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let {
                {
                    // Le repère hérite du style de la saisie : au style de corps
                    // par défaut, il serait d'un point plus petit que ce qu'on
                    // tape, et le champ changerait de taille en se remplissant.
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                    ) {
                        Text(text = it, color = colors.onSurfaceVariant)
                    }
                }
            },
            trailingIcon = trailingIcon,
            isError = error != null,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = auleFieldColors(),
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier
                        .padding(top = NOTE_GLYPH_DROP)
                        .size(NOTE_GLYPH),
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
        } else if (hint != null) {
            Spacer(modifier = Modifier.height(AuleSpacing.sm))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le libellé, et son étoile.
 *
 * L'étoile est un caractère du texte et non une icône : elle doit se replier
 * avec le libellé quand il passe à la ligne, et hériter de sa taille quand
 * l'utilisateur grossit les polices du système.
 */
private fun fieldLabel(
    label: String,
    required: Boolean,
    mark: SpanStyle,
): AnnotatedString = if (!required) {
    AnnotatedString(label)
} else {
    buildAnnotatedString {
        append(label)
        withStyle(mark) { append(" *") }
    }
}

/**
 * Les couleurs du champ web : une boîte pleine, bordée d'un filet.
 *
 * Publiques parce qu'un champ n'est pas toujours un champ de formulaire : la
 * recherche d'un réseau garde son libellé flottant et sa loupe — c'est une
 * recherche, pas une donnée à saisir — mais elle doit être de la même matière
 * que les champs d'à côté, sans quoi l'étape a deux styles de boîte.
 *
 * Material laisse le conteneur transparent et compte sur la bordure seule. Sur
 * le lavis de l'écran d'accueil — un dégradé, un tracé, une vignette — un champ
 * transparent laisse passer le motif derrière la saisie. Le web remplit donc la
 * boîte (`bg-card`), et c'est ce remplissage qui fait qu'un formulaire posé sur
 * une image reste un formulaire.
 */
@Composable
fun auleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/** Le glyphe d'une note sous le champ, sous la grille d'icône ordinaire. */
private val NOTE_GLYPH = 14.dp

/**
 * Ce dont l'œil de l'icône descend pour s'aligner sur la première ligne du
 * texte. Une icône calée sur le haut de la boîte flotte au-dessus de la lettre.
 */
private val NOTE_GLYPH_DROP = 2.dp
