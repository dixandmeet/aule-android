package io.aule.android.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.aule.android.core.designsystem.token.AuleStroke

/**
 * Les icônes métier d'Aule, en [ImageVector].
 *
 * Elles n'existent ici que parce qu'aucun Material Symbol ne porte leur
 * sémantique : un bus n'est pas une voiture, un arrêt n'est pas une
 * localisation générique, et le chevron de cap n'est pas une flèche de
 * navigation. Tout le reste de l'interface prend un Material Symbol.
 *
 * Elles s'affichent exclusivement via `Icon` de Material 3, pour hériter du
 * tint, de `LocalContentColor`, de la taille et de l'accessibilité.
 */
object AuleIcons {
    val Bus: ImageVector by lazy { bus() }
    val Tram: ImageVector by lazy { tram() }
    val Ticket: ImageVector by lazy { ticket() }
    val Stop: ImageVector by lazy { pin(filled = false) }
    val StopFilled: ImageVector by lazy { pin(filled = true) }
    val Heading: ImageVector by lazy { heading(filled = false) }
    val HeadingFilled: ImageVector by lazy { heading(filled = true) }
    val Route: ImageVector by lazy { route() }
    val Destination: ImageVector by lazy { flag(filled = false) }
    val DestinationFilled: ImageVector by lazy { flag(filled = true) }
}

private fun auleIcon(
    name: String,
    fill: Boolean = false,
    block: PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = "Aule.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = if (fill) SolidColor(Color.Black) else null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = AuleStroke.glyph.value,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    )
}.build()

private fun bus() = auleIcon("Bus") {
    roundRect(3.5f, 6.5f, 17f, 11f, 2.5f)
    moveTo(3.5f, 12f)
    lineTo(20.5f, 12f)
    circle(8f, 19f, 1.6f)
    circle(16f, 19f, 1.6f)
}

private fun tram() = auleIcon("Tram") {
    roundRect(4f, 8f, 16f, 9f, 2f)
    moveTo(12f, 4.5f)
    lineTo(12f, 8f)
    moveTo(8f, 4.5f)
    lineTo(16f, 4.5f)
    circle(8.5f, 19.5f, 1.5f)
    circle(15.5f, 19.5f, 1.5f)
}

private fun ticket() = auleIcon("Ticket") {
    roundRect(3.5f, 7f, 17f, 10f, 2f)
    moveTo(12f, 7f)
    lineTo(12f, 17f)
    circle(8f, 12f, 1.2f)
}

private fun pin(filled: Boolean) = auleIcon("Stop${if (filled) "Filled" else ""}", fill = filled) {
    moveTo(12f, 21f)
    quadTo(5f, 13f, 5f, 10f)
    quadTo(5f, 4.5f, 12f, 4.5f)
    quadTo(19f, 4.5f, 19f, 10f)
    quadTo(19f, 13f, 12f, 21f)
    close()
    circle(12f, 10f, 2.2f)
}

private fun heading(filled: Boolean) = auleIcon(
    "Heading${if (filled) "Filled" else ""}",
    fill = filled,
) {
    moveTo(12f, 2.8f)
    lineTo(19.5f, 20.5f)
    lineTo(12f, 16.6f)
    lineTo(4.5f, 20.5f)
    close()
}

private fun route() = auleIcon("Route") {
    moveTo(6f, 19f)
    lineTo(6f, 12f)
    quadTo(6f, 6.5f, 12f, 6.5f)
    lineTo(18.5f, 6.5f)
    moveTo(12f, 12f)
    quadTo(18.5f, 12f, 18.5f, 19f)
    moveTo(15.5f, 4f)
    lineTo(19.5f, 6.5f)
    lineTo(15.5f, 9f)
}

private fun flag(filled: Boolean) = auleIcon(
    "Destination${if (filled) "Filled" else ""}",
    fill = filled,
) {
    moveTo(12f, 3.5f)
    lineTo(21f, 20f)
    lineTo(3f, 20f)
    close()
    moveTo(12f, 10f)
    lineTo(12f, 14.5f)
    circle(12f, 17.2f, 1.1f)
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    lineTo(x + w, y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    lineTo(x + r, y + h)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    lineTo(x, y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    close()
}
