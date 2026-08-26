package io.aule.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleEnter
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleSpacing
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Les couleurs d'un bouton rempli HUD.
 *
 * Material pose `primary` comme encre sur la surface. De nuit, cette encre
 * s'éclaircit (`accentOnSurface`) et ne peut plus servir d'aplat. L'action
 * principale reprend donc [AuleTheme.tokens] `accent`, l'aplat HUD.
 *
 * Rien n'est dit ici de l'état désactivé, et c'est délibéré : Material y pose un
 * gris d'encre sur la surface courante, c'est-à-dire une couleur qui **n'est
 * plus celle de la marque**. C'est le bon signal — un bouton principal éteint
 * doit cesser d'être principal, pas devenir un teal pâle qu'on essaie encore
 * d'appuyer.
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
 *
 * ## Il n'entre pas en cascade, et c'est le seul du kit
 *
 * [auleEnter] est fait pour ce qui arrive **une fois** : une rangée de liste, un
 * volet qui s'ouvre. Un indicateur de chargement fait l'inverse — il apparaît et
 * disparaît au gré de l'état, à chaque caractère tapé dans la recherche, à
 * chaque rafraîchissement. La règle est écrite dans `PriseServiceScreen` à
 * propos de la cascade des lignes : une entrée qui rejoue sur du remue-ménage
 * d'état fait clignoter l'écran.
 *
 * S'y ajoute que l'entrée retarde de sa durée le seul signal que ce composant
 * existe pour donner — « l'application travaille ». Une requête qui répond en
 * 200 ms n'aurait montré qu'un fantôme en train de monter. La roue tourne déjà :
 * du mouvement, il y en a.
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
 *
 * ## Deux états vides, et un seul composant
 *
 * [icon] ne décore pas : il **choisit la forme**. Avec icône, l'absence occupe
 * l'écran — c'est le volet vide, le résultat introuvable, la panne — et le bloc
 * se centre. Sans icône, l'absence n'est qu'une ligne dans une colonne qui
 * continue, et elle s'aligne comme le reste. Deux composants pour cela auraient
 * divergé au premier écran pressé.
 *
 * ## Pourquoi soigner ce qui ne montre rien
 *
 * C'est l'écran qu'on voit quand le réseau tombe, donc en tournée, donc au pire
 * moment. Un état vide bâclé fait douter de tout le reste : si l'application ne
 * sait pas dire proprement qu'elle ne sait pas, que vaut ce qu'elle affirme ?
 * Le médaillon coûte un fond et une forme, et il fait la différence entre une
 * erreur et un trou.
 *
 * La forme du médaillon est celle des cartes du kit, pas un des polygones
 * expressifs : chacun de ceux-là a un emploi et un seul, et un état vide n'est
 * ni une pastille de mode, ni un état vivant, ni un compteur. La tuile arrondie
 * dit par ailleurs la bonne chose — elle rappelle celle de la marque, en petit.
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
    // La cascade compte à partir du médaillon quand il y en a un : le regard
    // doit se poser sur lui avant de lire le titre qui l'explique.
    val firstText = if (centered) 1 else 0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (centered) AuleSpacing.xl else AuleSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AuleSpacing.sm),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (icon != null) {
            val medallion = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .padding(bottom = AuleSpacing.sm)
                    .auleEnter(index = 0)
                    .size(EMPTY_MEDALLION)
                    // Pas d'ombre, et surtout pas la teintée : la lueur de
                    // marque est réservée à l'action principale, celle qu'elle
                    // désigne. Un état vide est l'exact contraire — c'est le
                    // contenu qui manque. Lui donner la lueur qui désigne le
                    // ferait passer pour ce qu'on attend de l'utilisateur, et
                    // le volet où il s'affiche porte de toute façon déjà son
                    // ombre.
                    .background(colors.primaryContainer, medallion),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(EMPTY_ICON),
                    // L'encre du conteneur, et non le gris secondaire : une
                    // icône grise sur un aplat de marque a l'air désactivée.
                    tint = colors.onPrimaryContainer,
                )
            }
        }
        Text(
            text = title,
            // Le titre centré monte d'un palier et prend le slot appuyé : c'est
            // la seule phrase de l'écran à ce moment-là, elle peut être le
            // titre. On évite `titleLarge`, dont les chiffres tabulaires
            // écriraient « 2 lignes » comme un tableau d'affichage.
            style = if (centered) {
                MaterialTheme.typography.headlineSmallEmphasized
            } else {
                MaterialTheme.typography.titleMediumEmphasized
            },
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
    }
}

/**
 * L'icône d'un état vide.
 *
 * Plus grande que la grille d'icône ordinaire — c'est une illustration, pas une
 * commande — mais pas au point de devenir le sujet de l'écran.
 */
private val EMPTY_ICON = 40.dp

/**
 * Le médaillon qui porte l'icône.
 *
 * Assez large pour que l'icône y respire — moitié moins haute que lui — et pas
 * plus : un médaillon qui dépasse la largeur d'une carte cesse d'être un
 * ornement et devient une illustration, qu'il faudrait alors dessiner.
 */
private val EMPTY_MEDALLION = 80.dp
