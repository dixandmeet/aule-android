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
    val teal = AulePalette.Teal.T30
}

/**
 * Un rôle sémantique métier : une couleur, ce qu'on y écrit, et le couple
 * conteneur / encre pour les pastilles.
 *
 * Ce n'est pas un rôle Material. « Temps réel » et « retard » sont des faits
 * transport ; les coller sur `secondary` / `tertiary` les ferait passer pour
 * de la hiérarchie visuelle. Les écrans passent par ici.
 */
data class AuleSemanticRole(
    val color: AuleRgba,
    val onColor: AuleRgba,
    val container: AuleRgba,
    val onContainer: AuleRgba,
)

/**
 * Les rôles de couleur du HUD, en deux jeux.
 *
 * Une distinction qui se paie cher si on la rate : [accent] est un **aplat**,
 * ce qui s'écrit dessus est [onAccent], et [accentOnSurface] est l'accent
 * **écrit** sur la surface. De nuit les deux derniers sont aux antipodes.
 * Confondre aplat et encre rend un texte illisible sans qu'aucun test de mise
 * en page ne le voie — d'où les tests de contraste.
 *
 * Tout vient d'[AulePalette]. Un hex ici serait une deuxième source de vérité.
 */
data class AuleTokens(
    val accent: AuleRgba,
    val onAccent: AuleRgba,
    val accentOnSurface: AuleRgba,
    val realtime: AuleSemanticRole,
    val delay: AuleSemanticRole,
    val alert: AuleRgba,
    val onAlert: AuleRgba,
    val surface: AuleRgba,
    val surfaceSolid: AuleRgba,
    val hairline: AuleRgba,
    val onSurface: AuleRgba,
    val onSurfaceMuted: AuleRgba,
) {
    companion object {
        val day = AuleTokens(
            accent = AulePalette.Teal.T30,
            onAccent = AulePalette.Teal.T100,
            accentOnSurface = AulePalette.Teal.T30,
            realtime = AuleSemanticRole(
                color = AulePalette.Green.T60,
                onColor = AulePalette.Teal.T100,
                container = AulePalette.Hud.realtimeContainerDay,
                onContainer = AulePalette.Hud.realtimeOnContainerDay,
            ),
            delay = AuleSemanticRole(
                color = AulePalette.Amber.T70,
                onColor = AulePalette.Teal.T100,
                container = AulePalette.Hud.delayContainerDay,
                onContainer = AulePalette.Hud.delayOnContainerDay,
            ),
            alert = AulePalette.Red.T50,
            onAlert = AulePalette.Teal.T100,
            surface = AulePalette.Neutral.T100.opacity(0.90),
            surfaceSolid = AulePalette.Neutral.T100,
            hairline = AulePalette.Neutral.ink.opacity(0.08),
            onSurface = AulePalette.Neutral.ink,
            onSurfaceMuted = AulePalette.Neutral.inkMuted,
        )

        val night = AuleTokens(
            accent = AulePalette.Hud.nightFill,
            onAccent = AulePalette.Hud.nightOnFill,
            accentOnSurface = AulePalette.Hud.nightOnSurface,
            realtime = AuleSemanticRole(
                color = AulePalette.Hud.realtimeNight,
                onColor = AulePalette.Hud.realtimeOnNight,
                container = AulePalette.Hud.realtimeContainerNight,
                onContainer = AulePalette.Hud.realtimeOnContainerNight,
            ),
            delay = AuleSemanticRole(
                color = AulePalette.Hud.delayNight,
                onColor = AulePalette.Hud.delayOnNight,
                container = AulePalette.Hud.delayContainerNight,
                onContainer = AulePalette.Hud.delayOnContainerNight,
            ),
            alert = AulePalette.Hud.nightError,
            onAlert = AulePalette.Hud.nightOnError,
            surface = AulePalette.Neutral.T8.opacity(0.95),
            surfaceSolid = AulePalette.Neutral.T8,
            hairline = AulePalette.Neutral.T100.opacity(0.20),
            onSurface = AulePalette.Neutral.inkNight,
            onSurfaceMuted = AulePalette.Neutral.inkMutedNight,
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
    TransportMode.TRAM -> if (night) AulePalette.Hud.nightOnSurface else AulePalette.Teal.T30
    TransportMode.BOAT -> if (night) AuleRgba(0x5FA8C4) else AuleRgba(0x2E7D9A)
    TransportMode.BUS -> if (night) AuleRgba(0x979FAE) else AuleRgba(0x55665F)
}
