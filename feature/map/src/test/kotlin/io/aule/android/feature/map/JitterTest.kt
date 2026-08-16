package io.aule.android.feature.map

import kotlin.random.Random
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JitterTest {

    /**
     * La gigue reste dans `[backoff/2, backoff]`. Tirer jusqu'à zéro
     * martèlerait précisément l'API en difficulté.
     */
    @Test
    fun `la gigue reste dans la moitie haute du recul`() {
        val random = Random(0)
        repeat(50) {
            val delay = jittered(30_000, random)
            assertTrue(delay in 15_000..30_000, "délai hors plage : $delay")
        }
    }

    @Test
    fun `un recul nul reste nul`() {
        assertTrue(jittered(0) == 0L)
    }
}
