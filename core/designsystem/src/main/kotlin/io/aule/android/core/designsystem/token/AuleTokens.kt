package io.aule.android.core.designsystem.token

import io.aule.android.core.model.TransportMode

/**
 * L'identité Aule. Elle **ne suit pas l'ambiance**.
 *
 * Le teal de marque est celui de la carte en production ; il reste le même de
 * jour comme de nuit, contrairement à l'accent, qui s'adapte pour rester
 * lisible.
 */
object AuleBrand {
    val teal = AulePalette.Teal.T30
}

/**
 * Un rôle sémantique métier : un aplat, son encre, ce qu'on écrit dessus, et le
 * couple conteneur / encre pour les pastilles.
 *
 * Ce n'est pas un rôle Material. « Temps réel » et « retard » sont des faits
 * transport ; les coller sur `secondary` / `tertiary` les ferait passer pour
 * de la hiérarchie visuelle. Les écrans passent par ici.
 *
 * ## [color] et [ink] : la distinction qui manquait
 *
 * C'est la même que celle qui sépare `accent` d'`accentOnSurface`, et elle se
 * paie aussi cher quand on l'oublie.
 *
 * [color] est un **aplat** : le point qui pulse, une pastille pleine, le rôle
 * `tertiary` que Material pose sous un bouton. Elle est choisie pour être vue
 * en tant que couleur, et son seuil est celui des éléments non textuels.
 *
 * [ink] est la même notion **écrite sur une surface**. Elle existe parce que la
 * couleur vive ne tient pas ce rôle de jour : le vert du temps réel posé sur le
 * cartouche le plus soutenu donnait **2,00:1**, l'ambre du retard **1,62:1** —
 * pour 4,5:1 exigés. Autrement dit, « à l'approche » et « +3 min », les deux
 * informations qu'un conducteur lit d'un coup d'œil en plein soleil, étaient
 * les moins lisibles de leur rangée. Aucun test de mise en page ne pouvait le
 * voir : le texte était là, à la bonne taille, au bon endroit.
 *
 * De nuit, l'aplat est déjà une encre valable — il ressort à plus de 6:1 sur
 * tous les crans de conteneur — et les deux coïncident donc. C'est la clarté
 * des surfaces qui décide, pas la teinte.
 */
data class AuleSemanticRole(
    val color: AuleRgba,
    val ink: AuleRgba,
    val onColor: AuleRgba,
    val container: AuleRgba,
    val onContainer: AuleRgba,
)

/**
 * Deux tons d'une même famille, du plus profond au plus vif.
 *
 * Un dégradé de marque n'est pas une décoration : c'est ce qui distingue une
 * surface **portante** — l'action principale, l'arrêt recommandé, le bandeau
 * qui dit quoi faire — d'un aplat de couleur. Un aplat uni de teal profond est
 * correct et mort ; les deux mêmes tons dégradés en diagonale donnent une
 * surface qui a une source de lumière, et c'est tout ce qui sépare une
 * application d'un formulaire colorié.
 *
 * Deux tons, et pas trois : un dégradé à trois arrêts se remarque en tant que
 * dégradé, ce qui est exactement ce qu'on ne veut pas.
 */
data class AuleSweep(val from: AuleRgba, val to: AuleRgba)

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
    val accentSweep: AuleSweep,
    val accentGlow: AuleRgba,
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
            // Du teal de marque vers le cran juste au-dessus : le dégradé
            // s'éclaircit dans le sens de lecture, ce qui pousse l'œil vers
            // l'action.
            //
            // Il partait du cran 25, deux crans sous la marque, et l'écart des
            // deux bouts atteignait 1,88:1 — assez pour qu'on lise deux
            // couleurs plutôt qu'une surface éclairée, et pour que la
            // transition marque une bande au milieu du bouton. À 1,55:1 le
            // dégradé se sent sans se voir, ce qui est tout ce qu'on lui
            // demande. Il part par ailleurs du teal de marque exact : la
            // surface la plus visible du produit commence désormais sur la
            // couleur du produit.
            accentSweep = AuleSweep(from = AulePalette.Teal.T30, to = AulePalette.Teal.T40),
            accentGlow = AulePalette.Teal.T60,
            realtime = AuleSemanticRole(
                color = AulePalette.Green.T60,
                // Le cran 30, et non le 40 : sur le conteneur le plus soutenu,
                // le 40 plafonne à 4,15:1. Quatre dixièmes sous le seuil, et
                // c'est exactement le genre d'écart qu'on accepte « pour cette
                // fois » avant de le retrouver sur six écrans.
                ink = AulePalette.Green.T30,
                // Une encre **sombre**, comme la nuit le faisait déjà. Le blanc
                // qu'elle portait tombait à 2,60:1 sur ce vert : un aplat clair
                // ne prend pas d'encre claire, quelle que soit l'ambiance. La
                // symétrie jour / nuit avait masqué l'erreur — on la cherche
                // dans les valeurs qui diffèrent, jamais dans celles qui se
                // ressemblent.
                onColor = AulePalette.Green.T10,
                container = AulePalette.Hud.realtimeContainerDay,
                onContainer = AulePalette.Hud.realtimeOnContainerDay,
            ),
            delay = AuleSemanticRole(
                color = AulePalette.Amber.T70,
                ink = AulePalette.Amber.T40,
                onColor = AulePalette.Amber.T10,
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
            // De nuit, le dégradé part du cran le plus sombre de la rampe et
            // monte vers l'aplat HUD : la surface reste un fond, elle ne se met
            // pas à briller dans une cabine sans lumière.
            accentSweep = AuleSweep(from = AulePalette.Teal.T20, to = AulePalette.Hud.nightFill),
            accentGlow = AulePalette.Hud.nightOnSurface,
            realtime = AuleSemanticRole(
                color = AulePalette.Hud.realtimeNight,
                // De nuit, l'aplat **est** l'encre : sur les cinq crans de
                // conteneur nocturnes, il ne descend jamais sous 6:1. Les
                // dédoubler ici n'ajouterait qu'une valeur à maintenir.
                ink = AulePalette.Hud.realtimeNight,
                onColor = AulePalette.Hud.realtimeOnNight,
                container = AulePalette.Hud.realtimeContainerNight,
                onContainer = AulePalette.Hud.realtimeOnContainerNight,
            ),
            delay = AuleSemanticRole(
                color = AulePalette.Hud.delayNight,
                ink = AulePalette.Hud.delayNight,
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
    TransportMode.BUS -> if (night) AuleRgba(0x97A6A7) else AuleRgba(0x55665F)
}
