package io.aule.android.core.map

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Ce qu'il advient des couches quand le style **disparaît sous elles**.
 *
 * Le style meurt deux fois par usage ordinaire : à chaque bascule d'ambiance —
 * `setStyle` vide l'ancien avant de charger le suivant — et au démontage de la
 * carte. Les couches, elles, sont écrites **hors du registre** : le ticker caméra
 * pousse le puck toutes les 66 ms, l'écran carte publie la flotte à chaque
 * sondage, le volet des lignes repeint les tracés au doigt. Aucun de ces
 * appelants n'apprend que la carte vient de disparaître sous lui.
 *
 * Le registre le savait et se taisait : `markAllUnmounted()` ne faisait que vider
 * un drapeau, quand les couches, elles, gardaient leurs sources. Sur iOS la même
 * faute tuait l'app — `MLNInvalidStyleSourceException`, donc `SIGABRT`, à la
 * première forme publiée. Ici MapLibre marque ses objets `detached` au
 * `Style.clear()` et ignore l'écriture : ce qu'on tient dans ces tests n'est donc
 * pas un plantage, c'est le contrat qui nous en dispense.
 */
class MapLayerRegistryTest {

    /** Une couche témoin : elle ne pose rien, elle note ce que le registre lui dit. */
    private class SpyLayer(override val id: String = "aule.spy") : MapLayer {
        var mounts = 0
            private set
        var forgets = 0
            private set

        override fun mount(style: Style, map: MapLibreMap) {
            mounts++
        }

        override fun unmount(style: Style) = Unit

        override fun forgetStyle() {
            forgets++
        }
    }

    @Test
    fun `le registre annonce a ses couches que le style a disparu`() {
        val registry = MapLayerRegistry()
        val spy = SpyLayer()
        registry.register(spy)

        registry.styleWasDiscarded()

        assertEquals(
            1,
            spy.forgets,
            "une couche qui garde sa source après la mort du style écrit dans le vide",
        )
    }

    /**
     * ⚠️ Le registre n'a jamais monté cette couche — `mounted` est vide. Le
     * prévenir quand même est délibéré : oublier deux fois ne coûte rien, sauter
     * une couche que le registre croit démontée à tort coûte son affichage.
     */
    @Test
    fun `meme une couche que le registre ne croit pas montee est prevenue`() {
        val registry = MapLayerRegistry()
        val spy = SpyLayer()
        registry.register(spy)

        registry.styleWasDiscarded()
        registry.styleWasDiscarded()

        assertEquals(2, spy.forgets)
    }

    /**
     * L'autre moitié du contrat : oublier n'est pas démonter. Après un style
     * abandonné, le registre doit reposer **toutes** ses couches sur le suivant —
     * sans quoi la carte remonterait nue d'un simple passage en mode sombre.
     */
    @Test
    fun `apres un style abandonne tout est a remonter`() {
        val registry = MapLayerRegistry()
        val spy = SpyLayer()
        registry.register(spy)

        registry.styleWasDiscarded()

        assertFalse(registry.isMounted(spy.id))
        assertEquals(0, spy.mounts, "rien n'est monté tant qu'aucun style n'est chargé")
    }
}
