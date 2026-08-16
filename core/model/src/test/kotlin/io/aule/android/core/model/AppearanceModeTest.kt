package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AppearanceModeTest {

    @Test
    fun `le theme clair est utilise par defaut`() {
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage(null))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage(""))
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("unknown"))
    }

    @Test
    fun `un choix enregistre se relit sous le nom Flutter`() {
        assertEquals(AppearanceMode.LIGHT, AppearanceMode.fromStorage("light"))
        assertEquals(AppearanceMode.DARK, AppearanceMode.fromStorage("dark"))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStorage("system"))
        assertEquals("light", AppearanceMode.LIGHT.storageName)
        assertEquals("dark", AppearanceMode.DARK.storageName)
        assertEquals("system", AppearanceMode.SYSTEM.storageName)
    }

    @Test
    fun `auto suit le systeme, les autres l ignorent`() {
        assertFalse(AppearanceMode.LIGHT.isNight(systemDark = true))
        assertTrue(AppearanceMode.DARK.isNight(systemDark = false))
        assertTrue(AppearanceMode.SYSTEM.isNight(systemDark = true))
        assertFalse(AppearanceMode.SYSTEM.isNight(systemDark = false))
    }
}
