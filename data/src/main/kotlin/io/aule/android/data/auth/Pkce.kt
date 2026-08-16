package io.aule.android.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE S256 — RFC 7636.
 *
 * GoTrue n'accepte le lien de confirmation que si le client qui a lancé
 * l'inscription présente le vérifieur. Flutter le fait via supabase-js ;
 * ici c'est OkHttp, donc le défi se calcule à la main.
 */
internal object Pkce {
    private val random = SecureRandom()

    fun generateVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return encode(bytes)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return encode(digest)
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
