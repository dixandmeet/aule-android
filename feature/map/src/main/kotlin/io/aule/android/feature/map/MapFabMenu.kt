package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleRadius

/**
 * Une action du menu flottant.
 *
 * Un libellé et un glyphe. Toutes **font** quelque chose : ouvrir un volet,
 * engager un service, lancer un formulaire. Aucune ne se contente d'emmener —
 * c'est ce qui a permis de plier la barre du bas dans ce menu.
 */
internal data class MapFabAction(
    val glyph: AuleGlyph,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Les actions du conducteur, repliées sous un seul bouton.
 *
 * Prendre un service, relever un collègue, calculer un itinéraire, lister les
 * lignes, signaler, voir les correspondances : des gestes qu'on fait une fois
 * ou deux dans une journée, et qui occupaient jusqu'ici autant de cibles
 * permanentes — trois dans la barre du bas, le reste ici. La barre a disparu ;
 * tout tient sous ce bouton, et ne se déplie qu'au moment où on le cherche.
 *
 * Le bouton porte l'ombre du composant Material et pas celle du design system.
 * [ToggleFloatingActionButton] n'expose pas son élévation : sa forme et sa
 * taille changent pendant l'ouverture, une ombre posée par-dessus garderait le
 * contour de départ et on verrait deux halos décalés le temps du morphing.
 *
 * Le menu ne se referme pas tout seul après un clic : c'est [onExpandedChange]
 * qui le fait, avant de lancer l'action. L'ordre compte — l'action ouvre
 * souvent un écran, et un menu resté déplié dessous réapparaîtrait au retour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MapFabMenu(
    actions: List<MapFabAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return
    val view = LocalView.current
    val colors = MaterialTheme.colorScheme
    val accent = AuleTheme.tokens.accent.color
    val onAccent = AuleTheme.tokens.onAccent.color
    val openLabel = stringResource(R.string.fab_menu_open)
    val closeLabel = stringResource(R.string.fab_menu_close)

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { checked ->
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onExpandedChange(checked)
                },
                // Le bouton descend de 56 à 48, la mesure du chrome, dans les
                // deux états. Material tient ses 56 dp ouvert comme fermé ; sur
                // une carte, c'est le plus gros objet permanent de l'écran pour
                // un geste qu'on fait deux fois par service. À 48 il reste au
                // plancher tactile — donc atteignable sans viser, ce qui est
                // tout ce qu'on lui demande — et cesse de dominer le coin.
                //
                // Ce qu'il continue de faire, c'est **changer de forme** : carré
                // arrondi au repos, rond une fois ouvert, là où la croix
                // remplace le plus. La taille fixe rend d'ailleurs ce morphing
                // plus lisible qu'avant : une seule chose bouge.
                containerSize = ToggleFloatingActionButtonDefaults.containerSize(
                    initialSize = AuleChrome.bar,
                    finalSize = AuleChrome.bar,
                ),
                containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(
                    initialSize = AuleRadius.md,
                    // La moitié du côté : c'est ce qui fait un rond, et la seule
                    // valeur qu'aucune échelle de rayon ne peut porter — elle
                    // dépend de la taille du bouton, pas du cran de forme.
                    finalSize = AuleChrome.bar / 2,
                ),
                // L'aplat de marque ne bouge pas entre les deux états. Material
                // le fait virer au conteneur neutre à l'ouverture ; ici la croix
                // dit déjà l'état, et un bouton qui change de couleur **en même
                // temps** que de forme et de taille fait trois choses là où une
                // seule se lit.
                containerColor = { accent },
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) closeLabel else openLabel
                },
            ) {
                // La valeur est lue dans un état dérivé : `checkedProgress`
                // change à chaque image de l'animation, et le lire directement
                // recomposerait l'icône soixante fois par seconde pour deux
                // vecteurs.
                val glyph by remember {
                    derivedStateOf {
                        if (checkedProgress > HALF_OPEN) Icons.Outlined.Close else Icons.Outlined.Add
                    }
                }
                Icon(
                    imageVector = glyph,
                    contentDescription = null,
                    modifier = with(ToggleFloatingActionButtonDefaults) {
                        Modifier.animateIcon({ checkedProgress }, { onAccent }, iconSize())
                    },
                )
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onExpandedChange(false)
                    action.onClick()
                },
                // Material pose l'entrée à 56 dp et son libellé en `titleMedium`
                // — la mesure d'une rangée de liste. Six rangées de liste
                // empilées au-dessus d'une carte en couvrent la moitié : ici
                // l'entrée descend au chrome, et le libellé au cran du dessous,
                // appuyé pour ne rien perdre de sa présence. Même geste, deux
                // fois moins de ville cachée.
                modifier = Modifier.height(AuleChrome.bar),
                text = {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                },
                icon = {
                    Icon(
                        imageVector = action.glyph.asImageVector(),
                        contentDescription = null,
                    )
                },
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            )
        }
    }
}

/**
 * À mi-course, l'icône bascule.
 *
 * Plus tôt, la croix apparaît alors que le bouton a encore sa forme fermée ;
 * plus tard, elle arrive après que le menu s'est déplié.
 */
private const val HALF_OPEN = 0.5f
