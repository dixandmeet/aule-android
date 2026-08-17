package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.token.AuleSpacing

/**
 * Le registre d'un message.
 *
 * [ALERT] est réservé à ce qui a échoué et que l'utilisateur peut corriger.
 * L'étendre à l'information rendrait le rouge banal, et le prochain vrai échec
 * se lirait comme le reste.
 */
enum class AuleTone { NEUTRAL, ALERT }

/**
 * Un message posé dans le flux, avec au plus une action.
 *
 * Material 3 n'a volontairement pas de composant « bandeau » : il propose la
 * `Snackbar` pour ce qui passe, et le `supportingText` d'un champ pour ce qui
 * concerne une saisie. Reste le message qui doit **demeurer** sans interrompre
 * — la tournée interrompue, le réseau absent — pour lequel Material recommande
 * une surface posée dans le flux. C'est ce composant, et c'est pourquoi il
 * n'est pas une redite d'un composant existant.
 *
 * Ce qu'il ajoute par-dessus la `Surface` : le registre, qui choisit le couple
 * conteneur/encre dans le thème plutôt qu'à chaque appel, et l'annonce en
 * région vivante — un texte qui apparaît après un appui n'est pas lu par
 * TalkBack si personne ne le lui demande, et l'échec de connexion resterait
 * muet pour qui ne voit pas l'écran.
 */
@Composable
fun AuleBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: AuleTone = AuleTone.NEUTRAL,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container = when (tone) {
        AuleTone.NEUTRAL -> colors.surfaceContainerHigh
        AuleTone.ALERT -> colors.errorContainer
    }
    val ink = when (tone) {
        AuleTone.NEUTRAL -> colors.onSurface
        AuleTone.ALERT -> colors.onErrorContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (tone == AuleTone.ALERT) error(message)
            },
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = ink,
    ) {
        Row(
            modifier = Modifier.padding(
                start = AuleSpacing.md,
                end = if (action != null) AuleSpacing.xs else AuleSpacing.md,
                top = AuleSpacing.sm,
                bottom = AuleSpacing.sm,
            ),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (action != null && onAction != null) {
                // Le bouton texte porte lui-même sa cible tactile de 48 dp et son
                // ondulation : c'est ce que le texte simplement cliquable d'avant
                // n'avait ni l'un ni l'autre.
                TextButton(onClick = onAction) {
                    Text(text = action)
                }
            }
        }
    }
}
