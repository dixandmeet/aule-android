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
     * Le contrat de la 3D : elle existe à l'échelle de la rue, elle
     * disparaît à l'échelle de la ville. Entre les deux on descend, sans
     * palier — un redressement d'un coup au milieu d'un pincement se voit.
     */
    @Test
    fun `a l echelle de la rue, l inclinaison reste entiere`() {
        assertEquals(MAX_PITCH, NavigationCamera.maxPitchForZoom(PITCH_FULL_ZOOM))
        assertEquals(MAX_PITCH, NavigationCamera.maxPitchForZoom(18.5))
    }

    @Test
    fun `en prenant de la hauteur, la carte passe a plat`() {
        assertEquals(0.0, NavigationCamera.maxPitchForZoom(PITCH_FLAT_ZOOM))
        assertEquals(0.0, NavigationCamera.maxPitchForZoom(11.0))
    }

    @Test
    fun `entre les deux, l inclinaison suit le zoom sans palier`() {
        var zoom = PITCH_FLAT_ZOOM
        var previous = -1.0
        while (zoom <= PITCH_FULL_ZOOM) {
            val pitch = NavigationCamera.maxPitchForZoom(zoom)
            assertTrue(pitch > previous, "à z$zoom : $pitch n'est pas au-dessus de $previous")
            assertTrue(pitch in 0.0..MAX_PITCH, "à z$zoom : $pitch hors bornes")
            previous = pitch
            zoom += 0.1
        }
        // Au milieu de la rampe, la moitié de l'inclinaison : la descente est
        // linéaire, et c'est ce qui la rend prévisible au doigt.
        val middle = (PITCH_FLAT_ZOOM + PITCH_FULL_ZOOM) / 2.0
        assertEquals(MAX_PITCH / 2.0, NavigationCamera.maxPitchForZoom(middle), 1e-9)
    }

    @Test
    fun `un zoom aberrant laisse la carte a plat plutot qu inclinee`() {
        assertEquals(0.0, NavigationCamera.maxPitchForZoom(Double.NaN))
        assertEquals(0.0, NavigationCamera.maxPitchForZoom(Double.NEGATIVE_INFINITY))
        assertEquals(0.0, NavigationCamera.maxPitchForZoom(Double.POSITIVE_INFINITY))
    }

    /**
     * Le va-et-vient complet, celui qu'on ne peut pas relire dans le code :
     * on couche la carte en prenant de la hauteur, on la relève en
     * redescendant, et on retrouve **exactement** l'inclinaison de départ.
     */
    @Test
    fun `ce qui est retire au dezoom est rendu au rezoom`() {
        var pitch = 55.0
        var owed: Double? = null

        // On monte : z17 → z13,5, par pas d'un quart de niveau.
        var zoom = 17.0
        while (zoom >= 13.5) {
            val decision = NavigationCamera.pitchForZoom(pitch, zoom, owed)
            owed = decision.owedPitch
            decision.pitch?.let { pitch = it }
            assertTrue(pitch <= NavigationCamera.maxPitchForZoom(zoom) + PITCH_STEP_EPSILON)
            zoom -= 0.25
        }
        assertEquals(0.0, pitch, PITCH_STEP_EPSILON, "à z13,5 la carte doit être à plat")
        assertEquals(55.0, assertNotNull(owed), 1e-9, "l'inclinaison retirée doit rester due")

        // On redescend, et la 3D revient d'elle-même.
        while (zoom <= 17.0) {
            val decision = NavigationCamera.pitchForZoom(pitch, zoom, owed)
            owed = decision.owedPitch
            decision.pitch?.let { pitch = it }
            zoom += 0.25
        }
        assertEquals(55.0, pitch, PITCH_STEP_EPSILON, "l'inclinaison de départ doit être rendue")
        assertNull(owed, "une dette soldée s'éteint")
    }

    @Test
    fun `au milieu de la remontee, on ne rend que ce que la rampe autorise`() {
        val decision = NavigationCamera.pitchForZoom(currentPitch = 0.0, zoom = 15.0, owedPitch = 55.0)
        // À z15 le plafond vaut la moitié de MAX_PITCH : on rend cela, pas 55.
        assertEquals(MAX_PITCH / 2.0, assertNotNull(decision.pitch), 1e-9)
        assertEquals(55.0, assertNotNull(decision.owedPitch), 1e-9)
    }

    /**
     * Le garde-fou qui distingue « relever ce qu'on a couché » de « le zoom
     * pilote l'inclinaison » : une carte posée à plat par une recherche
     * d'adresse n'a retiré son inclinaison à personne.
     */
    @Test
    fun `sans rien de du, zoomer ne releve pas une carte posee a plat`() {
        val decision = NavigationCamera.pitchForZoom(currentPitch = 0.0, zoom = 18.0, owedPitch = null)
        assertNull(decision.pitch)
        assertNull(decision.owedPitch)
    }

    @Test
    fun `une carte deja conforme au plafond n est pas reecrite`() {
        val decision = NavigationCamera.pitchForZoom(currentPitch = 55.0, zoom = 17.0, owedPitch = null)
        assertNull(decision.pitch)
    }

    /**
     * Les modes pilotés cadrent tous au-dessus du seuil : la rampe ne doit
     * rien leur retirer aujourd'hui. Le jour où un profil descendra plus
     * bas, c'est ce test qui dira qu'il vient de perdre son inclinaison.
     */
    @ParameterizedTest
    @EnumSource(value = CameraMode::class, names = ["FOLLOW", "NAVIGATION", "FOLLOW_VEHICLE"])
    fun `les modes pilotes cadrent au-dessus du seuil de mise a plat`(mode: CameraMode) {
        val profile = assertNotNull(CameraProfile.of(mode))
        assertTrue(profile.minZoom >= PITCH_FULL_ZOOM, "minZoom ${profile.minZoom} < $PITCH_FULL_ZOOM")

        var speed = 0.0
        while (speed <= 40.0) {
            val target = requireNotNull(NavigationCamera.target(input(mode, speed = speed)))
            assertTrue(
                target.pitch <= NavigationCamera.maxPitchForZoom(target.zoom),
                "pitch ${target.pitch} au-dessus du plafond à z${target.zoom}",
            )
            assertTrue(target.pitch >= profile.minPitch, "pitch ${target.pitch} < ${profile.minPitch}")
            speed += 2.5
        }
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
