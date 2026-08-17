package io.aule.android.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset

/**
 * Le débord horizontal : un contenu plus large que la gouttière qui le porte.
 *
 * Un `ListItem` Material pose sa propre marge de 16 dp. Placé dans une colonne
 * qui en applique déjà une, il se retrouve à 32 dp du bord et cesse d'être une
 * liste bord à bord : l'aplat de sélection ne touche plus les côtés, et la
 * cible tactile rétrécit d'autant.
 *
 * La tentation est d'écrire une marge négative. **Compose n'en a pas** :
 * `padding(-16.dp)` compile et lève `IllegalArgumentException: Padding must be
 * non-negative` à la première composition — un plantage que rien ne signale
 * avant l'écran. C'est exactement ce qui arrivait à l'écran de relève dès qu'une
 * ligne s'affichait.
 *
 * Ce modificateur fait ce que la marge négative promettait : il mesure l'enfant
 * avec les contraintes élargies de [gutter] de chaque côté, puis le replace à
 * gauche de la gouttière. Le parent, lui, ne voit que la largeur d'origine.
 */
fun Modifier.bleedHorizontal(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx() * 2
    val placeable = measurable.measure(constraints.offset(horizontal = extra))
    val width = if (constraints.maxWidth == Constraints.Infinity) {
        placeable.width
    } else {
        (placeable.width - extra).coerceAtLeast(0)
    }
    layout(width, placeable.height) {
        placeable.place(-gutter.roundToPx(), 0)
    }
}
