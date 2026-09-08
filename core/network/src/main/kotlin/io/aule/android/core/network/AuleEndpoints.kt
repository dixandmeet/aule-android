package io.aule.android.core.network

/**
 * Les points d'entrée du BFF Aule.
 *
 * Port de `SAE/lib/config/api_endpoints.dart`, et des mêmes chemins que le proto
 * iOS. Ils sont rassemblés ici pour qu'aucun repository n'écrive une URL en dur —
 * un chemin recopié à deux endroits finit toujours par diverger.
 */
class AuleEndpoints(base: String) {

    private val root = base.trimEnd('/')

    val vehicles = "$root/api/carte-immersive/vehicles"
    val stops = "$root/api/carte-immersive/stops"
    val stopDepartures = "$root/api/carte-immersive/stop-departures"
    val stopServingLines = "$root/api/carte-immersive/stop-serving-lines"

    /**
     * La grille théorique d'une desserte, un jour donné.
     *
     * Servie `s-maxage=300` depuis un cache **partagé** : deux voyageurs du même
     * quai n'y paient qu'une lecture. C'est aussi pourquoi elle ne passe pas par
     * le cache disque du client — l'y garder la ferait survivre à un changement de
     * grille, deux fois l'an, sans que rien à l'écran ne dise que l'horaire
     * affiché est celui de l'ancienne.
     */
    val stopDaySchedule = "$root/api/carte-immersive/stop-day-schedule"
    val geocode = "$root/api/geocode"

    /**
     * Le calcul d'itinéraire. `from` et `to` s'écrivent en **`lng,lat`** —
     * inversés, le serveur répond 404 sans rien expliquer.
     */
    val route = "$root/api/route"

    /**
     * Les notes de service affichées aujourd'hui — ce que l'exploitant a décidé.
     *
     * Exige un jeton porteur : la RLS `line_service_notes_read` n'ouvre la lecture
     * qu'aux membres du réseau et à ses conducteurs.
     *
     * ⚠️ **Une lecture, donc un 404 sans conséquence** : « la route n'existe pas »
     * et « rien n'est affiché » mènent au même écran vide. C'est l'inverse des
     * routes d'écriture du service, où les confondre ferait croire à un geste
     * enregistré.
     */
    val driverServiceNotes = "$root/api/driver/service-notes"

    // ------------------------------------------------------------------
    // Messagerie Aule Pro — contrat §12
    // ------------------------------------------------------------------

    /**
     * ⚠️ **À appeler avant tout le reste, et pas à chaque tour.** C'est la seule
     * route de la messagerie qui écrit sans qu'on le lui demande : elle crée les
     * canaux Réseau et Dépôt et y inscrit l'agent. La lecture des canaux, elle,
     * est sondée toutes les quinze secondes.
     */
    val hubBootstrap = "$root/api/hub/bootstrap"
    val hubChannels = "$root/api/hub/channels"
    val hubDirect = "$root/api/hub/channels/direct"
    val hubColleagues = "$root/api/hub/colleagues/search"

    /** Sa propre porte : `PUT {acceptsDirect}`. Il n'y a pas de `GET` — le
     * répertoire rend déjà `meAcceptsDirect`. */
    val hubContactPreference = "$root/api/hub/contact-preference"
    val hubUnread = "$root/api/hub/unread"
    val hubPushToken = "$root/api/hub/push-token"

    fun hubChannel(channelId: String) = "$root/api/hub/channels/$channelId"
    fun hubMessages(channelId: String) = "${hubChannel(channelId)}/messages"
    fun hubMembers(channelId: String) = "${hubChannel(channelId)}/members"
    fun hubLeave(channelId: String) = "${hubChannel(channelId)}/leave"
    fun hubRead(channelId: String) = "${hubChannel(channelId)}/read"
    fun hubFiles(channelId: String) = "${hubChannel(channelId)}/files"
    fun hubSignUpload(channelId: String) = "${hubFiles(channelId)}/sign-upload"
    fun hubMessage(messageId: String) = "$root/api/hub/messages/$messageId"
    fun hubReactions(messageId: String) = "${hubMessage(messageId)}/reactions"
}
