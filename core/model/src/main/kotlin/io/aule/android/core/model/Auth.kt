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
    /**
     * Périmé — **ou sur le point de l'être**.
     *
     * La marge n'est pas de la prudence gratuite : sans elle, un jeton qui
     * expirait dans deux secondes était jugé valide au démarrage, et le premier
     * appel PostgREST qui suivait prenait un 401 que rien ne rattrape (il n'y a
     * pas d'intercepteur de rafraîchissement sur 401). Une minute couvre le
     * lancement de l'application et le premier aller-retour réseau.
     */
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1_000): Boolean =
        nowEpochSeconds >= expiresAtEpochSeconds - AUTH_EXPIRY_MARGIN_SECONDS
}

/** De combien on anticipe l'expiration d'un jeton d'accès, en secondes. */
const val AUTH_EXPIRY_MARGIN_SECONDS = 60L

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

    /**
     * Retour d'un fournisseur externe pour une **inscription** : ouvre
     * l'application, comme [SIGN_UP], mais pose d'abord les métadonnées
     * d'onboarding.
     *
     * C'est ce qui le distingue : `/authorize` ne transporte pas de `data`. Le
     * métier, le réseau, le matricule — tout ce que les quatre premières étapes
     * ont collecté — n'atteignent donc le compte qu'après coup, sur la session
     * ouverte. Sans cette pose, le back-office recevrait un compte sans demande
     * d'habilitation : quelqu'un qui a rempli l'inscription en entier, et que
     * personne ne peut valider parce que rien ne dit ce qu'il a demandé.
     */
    OAUTH_SIGN_UP,

    /**
     * Retour d'un fournisseur externe pour **entrer**, que le compte existe
     * déjà ou non : ouvre l'application comme [SIGN_UP], et ne pose rien.
     *
     * C'est le chemin de l'application grand public — « Continuer avec
     * Google » y vaut connexion et inscription à la fois, GoTrue créant le
     * compte à la première venue et `handle_new_auth_user` lui posant le rôle
     * passager. Un genre distinct d'[OAUTH_SIGN_UP], parce que ce retour-là
     * n'a aucune métadonnée d'onboarding à poser : le confondre avec
     * l'inscription professionnelle ferait chercher un brouillon qui n'existe
     * pas, et le journal s'en plaindrait à chaque connexion.
     */
    OAUTH_SIGN_IN,
}

/**
 * Les fournisseurs d'identité acceptés par GoTrue pour Aule Pro.
 *
 * [key] est le `provider` de `/authorize` — la valeur exacte attendue par
 * Supabase, pas un libellé. Un fournisseur n'entre ici qu'une fois **activé
 * côté projet Supabase** : sans identifiant client déposé au tableau de bord,
 * `/authorize` répond 400 et l'écran n'a rien d'intelligent à en dire.
 */
enum class OAuthProvider(val key: String) {
    GOOGLE("google"),
}
