package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleCappedFontScale
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.token.AuleChrome
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleRadius
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Une action du menu flottant.
 *
 * Un libellé et un glyphe. Toutes **font** quelque chose : ouvrir un volet,
 * engager un service, lancer un formulaire.
 */
internal data class MapFabAction(
    val glyph: AuleGlyph,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Les actions contextuelles de la carte, repliées sous une carte flottante compacte.
 *
 * Plutôt qu'un empilement de grosses gélules disjointes couvrant la moitié de
 * la ville, les actions se déploient dans un panneau compact et unifié ancré
 * juste au-dessus du bouton de bascule.
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

    Column(
        modifier = modifier.padding(end = AuleSpacing.lg),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(160)) +
                scaleIn(
                    initialScale = 0.85f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = tween(200),
                ) +
                slideInVertically(
                    initialOffsetY = { it / 4 },
                    animationSpec = tween(200),
                ),
            exit = fadeOut(animationSpec = tween(120)) +
                scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = tween(150),
                ) +
                slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(150),
                ),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainerHigh,
                shadowElevation = AuleElevation.FLOATING.height(AuleTheme.night),
                border = BorderStroke(
                    AuleStroke.hairline,
                    colors.outlineVariant.copy(alpha = AuleAlpha.OUTLINE),
                ),
                modifier = Modifier.widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = AuleSpacing.xs),
                ) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = colors.outlineVariant.copy(alpha = AuleAlpha.OUTLINE),
                                modifier = Modifier.padding(horizontal = AuleSpacing.md),
                            )
                        }
                        MapActionRow(
                            action = action,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onExpandedChange(false)
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }

        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = { checked ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onExpandedChange(checked)
            },
            containerSize = ToggleFloatingActionButtonDefaults.containerSize(
                initialSize = AuleChrome.button,
                finalSize = AuleChrome.button,
            ),
            containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(
                initialSize = AuleRadius.md,
                finalSize = AuleChrome.button / 2,
            ),
            containerColor = { accent },
            modifier = Modifier.semantics {
                contentDescription = if (expanded) closeLabel else openLabel
            },
        ) {
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
    }
}

/**
 * Une ligne d'action dans le panneau unifié du menu.
 */
@Composable
private fun MapActionRow(
    action: MapFabAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    AuleCappedFontScale {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = AuleChrome.bar)
                .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            Icon(
                imageVector = action.glyph.asImageVector(),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(AuleControl.icon),
            )
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * À mi-course, l'icône bascule.
 */
private const val HALF_OPEN = 0.5f

/**
 * Les bornes du menu, et pourquoi le plafond décide seul.
 *
 * Les rangées portent `fillMaxWidth` : elles prennent la place qu'on leur
 * donne, elles n'en réclament pas. C'est donc le plafond qui fixe la largeur —
 * mesurée à 260 points sur le S21, texte compris.
 *
 * Il tient le plus long des quatre intitulés sur une ligne : « Signaler un
 * événement » demande 146 points de texte, et le chrome de la rangée — deux
 * gouttières, le glyphe, l'écart — en prend 60. Sous 206, il se replierait en
 * points de suspension, ce que `maxLines = 1` promet et qu'on ne veut pas voir
 * sur un intitulé d'action.
 *
 * Et il l'empêche de devenir une colonne : ce menu est posé au coin d'une carte
 * qu'on veut continuer de lire. Pleine largeur, il cesse d'être un menu pour
 * devenir un écran.
 *
 * Le plancher ne sert aucun texte — même « Lignes du réseau », le plus court,
 * tient bien en dessous. Il garde au menu la silhouette d'une plaque là où les
 * intitulés seraient brefs, plutôt qu'une bande étroite collée au bord.
 */
private val MENU_MIN_WIDTH = 190.dp
private val MENU_MAX_WIDTH = 260.dp
