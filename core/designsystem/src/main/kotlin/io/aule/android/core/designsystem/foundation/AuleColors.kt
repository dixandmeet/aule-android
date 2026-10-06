package io.aule.android.core.designsystem.foundation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.designsystem.token.AuleSemanticRole
import io.aule.android.core.designsystem.token.AuleSweep
import io.aule.android.core.designsystem.token.AuleTokens

/**
 * La palette de la langue Aule (voir `Langue Aule.pdf`, « Couleurs »).
 *
 * Tout descend de la graine turquoise `#006B5E`. Les valeurs sont celles du brief, à l'octet
 * près ; ce qui n'y figure pas — les crans intermédiaires de surface, les rôles de nuit — est
 * dérivé par les règles de teinte Material 3, à la même teinte que la graine.
 *
 * ⚠️ **Les couleurs des lignes de transport ne viennent pas d'ici** : elles restent celles du
 * GTFS, lues par `parseLineColor`. La marque n'a pas à recolorer le réseau.
 */
object AuleVoyageurPalette {
    val Primary = AuleRgba(0x006B5E)
    /** Accent du réseau illustré dans la scène professionnelle du web. */
    val PrimaryBright = AuleRgba(0x33BFA3)
    val OnPrimary = AuleRgba(0xFFFFFF)
    val PrimaryContainer = AuleRgba(0x7DF7C0)
    val OnPrimaryContainer = AuleRgba(0x00201A)

    /** L'accent de nuit : boutons, kicker, « à l'heure ». */
    val PrimaryDark = AuleRgba(0x5EDBBF)
    val OnPrimaryDark = AuleRgba(0x00382F)
    val PrimaryContainerDark = AuleRgba(0x005047)

    val Secondary = AuleRgba(0x315DA8)
    val OnSecondary = AuleRgba(0xFFFFFF)

    /**
     * L'aplat de **sélection** — puce active, rangée choisie, indicateur.
     *
     * Material y lit `secondaryContainer`. Le bleu secondaire du brief n'est « pas encore
     * mobilisé » ; le laisser ici teinterait chaque sélection en bleu, à deux centimètres du
     * turquoise qui signifie « actif ». La sélection reste donc de la famille de la marque, un
     * cran plus doux que le conteneur primaire, qui garde les moments qu'on veut voir.
     */
    val SelectionContainer = AuleRgba(0xCDEEE3)
    val OnSelectionContainer = AuleRgba(0x062E26)
    val SelectionContainerDark = AuleRgba(0x1E4A3F)
    val OnSelectionContainerDark = AuleRgba(0xC4EEDD)

    val Tertiary = AuleRgba(0x8A5000)
    val OnTertiary = AuleRgba(0xFFFFFF)
    val TertiaryContainer = AuleRgba(0xFFDCBE)
    val OnTertiaryContainer = AuleRgba(0x2E1500)
    val TertiaryDark = AuleRgba(0xFFB874)
    val OnTertiaryDark = AuleRgba(0x492900)
    val TertiaryContainerDark = AuleRgba(0x693C00)

    val SurfaceLowest = AuleRgba(0xFFFFFF)
    val Surface = AuleRgba(0xF6FAF8)
    val SurfaceLow = AuleRgba(0xEFF4F1)
    val SurfaceContainer = AuleRgba(0xE9EFEC)
    val SurfaceHigh = AuleRgba(0xE2E9E5)
    val SurfaceHighest = AuleRgba(0xDCE4DF)
    val OnSurface = AuleRgba(0x161D1A)
    val OnSurfaceVariant = AuleRgba(0x3F4945)
    val Outline = AuleRgba(0x6F7975)
    val OutlineVariant = AuleRgba(0xBEC9C4)

    /** « Nuit du poste de contrôle » : la surface inverse du brief, et le fond de nuit. */
    val InverseSurface = AuleRgba(0x0B1A17)
    val InverseSurfaceHigh = AuleRgba(0x143028)
    val InverseOnSurface = AuleRgba(0xE6F1EC)
    val InverseVariant = AuleRgba(0xB7CBC4)

    val NightSurfaceDim = AuleRgba(0x06110F)
    val NightSurfaceLow = AuleRgba(0x0F231E)
    val NightSurfaceHigh = AuleRgba(0x1A3830)
    val NightSurfaceHighest = AuleRgba(0x25443B)
    val NightSurfaceBright = AuleRgba(0x29443C)
    val NightOutline = AuleRgba(0x89938F)

    val Error = AuleRgba(0xBA1A1A)
    val OnError = AuleRgba(0xFFFFFF)
    val ErrorContainer = AuleRgba(0xFFDAD6)
    val OnErrorContainer = AuleRgba(0x410002)
    val ErrorDark = AuleRgba(0xFFB4AB)
    val OnErrorDark = AuleRgba(0x690005)
    val ErrorContainerDark = AuleRgba(0x93000A)

    val Scrim = AuleRgba(0x000000)
}

internal fun auleLightColorScheme(): ColorScheme = with(AuleVoyageurPalette) {
    lightColorScheme(
        primary = Primary.color,
        onPrimary = OnPrimary.color,
        primaryContainer = PrimaryContainer.color,
        onPrimaryContainer = OnPrimaryContainer.color,
        inversePrimary = PrimaryDark.color,
        secondary = Secondary.color,
        onSecondary = OnSecondary.color,
        secondaryContainer = SelectionContainer.color,
        onSecondaryContainer = OnSelectionContainer.color,
        tertiary = Tertiary.color,
        onTertiary = OnTertiary.color,
        tertiaryContainer = TertiaryContainer.color,
        onTertiaryContainer = OnTertiaryContainer.color,
        background = Surface.color,
        onBackground = OnSurface.color,
        surface = Surface.color,
        onSurface = OnSurface.color,
        surfaceVariant = SurfaceContainer.color,
        onSurfaceVariant = OnSurfaceVariant.color,
        surfaceTint = Primary.color,
        surfaceBright = SurfaceLowest.color,
        surfaceDim = SurfaceHighest.color,
        surfaceContainerLowest = SurfaceLowest.color,
        surfaceContainerLow = SurfaceLow.color,
        surfaceContainer = SurfaceContainer.color,
        surfaceContainerHigh = SurfaceHigh.color,
        surfaceContainerHighest = SurfaceHighest.color,
        inverseSurface = InverseSurface.color,
        inverseOnSurface = InverseOnSurface.color,
        error = Error.color,
        onError = OnError.color,
        errorContainer = ErrorContainer.color,
        onErrorContainer = OnErrorContainer.color,
        outline = Outline.color,
        outlineVariant = OutlineVariant.color,
        scrim = Scrim.color,
    )
}

internal fun auleDarkColorScheme(): ColorScheme = with(AuleVoyageurPalette) {
    darkColorScheme(
        primary = PrimaryDark.color,
        onPrimary = OnPrimaryDark.color,
        primaryContainer = PrimaryContainerDark.color,
        onPrimaryContainer = PrimaryContainer.color,
        inversePrimary = Primary.color,
        secondary = AuleRgba(0xAFC6FF).color,
        onSecondary = AuleRgba(0x002E69).color,
        secondaryContainer = SelectionContainerDark.color,
        onSecondaryContainer = OnSelectionContainerDark.color,
        tertiary = TertiaryDark.color,
        onTertiary = OnTertiaryDark.color,
        tertiaryContainer = TertiaryContainerDark.color,
        onTertiaryContainer = TertiaryContainer.color,
        background = InverseSurface.color,
        onBackground = InverseOnSurface.color,
        surface = InverseSurface.color,
        onSurface = InverseOnSurface.color,
        surfaceVariant = InverseSurfaceHigh.color,
        onSurfaceVariant = InverseVariant.color,
        surfaceTint = PrimaryDark.color,
        surfaceBright = NightSurfaceBright.color,
        surfaceDim = NightSurfaceDim.color,
        surfaceContainerLowest = NightSurfaceDim.color,
        surfaceContainerLow = NightSurfaceLow.color,
        surfaceContainer = InverseSurfaceHigh.color,
        surfaceContainerHigh = NightSurfaceHigh.color,
        surfaceContainerHighest = NightSurfaceHighest.color,
        inverseSurface = InverseOnSurface.color,
        inverseOnSurface = AuleRgba(0x24322E).color,
        error = ErrorDark.color,
        onError = OnErrorDark.color,
        errorContainer = ErrorContainerDark.color,
        onErrorContainer = ErrorContainer.color,
        outline = NightOutline.color,
        outlineVariant = OnSurfaceVariant.color,
        scrim = Scrim.color,
    )
}

/**
 * Les couleurs qui disent un **fait transport**, pas un cran de hiérarchie.
 *
 * « Mesuré », « en retard », « perturbé » ne sont pas `primary` ou `tertiary` : ce sont des
 * informations, et une information ne se lit jamais par la couleur seule — chaque usage porte
 * aussi un signe (point plein / anneau, tilde, icône) ou un mot.
 *
 * Les encres tiennent 4,5:1 sur les surfaces de leur ambiance ; le brief réserve les teintes de
 * ponctualité pâles au fond nuit, et c'est respecté : de jour, « à l'heure » est le turquoise
 * profond, jamais le pâle.
 */
@Immutable
data class AuleSemanticColors(
    /** Donnée mesurée en temps réel : encre sur surface. */
    val live: Color,
    /** Le conteneur « à l'approche » et son encre. */
    val liveContainer: Color,
    val onLiveContainer: Color,
    /** Un retard annoncé, une donnée théorique qu'on souligne. */
    val delay: Color,
    val delayContainer: Color,
    val onDelayContainer: Color,
    /** Une perturbation du réseau. */
    val disruption: Color,
    val disruptionContainer: Color,
    val onDisruptionContainer: Color,
    /** Une réussite discrète : favori posé, lieu enregistré. */
    val success: Color,
    /** Une information neutre venue du réseau. */
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

fun auleSemanticColors(night: Boolean): AuleSemanticColors = with(AuleVoyageurPalette) {
    if (night) {
        AuleSemanticColors(
            live = PrimaryDark.color,
            liveContainer = PrimaryContainerDark.color,
            onLiveContainer = PrimaryContainer.color,
            delay = TertiaryDark.color,
            delayContainer = TertiaryContainerDark.color,
            onDelayContainer = TertiaryContainer.color,
            disruption = ErrorDark.color,
            disruptionContainer = ErrorContainerDark.color,
            onDisruptionContainer = ErrorContainer.color,
            success = PrimaryDark.color,
            info = AuleRgba(0xAFC6FF).color,
            infoContainer = AuleRgba(0x17457F).color,
            onInfoContainer = AuleRgba(0xD9E2FF).color,
        )
    } else {
        AuleSemanticColors(
            live = Primary.color,
            liveContainer = PrimaryContainer.color,
            onLiveContainer = OnPrimaryContainer.color,
            delay = Tertiary.color,
            delayContainer = TertiaryContainer.color,
            onDelayContainer = OnTertiaryContainer.color,
            disruption = Error.color,
            disruptionContainer = ErrorContainer.color,
            onDisruptionContainer = OnErrorContainer.color,
            success = Primary.color,
            info = Secondary.color,
            infoContainer = AuleRgba(0xD9E2FF).color,
            onInfoContainer = AuleRgba(0x0B2E60).color,
        )
    }
}

val LocalAuleSemantics = staticCompositionLocalOf { auleSemanticColors(night = false) }

/**
 * Le pont vers les jetons du socle.
 *
 * `LineBadge`, `RealtimeDot`, `auleShadow` et quelques autres composants empruntés lisent encore
 * `AuleTheme.tokens`. Plutôt que de les réécrire, on leur sert les rôles Aule voyageur sous
 * la forme qu'ils attendent. Ce pont n'est pas une seconde source de vérité : chaque valeur
 * est celle du schéma de couleurs ou des couleurs sémantiques ci-dessus.
 */
internal fun auleBridgeTokens(night: Boolean): AuleTokens = with(AuleVoyageurPalette) {
    if (night) {
        AuleTokens(
            accent = PrimaryDark,
            onAccent = OnPrimaryDark,
            accentOnSurface = PrimaryDark,
            accentSweep = AuleSweep(PrimaryContainerDark, PrimaryDark),
            accentGlow = PrimaryDark,
            realtime = AuleSemanticRole(
                color = PrimaryDark,
                ink = PrimaryDark,
                onColor = OnPrimaryDark,
                container = PrimaryContainerDark,
                onContainer = PrimaryContainer,
            ),
            delay = AuleSemanticRole(
                color = TertiaryDark,
                ink = TertiaryDark,
                onColor = OnTertiaryDark,
                container = TertiaryContainerDark,
                onContainer = TertiaryContainer,
            ),
            alert = ErrorDark,
            onAlert = OnErrorDark,
            surface = InverseSurface.opacity(0.94),
            surfaceSolid = InverseSurface,
            hairline = AuleRgba(0xFFFFFF).opacity(0.18),
            onSurface = InverseOnSurface,
            onSurfaceMuted = InverseVariant,
        )
    } else {
        AuleTokens(
            accent = Primary,
            onAccent = OnPrimary,
            accentOnSurface = Primary,
            accentSweep = AuleSweep(Primary, AuleRgba(0x1F8A79)),
            accentGlow = PrimaryDark,
            realtime = AuleSemanticRole(
                color = Primary,
                ink = Primary,
                onColor = OnPrimary,
                container = PrimaryContainer,
                onContainer = OnPrimaryContainer,
            ),
            delay = AuleSemanticRole(
                color = Tertiary,
                ink = Tertiary,
                onColor = OnTertiary,
                container = TertiaryContainer,
                onContainer = OnTertiaryContainer,
            ),
            alert = Error,
            onAlert = OnError,
            surface = SurfaceLowest.opacity(0.92),
            surfaceSolid = Surface,
            hairline = OutlineVariant.opacity(0.78),
            onSurface = OnSurface,
            onSurfaceMuted = OnSurfaceVariant,
        )
    }
}
