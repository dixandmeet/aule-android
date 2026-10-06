package io.aule.android.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.foundation.AuleOpacity
import androidx.compose.foundation.BorderStroke
import io.aule.android.core.designsystem.R
import io.aule.android.core.designsystem.foundation.LocalAuleSemantics
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.foundation.AuleLayout
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Les six niveaux d'un message d'Aule (§21 du brief).
 *
 * Chacun a sa surface, son encre **et son signe** : la couleur ne porte jamais seule le niveau.
 * Le composant qui le montre dépend du contexte — dans le flux ([AuleNotice]), flottant sur la
 * carte ([AuleNotice] `floating`), en passant ([AuleSnackbarHost]) —, jamais un dialogue par
 * défaut.
 */
enum class AuleNoticeLevel { Information, Success, Warning, Disruption, Approaching, ActionRequired }

/**
 * Un message posé dans le flux ou sur la carte, avec une action, une issue secondaire et une croix.
 *
 * Annoncé en région vivante : un texte qui apparaît après un appui n'est pas lu par TalkBack si
 * personne ne le lui demande. Une perturbation est annoncée comme une erreur.
 */
@Composable
fun AuleNotice(
    level: AuleNoticeLevel,
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    floating: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val semantics = LocalAuleSemantics.current
    val (container, ink) = when (level) {
        // Sur la carte, l'information est en verre — la même surface que la capsule de
        // recherche — pour que le chrome flottant se lise comme une seule famille.
        AuleNoticeLevel.Information ->
            (if (floating) colors.surface.copy(alpha = AuleOpacity.GLASS) else colors.surfaceContainerHigh) to colors.onSurface
        AuleNoticeLevel.Success -> semantics.liveContainer to semantics.onLiveContainer
        AuleNoticeLevel.Warning -> semantics.delayContainer to semantics.onDelayContainer
        AuleNoticeLevel.Disruption -> semantics.disruptionContainer to semantics.onDisruptionContainer
        AuleNoticeLevel.Approaching -> colors.primaryContainer to colors.onPrimaryContainer
        AuleNoticeLevel.ActionRequired -> colors.inverseSurface to colors.inverseOnSurface
    }
    val sign = icon ?: when (level) {
        AuleNoticeLevel.Information -> Icons.Outlined.Info
        AuleNoticeLevel.Success -> Icons.Outlined.CheckCircle
        AuleNoticeLevel.Warning -> Icons.Outlined.Schedule
        AuleNoticeLevel.Disruption -> Icons.Outlined.WarningAmber
        AuleNoticeLevel.Approaching -> Icons.Outlined.NotificationsActive
        AuleNoticeLevel.ActionRequired -> Icons.Outlined.ReportProblem
    }
    val actionInk = if (level == AuleNoticeLevel.ActionRequired) colors.inversePrimary else ink
    val actionButton: @Composable (String, () -> Unit) -> Unit = { label, onClick ->
        Box(
            modifier = Modifier
                .heightIn(min = AuleLayout.touch)
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = actionInk,
            )
        }
    }
    val spoken = listOfNotNull(title, detail).joinToString(". ")
    val semanticsModifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite
        if (level == AuleNoticeLevel.Disruption) error(spoken)
    }
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AuleSpacing.lg,
                    end = if (onDismiss != null) AuleSpacing.xs else AuleSpacing.lg,
                    top = AuleSpacing.md,
                    bottom = AuleSpacing.md,
                ),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = if (action != null && onAction != null) Alignment.Top else Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
            } else {
                Icon(imageVector = sign, contentDescription = null, tint = ink, modifier = Modifier.size(AuleLayout.icon))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = ink,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = DETAIL_ALPHA),
                        // ⚠️ **Trois lignes coupaient l'explication au milieu d'un mot.**
                        //
                        // Le plafond est là pour qu'un bandeau ne mange pas l'écran, pas pour
                        // tronquer une phrase : « Aule s'en sert pour vous placer sur le plan,
                        // trier les arrêt… » disait l'invite de localisation au premier
                        // lancement, faute de place — la même phrase tenait entière dans le
                        // bandeau de refus, qui ne porte qu'une action au lieu de deux et laisse
                        // donc plus de largeur au texte. Relevé en recette les 22/09/2026 matin
                        // et soir.
                        //
                        // ⚠️ **Et six ne suffisaient pas non plus.** Le texte de l'invite fait
                        // cent cinquante-trois caractères ; la colonne, coincée entre un
                        // symbole, une action et une fermeture, en tient vingt-deux par ligne.
                        // Il en faut sept. Dix laissent la marge sans lever la garde : un
                        // bandeau ne doit toujours pas manger l'écran.
                        //
                        // ⚠️ **La vraie étroitesse était ailleurs** : l'action partageait la
                        // rangée du texte et lui prenait la moitié de la largeur — « Continuer »
                        // à droite, l'explication en sept lignes à gauche (29/09/2026, Galaxy
                        // S21). L'action passe désormais sous le texte.
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // ⚠️ **Toujours sous le texte, aligné sur lui, comme `AuleNotice` d'iOS.** À côté,
                // elle prenait la moitié de la rangée : l'explication de l'invite de position et
                // « Le fournisseur de données ne répond pas… » s'étalaient sur cinq à sept lignes
                // (29/09/2026, Galaxy S21). Un titre court la garderait bien à côté, mais les
                // deux applications doivent poser le même bandeau de la même façon.
                //
                // ⚠️ **Pas un `TextButton`** : sa largeur minimale (58 dp) centre un libellé court,
                // et « Annuler » sortait décalé sous « Ligne suivie ». Le libellé s'aligne sur le
                // titre ; la hauteur reste celle d'une cible tactile.
                if (action != null && onAction != null) {
                    if (secondaryAction != null && onSecondaryAction != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md)) {
                            actionButton(action, onAction)
                            actionButton(secondaryAction, onSecondaryAction)
                        }
                    } else {
                        actionButton(action, onAction)
                    }
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.aule_common_dismiss),
                        tint = ink,
                        modifier = Modifier.size(AuleLayout.glyph),
                    )
                }
            }
        }
    }
    if (floating) {
        // Sur la carte, le message flotte : la surface du niveau, mais avec l'ombre basse et le
        // rayon des cartes flottantes, pour être de la même famille que la capsule de recherche.
        Surface(
            modifier = modifier.then(semanticsModifier),
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = ink,
            border = if (level == AuleNoticeLevel.Information) {
                BorderStroke(AuleStroke.hairline, colors.outlineVariant.copy(alpha = AuleOpacity.GLASS_EDGE))
            } else {
                null
            },
            shadowElevation = AuleElevation.floating,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier.then(semanticsModifier),
            shape = MaterialTheme.shapes.small,
            color = container,
            contentColor = ink,
            content = body,
        )
    }
}

/**
 * Ce qui parle **en passant** : « Ajouté à vos lieux », « Ligne suivie ».
 *
 * Le messager est fourni par l'écran racine ; un volet dit un mot et s'en va, sans savoir où le
 * snackbar se pose. Un message de succès n'a jamais de dialogue.
 */
@Stable
class AuleMessenger(private val scope: CoroutineScope, val host: SnackbarHostState) {
    fun say(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = action,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalAuleMessenger = staticCompositionLocalOf<AuleMessenger?> { null }

@Composable
fun rememberAuleMessenger(): AuleMessenger {
    val scope = rememberCoroutineScope()
    val host = remember { SnackbarHostState() }
    return remember(scope, host) { AuleMessenger(scope, host) }
}

/** Le snackbar d'Aule : surface inverse, coin des petites cartes, action à l'accent de nuit. */
@Composable
fun AuleSnackbarHost(messenger: AuleMessenger, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(hostState = messenger.host, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.small,
            containerColor = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            actionColor = colors.inversePrimary,
            dismissActionContentColor = colors.inverseOnSurface,
        )
    }
}

/** L'encre d'un texte de précision sur un conteneur teinté. */
private const val DETAIL_ALPHA = 0.86f
