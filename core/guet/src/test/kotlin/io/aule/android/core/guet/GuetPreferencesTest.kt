package io.aule.android.core.guet

import io.aule.android.core.model.TransportMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les réglages, et les deux règles qui les gouvernent.
 *
 * La première : **le Guet est éteint par défaut.** Une veille qui s'allume seule
 * et se met à sonner le lendemain matin serait une surprise, pas un service.
 *
 * La seconde : **le décodage est tolérant.** Un décodage strict effacerait tous
 * les réglages pour un champ ajouté — c'est-à-dire que le Guet s'éteindrait tout
 * seul chez quelqu'un qui vient de mettre à jour, sans un mot.
 *
 * Port de `GuetPreferencesTests.swift`.
 */
class GuetPreferencesTest {

    @Test
    fun `le Guet est eteint par defaut et la preparation nulle`() {
        val defaults = GuetPreferences()

        assertFalse(defaults.isEnabled)
        assertEquals(0, defaults.preparationMinutes)
        // La marge de quai, elle, n'est pas nulle : deux minutes, de quoi voir le
        // véhicule arriver sans avoir couru.
        assertEquals(2, defaults.platformMarginMinutes)
        assertEquals(WalkingPace.AUTOMATIC, defaults.pace)
        assertEquals(
            setOf(TransportMode.BUS, TransportMode.TRAM, TransportMode.BOAT),
            defaults.modes,
        )
        assertTrue(defaults.followedLines.isEmpty())
    }

    @Test
    fun `les minutes deviennent des secondes et une valeur negative vaut zero`() {
        assertEquals(180, GuetPreferences(preparationMinutes = 3).preparationSeconds)
        assertEquals(120, GuetPreferences(platformMarginMinutes = 2).platformSeconds)
        assertEquals(0, GuetPreferences(preparationMinutes = -5).preparationSeconds)
        assertEquals(0, GuetPreferences(platformMarginMinutes = -1).platformSeconds)
    }

    // ------------------------------------------------------ l'allure de marche

    @Test
    fun `l allure automatique se comporte comme normale tant qu on n a rien mesure`() {
        // Une estimation par défaut vaut mieux qu'un réglage qu'on demande avant
        // d'avoir de quoi le remplir.
        assertEquals(1.0, WalkingPace.AUTOMATIC.factor(measured = null))
        assertEquals(WalkingPace.NORMAL.factor(null), WalkingPace.AUTOMATIC.factor(null))
    }

    @Test
    fun `l allure automatique adopte la mesure quand il y en a une`() {
        assertEquals(1.2, WalkingPace.AUTOMATIC.factor(measured = 1.2))
        // Les allures déclarées, elles, ignorent la mesure : c'est un choix dit à
        // voix haute, il ne se fait pas corriger.
        assertEquals(1.25, WalkingPace.SLOW.factor(measured = 1.2))
        assertEquals(0.85, WalkingPace.FAST.factor(measured = 1.2))
    }

    @Test
    fun `marcher lentement allonge la marche et vite la raccourcit`() {
        assertTrue(WalkingPace.SLOW.factor(null) > WalkingPace.NORMAL.factor(null))
        assertTrue(WalkingPace.FAST.factor(null) < WalkingPace.NORMAL.factor(null))
    }

    // ------------------------------------------------------------ l'intensité

    /**
     * ⚠️ **Un niveau jumeau d'un autre est un réglage qui ment.** L'utilisateur
     * choisit, l'écran confirme, et rien ne bouge — c'est ce qu'`INSISTENT` a été
     * pendant tout un lot côté iOS.
     */
    @Test
    fun `deux niveaux d intensite ne font jamais la meme chose`() {
        val signatures = AlertPreferences.Intensity.entries.map { intensity ->
            val prefs = AlertPreferences(intensity = intensity)
            prefs.chimeVolume to prefs.repriseDelaySeconds
        }

        assertEquals(
            signatures.size,
            signatures.toSet().size,
            "chaque intensité doit changer quelque chose",
        )
    }

    @Test
    fun `discret baisse le carillon insistant le rejoue une fois`() {
        val discreet = AlertPreferences(intensity = AlertPreferences.Intensity.DISCREET)
        val standard = AlertPreferences(intensity = AlertPreferences.Intensity.STANDARD)
        val insistent = AlertPreferences(intensity = AlertPreferences.Intensity.INSISTENT)

        assertTrue(discreet.chimeVolume < standard.chimeVolume)
        // Discret **n'éteint pas** le son : pour l'éteindre, il y a l'interrupteur.
        assertTrue(discreet.chimeVolume > 0f)
        assertNull(standard.repriseDelaySeconds)
        assertEquals(3, insistent.repriseDelaySeconds)
    }

    // ---------------------------------------------------- le décodage tolérant

    @Test
    fun `un aller-retour garde tout`() {
        val prefs = GuetPreferences(
            isEnabled = true,
            preparationMinutes = 5,
            platformMarginMinutes = 3,
            pace = WalkingPace.FAST,
            modes = setOf(TransportMode.TRAM),
            followedLines = setOf("C6", "1"),
            alerts = AlertPreferences(
                sound = false,
                intensity = AlertPreferences.Intensity.INSISTENT,
            ),
        )

        assertEquals(prefs, GuetPreferences.decode(prefs.encode()))
    }

    /**
     * ⚠️ **Le test qui empêche le Guet de s'éteindre tout seul après une mise à
     * jour.** Un champ absent retombe sur son défaut, et **sur lui seul**.
     */
    @Test
    fun `un champ absent ne fait pas perdre les autres`() {
        val partial = """{"isEnabled":true,"preparationMinutes":5}"""

        val decoded = GuetPreferences.decode(partial)

        assertTrue(decoded.isEnabled, "ce que le fichier disait est gardé")
        assertEquals(5, decoded.preparationMinutes)
        // Et le reste retombe sur ses défauts, sans emporter ce qui précède.
        assertEquals(2, decoded.platformMarginMinutes)
        assertEquals(WalkingPace.AUTOMATIC, decoded.pace)
        assertEquals(GuetPreferences.DEFAULTS.modes, decoded.modes)
    }

    @Test
    fun `une valeur inconnue retombe sur le defaut de son champ`() {
        // Un réglage écrit par une version plus récente. Perdre une préférence est
        // déjà fâcheux ; les perdre toutes parce que l'une n'a pas été comprise ne
        // l'est plus.
        val futureVersion = """
            {"isEnabled":true,"pace":"TELEPORTATION",
             "alerts":{"intensity":"ASSOURDISSANT","sound":false}}
        """.trimIndent()

        val decoded = GuetPreferences.decode(futureVersion)

        assertTrue(decoded.isEnabled)
        assertEquals(WalkingPace.AUTOMATIC, decoded.pace)
        assertEquals(AlertPreferences.Intensity.STANDARD, decoded.alerts.intensity)
        assertFalse(decoded.alerts.sound, "ce qui était lisible reste lu")
    }

    @Test
    fun `un mode inconnu est ignore sans emporter les autres`() {
        // Le jour où le réseau gagne un mode, une version ancienne doit continuer
        // de surveiller ceux qu'elle connaît.
        val decoded = GuetPreferences.decode("""{"modes":["BUS","FUNICULAIRE","TRAM"]}""")

        assertEquals(setOf(TransportMode.BUS, TransportMode.TRAM), decoded.modes)
    }

    @Test
    fun `un fichier illisible rend les defauts plutot que de lever`() {
        assertEquals(GuetPreferences.DEFAULTS, GuetPreferences.decode(null))
        assertEquals(GuetPreferences.DEFAULTS, GuetPreferences.decode(""))
        assertEquals(GuetPreferences.DEFAULTS, GuetPreferences.decode("pas du JSON"))
        assertEquals(GuetPreferences.DEFAULTS, GuetPreferences.decode("""["un tableau"]"""))
    }

    @Test
    fun `l ecriture est stable d un enregistrement a l autre`() {
        // Un tableau trié plutôt qu'un ensemble : deux enregistrements identiques
        // doivent produire deux fichiers identiques, sinon le dépôt réécrit sans
        // que rien n'ait changé.
        val a = GuetPreferences(
            modes = setOf(TransportMode.TRAM, TransportMode.BUS),
            followedLines = setOf("C6", "1"),
        )
        val b = GuetPreferences(
            modes = setOf(TransportMode.BUS, TransportMode.TRAM),
            followedLines = setOf("1", "C6"),
        )

        assertEquals(a.encode(), b.encode())
    }
}
