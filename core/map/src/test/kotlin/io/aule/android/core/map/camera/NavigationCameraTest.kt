package io.aule.android.core.map.camera

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.map.MapZoom
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
        pace: Double = 0.0,
        speed: Double = 0.0,
        heading: Double? = 90.0,
        sheet: Double = 0.0,
        routeBearing: Double? = null,
        viewportHeight: Double = 800.0,
        currentBearing: Double = 0.0,
        travel: TravelStyle = TravelStyle.DRIVE,
        maneuverFocus: Double = 0.0,
    ) = CameraInput(
        mode = mode,
        center = Coordinate.NANTES,
        headingDegrees = heading,
        speedMps = speed,
        viewportHeight = viewportHeight,
        sheetHeightPx = sheet,
        currentBearing = currentBearing,
        routeBearingDegrees = routeBearing,
        travel = travel,
        pace = pace,
        maneuverFocus = maneuverFocus,
    )

    @Test
    fun `les modes libres ne pilotent pas la camera`() {
        assertNull(NavigationCamera.target(input(CameraMode.FREE_EXPLORE)))
        assertNull(NavigationCamera.target(input(CameraMode.OVERVIEW)))
    }

    /**
     * Le contrat du cadrage à l'ouverture, celui qui a motivé toute la
     * refonte : **on doit voir plusieurs rues autour de soi**, pas deux
     * façades. Un demi-niveau de zoom vaut environ quarante pour cent de
     * largeur en plus ; c'est ce qu'on vérifie, en niveaux plutôt qu'en
     * mètres — la conversion dépend de la latitude, pas la décision.
     */
    @Test
    fun `l exploration cadre plus large que l echelle du trottoir`() {
        val profile = assertNotNull(CameraProfile.of(CameraMode.FOLLOW))
        assertTrue(profile.maxZoom <= 16.8, "l'exploration ne doit pas coller au sol : ${profile.maxZoom}")
        assertEquals(MapZoom.OPENING, profile.zoom.rest, "l'ouverture et le repos sont le même cadre")
    }

    /**
     * La 3D reste allumée en exploration, mais elle cesse de faire des murs :
     * le brief demande 45 à 55°, et c'est la fourchette où les façades
     * proches laissent voir la rue d'à côté.
     */
    @Test
    fun `l exploration reste en volume sans se coucher`() {
        val profile = assertNotNull(CameraProfile.of(CameraMode.FOLLOW))
        assertTrue(profile.minPitch >= 45.0, "trop à plat : ${profile.minPitch}")
        assertTrue(profile.maxPitch <= 55.0, "trop couché : ${profile.maxPitch}")
    }

    /**
     * Le cadre suit l'allure **en U** : large au repos parce qu'on se repère,
     * serré au pas parce qu'on veut le trottoir, large à nouveau une fois
     * lancé parce que ce qui compte est devant.
     */
    @ParameterizedTest
    @EnumSource(value = CameraMode::class, names = ["FOLLOW", "NAVIGATION", "FOLLOW_VEHICLE"])
    fun `le cadre se resserre au pas et recule une fois lance`(mode: CameraMode) {
        val curve = assertNotNull(CameraProfile.of(mode)).zoom
        assertTrue(curve.close > curve.rest, "au pas on se rapproche : ${curve.close} vs ${curve.rest}")
        assertTrue(curve.cruise < curve.close, "puis on recule : ${curve.cruise} vs ${curve.close}")
        assertTrue(curve.far <= curve.cruise, "et on recule encore : ${curve.far} vs ${curve.cruise}")
        assertTrue(curve.far < curve.rest, "lancé, on voit plus large qu'à l'arrêt")
    }

    @ParameterizedTest
    @EnumSource(value = SpeedGear::class)
    fun `chaque cran d allure retrouve exactement son ancre`(gear: SpeedGear) {
        val curve = ZoomCurve(rest = 16.5, close = 16.8, cruise = 16.4, far = 16.0)
        val expected = when (gear) {
            SpeedGear.STILL -> 16.5
            SpeedGear.SLOW -> 16.8
            SpeedGear.CRUISE -> 16.4
            SpeedGear.FAST -> 16.0
        }
        assertEquals(expected, curve.at(gear.pace), 1e-9)
    }

    @Test
    fun `entre deux crans, le cadre passe sans palier`() {
        val curve = ZoomCurve(rest = 16.5, close = 16.9, cruise = 16.4, far = 16.0)
        val middle = (SpeedGear.STILL.pace + SpeedGear.SLOW.pace) / 2.0
        assertEquals((16.5 + 16.9) / 2.0, curve.at(middle), 1e-9)
        // Hors bornes, on ne sort pas de la courbe.
        assertEquals(16.5, curve.at(-3.0), 1e-9)
        assertEquals(16.0, curve.at(4.0), 1e-9)
    }

    @Test
    fun `au-dela de la vitesse de croisiere, le cadrage ne bouge plus`() {
        val cruising = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 1.0)))
        val faster = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 3.0)))
        assertEquals(cruising.zoom, faster.zoom)
        assertEquals(cruising.pitch, faster.pitch)
    }

    @ParameterizedTest
    @EnumSource(value = CameraMode::class, names = ["FOLLOW", "NAVIGATION", "FOLLOW_VEHICLE"])
    fun `l inclinaison ne depasse jamais le plafond`(mode: CameraMode) {
        var pace = 0.0
        while (pace <= 1.0) {
            val target = requireNotNull(NavigationCamera.target(input(mode, pace = pace)))
            assertTrue(target.pitch <= MAX_PITCH, "pitch ${target.pitch} > $MAX_PITCH")
            assertTrue(target.pitch >= 0.0)
            pace += 0.05
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

    // ------------------------------------------------- les cadres contextuels

    /**
     * Un piéton et une voiture ne veulent pas le même cadre, et c'est la
     * distinction qui manquait : la caméra de navigation était réglée pour
     * 65 km/h, y compris sur un trottoir.
     */
    @Test
    fun `le pieton cadre plus pres et moins couche que l automobiliste`() {
        val walk = assertNotNull(CameraProfile.of(CameraMode.NAVIGATION, TravelStyle.WALK))
        val drive = assertNotNull(CameraProfile.of(CameraMode.NAVIGATION, TravelStyle.DRIVE))

        assertTrue(walk.zoom.rest > drive.zoom.rest, "le piéton reste plus près")
        assertTrue(walk.maxPitch < drive.maxPitch, "l'automobiliste voit plus rasant")
        assertTrue(
            walk.cruiseSpeedMps < drive.cruiseSpeedMps,
            "une allure de marche n'est pas une allure de voiture",
        )
    }

    /**
     * Le cadrage automobile place le sujet dans le tiers bas : c'est ce qui
     * laisse la route à venir occuper le haut de l'écran.
     */
    @Test
    fun `en voiture, le sujet descend dans le tiers bas`() {
        val target = requireNotNull(
            NavigationCamera.target(
                input(CameraMode.NAVIGATION, pace = 0.6, travel = TravelStyle.DRIVE),
            ),
        )
        // Le sujet tombe à la moitié plus le décalage, rapporté à la hauteur.
        val position = 0.5 + target.forwardOffsetPx / 800.0
        assertTrue(position in 0.6..0.78, "sujet à ${(position * 100).toInt()} % de la hauteur")
    }

    /**
     * En exploration, le brief demande 60 à 65 % : assez bas pour dégager
     * l'avant, assez haut pour qu'on se voie toujours au milieu de la carte.
     */
    @Test
    fun `en exploration, le sujet est legerement sous le centre`() {
        val target = requireNotNull(NavigationCamera.target(input(CameraMode.FOLLOW)))
        val position = 0.5 + target.forwardOffsetPx / 800.0
        assertTrue(position in 0.58..0.67, "sujet à ${(position * 100).toInt()} % de la hauteur")
    }

    // ------------------------------------------------------ les intersections

    @Test
    fun `a l approche d un carrefour, la camera se rapproche et se redresse`() {
        val far = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 0.7, maneuverFocus = 0.0)),
        )
        val near = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 0.7, maneuverFocus = 1.0)),
        )
        assertTrue(near.zoom > far.zoom, "on doit se rapprocher : ${near.zoom} vs ${far.zoom}")
        assertTrue(near.pitch < far.pitch, "et se redresser : ${near.pitch} vs ${far.pitch}")
        assertTrue(
            near.forwardOffsetPx < far.forwardOffsetPx,
            "le carrefour remonte vers le centre : ${near.forwardOffsetPx} vs ${far.forwardOffsetPx}",
        )
    }

    @Test
    fun `le rapprochement sur un carrefour est progressif`() {
        var previous = -1.0
        var focus = 0.0
        while (focus <= 1.0) {
            val zoom = requireNotNull(
                NavigationCamera.target(input(CameraMode.NAVIGATION, maneuverFocus = focus)),
            ).zoom
            assertTrue(zoom > previous, "à $focus : $zoom n'est pas au-dessus de $previous")
            previous = zoom
            focus += 0.1
        }
    }

    /**
     * Une caméra qui suit un véhicule ou qui explore n'a pas de carrefour à
     * prendre : le cadrage d'intersection ne doit pas déborder sur elle.
     */
    @Test
    fun `hors navigation, aucun carrefour ne reprend le cadre`() {
        for (mode in listOf(CameraMode.FOLLOW, CameraMode.FOLLOW_VEHICLE)) {
            val calm = requireNotNull(NavigationCamera.target(input(mode, maneuverFocus = 0.0)))
            val focused = requireNotNull(NavigationCamera.target(input(mode, maneuverFocus = 1.0)))
            assertEquals(calm.zoom, focused.zoom, 1e-9, "$mode ne doit pas zoomer sur un carrefour")
        }
    }

    // ------------------------------------------------------------ la 3D et le zoom

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

    /**
     * La rampe de mise à plat encadre le seuil des volumes du style : sous
     * quinze et demi, `building-3d` n'est plus plein, et incliner n'incline
     * plus qu'un plan.
     */
    @Test
    fun `le plein droit a l inclinaison commence ou les volumes sont pleins`() {
        assertEquals(15.5, PITCH_FULL_ZOOM)
        assertTrue(PITCH_FLAT_ZOOM < PITCH_FULL_ZOOM)
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
        var pitch = 52.0
        var owed: Double? = null

        // On monte : z17 → z13, par pas d'un quart de niveau.
        var zoom = 17.0
        while (zoom >= 13.0) {
            val decision = NavigationCamera.pitchForZoom(pitch, zoom, owed)
            owed = decision.owedPitch
            decision.pitch?.let { pitch = it }
            assertTrue(pitch <= NavigationCamera.maxPitchForZoom(zoom) + PITCH_STEP_EPSILON)
            zoom -= 0.25
        }
        assertEquals(0.0, pitch, PITCH_STEP_EPSILON, "à z13 la carte doit être à plat")
        assertEquals(52.0, assertNotNull(owed), 1e-9, "l'inclinaison retirée doit rester due")

        // On redescend, et la 3D revient d'elle-même.
        while (zoom <= 17.0) {
            val decision = NavigationCamera.pitchForZoom(pitch, zoom, owed)
            owed = decision.owedPitch
            decision.pitch?.let { pitch = it }
            zoom += 0.25
        }
        assertEquals(52.0, pitch, PITCH_STEP_EPSILON, "l'inclinaison de départ doit être rendue")
        assertNull(owed, "une dette soldée s'éteint")
    }

    @Test
    fun `au milieu de la remontee, on ne rend que ce que la rampe autorise`() {
        val middle = (PITCH_FLAT_ZOOM + PITCH_FULL_ZOOM) / 2.0
        val decision = NavigationCamera.pitchForZoom(currentPitch = 0.0, zoom = middle, owedPitch = 55.0)
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
        for (travel in TravelStyle.entries) {
            val profile = assertNotNull(CameraProfile.of(mode, travel))
            assertTrue(profile.minZoom >= PITCH_FULL_ZOOM, "minZoom ${profile.minZoom} < $PITCH_FULL_ZOOM")

            var pace = 0.0
            while (pace <= 1.0) {
                val target = requireNotNull(
                    NavigationCamera.target(input(mode, pace = pace, travel = travel)),
                )
                assertTrue(
                    target.pitch <= NavigationCamera.maxPitchForZoom(target.zoom),
                    "pitch ${target.pitch} au-dessus du plafond à z${target.zoom}",
                )
                assertTrue(target.pitch >= profile.minPitch, "pitch ${target.pitch} < ${profile.minPitch}")
                pace += 0.05
            }
        }
    }

    // ------------------------------------------------------------- la sélection

    /**
     * Désigner un lieu doit **rapprocher**, pas téléporter : le cadre courant
     * est gardé tant qu'il tient dans les bornes.
     */
    @Test
    fun `une selection garde le cadre courant quand il est raisonnable`() {
        assertEquals(
            16.7,
            NavigationCamera.selectionZoom(16.7, MapZoom.SELECTION_MIN, MapZoom.SELECTION_MAX),
        )
    }

    @Test
    fun `venu de trop loin ou de trop pres, la selection revient dans les bornes`() {
        assertEquals(
            MapZoom.SELECTION_MIN,
            NavigationCamera.selectionZoom(11.0, MapZoom.SELECTION_MIN, MapZoom.SELECTION_MAX),
        )
        assertEquals(
            MapZoom.SELECTION_MAX,
            NavigationCamera.selectionZoom(19.5, MapZoom.SELECTION_MIN, MapZoom.SELECTION_MAX),
        )
        assertEquals(
            MapZoom.SELECTION_MIN,
            NavigationCamera.selectionZoom(Double.NaN, MapZoom.SELECTION_MIN, MapZoom.SELECTION_MAX),
        )
    }

    /**
     * Les bornes de sélection restent **au-dessus du seuil des volumes** :
     * une fiche d'arrêt s'ouvre sur une ville en relief, jamais sur un plan.
     */
    @Test
    fun `une selection reste dans le monde en volume`() {
        assertTrue(MapZoom.SELECTION_MIN >= PITCH_FULL_ZOOM)
        assertTrue(MapZoom.SELECTION_MAX > MapZoom.SELECTION_MIN)
    }

    // ---------------------------------------------------------------- le volet

    /**
     * Le décalage se calcule sur la **bande visible**, volet déduit. Sans
     * cela, un volet à mi-hauteur repousse le puck derrière lui.
     */
    @Test
    fun `le decalage avant se reduit quand le volet monte`() {
        val bare = requireNotNull(NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 0.5, sheet = 0.0)))
        val covered = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 0.5, sheet = 500.0)),
        )
        assertTrue(covered.forwardOffsetPx < bare.forwardOffsetPx)
    }

    @Test
    fun `le decalage reste dans les bornes du profil, meme sur un tres petit ecran`() {
        val profile = assertNotNull(CameraProfile.of(CameraMode.NAVIGATION))
        val target = requireNotNull(
            NavigationCamera.target(input(CameraMode.NAVIGATION, pace = 0.5, viewportHeight = 100.0)),
        )
        assertTrue(target.forwardOffsetPx >= profile.minOffsetPx)
        assertTrue(target.forwardOffsetPx <= profile.maxOffsetPx)
    }
}
