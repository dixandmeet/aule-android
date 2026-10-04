package io.aule.android.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.foundation.AuleOpacity
import io.aule.android.core.designsystem.foundation.AuleLayout

/**
 * L'en-tête d'un volet : ce qu'on regarde, en une composition.
 *
 * Un **kicker** au-dessus — le mode, la section —, le **titre** au cran appuyé, une précision
 * dessous, et des pastilles d'état quand il y a un état à dire. Le titre est la question ou le
 * nom, jamais le nom de l'écran : « Où allez-vous ? » plutôt que « Recherche ».
 *
 * @param leading un bouton de retour, une pastille de ligne : ce qui précède le titre.
 * @param trailing les gestes sur ce que l'en-tête nomme — l'étoile, « Y aller ».
 * @param chips les pastilles d'état sous le titre.
 * @param wideSubtitle la précision passe sous [leading], sur toute la largeur. Pour une phrase
 *   qui compte jusqu'au bout : serrée contre une flèche de retour, « Tout est facultatif, et se
 *   coupe ici » perdait ses deux derniers mots (vu sur le S21 le 23/09/2026).
 * @param subtitleMaxLines limite de l'explication ; les consignes d'un formulaire peuvent
 *   demander toutes leurs lignes plutôt que le résumé d'une fiche.
 */
@Composable
fun AuleSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    subtitle: String? = null,
    subtitleIcon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chips: (@Composable RowScope.() -> Unit)? = null,
    compact: Boolean = false,
    wideSubtitle: Boolean = false,
    subtitleMaxLines: Int = 2,
) {
    val colors = MaterialTheme.colorScheme
    val subtitleRow: @Composable (String) -> Unit = { text ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.xs),
        ) {
            if (subtitleIcon != null) {
                Icon(
                    imageVector = subtitleIcon,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(AuleLayout.noteGlyph),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (kicker != null) {
                    Text(
                        text = kicker.uppercase(),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = title,
                    style = if (compact) {
                        MaterialTheme.typography.titleLargeEmphasized
                    } else {
                        MaterialTheme.typography.headlineSmallEmphasized
                    },
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null && !wideSubtitle) subtitleRow(subtitle)
            }
            trailing?.invoke(this)
        }
        if (subtitle != null && wideSubtitle) subtitleRow(subtitle)
        if (chips != null) {
            // Les pastilles **se replient** : à police agrandie, « Accessible » et le titre
            // d'une perturbation ne tiennent pas sur une ligne, et une pastille coupée ne dit
            // plus l'état qu'elle porte. `FlowRowScope` étend `RowScope` : l'appelant écrit
            // ses pastilles comme dans une rangée, et c'est la mise en page qui les replie.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
                content = chips,
            )
        }
    }
}

/**
 * La poignée d'un volet.
 *
 * À l'encre secondaire atténuée : présente, jamais lue en premier. Plus large que celle du kit —
 * elle dit où saisir la feuille, et une prise qu'on ne trouve pas du pouce ne sert qu'à décorer.
 */
@Composable
fun AuleSheetGrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = AuleLayout.gripWidth, height = AuleLayout.gripHeight)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AuleOpacity.SUBDUED),
                    shape = CircleShape,
                ),
        )
    }
}

/** Ce que la poignée prend au volet, bande comprise — publié parce qu'il entre dans une soustraction. */
val AuleSheetGripHeight: Dp = AuleLayout.gripHeight + AuleSpacing.md * 2
