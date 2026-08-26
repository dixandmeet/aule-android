package io.aule.android.core.map.camera

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La mémoire de la caméra, vérifiée seconde par seconde.
 *
 * Ce que ces tests protègent ne se relit pas dans le code : **une carte qui
 * ne respire pas**. Un zoom qui suit la vitesse instantanée bouge à chaque
 * oscillation du GPS, et personne ne saurait dire, en lisant une
 * multiplication, si le résultat sera confortable ou écœurant.
 */
class CameraDynamicsTest {

    private val tick = 1.0 / 15.0

    /** Fait tourner le ticker pendant [seconds] à vitesse constante. */
    private fun CameraDynamics.run(
        seconds: Double,
        speedMps: Double,
        cruiseSpeedMps: Double = CRUISE_SPEED_MPS,
        maneuverMeters: Double? = null,
        travel: TravelStyle = TravelStyle.DRIVE,
    ): CameraDrive {
        var drive = advance(speedMps, cruiseSpeedMps, maneuverMeters, travel, 0.0)
        var elapsed = 0.0
        while (elapsed < seconds) {
            drive = advance(speedMps, cruiseSpeedMps, maneuverMeters, travel, tick)
            elapsed += tick
        }
        return drive
    }

    /**
     * ⚠️ Le garde-fou de [ZoomCurve] : elle interpole **entre les crans**, dans
     * l'ordre de déclaration, en supposant des allures croissantes de 0 à 1.
     * Réordonner l'énumération ou changer une valeur d'allure ne casserait rien
     * à la compilation — mais rendrait un zoom qui recule quand on accélère à
     * un endroit et avance à un autre, ce que personne ne relirait dans le code.
     */
    @Test
    fun `les crans d allure sont croissants, de zero a un`() {
        val paces = SpeedGear.entries.map { it.pace }
        assertEquals(paces.sorted(), paces, "les crans doivent aller en montant : $paces")
        assertEquals(0.0, paces.first(), "l'arrêt vaut zéro")
        assertEquals(1.0, paces.last(), "le cran le plus haut vaut un")
        assertEquals(paces.distinct().size, paces.size, "deux crans ne peuvent pas valoir pareil")
    }

    @Test
    fun `a l arret, la camera reste au cran du repos`() {
        val drive = CameraDynamics().run(seconds = 5.0, speedMps = 0.0)
        assertEquals(SpeedGear.STILL, drive.gear)
        assertEquals(0.0, drive.pace, 1e-6)
    }

    @Test
    fun `chaque allure trouve son cran`() {
        assertEquals(SpeedGear.SLOW, CameraDynamics().run(10.0, speedMps = 4.0).gear)
        assertEquals(SpeedGear.CRUISE, CameraDynamics().run(10.0, speedMps = 9.0).gear)
        assertEquals(SpeedGear.FAST, CameraDynamics().run(10.0, speedMps = 16.0).gear)
    }

    /**
     * Le défaut que l'hystérésis existe pour empêcher : un GPS urbain
     * oscille en permanence autour d'une vitesse, et sans marge le cadre
     * oscillerait avec lui — une carte qui respire à chaque seconde.
     */
    @Test
    fun `une vitesse qui oscille autour d un seuil ne fait pas osciller le cadre`() {
        val dynamics = CameraDynamics()
        // Le seuil de montée en croisière est à 0,40 × 18 = 7,2 m/s.
        dynamics.run(6.0, speedMps = 8.0)
        assertEquals(SpeedGear.CRUISE, dynamics.gear)

        // On repasse **sous** le seuil de montée, mais pas sous celui de
        // descente (0,30 × 18 = 5,4 m/s) : le cran ne doit pas bouger.
        repeat(60) {
            dynamics.advance(6.9, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, tick)
            assertEquals(SpeedGear.CRUISE, dynamics.gear)
            dynamics.advance(7.5, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, tick)
            assertEquals(SpeedGear.CRUISE, dynamics.gear)
        }
    }

    @Test
    fun `il faut ralentir franchement pour reprendre le cran du dessous`() {
        val dynamics = CameraDynamics()
        dynamics.run(6.0, speedMps = 8.0)
        assertEquals(SpeedGear.CRUISE, dynamics.gear)
        dynamics.run(2.0, speedMps = 4.0)
        assertEquals(SpeedGear.SLOW, dynamics.gear)
    }

    /**
     * Le premier point GPS d'un véhicule lancé arrive parfois après un arrêt
     * complet : il faut alors sauter deux crans d'un coup, sinon le cadrage
     * traverse les paliers un par un en dix secondes.
     */
    @Test
    fun `on saute plusieurs crans quand la vitesse le demande`() {
        val dynamics = CameraDynamics()
        dynamics.advance(0.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, tick)
        assertEquals(SpeedGear.STILL, dynamics.gear)
        dynamics.advance(20.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, tick)
        assertEquals(SpeedGear.FAST, dynamics.gear)
    }

    /**
     * L'allure est **relative** au profil : 1,4 m/s est une marche soutenue
     * et un embouteillage, et les deux ne demandent pas le même cadre.
     */
    @Test
    fun `la meme vitesse ne vaut pas la meme allure a pied et en voiture`() {
        val walking = CameraDynamics().run(
            10.0, speedMps = 1.5, cruiseSpeedMps = WALK_CRUISE_SPEED_MPS, travel = TravelStyle.WALK,
        )
        val driving = CameraDynamics().run(10.0, speedMps = 1.5)
        assertEquals(SpeedGear.CRUISE, walking.gear, "1,5 m/s est une marche soutenue")
        assertEquals(SpeedGear.STILL, driving.gear, "1,5 m/s en voiture, c'est un arrêt")
    }

    /**
     * Le lissage, et sa raison d'être : un cran franchi ne doit **jamais**
     * arriver d'un bloc dans le cadrage.
     */
    @Test
    fun `un changement de cran arrive progressivement`() {
        val dynamics = CameraDynamics()
        dynamics.run(5.0, speedMps = 0.0)
        val justAfter = dynamics.advance(16.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, tick)
        assertEquals(SpeedGear.FAST, justAfter.gear, "le cran, lui, bascule tout de suite")
        assertTrue(justAfter.pace < 0.2, "mais l'allure, non : ${justAfter.pace}")

        val later = dynamics.run(3.0, speedMps = 16.0)
        assertTrue(later.pace > 0.9, "et elle arrive quand même : ${later.pace}")
    }

    /**
     * Un écran qui revient de veille peut donner un pas de temps en minutes.
     * L'intégrer tel quel téléporterait le cadre — le seul saut visuel qu'on
     * s'interdit.
     */
    @Test
    fun `un pas de temps aberrant ne teleporte pas le cadre`() {
        val dynamics = CameraDynamics()
        val drive = dynamics.advance(16.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, 600.0)
        assertTrue(drive.pace < 0.6, "un saut de dix minutes ne vaut pas un cadrage instantané : ${drive.pace}")
    }

    @Test
    fun `un pas de temps nul ou absurde ne fait rien bouger`() {
        val dynamics = CameraDynamics()
        dynamics.run(5.0, speedMps = 16.0)
        val before = dynamics.pace
        dynamics.advance(16.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, 0.0)
        dynamics.advance(16.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, Double.NaN)
        dynamics.advance(16.0, CRUISE_SPEED_MPS, null, TravelStyle.DRIVE, -4.0)
        assertEquals(before, dynamics.pace, 1e-12)
    }

    // ------------------------------------------------------- les intersections

    @Test
    fun `loin d une manoeuvre, le carrefour ne compte pas`() {
        val drive = CameraDynamics().run(4.0, speedMps = 12.0, maneuverMeters = 600.0)
        assertEquals(0.0, drive.maneuverFocus, 1e-6)
    }

    @Test
    fun `au carrefour, le cadrage d intersection est plein`() {
        val drive = CameraDynamics().run(4.0, speedMps = 6.0, maneuverMeters = 20.0)
        assertTrue(drive.maneuverFocus > 0.95, "focus ${drive.maneuverFocus}")
    }

    @Test
    fun `sans manoeuvre annoncee, rien ne reprend le cadre`() {
        val drive = CameraDynamics().run(4.0, speedMps = 12.0, maneuverMeters = null)
        assertEquals(0.0, drive.maneuverFocus, 1e-6)
    }

    /**
     * Un piéton et une voiture n'approchent pas un carrefour à la même
     * distance : à cent mètres, l'automobiliste y est déjà, le piéton non.
     */
    @Test
    fun `la portee d un carrefour depend du mode de deplacement`() {
        val driving = CameraDynamics().run(
            4.0, speedMps = 12.0, maneuverMeters = 100.0, travel = TravelStyle.DRIVE,
        )
        val walking = CameraDynamics().run(
            4.0, speedMps = 1.4, cruiseSpeedMps = WALK_CRUISE_SPEED_MPS,
            maneuverMeters = 100.0, travel = TravelStyle.WALK,
        )
        assertTrue(driving.maneuverFocus > 0.0, "en voiture, cent mètres, c'est tout de suite")
        assertEquals(0.0, walking.maneuverFocus, 1e-6, "à pied, cent mètres, c'est loin")
    }

    /**
     * Le contrat du **retour progressif** : passé le carrefour, la manœuvre
     * suivante est souvent à trois cents mètres et l'imminence retombe d'un
     * coup. Rendre le cadre au même rythme qu'on l'a pris ferait un dézoom
     * sec juste après le virage.
     */
    @Test
    fun `apres la manoeuvre, le cadre revient plus lentement qu il n est venu`() {
        val rising = CameraDynamics()
        rising.advance(8.0, CRUISE_SPEED_MPS, 600.0, TravelStyle.DRIVE, 0.0)
        rising.run(0.5, speedMps = 8.0, maneuverMeters = 20.0)
        val gained = rising.maneuverFocus

        val falling = CameraDynamics()
        falling.advance(8.0, CRUISE_SPEED_MPS, 20.0, TravelStyle.DRIVE, 0.0)
        falling.run(3.0, speedMps = 8.0, maneuverMeters = 20.0)
        val before = falling.maneuverFocus
        falling.run(0.5, speedMps = 8.0, maneuverMeters = 600.0)
        val released = before - falling.maneuverFocus

        assertTrue(
            released < gained,
            "on rend plus lentement qu'on ne prend : rendu $released, pris $gained",
        )
    }

    @Test
    fun `un guidage qui s arrete ne laisse rien derriere lui`() {
        val dynamics = CameraDynamics()
        dynamics.run(5.0, speedMps = 16.0, maneuverMeters = 20.0)
        assertTrue(dynamics.pace > 0.5)
        dynamics.reset()
        assertEquals(0.0, dynamics.pace)
        assertEquals(0.0, dynamics.maneuverFocus)
        assertEquals(SpeedGear.STILL, dynamics.gear)
    }
}
