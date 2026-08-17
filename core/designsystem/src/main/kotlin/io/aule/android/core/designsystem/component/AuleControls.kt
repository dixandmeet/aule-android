package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Les couleurs d'un bouton rempli HUD.
 *
 * Material pose `primary` comme encre sur la surface. De nuit, cette encre
 * s'éclaircit (`accentOnSurface`) et ne peut plus servir d'aplat. L'action
 * principale reprend donc [AuleTheme.tokens] `accent`, l'aplat HUD.
 */
@Composable
fun auleAccentButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = AuleTheme.tokens.accent.color,
    contentColor = AuleTheme.tokens.onAccent.color,
)

/**
 * Un chargement lisible et annoncé, commun aux volets et aux écrans.
 *
 * La roue est celle de Material : elle porte déjà la réduction d'animation du
 * système et la sémantique de progression. Ce composant n'ajoute que le
 * libellé — « on charge » sans dire quoi ne renseigne personne.
 */
@Composable
fun AuleLoadingState(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AuleSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(AuleSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(AuleControl.icon),
            strokeWidth = AuleStroke.glyph,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Ce qu'on affiche quand il n'y a rien à afficher — et qui doit dire **pourquoi**.
 *
 * « Aucun passage » et « le fournisseur ne répond pas » sont deux absences
 * différentes. Les confondre fait dire à l'app qu'il n'y a pas de bus alors
 * qu'elle ne sait simplement pas.
 */
@Composable
fun AuleEmptyState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val centered = icon != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (centered) AuleSpacing.xl else AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = AuleSpacing.sm)
                    .size(EMPTY_ICON),
                tint = colors.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            style = if (centered) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = if (centered) FontWeight.Normal else FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/**
 * L'icône d'un état vide.
 *
 * Plus grande que la grille d'icône ordinaire — c'est une illustration, pas une
 * commande — mais pas au point de devenir le sujet de l'écran.
 */
private val EMPTY_ICON = 40.dp
