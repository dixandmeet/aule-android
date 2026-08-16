package io.aule.android.core.model

/**
 * Une session ouverte — ce que le reste de l'app a le droit de savoir.
 *
 * Les jetons restent dans le dépôt de session : un écran qui les lirait
 * finirait par les journaliser, et le journal interdit les jetons.
 */
data class AuthUser(
    val id: String,
    val email: String,
)

data class AuthSession(
    val user: AuthUser,
    val accessToken: String,
    val refreshToken: String,
    /** Instant d'expiration du jeton d'accès, epoch secondes. */
    val expiresAtEpochSeconds: Long,
) {
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1_000): Boolean =
        nowEpochSeconds >= expiresAtEpochSeconds
}

/**
 * Pourquoi la connexion a échoué, sans phrase.
 *
 * L'UI traduit (ADR-011). Les codes reprennent ceux de GoTrue /
 * `dashboard/lib/auth-errors.ts` quand ils existent.
 */
enum class AuthFailureKind {
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    RATE_LIMITED,
    WEAK_PASSWORD,
    INVALID_EMAIL,
    NOT_CONFIGURED,
    NETWORK,
    NO_HABILITATION,
    HABILITATION_UNVERIFIED,
    USER_ALREADY_EXISTS,
    UNKNOWN,
}

class AuthException(
    val kind: AuthFailureKind,
    val serverMessage: String? = null,
) : Exception(serverMessage ?: kind.name)
