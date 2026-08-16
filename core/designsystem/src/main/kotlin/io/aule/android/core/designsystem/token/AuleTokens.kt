package io.aule.android.core.designsystem.token

import io.aule.android.core.model.TransportMode

/**
 * L'identité Aule. Elle **ne suit pas l'ambiance**.
 *
 * Le vert de marque est celui de la carte en production ; il reste le même de
 * jour comme de nuit, contrairement à l'accent, qui s'adapte pour rester
 * lisible.
 */
object AuleBrand {
    val teal = AuleRgba(0x0D595E)
    val onRealtime = AuleRgba(0x06231A)
}

/**
 * Les onze rôles de couleur, en deux jeux.
 *
 * Une distinction qui se paie cher si on la rate : [accent] est un **aplat**, ce
 * qui s'écrit dessus est [onAccent], et [accentOnSurface] est l'accent **écrit**
 * sur la surface. De nuit les deux derniers sont aux antipodes. Confondre aplat
 * et encre rend un texte illisible sans qu'aucun test de mise en page ne le voie
 * — d'où les tests de contraste.
 */
data class AuleTokens(
    val accent: AuleRgba,
    val onAccent: AuleRgba,
    val accentOnSurface: AuleRgba,
    val realtime: AuleRgba,
    val delay: AuleRgba,
    val alert: AuleRgba,
    val surface: AuleRgba,
    val surfaceSolid: AuleRgba,
    val hairline: AuleRgba,
    val onSurface: AuleRgba,
    val onSurfaceMuted: AuleRgba,
) {
    companion object {
        val day = AuleTokens(
            accent = AuleRgba(0x0D595E),
            onAccent = AuleRgba(0xFFFFFF),
            accentOnSurface = AuleRgba(0x0D595E),
            realtime = AuleRgba(0x19B37B),
            delay = AuleRgba(0xE8A13C),
            alert = AuleRgba(0xD64545),
            surface = AuleRgba(0xFFFFFF, alpha = 0.90),
            surfaceSolid = AuleRgba(0xFFFFFF),
            hairline = AuleRgba(0x171717, alpha = 0.08),
            onSurface = AuleRgba(0x171717),
            onSurfaceMuted = AuleRgba(0x4A4A4A),
        )

        val night = AuleTokens(
            accent = AuleRgba(0x1A5C47),
            onAccent = AuleRgba(0xF1F6F3),
            accentOnSurface = AuleRgba(0x8AC79B),
            realtime = AuleRgba(0x41C895),
            delay = AuleRgba(0xF0B45C),
            alert = AuleRgba(0xE86060),
            surface = AuleRgba(0x0D1512, alpha = 0.95),
            surfaceSolid = AuleRgba(0x0D1512),
            hairline = AuleRgba(0xFFFFFF, alpha = 0.20),
            onSurface = AuleRgba(0xF3F5F7),
            onSurfaceMuted = AuleRgba(0xBFC7C3),
        )

        fun of(night: Boolean): AuleTokens = if (night) this.night else day
    }
}

/**
 * La couleur du marqueur d'un mode sur la carte.
 *
 * Elle vit dans le design system et non dans le modèle : un mode de transport
 * est un fait métier, sa teinte est une décision d'affichage.
 */
fun TransportMode.markerColor(night: Boolean): AuleRgba = when (this) {
    TransportMode.TRAM -> if (night) AuleRgba(0x8AC79B) else AuleRgba(0x0D595E)
    TransportMode.BOAT -> if (night) AuleRgba(0x5FA8C4) else AuleRgba(0x2E7D9A)
    TransportMode.BUS -> if (night) AuleRgba(0x979FAE) else AuleRgba(0x55665F)
}
