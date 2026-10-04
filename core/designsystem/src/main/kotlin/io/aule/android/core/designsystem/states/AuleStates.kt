package io.aule.android.core.designsystem.states

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.foundation.LocalAuleSemantics
import io.aule.android.core.designsystem.components.AulePrimaryButton
import io.aule.android.core.designsystem.components.AuleTextButton
import io.aule.android.core.designsystem.components.AuleTonalButton
import io.aule.android.core.designsystem.foundation.AuleElevation
import io.aule.android.core.designsystem.foundation.AuleExpressiveShapes
import io.aule.android.core.designsystem.foundation.AuleLayout
import io.aule.android.core.designsystem.foundation.AuleMotion
import io.aule.android.core.designsystem.foundation.AuleOpacity

/**
 * Un chargement lisible et annoncé.
 *
 * L'indicateur est celui de Material 3 Expressive — sept silhouettes qui se morphent l'une dans
 * l'autre — et il porte déjà la réduction d'animation du système et la sémantique de
 * progression. Le libellé dit **quoi** : « on charge » sans dire quoi ne renseigne personne.
 *
 * Il n'entre pas en cascade : un indicateur apparaît et disparaît au gré de l'état, et une
 * entrée qui rejoue à chaque caractère tapé ferait clignoter l'écran.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleLoadingState(
    label: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    if (centered) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = AuleSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        ) {
            LoadingIndicator(modifier = Modifier.size(INDICATOR_LARGE), color = colors.primary)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = AuleSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(modifier = Modifier.size(INDICATOR), color = colors.primary)
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * L'attente qui **prend la main** : une réponse qu'on ne peut rien faire d'autre qu'attendre.
 *
 * ## Le seul dialogue de l'application, et pourquoi il existe
 *
 * La règle de la maison est écrite ailleurs — « jamais un dialogue par défaut », voir
 * [io.aule.android.core.designsystem.components.AuleNotice]. Celui-ci est l'exception, et elle se
 * justifie par une mesure : relevé sur le S21 le 15/09/2026, un itinéraire demandé depuis une
 * fiche d'arrêt met plusieurs secondes à revenir du moteur. Pendant tout ce temps, le volet ne
 * montrait que des fantômes gris — le libellé « Recherche du meilleur trajet… » naissait sous
 * le bord du palier replié — et le bouton « Y aller » se lisait comme un bouton en panne. Un
 * fantôme dit *où* le contenu ira ; il ne dit pas *que quelque chose est en cours*.
 *
 * ## Ce qu'il promet
 *
 * L'indicateur est celui de Material 3 Expressive, le même qu'[AuleLoadingState] : il porte la
 * réduction d'animation du système et la sémantique de progression. Le titre dit ce qu'on
 * cherche, la précision dit pour quoi, et la sortie est **explicite**.
 *
 * ⚠️ **Un toucher à côté ne referme pas.** Une attente qu'on annule d'un doigt posé au mauvais
 * endroit perdrait la demande sans le dire ; le geste retour et le bouton, eux, sont des
 * décisions. Les deux mènent à [onCancel] : ici, refermer *est* renoncer.
 *
 * @param title ce qu'on cherche. Au présent, et suivi de points de suspension.
 * @param detail pour quoi — la destination, le plus souvent. `null` quand le titre se suffit.
 * @param cancelLabel le mot du renoncement. `null` pour une attente qu'on ne peut pas abréger —
 *   le geste retour reste alors sans effet, et c'est à l'appelant d'en répondre.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuleWaitDialog(
    title: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    cancelLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier,
        properties = DialogProperties(
            dismissOnBackPress = cancelLabel != null,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            shadowElevation = AuleElevation.lifted,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuleSpacing.xl, vertical = AuleSpacing.xl)
                    // Le dialogue s'ouvre après coup, sur une demande : sans région vivante,
                    // TalkBack ne dirait rien de l'attente qu'il vient d'imposer.
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            ) {
                LoadingIndicator(modifier = Modifier.size(INDICATOR_LARGE), color = colors.primary)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (cancelLabel != null) {
                    AuleTextButton(label = cancelLabel, onClick = onCancel, prominent = false)
                }
            }
        }
    }
}

/**
 * Un fantôme : la place que le contenu va prendre.
 *
 * ## Il ne scintille pas, et il ne respire pas non plus
 *
 * C'est la règle de la maison — « la carte bouge, l'interface non » — et elle a une seconde
 * raison, mesurée : une animation **infinie** empêche la fenêtre de devenir *idle*, et
 * `UiDevice.dumpWindowHierarchy` attend précisément cet état. Le parcours d'écran
 * (`DefilementFicheLigneTest`) lit l'arbre toutes les 300 ms ; sous un fantôme qui pulse, ses
 * lectures reviennent vides et la fiche d'arrêt « ne s'ouvre pas » alors qu'elle est à l'écran.
 * Vu deux fois sur le S21, sur un fantôme qui variait d'opacité.
 *
 * Ce qui dit « ça vient » est donc l'indicateur voisin — lui s'arrête avec la réponse — et la
 * **forme** du contenu à venir, qui suffit à ce qu'on ne lise pas un écran vide.
 *
 * Muet pour TalkBack : c'est l'état de chargement voisin qui parle.
 */
@Composable
fun AuleSkeleton(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AuleOpacity.SKELETON), shape)
            .clearAndSetSemantics {},
    )
}

/** Une rangée fantôme : médaillon, titre et précision. */
@Composable
fun AuleSkeletonRow(modifier: Modifier = Modifier, medallion: Boolean = true) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (medallion) AuleSkeleton(modifier = Modifier.size(AuleLayout.medallion), shape = CircleShape)
        Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm), modifier = Modifier.weight(1f)) {
            AuleSkeleton(modifier = Modifier.fillMaxWidth(0.62f).height(BONE_TITLE))
            AuleSkeleton(modifier = Modifier.fillMaxWidth(0.38f).height(BONE_TEXT))
        }
    }
}

/** Un tableau de départs fantôme : pastille, destination, attente. */
@Composable
fun AuleSkeletonDepartures(count: Int = 3, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        repeat(count) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small)
                    .padding(horizontal = AuleSpacing.md, vertical = AuleSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AuleSkeleton(modifier = Modifier.size(width = BONE_BADGE, height = BONE_TITLE))
                Column(verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm), modifier = Modifier.weight(1f)) {
                    AuleSkeleton(modifier = Modifier.fillMaxWidth(0.55f).height(BONE_TEXT))
                    AuleSkeleton(modifier = Modifier.fillMaxWidth(0.30f).height(BONE_TEXT))
                }
                AuleSkeleton(modifier = Modifier.size(width = BONE_WAIT, height = BONE_TITLE), shape = CircleShape)
            }
        }
    }
}

/** Deux lignes fantômes, pour un paragraphe qu'on attend. */
@Composable
fun AuleSkeletonLines(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        AuleSkeleton(modifier = Modifier.fillMaxWidth(0.9f).height(BONE_TEXT))
        AuleSkeleton(modifier = Modifier.fillMaxWidth(0.6f).height(BONE_TEXT))
    }
}

/**
 * Ce qu'on affiche quand il n'y a rien à afficher — et qui doit dire **pourquoi**.
 *
 * Avec icône, l'absence occupe l'écran et se centre : c'est le niveau 3 d'expressivité, le
 * médaillon prend une silhouette Aule — cookie, trèfle, soleil — et une action éventuelle. Sans
 * icône, l'absence n'est qu'une ligne dans une colonne qui continue.
 */
@Composable
fun AuleEmptyState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = AuleExpressiveShapes.cookie9,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val centered = icon != null
    val firstText = if (centered) 1 else 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (centered) AuleSpacing.xl else AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = AuleSpacing.sm)
                    .auleEnter(index = 0)
                    .size(AuleLayout.emptyMedallion)
                    .background(colors.primaryContainer, shape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(EMPTY_ICON),
                    tint = colors.onPrimaryContainer,
                )
            }
        }
        Text(
            text = title,
            style = if (centered) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMediumEmphasized,
            color = colors.onSurface,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.auleEnter(index = firstText),
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.auleEnter(index = firstText + 1),
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(AuleSpacing.xs))
            AuleTonalButton(
                label = action,
                onClick = onAction,
                modifier = Modifier.auleEnter(index = firstText + 2),
            )
        }
    }
}

/**
 * Une erreur contextualisée : ce qui a échoué, ce qui reste, comment réessayer.
 *
 * Le brief interdit « Une erreur est survenue ». Le titre nomme ce qui manque, [detail] dit
 * pourquoi — la phrase du réseau —, [kept] dit ce qui reste consultable, et le bouton réessaie.
 * Annoncée en région vivante, comme une erreur.
 */
@Composable
fun AuleErrorState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    kept: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.CloudOff,
    centered: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val semantics = LocalAuleSemantics.current
    val spoken = listOfNotNull(title, detail, kept).joinToString(". ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (centered) AuleSpacing.xl else AuleSpacing.md)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                error(spoken)
            },
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(if (centered) AuleLayout.emptyMedallion else ERROR_MEDALLION)
                    .background(semantics.disruptionContainer, AuleExpressiveShapes.cookie4),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = semantics.onDisruptionContainer,
                    modifier = Modifier.size(if (centered) EMPTY_ICON else AuleLayout.icon),
                )
            }
            if (!centered) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMediumEmphasized, color = colors.onSurface)
                    if (detail != null) {
                        Text(text = detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if (centered) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (kept != null) {
            Text(
                text = kept,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        if (retryLabel != null && onRetry != null) {
            Spacer(Modifier.height(AuleSpacing.xs))
            AulePrimaryButton(label = retryLabel, onClick = onRetry, compact = true)
        }
    }
}

/**
 * Le petit moment satisfaisant : une coche qui s'ouvre en trèfle.
 *
 * Elle entre sur un ressort spatial rapide — elle dépasse, puis se pose — et disparaît en
 * fondu. À réserver à ce qui vient d'aboutir : un lieu enregistré, un signalement envoyé.
 */
@Composable
fun AuleSuccessMark(
    visible: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = SUCCESS_SIZE,
) {
    val semantics = LocalAuleSemantics.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(AuleMotion.spatialFast(), initialScale = 0.4f) + fadeIn(AuleMotion.effectFast()),
        exit = scaleOut(AuleMotion.effectDefault(), targetScale = 0.6f) + fadeOut(AuleMotion.effectDefault()),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(semantics.liveContainer, AuleExpressiveShapes.clover4),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = semantics.onLiveContainer,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

/** L'attente d'un contenu en une ligne, avec de la place réservée à sa droite. */
@Composable
fun AuleInlineWait(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        AuleSkeleton(Modifier.width(BONE_BADGE).height(BONE_TITLE))
        AuleSkeleton(Modifier.width(BONE_WAIT).height(BONE_TITLE))
    }
}

private val INDICATOR = 28.dp
private val INDICATOR_LARGE = 44.dp
private val EMPTY_ICON = 40.dp
private val ERROR_MEDALLION = 48.dp
private val SUCCESS_SIZE = 40.dp
private val BONE_TITLE = 16.dp
private val BONE_TEXT = 12.dp
private val BONE_BADGE = 40.dp
private val BONE_WAIT = 52.dp
