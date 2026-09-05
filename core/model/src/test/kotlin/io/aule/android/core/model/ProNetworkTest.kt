package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ProNetworkTest {

    @Test
    fun `une requete vide rend tout le catalogue`() {
        assertEquals(SIGNUP_NETWORKS, signupNetworks(""))
        assertEquals(SIGNUP_NETWORKS, signupNetworks("   "))
    }

    @Test
    fun `le nom d'usage du reseau retrouve le reseau`() {
        // « TAN » n'est écrit nulle part à l'écran, et c'est pourtant le mot
        // que tape un conducteur nantais.
        val found = signupNetworks("tan")
        assertEquals(listOf(ProRegistrationDraft.NAOLIB_NETWORK_KEY), found.map { it.key })
    }

    @Test
    fun `la recherche ignore les accents et la casse`() {
        assertTrue(signupNetworks("METROPOLE").isNotEmpty())
        assertTrue(signupNetworks("métropole").isNotEmpty())
        assertTrue(signupNetworks("Nantes").isNotEmpty())
    }

    @Test
    fun `la requete se cherche au milieu d'un nom et pas seulement en tete`() {
        assertTrue(signupNetworks("olib").isNotEmpty())
    }

    @Test
    fun `un reseau inconnu ne ramene rien`() {
        assertTrue(signupNetworks("ratp").isEmpty())
    }

    @Test
    fun `l'initiale sert d'embleme tant que le logo manque`() {
        assertEquals("N", SIGNUP_NETWORKS.first().initial)
    }

    @Test
    fun `chaque cle du catalogue est celle qu'attend le brouillon`() {
        // Une clé qui diverge ne casse rien à la compilation : elle produit un
        // compte rattaché à un réseau que le back-office ne connaît pas.
        assertTrue(SIGNUP_NETWORKS.all { it.key.isNotBlank() })
        assertEquals(
            SIGNUP_NETWORKS.map { it.key }.distinct().size,
            SIGNUP_NETWORKS.size,
        )
        assertTrue(
            SIGNUP_NETWORKS.any { it.key == ProRegistrationDraft.NAOLIB_NETWORK_KEY },
        )
    }
}
