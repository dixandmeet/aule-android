package io.aule.android.feature.map

import io.aule.android.core.guet.AlertPreferences
import io.aule.android.core.guet.GuetPreferences
import io.aule.android.core.guet.WalkingPace
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.repository.GuetPreferencesStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les réglages du Guet, côté écran.
 *
 * Deux choses comptent ici. **Chaque geste écrit** : un écran de réglages n'a pas
 * de bouton « Enregistrer », et enregistrer à la fermeture perdrait tout si le
 * système tue l'écran — ce qui arrive précisément quand on part chercher une
 * autorisation dans les paramètres.
 *
 * Et **un mode ne se décoche pas jusqu'au vide** : sans cette garde, la veille
 * resterait allumée et ne proposerait plus jamais rien, sans qu'aucun mot ne dise
 * pourquoi.
 */
class GuetSettingsModelTest {

    private class MemoryStore(private var encoded: String? = null) : GuetPreferencesStore {
        var writes = 0
        override fun read(): String? = encoded
        override fun write(encoded: String) {
            writes++
            this.encoded = encoded
        }
    }

    @Test
    fun `un ecran neuf part des defauts`() {
        val model = GuetSettingsModel(MemoryStore())

        assertEquals(GuetPreferences.DEFAULTS, model.state.value)
        assertFalse(model.state.value.isEnabled, "le Guet est éteint par défaut")
    }

    @Test
    fun `chaque geste ecrit`() {
        val store = MemoryStore()
        val model = GuetSettingsModel(store)

        model.setEnabled(true)
        model.setPreparationMinutes(3)
        model.setPace(WalkingPace.SLOW)

        assertEquals(3, store.writes)
        // Et ce qui a été écrit se relit à l'identique.
        assertEquals(model.state.value, GuetPreferences.decode(store.read()))
    }

    @Test
    fun `reposer la meme valeur n ecrit pas`() {
        val store = MemoryStore()
        val model = GuetSettingsModel(store)

        model.setEnabled(true)
        model.setEnabled(true)

        // Une recomposition qui repose la même valeur ne doit pas toucher le
        // disque : ce serait une écriture par image sur un interrupteur immobile.
        assertEquals(1, store.writes)
    }

    @Test
    fun `l ecran reprend ce qui etait enregistre`() {
        val saved = GuetPreferences(
            isEnabled = true,
            preparationMinutes = 5,
            pace = WalkingPace.FAST,
        )
        val model = GuetSettingsModel(MemoryStore(saved.encode()))

        assertEquals(saved, model.state.value)
    }

    /**
     * ⚠️ **La garde qui empêche un service muet.** Un réglage qui rend la veille
     * silencieuse doit être l'interrupteur principal, pas l'effet de bord de trois
     * cases décochées une à une.
     */
    @Test
    fun `le dernier mode ne se decoche pas`() {
        val model = GuetSettingsModel(MemoryStore())

        model.toggleMode(TransportMode.BUS)
        model.toggleMode(TransportMode.TRAM)
        assertEquals(setOf(TransportMode.BOAT), model.state.value.modes)

        model.toggleMode(TransportMode.BOAT)

        assertEquals(
            setOf(TransportMode.BOAT),
            model.state.value.modes,
            "le dernier mode tient",
        )
    }

    @Test
    fun `un mode se recoche`() {
        val model = GuetSettingsModel(MemoryStore())

        model.toggleMode(TransportMode.BUS)
        assertFalse(TransportMode.BUS in model.state.value.modes)

        model.toggleMode(TransportMode.BUS)
        assertTrue(TransportMode.BUS in model.state.value.modes)
    }

    @Test
    fun `les lignes suivies s ajoutent et se retirent`() {
        val model = GuetSettingsModel(MemoryStore())

        model.toggleFollowedLine("C6")
        model.toggleFollowedLine("1")
        assertEquals(setOf("C6", "1"), model.state.value.followedLines)

        model.toggleFollowedLine("C6")
        assertEquals(setOf("1"), model.state.value.followedLines)
    }

    @Test
    fun `toutes les lignes peuvent se retirer`() {
        // Contrairement aux modes : aucune ligne suivie n'éteint rien. Elles
        // pèsent dans le classement, elles n'en excluent pas les autres.
        val model = GuetSettingsModel(MemoryStore())

        model.toggleFollowedLine("C6")
        model.toggleFollowedLine("C6")

        assertTrue(model.state.value.followedLines.isEmpty())
    }

    @Test
    fun `les reglages d alerte se modifient un par un`() {
        val model = GuetSettingsModel(MemoryStore())

        model.setSound(false)
        model.setIntensity(AlertPreferences.Intensity.INSISTENT)

        // Toucher l'un ne réinitialise pas les autres.
        assertFalse(model.state.value.alerts.sound)
        assertTrue(model.state.value.alerts.haptics)
        assertEquals(AlertPreferences.Intensity.INSISTENT, model.state.value.alerts.intensity)
    }

    @Test
    fun `sans depot branche l ecran fonctionne quand meme`() {
        // Ce que voient les tests et les aperçus : les réglages vivent en
        // mémoire, et rien ne lève.
        val model = GuetSettingsModel(store = null)

        model.setEnabled(true)

        assertTrue(model.state.value.isEnabled)
    }
}
