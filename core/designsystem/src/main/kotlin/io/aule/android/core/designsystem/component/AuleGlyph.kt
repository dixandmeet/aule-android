package io.aule.android.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * La famille d'icônes d'Aule, côté appel.
 *
 * Un nom de glyphe n'est pas un dessin : c'est l'intention. Le dessin vient
 * ensuite, et il vient d'un [ImageVector] — Material Symbol pour tout ce qui
 * est générique, [AuleIcons] pour ce qui est métier transport. Dans les deux
 * cas, l'affichage passe par `Icon` de Material 3.
 *
 * Les valeurs métier restent dans cette énumération le temps que les écrans
 * appellent [AuleIcons] directement. Les valeurs génériques n'existent plus
 * que comme pont : un écran neuf doit prendre le Material Symbol.
 */
enum class AuleGlyph {
    MAIL, LOCK, EYE, EYE_OFF, SHIELD, SEARCH, BACK, CLOSE, MENU, SIGN_OUT,
    HEADING, PERSON, CHEVRON, CAMERA, EDIT, IMAGE, TRASH, CHECK,
    BUS, TICKET, PIN, TRAM, SUN, MOON, AUTO, FLAG, ROUTE, PLAY, SWAP,
}

/**
 * Le vecteur à afficher pour ce glyphe.
 *
 * [filled] est l'état sélectionné : un Material Symbol plein, ou la variante
 * pleine d'une icône métier. Ce n'est jamais une deuxième icône.
 */
fun AuleGlyph.asImageVector(filled: Boolean = false): ImageVector = when (this) {
    AuleGlyph.MAIL -> Icons.Outlined.Email
    AuleGlyph.LOCK -> Icons.Outlined.Lock
    AuleGlyph.EYE -> Icons.Outlined.Visibility
    AuleGlyph.EYE_OFF -> Icons.Outlined.VisibilityOff
    AuleGlyph.SHIELD -> if (filled) Icons.Filled.Shield else Icons.Outlined.Shield
    AuleGlyph.SEARCH -> Icons.Outlined.Search
    AuleGlyph.BACK -> Icons.AutoMirrored.Outlined.ArrowBack
    AuleGlyph.CLOSE -> Icons.Outlined.Close
    AuleGlyph.MENU -> Icons.Outlined.Menu
    AuleGlyph.SIGN_OUT -> if (filled) Icons.AutoMirrored.Filled.Logout else Icons.AutoMirrored.Outlined.Logout
    AuleGlyph.HEADING -> if (filled) AuleIcons.HeadingFilled else AuleIcons.Heading
    AuleGlyph.PERSON -> if (filled) Icons.Filled.Person else Icons.Outlined.Person
    AuleGlyph.CHEVRON -> Icons.AutoMirrored.Outlined.KeyboardArrowRight
    AuleGlyph.CAMERA -> Icons.Outlined.PhotoCamera
    AuleGlyph.EDIT -> Icons.Outlined.Edit
    AuleGlyph.IMAGE -> Icons.Outlined.Image
    AuleGlyph.TRASH -> Icons.Outlined.Delete
    AuleGlyph.CHECK -> if (filled) Icons.Filled.Check else Icons.Outlined.Check
    AuleGlyph.BUS -> AuleIcons.Bus
    AuleGlyph.TICKET -> AuleIcons.Ticket
    AuleGlyph.PIN -> if (filled) AuleIcons.StopFilled else AuleIcons.Stop
    AuleGlyph.TRAM -> AuleIcons.Tram
    AuleGlyph.SUN -> if (filled) Icons.Filled.LightMode else Icons.Outlined.LightMode
    AuleGlyph.MOON -> if (filled) Icons.Filled.DarkMode else Icons.Outlined.DarkMode
    AuleGlyph.AUTO -> if (filled) Icons.Filled.BrightnessAuto else Icons.Outlined.BrightnessAuto
    AuleGlyph.FLAG -> if (filled) AuleIcons.DestinationFilled else AuleIcons.Destination
    AuleGlyph.ROUTE -> AuleIcons.Route
    AuleGlyph.PLAY -> if (filled) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow
    AuleGlyph.SWAP -> Icons.Outlined.SwapVert
}
