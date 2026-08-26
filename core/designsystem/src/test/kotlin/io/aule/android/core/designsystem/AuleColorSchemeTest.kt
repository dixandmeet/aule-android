package io.aule.android.core.designsystem

import io.aule.android.core.designsystem.token.AulePalette
import io.aule.android.core.designsystem.token.AuleTokens
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Le ColorScheme reprend les jetons HUD. S'il diverge ici, Material peint
 * l'application entière avec une autre charte que celle qu'on mesure.
 */
class AuleColorSchemeTest {

    @Test
    fun `le jour reprend les jetons HUD`() {
        val tokens = AuleTokens.day
        val scheme = auleLightColorScheme()

        assertEquals(tokens.accent.color, scheme.primary)
        assertEquals(tokens.onAccent.color, scheme.onPrimary)
        assertEquals(tokens.surfaceSolid.color, scheme.surface)
        assertEquals(tokens.onSurface.color, scheme.onSurface)
        assertEquals(tokens.onSurfaceMuted.color, scheme.onSurfaceVariant)
        assertEquals(tokens.hairline.color, scheme.outlineVariant)
        assertEquals(tokens.delay.container.color, scheme.tertiaryContainer)
        assertEquals(tokens.delay.onContainer.color, scheme.onTertiaryContainer)
    }

    @Test
    fun `la nuit pose l aplat HUD dans primaryContainer et l encre dans primary`() {
        val tokens = AuleTokens.night
        val scheme = auleDarkColorScheme()

        assertEquals(tokens.accentOnSurface.color, scheme.primary)
        assertEquals(tokens.surfaceSolid.color, scheme.onPrimary)
        assertEquals(tokens.accent.color, scheme.primaryContainer)
        assertEquals(tokens.onAccent.color, scheme.onPrimaryContainer)
        assertEquals(tokens.surfaceSolid.color, scheme.surface)
        assertEquals(tokens.onSurface.color, scheme.onSurface)
        assertEquals(tokens.onSurfaceMuted.color, scheme.onSurfaceVariant)
        assertEquals(tokens.hairline.color, scheme.outlineVariant)
        assertEquals(tokens.alert.color, scheme.error)
        assertEquals(AulePalette.Red.T10.color, scheme.onError)
        assertEquals(tokens.delay.color.color, scheme.tertiary)
    }

    /**
     * Un conteneur translucide laisse remonter la carte : le volet de saisie
     * devient illisible et la barre de navigation se salit de ce qui passe
     * dessous. Le verre d'Aule se demande — c'est `AuleTokens.surface` — il ne
     * se subit pas par le thème.
     */
    @Test
    fun `l echelle de conteneurs est opaque`() {
        listOf(auleLightColorScheme(), auleDarkColorScheme()).forEach { scheme ->
            listOf(
                "surfaceContainerLowest" to scheme.surfaceContainerLowest,
                "surfaceContainerLow" to scheme.surfaceContainerLow,
                "surfaceContainer" to scheme.surfaceContainer,
                "surfaceContainerHigh" to scheme.surfaceContainerHigh,
                "surfaceContainerHighest" to scheme.surfaceContainerHighest,
            ).forEach { (name, color) ->
                assertEquals(1f, color.alpha, "$name doit être opaque")
            }
        }
    }

    /**
     * Material lit les cinq crans comme une hiérarchie : la carte se détache du
     * fond, le menu de la page, le volet de la carte. Cinq crans identiques
     * suppriment la hiérarchie sans qu'aucun écran ne le signale.
     */
    @Test
    fun `les cinq crans de conteneur sont distincts`() {
        listOf(auleLightColorScheme(), auleDarkColorScheme()).forEach { scheme ->
            val ladder = listOf(
                scheme.surfaceContainerLowest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
            )
            assertEquals(ladder.size, ladder.distinct().size, "Les crans se répètent : $ladder")
        }
    }

    /**
     * `secondaryContainer` est l'aplat que Material pose sous **tout** ce qui
     * est sélectionné : puce de filtre, bouton segmenté, indicateur de barre de
     * navigation. Il a porté le conteneur du temps réel, et le produit affichait
     * alors un filtre actif dans la couleur qui signifie « donnée mesurée » deux
     * centimètres plus bas.
     *
     * Ce test interdit le retour en arrière. Le temps réel garde sa porte —
     * `AuleTheme.tokens.realtime` — qui n'a jamais eu besoin du `ColorScheme`.
     */
    @Test
    fun `le role secondaire ne porte pas le temps reel`() {
        listOf(
            AuleTokens.day to auleLightColorScheme(),
            AuleTokens.night to auleDarkColorScheme(),
        ).forEach { (tokens, scheme) ->
            listOf(
                "secondary" to scheme.secondary,
                "secondaryContainer" to scheme.secondaryContainer,
                "onSecondaryContainer" to scheme.onSecondaryContainer,
            ).forEach { (name, role) ->
                assertNotEquals(
                    tokens.realtime.color.color,
                    role,
                    "$name reprend la couleur du temps réel",
                )
                assertNotEquals(
                    tokens.realtime.container.color,
                    role,
                    "$name reprend le conteneur du temps réel",
                )
            }
        }
    }

    /**
     * Une sélection doit se distinguer d'une action. Deux conteneurs identiques,
     * et une puce de filtre active a exactement l'aspect du bouton qui lance le
     * guidage.
     */
    @Test
    fun `le conteneur secondaire se distingue du conteneur primaire`() {
        listOf(auleLightColorScheme(), auleDarkColorScheme()).forEach { scheme ->
            assertNotEquals(scheme.primaryContainer, scheme.secondaryContainer)
        }
    }

    @Test
    fun `l erreur de jour reste le cran qui tient le texte`() {
        val scheme = auleLightColorScheme()
        assertEquals(AulePalette.Red.T40.color, scheme.error)
        assertEquals(AuleTokens.day.onAlert.color, scheme.onError)
    }
}
