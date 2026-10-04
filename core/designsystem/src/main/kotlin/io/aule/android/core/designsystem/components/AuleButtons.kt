package io.aule.android.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.aulePress
import io.aule.android.core.designsystem.reduceMotionEnabled
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.R
import io.aule.android.core.designsystem.foundation.AuleHaptic
import io.aule.android.core.designsystem.foundation.AuleLayout
import io.aule.android.core.designsystem.foundation.AuleMotion
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.foundation.rememberAuleHaptics

/**
 * Les boutons d'Aule.
 *
 * Ce sont les boutons de Material 3 Expressive — mêmes ondulations, même sémantique, même
 * gestion des états — sous un contrat commun que le kit ne pose pas seul :
 *
 * - une **capsule** au repos qui se **carre** sous le doigt (`ButtonShapes`), le morphing que la
 *   landing joue au survol ;
 * - l'**enfoncement** à 0,96, en `graphicsLayer`, donc sans remettre l'écran en page ;
 * - un **retour haptique** d'action, léger, une fois par appui ;
 * - la hauteur du brief — 56 dp pour l'action d'un volet, 48 en compact — jamais les 40 dp de
 *   Material, qu'on vise debout dans un véhicule ;
 * - un état **occupé** qui garde le libellé et la couleur : un bouton qui se vide de son texte
 *   change de largeur au moment précis où l'on attend une réponse.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AulePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
    /**
     * L'aplat du rôle `error` au lieu de la marque : pour le seul geste qui ne se rattrape pas —
     * « Supprimer le compte ». Un rouge ailleurs userait le signal.
     */
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (destructive) colors.error else colors.primary
    val ink = if (destructive) colors.onError else colors.onPrimary
    val haptics = rememberAuleHaptics()
    val interaction = remember { MutableInteractionSource() }
    val busyLabel = stringResource(R.string.aule_common_busy)
    Button(
        onClick = {
            haptics.play(AuleHaptic.ACTION)
            onClick()
        },
        shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.small),
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) AuleLayout.buttonCompact else AuleLayout.button)
            .aulePress(interaction, AuleMotion.PRESSED_SCALE)
            .semantics {
                if (busy) {
                    disabled()
                    stateDescription = busyLabel
                }
            },
        enabled = enabled && !busy,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = ink,
            // Occupé, le bouton reste de la marque : c'est le même geste qui se termine, pas un
            // contrôle éteint. Désactivé pour de bon, il cesse d'être principal.
            disabledContainerColor = if (busy) container else colors.onSurface.copy(alpha = AuleOpacity.SKELETON),
            disabledContentColor = if (busy) ink else colors.onSurface.copy(alpha = AuleOpacity.DISABLED),
        ),
        contentPadding = PaddingValues(horizontal = AuleSpacing.xl, vertical = AuleSpacing.sm),
        interactionSource = interaction,
    ) {
        ButtonContent(label = label, icon = icon, busy = busy, ink = ink)
    }
}

/**
 * Le bouton tonal : une action secondaire qui reste de la marque.
 *
 * Sur le conteneur primaire du brief — le vert menthe —, et non sur l'aplat de sélection : un
 * bouton tonal est une action, une puce cochée est un état, et les deux ne doivent pas se
 * ressembler.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleTonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAuleHaptics()
    val interaction = remember { MutableInteractionSource() }
    val busyLabel = stringResource(R.string.aule_common_busy)
    FilledTonalButton(
        onClick = {
            haptics.play(AuleHaptic.ACTION)
            onClick()
        },
        shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.small),
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) AuleLayout.buttonCompact else AuleLayout.button)
            .aulePress(interaction, AuleMotion.PRESSED_SCALE)
            .semantics {
                if (busy) {
                    disabled()
                    stateDescription = busyLabel
                }
            },
        enabled = enabled && !busy,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            disabledContainerColor = if (busy) colors.primaryContainer else colors.onSurface.copy(alpha = AuleOpacity.SKELETON),
            disabledContentColor = if (busy) colors.onPrimaryContainer else colors.onSurface.copy(alpha = AuleOpacity.DISABLED),
        ),
        contentPadding = PaddingValues(horizontal = AuleSpacing.lg, vertical = AuleSpacing.sm),
        interactionSource = interaction,
    ) {
        ButtonContent(label = label, icon = icon, busy = busy, ink = colors.onPrimaryContainer)
    }
}

/**
 * Le bouton à contour : une action qu'on propose sans la recommander.
 *
 * Se déconnecter, annuler, repartir — ce qui doit rester atteignable sans peser autant que
 * l'action de marque. Transparent, à l'encre du texte, sous le liseré des champs : c'est le
 * `.outlinedButton` du web, à la hauteur de l'action principale pour que deux boutons
 * superposés fassent une seule colonne. Il se carre sous le doigt et s'occupe comme les autres.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAuleHaptics()
    val interaction = remember { MutableInteractionSource() }
    val busyLabel = stringResource(R.string.aule_common_busy)
    val faded = colors.onSurface.copy(alpha = AuleOpacity.DISABLED)
    OutlinedButton(
        onClick = {
            haptics.play(AuleHaptic.ACTION)
            onClick()
        },
        shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.small),
        modifier = modifier
            .defaultMinSize(minHeight = if (compact) AuleLayout.buttonCompact else AuleLayout.button)
            .aulePress(interaction, AuleMotion.PRESSED_SCALE)
            .semantics {
                if (busy) {
                    disabled()
                    stateDescription = busyLabel
                }
            },
        enabled = enabled && !busy,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.onSurface,
            // Occupé, le bouton garde son encre : c'est le même geste qui se termine.
            disabledContentColor = if (busy) colors.onSurface else faded,
        ),
        border = BorderStroke(AuleStroke.hairline, if (enabled || busy) colors.outlineVariant else faded),
        contentPadding = PaddingValues(horizontal = AuleSpacing.xl, vertical = AuleSpacing.sm),
        interactionSource = interaction,
    ) {
        ButtonContent(label = label, icon = icon, busy = busy, ink = colors.onSurface)
    }
}

/**
 * Le bouton texte : une issue, un retrait, une action de rangée.
 *
 * @param prominent l'action qu'on propose (encre de marque) plutôt que celle par laquelle on
 *   repart (encre secondaire). Deux sorties de même poids ne se hiérarchisent plus.
 */
@Composable
fun AuleTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    prominent: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val ink = if (prominent) colors.primary else colors.onSurfaceVariant
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = AuleLayout.touch),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = ink),
        contentPadding = PaddingValues(horizontal = AuleSpacing.md, vertical = AuleSpacing.sm),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AuleLayout.glyph))
            Spacer(Modifier.width(AuleSpacing.sm))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ButtonContent(label: String, icon: ImageVector?, busy: Boolean, ink: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // L'indicateur prend la place de l'icône, ou s'ajoute devant le texte : la largeur ne
        // bouge que de la sienne, jamais de celle d'un libellé qui disparaît.
        AnimatedContent(
            targetState = busy,
            transitionSpec = {
                (fadeIn(AuleMotion.effectDefault()) + scaleIn(AuleMotion.spatialFast(), initialScale = 0.6f))
                    .togetherWith(fadeOut(AuleMotion.effectFast()))
            },
            label = "button-busy",
        ) { occupied ->
            when {
                occupied -> LoadingIndicator(modifier = Modifier.size(AuleLayout.glyph), color = ink)
                icon != null -> Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AuleLayout.glyph))
                else -> Unit
            }
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Les trois registres d'un bouton d'icône. */
enum class AuleIconButtonStyle { Standard, Tonal, Filled, Outlined }

/**
 * Un bouton d'icône, aux formes expressives du kit : rond au repos, carré sous le doigt.
 *
 * La cible reste 48 dp quel que soit le dessin ; `contentDescription` est obligatoire, parce
 * qu'un bouton d'icône sans nom est un bouton que TalkBack appelle « bouton ».
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AuleIconButtonStyle = AuleIconButtonStyle.Standard,
    enabled: Boolean = true,
    tint: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(AuleLayout.icon),
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
        )
    }
    when (style) {
        AuleIconButtonStyle.Standard -> IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
        AuleIconButtonStyle.Tonal -> FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
            ),
            content = content,
        )
        AuleIconButtonStyle.Filled -> FilledIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            content = content,
        )
        AuleIconButtonStyle.Outlined -> OutlinedIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
    }
}

/**
 * Le favori : le petit moment satisfaisant du brief.
 *
 * Tap → micro-échelle → l'icône change → ressort → haptique. L'étoile se remplit en
 * **rebondissant** — ressort spatial rapide, qui dépasse — et le retour tactile est celui du
 * succès quand on l'ajoute, de la sélection quand on la retire. La confirmation discrète, elle,
 * est à l'appelant : un mot dans un snackbar, jamais un dialogue.
 */
@Composable
fun AuleFavouriteToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    checkedIcon: ImageVector,
    uncheckedIcon: ImageVector,
    checkedDescription: String,
    uncheckedDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAuleHaptics()
    val still = reduceMotionEnabled()
    // L'étoile ne rebondit qu'au moment où elle change, pas à chaque composition : le drapeau
    // s'arme au geste et se désarme quand le ressort est retombé.
    var bounce by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bounce) 1.25f else 1f,
        animationSpec = AuleMotion.spatialFast(),
        label = "favourite-bounce",
        finishedListener = { bounce = false },
    )
    LaunchedEffect(bounce) { if (bounce && still) bounce = false }

    IconToggleButton(
        checked = checked,
        onCheckedChange = { wanted ->
            haptics.play(if (wanted) AuleHaptic.SUCCESS else AuleHaptic.SELECTION)
            if (!still) bounce = true
            onCheckedChange(wanted)
        },
        modifier = modifier,
        enabled = enabled,
    ) {
        AnimatedContent(
            targetState = checked,
            transitionSpec = {
                (fadeIn(AuleMotion.effectFast()) + scaleIn(AuleMotion.spatialFast(), initialScale = 0.7f))
                    .togetherWith(fadeOut(AuleMotion.effectFast()) + scaleOut(AuleMotion.effectFast(), targetScale = 0.7f))
            },
            label = "favourite-icon",
        ) { on ->
            Icon(
                imageVector = if (on) checkedIcon else uncheckedIcon,
                contentDescription = if (on) checkedDescription else uncheckedDescription,
                tint = if (on) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier
                    .size(AuleLayout.icon)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}

/**
 * Le bouton flottant d'Aule : rond, de la marque, et qui s'enfonce.
 *
 * `FloatingActionButton` de Material porte déjà l'ombre qui le rend touchable au-dessus de la
 * `MapView` — sans elle, c'est la carte qui reçoit le doigt. On lui donne la forme capsule du
 * brief plutôt que le carré arrondi du kit, et un retour d'action.
 */
@Composable
fun AuleFAB(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    tonal: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = rememberAuleHaptics()
    val interaction = remember { MutableInteractionSource() }
    val container = if (tonal) colors.primaryContainer else colors.primary
    val ink = if (tonal) colors.onPrimaryContainer else colors.onPrimary
    val pressed = modifier.aulePress(interaction, AuleMotion.PRESSED_SCALE)
    val click = {
        haptics.play(AuleHaptic.ACTION)
        onClick()
    }
    if (small) {
        SmallFloatingActionButton(
            onClick = click,
            modifier = pressed,
            shape = CircleShape,
            containerColor = container,
            contentColor = ink,
            interactionSource = interaction,
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    } else {
        FloatingActionButton(
            onClick = click,
            modifier = pressed,
            shape = CircleShape,
            containerColor = container,
            contentColor = ink,
            interactionSource = interaction,
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/** La taille d'un indicateur posé dans un bouton : celle d'un glyphe, pas d'une roue d'écran. */
@Suppress("unused")
private val BUTTON_INDICATOR = 20.dp
