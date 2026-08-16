package io.aule.android.core.common.config

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DataSourceTest {

    @Test
    fun `les trois sources se lisent par leur identifiant`() {
        assertEquals(DataSource.MOCK, DataSource.of("mock"))
        assertEquals(DataSource.DEVELOPMENT, DataSource.of("development"))
        assertEquals(DataSource.PRODUCTION, DataSource.of("production"))
    }

    /**
     * Une valeur inconnue ne prend pas un défaut silencieux.
     *
     * Se rabattre sur `production` semblerait prudent et serait le contraire :
     * une faute de frappe dans un flavor passerait inaperçue jusqu'au jour où
     * quelqu'un regarde des véhicules qui ne sont pas ceux qu'il croit.
     */
    @Test
    fun `une source inconnue leve, elle ne se rabat pas`() {
        val failure = assertThrows<IllegalStateException> { DataSource.of("prod") }
        assertTrue(failure.message!!.contains("prod"), "le message doit citer la valeur reçue")
        assertTrue(failure.message!!.contains("production"), "et rappeler les valeurs attendues")
    }
}

class AppConfigTest {

    private fun config(
        apiBase: String = "https://www.aule.fr",
        supabaseUrl: String = "https://exemple.supabase.co",
        supabaseKey: String = "clé",
    ) = AppConfig(
        dataSource = DataSource.PRODUCTION,
        apiBase = apiBase,
        supabaseUrl = supabaseUrl,
        supabasePublishableKey = supabaseKey,
        environmentLabel = "Développement",
        versionName = "0.1.0-dev",
        versionCode = 1,
    )

    /**
     * Le backend porte des positions de conducteurs. Un `http://` accidentel dans
     * un fichier de configuration ne doit pas pouvoir démarrer l'application.
     */
    @Test
    fun `une API en clair est refusee au demarrage`() {
        assertThrows<IllegalArgumentException> { config(apiBase = "http://www.aule.fr") }
    }

    @Test
    fun `une API en HTTPS passe`() {
        assertEquals("https://www.aule.fr", config().apiBase)
    }

    /**
     * Une configuration absente doit se dire. C'est la différence entre un écran
     * vide qu'on explique et un écran vide qu'on cherche.
     */
    @Test
    fun `Supabase est declare non configure quand une valeur manque`() {
        assertTrue(config().supabaseConfigured)
        assertFalse(config(supabaseUrl = "").supabaseConfigured)
        assertFalse(config(supabaseKey = "").supabaseConfigured)
        assertFalse(config(supabaseUrl = "   ").supabaseConfigured, "le blanc ne vaut pas une valeur")
    }

    /**
     * Trois APK Aule cohabitent sur l'appareil de test. Le pied de page doit dire
     * lequel on regarde, sans quoi on conclut sur le mauvais binaire.
     */
    @Test
    fun `le libelle de build nomme l environnement, la version et la source`() {
        val label = config().buildLabel
        assertTrue(label.contains("Développement"), label)
        assertTrue(label.contains("0.1.0-dev"), label)
        assertTrue(label.contains("1"), label)
        assertTrue(label.contains("production"), label)
    }
}
