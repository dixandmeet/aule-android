package io.aule.android.core.security

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * Les huit combinaisons, parce qu'il n'y en a que huit.
 *
 * Une table exhaustive vaut mieux qu'un échantillon quand l'espace tient dans
 * une page : elle dit du même geste ce que la règle fait et ce qu'elle ne fait
 * pas.
 */
class BiometricTypeResolverTest {

    @Test
    fun `l'empreinte seule est le seul cas nomme`() {
        assertEquals(
            BiometricType.FINGERPRINT,
            BiometricTypeResolver.resolve(
                hasFingerprintFeature = true,
                hasFaceFeature = false,
                hasIrisFeature = false,
            ),
        )
    }

    @Test
    fun `un capteur accompagne retombe sur le mot generique`() {
        // Android ne dit jamais ce qui est *enrôlé* : dès qu'un second capteur
        // existe, promettre « empreinte » revient à parier sur le dialogue qui
        // s'ouvrira ensuite.
        assertEquals(
            BiometricType.GENERIC,
            BiometricTypeResolver.resolve(
                hasFingerprintFeature = true,
                hasFaceFeature = true,
                hasIrisFeature = false,
            ),
        )
        assertEquals(
            BiometricType.GENERIC,
            BiometricTypeResolver.resolve(
                hasFingerprintFeature = true,
                hasFaceFeature = false,
                hasIrisFeature = true,
            ),
        )
        assertEquals(
            BiometricType.GENERIC,
            BiometricTypeResolver.resolve(
                hasFingerprintFeature = true,
                hasFaceFeature = true,
                hasIrisFeature = true,
            ),
        )
    }

    @Test
    fun `sans empreinte le mot reste generique`() {
        val sansEmpreinte = listOf(
            Triple(false, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(false, true, true),
        )
        sansEmpreinte.forEach { (empreinte, visage, iris) ->
            assertEquals(
                BiometricType.GENERIC,
                BiometricTypeResolver.resolve(empreinte, visage, iris),
                "empreinte=$empreinte visage=$visage iris=$iris",
            )
        }
    }
}
