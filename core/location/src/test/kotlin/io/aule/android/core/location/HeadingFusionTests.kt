package io.aule.android.core.location

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le cap du cône de direction — celui qui dit « vous êtes tourné par là ».
 *
 * Le pas de temps du ticker caméra, 66 ms. Les tests s'en servent tels quels
 * pour que les durées qu'ils affirment se lisent en battements réels.
 */
private const val TICK = 0.066

class HeadingFusionTests {

    /**
     * Ce que le cap GPS seul ne savait pas faire, et qui motive tout le
     * reste : à l'arrêt, sur le trottoir, on veut savoir de quel côté partir.
     */
    @Test
    fun `a l arret, la boussole tient le cone toute seule`() {
        val fusion = HeadingFusion()
        val facing = fusion.advance(compass = 120.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        assertEquals(120.0, facing)
    }

    /**
     * Sans magnétomètre, on ne fabrique pas une direction : on retombe
     * exactement sur l'ancien comportement — pas de cône plutôt qu'un cône
     * qui pointe là où l'on allait il y a une minute.
     */
    @Test
    fun `sans boussole ni cap de route, le cone n existe pas`() {
        val fusion = HeadingFusion()
        assertNull(fusion.advance(compass = null, course = null, speedMps = 0.0, stepSeconds = TICK))
    }

    @Test
    fun `le cone disparait quand ses deux sources se taisent`() {
        val fusion = HeadingFusion()
        fusion.advance(compass = 90.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        assertNotNull(fusion.facing)

        // Capteur déclaré inexploitable en cours de route, et toujours à l'arrêt.
        assertNull(fusion.advance(compass = null, course = null, speedMps = 0.0, stepSeconds = TICK))
        assertNull(fusion.facing)
    }

    /**
     * Posé sur une table, un téléphone rend un cap qui oscille de deux ou
     * trois degrés. Le cône ne doit pas frémir pour autant.
     */
    @Test
    fun `le tremblement de la boussole a l arret ne bouge pas le cone`() {
        val fusion = HeadingFusion()
        fusion.advance(compass = 200.0, course = null, speedMps = 0.0, stepSeconds = TICK)

        for (jitter in listOf(202.0, 198.0, 201.5, 197.5, 202.5)) {
            fusion.advance(compass = jitter, course = null, speedMps = 0.0, stepSeconds = TICK)
        }
        assertEquals(200.0, fusion.facing, "sous le seuil, rien ne doit bouger")
    }

    /** Un vrai demi-tour, lui, doit passer la zone morte et être suivi. */
    @Test
    fun `un pivot sur place est suivi`() {
        val fusion = HeadingFusion()
        fusion.advance(compass = 0.0, course = null, speedMps = 0.0, stepSeconds = TICK)

        // Une seconde de ticker à faire face au sud.
        repeat(15) {
            fusion.advance(compass = 180.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        }
        val facing = fusion.facing ?: 0.0
        assertTrue(
            abs(180.0 - facing) < 15.0,
            "après une seconde, le cône doit avoir rejoint le sud : $facing",
        )
    }

    /**
     * Le cône ne doit pas sauter au premier pas. Marcher, c'est franchir un
     * seuil de vitesse plusieurs fois par minute : un basculement franc ferait
     * osciller la direction à chaque feu rouge.
     */
    @Test
    fun `le cap de route prend la main progressivement`() {
        assertEquals(0.0, HeadingFusion.courseTrust(0.0))
        assertEquals(0.0, HeadingFusion.courseTrust(FACING_COURSE_MIN_SPEED_MPS))
        assertEquals(1.0, HeadingFusion.courseTrust(FACING_COURSE_FULL_SPEED_MPS))
        assertEquals(1.0, HeadingFusion.courseTrust(12.0), "au-delà, on plafonne")

        val middle = (FACING_COURSE_MIN_SPEED_MPS + FACING_COURSE_FULL_SPEED_MPS) / 2
        assertEquals(0.5, HeadingFusion.courseTrust(middle), 1e-9)

        // Monotone, et sans marche : c'est ce qui sépare une transition d'un
        // basculement.
        var previous = -1.0
        var speed = 0.0
        while (speed <= 3.0) {
            val trust = HeadingFusion.courseTrust(speed)
            assertTrue(trust >= previous, "la confiance ne doit jamais reculer, à $speed m/s")
            previous = trust
            speed += 0.05
        }
    }

    /**
     * Le poids se lit sur le résultat, pas seulement sur la courbe : à mi-
     * vitesse, le cône doit se poser entre la boussole et la route.
     */
    @Test
    fun `a mi-vitesse, le cone est entre la boussole et la route`() {
        val fusion = HeadingFusion()
        val middle = (FACING_COURSE_MIN_SPEED_MPS + FACING_COURSE_FULL_SPEED_MPS) / 2

        // Premier pas : on se pose sur la cible, sans lissage.
        val facing = fusion.advance(
            compass = 0.0,
            course = 80.0,
            speedMps = middle,
            stepSeconds = TICK,
        )
        assertEquals(40.0, facing ?: 0.0, 1e-9)
    }

    /** À pleine vitesse, la boussole ne pèse plus rien. */
    @Test
    fun `lance, le cone suit la route et non le telephone`() {
        val fusion = HeadingFusion()
        val facing = fusion.advance(
            compass = 300.0,
            course = 45.0,
            speedMps = FACING_COURSE_FULL_SPEED_MPS + 5.0,
            stepSeconds = TICK,
        )
        assertEquals(45.0, facing ?: 0.0, 1e-9)
    }

    /**
     * Le passage du nord : un mélange arithmétique de 350° et 10° donnerait
     * 180°, soit très exactement le dos de la direction réelle.
     */
    @Test
    fun `le melange passe le nord par le plus court chemin`() {
        val fusion = HeadingFusion()
        val middle = (FACING_COURSE_MIN_SPEED_MPS + FACING_COURSE_FULL_SPEED_MPS) / 2
        val facing = fusion.advance(
            compass = 350.0,
            course = 10.0,
            speedMps = middle,
            stepSeconds = TICK,
        ) ?: 0.0
        assertEquals(0.0, facing, 1e-9)
    }

    /** Et le lissage aussi : de 350° vers 10°, on passe par 0, pas par 180. */
    @Test
    fun `le lissage passe le nord par le plus court chemin`() {
        val fusion = HeadingFusion()
        fusion.advance(compass = 350.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        repeat(5) {
            fusion.advance(compass = 10.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        }
        val facing = fusion.facing ?: 0.0
        assertTrue(
            facing > 350.0 || facing < 10.0,
            "le cône doit franchir le nord, pas en faire le tour : $facing",
        )
    }

    /**
     * Le lissage raisonne en temps et non en nombre d'échantillons : au retour
     * d'un arrière-plan, le premier battement porte plusieurs secondes et doit
     * rattraper d'un coup plutôt que de traîner un cap périmé.
     */
    @Test
    fun `un pas de temps long rattrape immediatement`() {
        val fusion = HeadingFusion()
        fusion.advance(compass = 0.0, course = null, speedMps = 0.0, stepSeconds = TICK)
        fusion.advance(compass = 270.0, course = null, speedMps = 0.0, stepSeconds = 30.0)

        val facing = fusion.facing ?: 0.0
        assertTrue(abs(270.0 - facing) < 4.0, "le cône doit avoir rattrapé : $facing")
    }
}
