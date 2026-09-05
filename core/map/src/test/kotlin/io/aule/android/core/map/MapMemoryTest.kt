package io.aule.android.core.map

import android.content.ComponentCallbacks2
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class MapMemoryTest {

    /**
     * Le palier le plus bas arrive sur un appareil qui respire encore : y rendre
     * les textures ferait clignoter la carte pour rien.
     */
    @Test
    fun `une suggestion polie ne vide pas le cache`() {
        assertFalse(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }

    @Test
    fun `une demande serieuse le vide`() {
        assertTrue(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
    }

    /**
     * Les paliers d'arrière-plan valent tous plus que le seuil : personne ne
     * regarde la carte, on rend d'autant plus volontiers.
     */
    @Test
    fun `en arriere-plan, on rend toujours`() {
        assertTrue(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
        assertTrue(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
        assertTrue(shouldReleaseGraphics(ComponentCallbacks2.TRIM_MEMORY_COMPLETE))
    }
}
