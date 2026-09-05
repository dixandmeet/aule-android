package io.aule.android.core.model

import java.time.Instant

/**
 * Comment une page reçue se fond dans ce qui est déjà à l'écran.
 *
 * ## Pourquoi un objet pur, et pas une méthode du ViewModel
 *
 * C'est la règle la plus facile à casser de toute la messagerie, et la seule
 * dont l'erreur ne se voie pas tout de suite : un doublon apparaît une fois sur
 * dix, à la sortie d'un tunnel. Isolée ici, elle s'éprouve sans réseau, sans
 * horloge et sans vue.
 *
 * ## Les quatre règles, et ce qu'elles coûtent quand on les oublie
 *
 * 1. **Le serveur fait foi sur un identifiant partagé.** Un message déjà connu
 *    est remplacé, pas ajouté : le delta rend plusieurs fois les mêmes messages,
 *    par construction — le recouvrement de cinq secondes en dépend.
 * 2. **L'écho d'un envoi optimiste s'apparie par [HubMessage.clientId].** Le
 *    message affiché tout de suite n'a pas encore d'identifiant serveur ; sans
 *    cet appariement, il resterait à côté de sa propre copie confirmée.
 * 3. **Une pierre tombale retire.** Le serveur ne rend un message supprimé que
 *    pour dire qu'il l'est ; le garder à l'écran serait exactement ce que la
 *    suppression cherchait à éviter.
 * 4. **L'ordre est celui de la création**, l'identifiant départageant les
 *    ex æquo. Deux messages écrits dans la même transaction portent la même date
 *    à la microseconde près, et un tri instable les ferait sauter d'un
 *    rafraîchissement à l'autre.
 */
object HubMessageMerge {

    /** Le recouvrement appliqué à l'heure du serveur, en secondes. */
    const val RECOUVREMENT_SECONDES: Long = 5

    fun merge(local: List<HubMessage>, remote: List<HubMessage>): List<HubMessage> {
        val parIdentifiant = LinkedHashMap<String, HubMessage>()
        val ordre = ArrayList<String>(local.size + remote.size)

        for (message in local) {
            if (!parIdentifiant.containsKey(message.id)) ordre.add(message.id)
            parIdentifiant[message.id] = message
        }

        // Les envois en attente, retrouvables par leur identifiant client.
        val attenteParClient = HashMap<String, String>()
        for (message in local) {
            if (message.deliveryState == HubDeliveryState.ENVOYE) continue
            val client = message.clientId ?: continue
            attenteParClient[client] = message.id
        }

        for (message in remote) {
            val client = message.clientId
            val localId = if (client != null) attenteParClient[client] else null

            // L'écho de notre propre envoi : il prend la place de l'optimiste,
            // au même rang.
            if (client != null && localId != null && localId != message.id) {
                parIdentifiant.remove(localId)
                val rang = ordre.indexOf(localId)
                if (rang >= 0) ordre[rang] = message.id else ordre.add(message.id)
                attenteParClient.remove(client)
                parIdentifiant[message.id] = message
                continue
            }

            if (message.isDeleted) {
                parIdentifiant.remove(message.id)
                ordre.remove(message.id)
                continue
            }

            if (!parIdentifiant.containsKey(message.id)) ordre.add(message.id)
            parIdentifiant[message.id] = message
        }

        return ordre
            .mapNotNull { parIdentifiant[it] }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
    }

    /**
     * Le curseur du tour suivant.
     *
     * ⚠️ **Deux cas, et les confondre perd des messages.** Tant que la page est
     * tronquée, on repart de `nextAfter` : c'est la seule façon d'atteindre ce
     * qu'elle n'a pas rendu. Une fois complète, on repart de l'heure du
     * **serveur** moins cinq secondes — ce recouvrement rattrape les
     * transactions qui ont commité après avoir daté leur écriture.
     *
     * L'horloge du téléphone n'entre jamais dans ce calcul : une date locale en
     * avance sauterait des messages, et une date en retard en redemanderait des
     * milliers.
     */
    fun nextCursor(page: HubMessagePage, previous: Instant?): Instant? {
        val suite = page.nextAfter
        if (page.hasMore && suite != null) return suite
        val serveur = page.serverTime ?: return previous
        return serveur.minusSeconds(RECOUVREMENT_SECONDES)
    }
}
