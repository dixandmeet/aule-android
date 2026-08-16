package io.aule.android.core.designsystem.token

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Une couleur, en composantes brutes.
 *
 * Volontairement distincte du `Color` de Compose : le calcul de contraste doit
 * pouvoir tourner dans un test JVM sans amorcer Compose, et c'est ce calcul qui
 * garantit l'accessibilité. Une couleur qu'on ne peut pas mesurer est une
 * couleur dont on affirme la lisibilité au lieu de la vérifier.
 */
data class AuleRgba(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double = 1.0,
) {

    constructor(hex: Int, alpha: Double = 1.0) : this(
        red = ((hex shr 16) and 0xFF) / 255.0,
        green = ((hex shr 8) and 0xFF) / 255.0,
        blue = (hex and 0xFF) / 255.0,
        alpha = alpha,
    )

    fun opacity(value: Double): AuleRgba = copy(alpha = value)

    /**
     * La couleur au format ARGB d'Android.
     *
     * Sert aux surfaces qui ne sont pas Compose — les icônes de carte, dessinées
     * au `Canvas`, et le fond de la `MapView`.
     */
    val argb: Int
        get() = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or
            ((red * 255).toInt().coerceIn(0, 255) shl 16) or
            ((green * 255).toInt().coerceIn(0, 255) shl 8) or
            (blue * 255).toInt().coerceIn(0, 255)

    val color: Color
        get() = Color(
            red = red.toFloat(),
            green = green.toFloat(),
            blue = blue.toFloat(),
            alpha = alpha.toFloat(),
        )

    /**
     * Luminance relative au sens WCAG — canaux **linéarisés**.
     *
     * C'est celle qui entre dans le rapport de contraste, et elle seule.
     */
    val relativeLuminance: Double
        get() = 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)

    /**
     * Luminance **perçue** — coefficients Rec. 709 sur les canaux tels quels,
     * sans linéarisation.
     *
     * Distincte de [relativeLuminance], et ce n'est pas une approximation
     * paresseuse : elle sert à décider si un badge de ligne s'écrit en clair ou
     * en sombre sur sa couleur GTFS, et c'est empiriquement le bon critère pour
     * ce choix-là.
     */
    val perceivedLuminance: Double
        get() = 0.2126 * red + 0.7152 * green + 0.0722 * blue

    /** Rapport de contraste WCAG, de 1:1 à 21:1. */
    fun contrastRatio(against: AuleRgba): Double {
        val a = relativeLuminance
        val b = against.relativeLuminance
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun linearize(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}
