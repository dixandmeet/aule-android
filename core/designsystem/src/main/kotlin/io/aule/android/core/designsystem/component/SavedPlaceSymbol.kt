package io.aule.android.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import io.aule.android.core.model.SavedPlaceIcon

/**
 * Le dessin d'une adresse favorite.
 *
 * La correspondance vit **ici** et non dans l'écran qui l'affiche, pour la même
 * raison que [AuleGlyph.asImageVector] : le modèle persiste une intention
 * — « crèche » —, le design system choisit le symbole. Écrire le nom d'une icône
 * Material dans la donnée enregistrée lierait les favoris de tout le monde à une
 * bibliothèque graphique, et un symbole renommé les rendrait illisibles.
 *
 * Tous génériques, aucun métier : une crèche, une salle de sport et un domicile
 * ne sont pas des objets de transport, et leur dessiner une icône Aule
 * laisserait croire à un lien avec le réseau qui n'existe pas. Le dépôt fait
 * exception — c'en est un.
 *
 * [filled] est l'état mis en avant : le raccourci qu'on est en train de suivre,
 * ou celui que l'éditeur a sous le doigt. Jamais une deuxième icône.
 */
fun SavedPlaceIcon.asImageVector(filled: Boolean = false): ImageVector = when (this) {
    SavedPlaceIcon.HOME -> if (filled) Icons.Filled.Home else Icons.Outlined.Home
    SavedPlaceIcon.WORK -> if (filled) Icons.Filled.Work else Icons.Outlined.Work
    SavedPlaceIcon.SCHOOL -> Icons.Outlined.School
    SavedPlaceIcon.GYM -> Icons.Outlined.FitnessCenter
    SavedPlaceIcon.FAMILY -> Icons.Outlined.Groups
    SavedPlaceIcon.SHOPPING -> Icons.Outlined.ShoppingBag
    SavedPlaceIcon.HEALTH -> Icons.Outlined.LocalHospital
    // Le seul symbole métier de la famille : un dépôt est un lieu du réseau, et
    // l'épingle générique n'en dirait rien.
    SavedPlaceIcon.DEPOT -> Icons.Outlined.Warehouse
    SavedPlaceIcon.STAR -> if (filled) Icons.Filled.Star else Icons.Outlined.Star
    SavedPlaceIcon.PIN -> AuleIcons.Stop
}
