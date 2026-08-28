package io.aule.android.core.security

import androidx.biometric.BiometricPrompt
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * La table des codes d'erreur, en entier.
 *
 * Elle tient sur la JVM parce que les `ERROR_*` d'androidx sont des constantes
 * de compilation : les citer n'appelle aucun code Android. C'est ce qui permet
 * de vérifier sans appareil la seule chose qui compte vraiment ici — qu'aucun
 * code ne mène à une impasse.
 */
class BiometricFailureKindTest {

    @Test
    fun `l'absence d'enrolement mene aux reglages`() {
        assertEquals(
            BiometricFailureKind.NOT_ENROLLED,
            biometricFailureKindOf(BiometricPrompt.ERROR_NO_BIOMETRICS),
        )
        assertEquals(
            BiometricFailureKind.NOT_ENROLLED,
            biometricFailureKindOf(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL),
        )
    }

    @Test
    fun `le materiel absent ou muet est un seul cas`() {
        listOf(
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
        ).forEach { code ->
            assertEquals(
                BiometricFailureKind.HARDWARE_UNAVAILABLE,
                biometricFailureKindOf(code),
                "code $code",
            )
        }
    }

    @Test
    fun `les deux verrous ne se confondent pas`() {
        // Le premier se rouvre tout seul, le second non : l'écran ne dit pas la
        // même phrase, et surtout il ne propose pas de réessayer dans un cas.
        assertEquals(
            BiometricFailureKind.TEMPORARY_LOCKOUT,
            biometricFailureKindOf(BiometricPrompt.ERROR_LOCKOUT),
        )
        assertEquals(
            BiometricFailureKind.PERMANENT_LOCKOUT,
            biometricFailureKindOf(BiometricPrompt.ERROR_LOCKOUT_PERMANENT),
        )
    }

    @Test
    fun `les trois facons de dire non se taisent pareil`() {
        listOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { code ->
            assertEquals(
                BiometricFailureKind.USER_CANCELED,
                biometricFailureKindOf(code),
                "code $code",
            )
        }
    }

    @Test
    fun `les rates passagers restent passagers`() {
        listOf(
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
        ).forEach { code ->
            assertEquals(
                BiometricFailureKind.TRANSIENT,
                biometricFailureKindOf(code),
                "code $code",
            )
        }
    }

    @Test
    fun `un code inconnu est un refus, jamais un passage`() {
        assertEquals(
            BiometricFailureKind.UNKNOWN,
            biometricFailureKindOf(BiometricPrompt.ERROR_VENDOR),
        )
        assertEquals(BiometricFailureKind.UNKNOWN, biometricFailureKindOf(4242))
        assertEquals(BiometricFailureKind.UNKNOWN, biometricFailureKindOf(-1))
    }

    /**
     * La clé invalidée ne vient jamais du dialogue : elle est levée par le
     * coffre au moment de fabriquer le `Cipher`. Ce test fige cette frontière —
     * si un code se mettait un jour à la produire, c'est ici qu'on l'apprendrait.
     */
    @Test
    fun `aucun code du dialogue ne produit KEY_INVALIDATED`() {
        val codes = (-1..20).toList() + listOf(4242)
        codes.forEach { code ->
            assert(biometricFailureKindOf(code) != BiometricFailureKind.KEY_INVALIDATED) {
                "le code $code ne devrait pas produire KEY_INVALIDATED"
            }
        }
    }
}
