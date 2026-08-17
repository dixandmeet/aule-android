package io.aule.android.feature.map

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.aule.android.core.designsystem.component.AuleGlyph
import io.aule.android.core.designsystem.component.asImageVector
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Une entrée de la barre de navigation.
 *
 * [active] est la destination courante : une seule à la fois, icône pleine
 * et indicateur Material. Le guide l'exige — une barre sans destination
 * active n'en est pas une.
 */
internal data class MapActionItem(
    val glyph: AuleGlyph,
    val label: String,
    val onClick: () -> Unit,
    val semanticsLabel: String? = null,
    val active: Boolean = false,
    val busy: Boolean = false,
)

/**
 * La barre de navigation, collée au bas de la fenêtre.
 *
 * Pleine largeur, une destination active, icônes pleines / outline, couleurs
 * du composant. [NavigationBar] répartit les items à poids égal : c'est ce
 * qui les garde tous visibles, contrairement à la barre flexible dont le
 * layout chassait les destinations hors de l'écran.
 */
@Composable
internal fun MapActionBar(
    items: List<MapActionItem>,
    modifier: Modifier = Modifier,
) {
    val destinations = items.withActiveDestination()
    if (destinations.isEmpty()) return
    val a11y = stringResource(R.string.rail_a11y)
    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = a11y },
    ) {
        destinations.forEach { item ->
            ActionBarItem(item = item)
        }
    }
}

@Composable
private fun RowScope.ActionBarItem(
    item: MapActionItem,
) {
    val view = LocalView.current
    val announced = item.semanticsLabel ?: item.label
    NavigationBarItem(
        selected = item.active,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            item.onClick()
        },
        enabled = !item.busy,
        modifier = Modifier.semantics { contentDescription = announced },
        icon = {
            if (item.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AuleControl.icon),
                    strokeWidth = AuleStroke.glyph,
                )
            } else {
                Icon(
                    imageVector = item.glyph.asImageVector(filled = item.active),
                    contentDescription = null,
                    modifier = Modifier.size(AuleControl.icon),
                )
            }
        },
        label = { Text(item.label) },
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(),
    )
}

/**
 * Une destination reste allumée, même si l'appelant n'en a marqué aucune :
 * le guide interdit une barre sans indicateur actif.
 */
private fun List<MapActionItem>.withActiveDestination(): List<MapActionItem> {
    if (isEmpty() || any { it.active }) return this
    return mapIndexed { index, item ->
        if (index == 0) item.copy(active = true) else item
    }
}
