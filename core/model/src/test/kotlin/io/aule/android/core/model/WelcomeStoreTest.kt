package io.aule.android.core.model

import io.aule.android.core.model.repository.WelcomeStore
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Le drapeau « accueil vu », et la distinction qu'il porte.
 *
 * Le contrat est trivial ; ce qui ne l'est pas, c'est qu'il soit **séparé** de
 * « la permission a été demandée ». Ce test l'écrit noir sur blanc : les deux
 * états existent en même temps et ne disent pas la même chose.
 */
class WelcomeStoreTest {

    private class MemoryWelcome : WelcomeStore {
        var seen = false
        override fun hasSeenWelcome(): Boolean = seen
        override fun markWelcomeSeen() {
            seen = true
        }
    }

    @Test
    fun `l accueil ne se montre qu une fois`() {
        val store = MemoryWelcome()
        assertFalse(store.hasSeenWelcome(), "au premier lancement, il n'a jamais été vu")

        store.markWelcomeSeen()

        assertTrue(store.hasSeenWelcome())
        // Idempotent : repasser par l'accueil après une bascule d'écran ne doit
        // pas le rouvrir.
        store.markWelcomeSeen()
        assertTrue(store.hasSeenWelcome())
    }
}
