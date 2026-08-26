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

/**
 * Ce qu'un échange PKCE en attente était venu faire.
 *
 * Les deux liens arrivent sur la **même** adresse — `io.aule.pro://login-callback/` —
 * et ouvrent tous deux une session. Ce qui les sépare est ce qu'on a le droit de
 * faire ensuite : une confirmation d'inscription entre dans l'application, une
 * récupération n'ouvre que l'écran du nouveau mot de passe. Sans cette distinction,
 * la boîte e-mail devient une porte d'entrée : il suffirait d'un vieux lien de
 * réinitialisation pour se retrouver sur la carte sans jamais retaper de mot de passe.
 *
 * Le genre est écrit **au moment de la demande**, à côté du vérifieur, plutôt que
 * relu dans l'URL de retour. GoTrue ne promet pas de reposer `type=recovery` sur un
 * retour PKCE — le SDK iOS ne sait d'ailleurs le lire que sur le flux implicite,
 * qu'Aule n'utilise pas — et une distinction de sécurité ne se fonde pas sur un
 * paramètre facultatif. Ce qu'on a demandé, on le sait ; l'URL ne fait que confirmer.
 */
enum class AuthPkceFlow {
    /** Confirmation d'e-mail après inscription : ouvre l'application. */
    SIGN_UP,

    /** Lien « mot de passe oublié » : n'ouvre que le choix d'un nouveau mot de passe. */
    RECOVERY,
}
