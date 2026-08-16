package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/** Port de `TransportModeTests` dans `Native/AuleTests/DesignSystemTests.swift`. */
class TransportModeTest {

    @Test
    fun `les noms de l API sont reconnus, y compris le nom local du bateau`() {
        assertEquals(TransportMode.BUS, TransportMode.fromApiValue("bus"))
        assertEquals(TransportMode.TRAM, TransportMode.fromApiValue("tram"))
        assertEquals(TransportMode.TRAM, TransportMode.fromApiValue("Tramway"))
        assertEquals(TransportMode.BOAT, TransportMode.fromApiValue("navibus"))
        assertEquals(TransportMode.BOAT, TransportMode.fromApiValue("ferry"))
    }

    /**
     * Un mode inconnu n'est pas une erreur : c'est un enregistrement à écarter.
     * Rendre un défaut (« bus ») afficherait un véhicule inventé.
     */
    @Test
    fun `un mode inconnu ne rend rien, et ne prend pas un defaut`() {
        assertNull(TransportMode.fromApiValue("funiculaire"))
        assertNull(TransportMode.fromApiValue(null))
        assertNull(TransportMode.fromApiValue(""))
    }

    @Test
    fun `le vocabulaire tolere la casse et les espaces du flux`() {
        assertEquals(TransportMode.BUS, TransportMode.fromApiValue("  BUS  "))
    }
}
