package io.aule.android.core.designsystem.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.aule.android.core.designsystem.token.AuleTouch

/**
 * Le sélecteur exclusif de la maison — un choix parmi deux, trois ou quatre.
 *
 * ## Ce qu'il remplace
 *
 * Quatre écrans — l'itinéraire, la desserte d'une ligne, le signalement, les
 * réglages du guet — dessinaient le **même** contrôle à la main : une `Row`
 * marquée `selectableGroup`, des `ToggleButton` pondérés, et le `when (index)`
 * qui distribue les formes connectées. Quatre copies d'une trentaine de lignes,
 * qui avaient déjà divergé — l'une posait un retour tactile, l'autre non ;
 * l'une bornait la hauteur au plancher tactile, l'autre laissait les 40 dp de
 * Material.
 *
 * ## Pourquoi le vrai `ButtonGroup` et non une `Row`
 *
 * Une `Row` place ; `ButtonGroup` **écoute**. C'est un `Layout` qui suit les
 * interactions de ses enfants : le segment sous le doigt s'élargit, ses voisins
 * se compriment d'autant, et la largeur totale ne bouge pas. Ce mouvement est
 * la signature du composant chez Material 3 Expressive — c'est lui qui fait la
 * différence entre « trois boutons collés » et « un groupe ». Il ne s'obtient
 * pas depuis l'extérieur : `Modifier.animateWidth` n'existe que dans la portée
 * du groupe.
 *
 * Sur un écran qu'on vise sans le regarder, ce n'est pas de l'ornement : le
 * segment qui gonfle sous le pouce dit *lequel* on est en train d'enfoncer,
 * avant même de relâcher.
 *
 * ## Les segments se partagent la largeur
 *
 * Chacun porte `weight(1f)` : le groupe occupe toute la ligne, et les segments
 * font la même largeur quelle que soit la longueur des libellés. Un groupe
 * dont les segments se règlent sur leur texte donnerait un « Marche » minuscule
 * à côté d'un « Transport en commun » — et une cible tactile de la taille du
 * mot le plus court.
 *
 * C'est aussi ce qui neutralise le débordement en menu que `ButtonGroup` sait
 * faire : à segments pondérés, la largeur désirée est exactement la largeur
 * disponible, et l'indicateur n'est jamais placé. On le fournit quand même —
 * l'API l'exige, et il reste la porte de sortie si un jour un groupe cesse
 * d'être pondéré.
 *
 * ## Un libellé se replie, il ne se coupe pas
 *
 * Les segments tenaient leur libellé sur **une** ligne et coupaient le reste.
 * Sur le sélecteur de desserte, cette coupe emportait la moitié de la réponse :
 * la ligne 1 se termine à « François Mitterrand / **Jamet** » et à « Beaujoire
 * / **Babinière** », et ce sont précisément les deux branches — les deux
 * endroits où le tram peut vous emmener — qui disparaissaient dans les points
 * de suspension. Un sélecteur qui cache ce entre quoi il fait choisir a cessé
 * de faire choisir.
 *
 * Deux lignes, donc, et une hauteur commune : `IntrinsicSize.Max` mesure le
 * segment le plus haut et impose sa hauteur aux autres. Sans ça, un segment à
 * une ligne resterait court à côté d'un segment à deux, et le groupe connecté —
 * dont tout l'effet tient à ce que les pastilles forment **une** barre — se
 * lirait comme deux boutons de tailles différentes.
 *
 * @param options les choix, dans l'ordre où ils se lisent.
 * @param selected celui qui est actif ; `null` n'en allume aucun.
 * @param label le libellé d'un choix, résolu avant la construction du groupe.
 * @param onSelect appelé pour le choix touché — jamais pour celui déjà actif.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> AuleConnectedButtonGroup(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
) {
    if (options.isEmpty()) return

    val view = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    // La compression maximale d'un voisin, c'est sa marge intérieure : au-delà,
    // le segment mangerait son propre texte.
    val compressionLimit = ButtonDefaults.ContentPadding.calculateEndPadding(layoutDirection)

    // Les libellés se résolvent **ici** : le bloc de construction du groupe
    // n'est pas une composition, `stringResource` n'y a pas cours.
    val labels = options.map { label(it) }
    val interactionSources = remember(options.size) {
        List(options.size) { MutableInteractionSource() }
    }

    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = modifier
            .height(IntrinsicSize.Max)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            val text = labels[index]
            val choose = {
                // Le retour tactile est la moitié de la réponse : dans un
                // véhicule, on sait au doigt qu'on a touché un segment avant
                // d'avoir vérifié lequel.
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelect(option)
            }
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { wanted -> if (wanted) choose() },
                        enabled = enabled,
                        colors = colors,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex ->
                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        interactionSource = interactionSources[index],
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactionSources[index], compressionLimit)
                            // Material dessine ses boutons à 40 dp et compte sur
                            // la cible invisible pour les 48. Dans un véhicule,
                            // ce qu'on vise est ce qu'on voit.
                            .defaultMinSize(minHeight = AuleTouch.minimum)
                            // Un choix exclusif s'annonce comme un bouton radio,
                            // pas comme un interrupteur : c'est ce qui fait dire
                            // à TalkBack « 2 sur 3 » plutôt que « activé ».
                            .semantics { role = Role.RadioButton },
                    ) {
                        Text(
                            text = text,
                            maxLines = SEGMENT_LINES,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                },
                menuContent = {
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            choose()
                            it.dismiss()
                        },
                        enabled = enabled,
                        interactionSource = interactionSources[index],
                    )
                },
            )
        }
    }
}

/**
 * Le nombre de lignes qu'un libellé de segment peut prendre.
 *
 * Deux : c'est ce qu'il faut pour un terminus à branches — « François Mitterrand
 * / Jamet » — sur un segment de demi-écran. Trois n'apporterait rien qu'un
 * sélecteur haut comme une carte.
 */
private const val SEGMENT_LINES = 2
