package io.aule.android.core.map

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import org.junit.jupiter.api.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/**
 * Le registre vit avec l'activité ; les couches, avec l'écran qui les pose.
 *
 * Les deux durées de vie ne coïncident pas — une déconnexion suffit à sortir
 * l'écran carte de la composition, et il revient ensuite — et c'est ce
 * décalage qui se vérifie ici. Monter pour de bon demanderait un `Style`
 * MapLibre, donc un appareil ; ce qui se vérifie sans, et qui suffit, c'est
 * **quelles instances** le registre tient et dans quel ordre : `mountPending`
 * ne monte rien d'autre que [MapLayerRegistry.layers], dans cet ordre-là.
 */
class MapLayerRegistryTest {

    /** Une couche qui ne dessine rien : seuls son id et son identité comptent. */
    private class FakeLayer(
        override val id: String,
        override val isAnimated: Boolean = false,
    ) : MapLayer {
        override fun mount(style: Style, map: MapLibreMap) = Unit
        override fun unmount(style: Style) = Unit
    }

    @Test
    fun `une couche remplace celle qui portait deja son id`() {
        val registry = MapLayerRegistry()
        val first = FakeLayer(STOPS)
        val second = FakeLayer(STOPS)

        registry.register(first)
        registry.register(second)

        assertEquals(1, registry.layers.size, "un id, une seule couche")
        assertSame(second, registry.layer(STOPS), "la derniere enregistree est celle qu'on monte")
    }

    @Test
    fun `le retour de l ecran carte monte les couches neuves`() {
        val registry = MapLayerRegistry()
        // Premier passage : l'écran pose ses couches.
        listOf(FakeLayer(STOPS), FakeLayer(VEHICLES), FakeLayer(PUCK, isAnimated = true))
            .forEach(registry::register)

        // L'écran quitte la composition : `MapController.detach` démonte tout,
        // mais le registre, lui, appartient à l'activité et reste debout.
        registry.markAllUnmounted()

        // L'écran revient : son `remember` a été jeté, les couches sont neuves,
        // et ce sont **elles** que ses effets alimenteront.
        val stops = FakeLayer(STOPS)
        val vehicles = FakeLayer(VEHICLES)
        val puck = FakeLayer(PUCK, isAnimated = true)
        listOf(stops, vehicles, puck).forEach(registry::register)

        assertEquals(3, registry.layers.size, "le retour ne duplique pas les couches")
        assertEquals(
            listOf<MapLayer>(stops, vehicles, puck),
            registry.layers,
            "le registre monterait sinon des couches que plus personne n'alimente",
        )
        assertFalse(registry.isMounted(STOPS), "rien n'est monté tant que le style n'est pas chargé")
    }

    @Test
    fun `le remplacement garde l ordre de superposition`() {
        val registry = MapLayerRegistry()
        registry.register(FakeLayer(STOPS))
        registry.register(FakeLayer(VEHICLES))
        registry.register(FakeLayer(PUCK, isAnimated = true))

        // Le puck doit rester au-dessus de tout ; les arrêts, en dessous. Une
        // couche réenregistrée par la fin passerait devant les autres.
        registry.register(FakeLayer(STOPS))

        assertEquals(listOf(STOPS, VEHICLES, PUCK), registry.layers.map { it.id })
    }

    @Test
    fun `une couche animee remplacee garde la boucle d images`() {
        val registry = MapLayerRegistry()
        registry.register(FakeLayer(PUCK, isAnimated = true))
        registry.register(FakeLayer(PUCK, isAnimated = true))

        assertEquals(true, registry.hasAnimatedLayer)
    }

    private companion object {
        const val STOPS = "stops"
        const val VEHICLES = "vehicles"
        const val PUCK = "puck"
    }
}
