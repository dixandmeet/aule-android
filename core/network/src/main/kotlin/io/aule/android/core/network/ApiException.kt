package io.aule.android.core.network

/**
 * Ce qui peut mal se passer, dit assez précisément pour qu'on puisse réagir
 * différemment.
 *
 * Le découpage n'est pas cosmétique : [NotFound] et [UpstreamUnavailable] mènent
 * au même écran vide mais ne veulent pas dire la même chose. Le premier signifie
 * « rien ne circule à cette heure », le second « le fournisseur ne répond pas ».
 * Les confondre fait annoncer à l'app qu'il n'y a pas de bus alors qu'elle ne
 * sait pas.
 *
 * La distinction est portée par le **type**, pas par un commentaire ni par un
 * code d'état qu'un appelant pourrait oublier de lire.
 */
sealed class ApiException(
    message: String,
    cause: Throwable? = null,
    val retryAfter: String? = null,
) : Exception(message, cause) {

    /** 404 — la ressource existe, elle n'a simplement rien à dire maintenant. */
    class NotFound(val serverMessage: String? = null) :
        ApiException(serverMessage ?: "Aucune donnée pour cette demande.")

    /** 502/503/504 — un fournisseur en amont est muet. */
    class UpstreamUnavailable(val status: Int, retryAfter: String? = null) :
        ApiException("Le service de transport ne répond pas (HTTP $status).", retryAfter = retryAfter)

    /** 4xx autre que 404 — la requête est fautive. Un défaut du client, pas un incident réseau. */
    class BadRequest(val status: Int, val serverMessage: String? = null, retryAfter: String? = null) :
        ApiException(serverMessage ?: "Requête invalide (HTTP $status).", retryAfter = retryAfter)

    class Server(val status: Int, retryAfter: String? = null) :
        ApiException("Le serveur a répondu $status.", retryAfter = retryAfter)

    class Transport(cause: Throwable) :
        ApiException("Connexion impossible : ${cause.message}", cause)

    class Decoding(cause: Throwable) :
        ApiException("Réponse inattendue du serveur : ${cause.message}", cause)

    class Cancelled : ApiException("Demande annulée.")

    /** Vrai quand l'absence de données est une information, pas une panne. */
    val isEmptyByDesign: Boolean get() = this is NotFound
}
