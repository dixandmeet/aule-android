package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import io.aule.android.core.designsystem.token.AuleControl
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
 *
 * ## L'alerte porte son signe
 *
 * Le registre ne tenait qu'à la couleur du conteneur, donc à une nuance de rose
 * sur un écran qu'on regarde vingt secondes entre deux manœuvres. Le triangle le
 * dit avant la couleur, et le dit encore quand l'écran est baissé au soleil. Il
 * reste décoratif pour TalkBack : le lecteur d'écran reçoit déjà
 * `error(message)`, qui est la version dite de ce triangle.
 *
 * Sa teinte est celle du conteneur — `onErrorContainer` — et non le rouge franc
 * `error`. La tentation était de le faire crier : un signe d'alerte a le droit
 * d'être plus fort que son texte. Sauf que de nuit `error` et `errorContainer`
 * sont deux crans voisins de la même rampe (`Red.T50` sur `Red.T30`), soit 2,9:1
 * — sous le minimum de 3:1 d'un signe qu'on doit distinguer. Un triangle qu'on
 * ne voit pas dans une cabine sans lumière ne dit rien du tout, et l'encre du
 * conteneur, elle, tient à 7,5:1. Le signe passe donc par la forme et non par un
 * troisième rouge.
 *
 * ## Et pourquoi il ne flotte pas
 *
 * Un bandeau est « posé **dans** le flux », pas au-dessus : c'est sa définition,
 * et c'est ce qui le distingue de la `Snackbar`. Une ombre le contredirait, et
 * elle coûterait cher là où il sert vraiment — trois bandeaux empilés dans une
 * colonne de profil, un bandeau dans un volet qui porte déjà l'ombre du volet.
 * La règle est celle de `SheetCard` : la nouvelle échelle des surfaces descend
 * assez bas pour qu'un aplat se suffise.
 *
 * Le texte prend le slot **appuyé** de son palier : mêmes taille et interligne,
 * donc aucune ligne ne se déplace, mais un message qui n'a pas le poids du
 * texte courant qu'il interrompt n'a pas de raison d'interrompre.
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
    val alert = tone == AuleTone.ALERT
    val container = if (alert) colors.errorContainer else colors.surfaceContainerHigh
    val ink = if (alert) colors.onErrorContainer else colors.onSurface
    // Le cran des cartes, pas celui des puces : le bandeau occupe la largeur, et
    // à pleine largeur un petit rayon se lit comme un rectangle mal coupé.
    val shape = MaterialTheme.shapes.medium

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (alert) error(message)
            },
        shape = shape,
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
            if (alert) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(AuleControl.icon),
                    // L'encre du conteneur, et non le rouge franc : de nuit
                    // celui-ci passe sous le contraste minimal d'un signe.
                    tint = ink,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMediumEmphasized,
                modifier = Modifier.weight(1f),
            )
            if (action != null && onAction != null) {
                // Le bouton texte porte lui-même sa cible tactile de 48 dp et son
                // ondulation : c'est ce que le texte simplement cliquable d'avant
                // n'avait ni l'un ni l'autre.
                //
                // Sa couleur est celle du bandeau et non `primary` : sur un
                // conteneur d'erreur rose, une action en teal est un troisième
                // registre dans un composant qui n'en porte que deux.
                TextButton(
                    onClick = onAction,
                    colors = ButtonDefaults.textButtonColors(contentColor = ink),
                ) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
    }
}
