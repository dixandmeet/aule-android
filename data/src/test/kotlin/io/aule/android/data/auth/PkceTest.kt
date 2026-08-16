package io.aule.android.data.auth

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class PkceTest {

    @Test
    fun `le defi S256 est stable pour un verifieur donne`() {
        val verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012"
        assertEquals(
            "10ugdeQlnGyAfEEB5m0oEJbPn_FLoBJg3udBsb2u8yY",
            Pkce.challenge(verifier),
        )
    }

    @Test
    fun `un verifieur aleatoire tient la longueur RFC`() {
        val verifier = Pkce.generateVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }
}
