package io.aule.android.core.map.camera

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Port de `Native/AuleTests/CameraTests.swift`.
 *
 * La caméra est un module **pur** : aucune carte, aucune plateforme. C'est
 * précisément ce qui permet de vérifier ici un contrat visuel que personne
 * ne saurait juger en relisant du code.
 */
class NavigationCameraTest {

    private fun input(
        mode: CameraMode,
        speed: Double,
        heading: Double? = 90.0,
        sheet: Double = 0.0,
        routeBearing: Double? = null,
        viewportHeight: Double = 800.0,
        currentBearing: Double = 0.0,
    ) = CameraInput(
        mode = mode,
        center = Coordinate.NANTES,
        headingDegrees = heading,
        speedMps = speed,
        viewportHeight = viewportHeight,
        sheetHeightPx = sheet,
        currentBearing = currentBearing,
        routeBearingDegrees = routeBearing,
    )

    @Test
    fun `les modes libres ne pilotent pas la camera`() {
        assertNull(NavigationCamera.target(input(CameraMode.FREE_EXPLORE, speed = 0.0)))
        assertNull(NavigationCamera.target(input(CameraMode.OVERVIEW, speed = 0.0)))
    }

    @ParameterizedTest
    @EnumSource(value = CameraMode::class, names = ["FOLLOW", "NAVIGATION", "FOLLOW_VEHICLE"])
    fun `le zoom recule quand on accelere`(mode: CameraMode) {
        val stopped = requireNotNull(NavigationCamera.target(input(mode, speed = 0.0)))
        val cruising = requireNotNull(NavigationCamera.target(input(mode, speed = CRUISE_SPEED_MPS)))
        assertTrue(cruising.zoom < stopped.zoom, "cruising ${cruising.zoom} vs stopped ${stopped.zoom}")
    }

    @Test
    fun `au-dela de la vitesse de croisiere, le cadrage ne bouge plus`() {
        val cruising = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, speed = CRUISE_SPEED_MPS)))
        val faster = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, speed = CRUISE_SPEED_MPS * 3)))
        assertEquals(cruising.zoom, faster.zoom)
        assertEquals(cruising.pitch, faster.pitch)
    }

    @ParameterizedTest
    @EnumSource(value = CameraMode::class, names = ["FOLLOW", "NAVIGATION", "FOLLOW_VEHICLE"])
    fun `l inclinaison ne depasse jamais le plafond`(mode: CameraMode) {
        var speed = 0.0
        while (speed <= 40.0) {
            val target = requireNotNull(NavigationCamera.target(input(mode, speed = speed)))
            assertTrue(target.pitch <= MAX_PITCH, "pitch ${target.pitch} > $MAX_PITCH")
            assertTrue(target.pitch >= 0.0)
            speed += 2.5
        }
    }

    @Test
    fun `le reperage garde le nord, la navigation suit le cap`() {
        val following = requireNotNull(
            NavigationCamera.target(input(CameraMode.FOLLOW, speed = 5.0, heading = 123.0)),
        )
        assertEquals(0.0, following.bearing)

        val navigating = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, speed = 5.0, heading = 123.0)),
        )
        assertEquals(123.0, navigating.bearing)
    }

    /**
     * Le cas qui a motivé le repli sur le cap de l'itinéraire : on **démarre
     * une navigation à l'arrêt**, là où le GPS ne donne aucun cap. Sans
     * repli, la première seconde de guidage se joue dans une direction
     * arbitraire.
     */
    @Test
    fun `a l arret, le cap vient de l itineraire plutot que du GPS`() {
        val target = requireNotNull(
            NavigationCamera.target(
                input(CameraMode.NAVIGATION, speed = 0.0, heading = 300.0, routeBearing = 45.0),
            ),
        )
        assertEquals(45.0, target.bearing)
    }

    @Test
    fun `sans cap ni itineraire, la camera garde l orientation courante`() {
        val target = requireNotNull(
            NavigationCamera.target(
                input(
                    CameraMode.NAVIGATION,
                    speed = 0.0,
                    heading = null,
                    routeBearing = null,
                    currentBearing = 210.0,
                ),
            ),
        )
        assertEquals(210.0, target.bearing)
    }

    /**
     * Le décalage se calcule sur la **bande visible**, volet déduit. Sans
     * cela, un volet à mi-hauteur repousse le puck derrière lui.
     */
    @Test
    fun `le decalage avant se reduit quand le volet monte`() {
        val bare = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, speed = 8.0, sheet = 0.0)))
        val covered = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, speed = 8.0, sheet = 500.0)))
        assertTrue(covered.forwardOffsetPx < bare.forwardOffsetPx)
    }

    @Test
    fun `le decalage reste dans les bornes du profil, meme sur un tres petit ecran`() {
        val profile = assertNotNull(CameraProfile.of(CameraMode.NAVIGATION))
        val target = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, speed = 8.0, viewportHeight = 100.0)),
        )
        assertTrue(target.forwardOffsetPx >= profile.minOffsetPx)
        assertTrue(target.forwardOffsetPx <= profile.maxOffsetPx)
    }
}
