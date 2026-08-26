package io.aule.android.core.map.camera

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ce que les bâtiments ont le droit de prendre à l'écran.
 *
 * Le contrat est un ordre, pas une valeur : la ville doit peser moins quand
 * on suit un trajet que quand on l'explore, et moins encore à l'approche
 * d'un carrefour. Les nombres exacts sont un réglage ; l'ordre, lui, est la
 * promesse.
 */
class BuildingEmphasisTest {

    @Test
    fun `en exploration, la ville est pleine`() {
        assertEquals(BuildingEmphasis.FULL, BuildingEmphasis.of(guiding = false))
    }

    @Test
    fun `pendant un guidage, elle recule`() {
        assertTrue(BuildingEmphasis.of(guiding = true) < BuildingEmphasis.of(guiding = false))
    }

    @Test
    fun `a l approche d un carrefour, elle recule encore`() {
        val cruising = BuildingEmphasis.of(guiding = true, maneuverFocus = 0.0)
        val junction = BuildingEmphasis.of(guiding = true, maneuverFocus = 1.0)
        assertTrue(junction < cruising, "$junction n'est pas sous $cruising")
        assertEquals(BuildingEmphasis.JUNCTION, junction, 1e-9)
    }

    @Test
    fun `l attenuation suit le carrefour sans palier`() {
        var previous = Double.MAX_VALUE
        var focus = 0.0
        while (focus <= 1.0) {
            val level = BuildingEmphasis.of(guiding = true, maneuverFocus = focus)
            assertTrue(level < previous, "à $focus : $level n'est pas sous $previous")
            previous = level
            focus += 0.1
        }
    }

    /**
     * ⚠️ Le cas qui compte : un doigt posé sur la carte fait passer la
     * caméra en exploration libre **sans annuler le trajet**. Rendre la ville
     * pleine à cet instant masquerait la route qu'on est en train de
     * vérifier — c'est pour cela que l'atténuation suit le guidage et non le
     * mode de caméra.
     */
    @Test
    fun `un geste pendant un guidage ne rend pas la ville pleine`() {
        assertEquals(
            BuildingEmphasis.of(guiding = true),
            BuildingEmphasis.of(guiding = true, followingVehicle = false),
        )
        assertTrue(BuildingEmphasis.of(guiding = true) < BuildingEmphasis.FULL)
    }

    @Test
    fun `suivre un vehicule attenue a peine`() {
        val vehicle = BuildingEmphasis.of(guiding = false, followingVehicle = true)
        assertTrue(vehicle < BuildingEmphasis.FULL)
        assertTrue(vehicle > BuildingEmphasis.of(guiding = true))
    }

    @Test
    fun `la ville ne disparait jamais`() {
        for (focus in listOf(0.0, 0.5, 1.0)) {
            for (guiding in listOf(true, false)) {
                val level = BuildingEmphasis.of(guiding, maneuverFocus = focus)
                assertTrue(level >= 0.4, "la 3D reste allumée : $level")
                assertTrue(level <= 1.0)
            }
        }
    }

    @Test
    fun `une imminence hors bornes ne sort pas des niveaux prevus`() {
        assertEquals(BuildingEmphasis.GUIDED, BuildingEmphasis.of(guiding = true, maneuverFocus = -2.0))
        assertEquals(BuildingEmphasis.JUNCTION, BuildingEmphasis.of(guiding = true, maneuverFocus = 5.0))
    }
}
