package io.aule.android.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.aule.android.core.common.AuleDispatchers
import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.Place
import io.aule.android.core.model.SavedPlace
import io.aule.android.core.model.SavedPlaceIcon
import io.aule.android.core.model.SavedPlaceSlot
import io.aule.android.core.model.mergeSavedPlaces
import io.aule.android.core.model.pruneSavedTombstones
import io.aule.android.core.model.removeSavedPlace
import io.aule.android.core.model.repository.SavedPlaceRepository
import io.aule.android.core.model.repository.SavedPlacesStore
import io.aule.android.core.model.savedPlaceAt
import io.aule.android.core.model.shortPlaceName
import io.aule.android.core.model.upsertSavedPlace
import io.aule.android.core.model.visibleSavedPlaces
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ce qu'on enregistre ou modifie : un nom, un emplacement, une icône, un lieu.
 *
 * Un type plutôt que sept paramètres — c'est ce que l'éditeur tient à l'écran,
 * et le passer d'un bloc évite qu'un appelant intervertisse deux chaînes que le
 * compilateur ne distingue pas.
 *
 * [id] absent : c'est une création. Présent : une modification, qui garde sa
 * place dans la liste.
 */
internal data class SavedPlaceEdit(
    val id: String? = null,
    val name: String,
    val slot: SavedPlaceSlot,
    val icon: SavedPlaceIcon,
    val place: Place,
)

/**
 * Les adresses favorites de l'écran carte.
 *
 * ## L'appareil décide, le compte rattrape
 *
 * La liste vient du disque, **synchrone**, et elle est à l'écran avant la
 * première image. Le réseau ne fait que rattraper : sans lui, les raccourcis
 * marchent — dans un parking souterrain comme au premier lancement après une
 * réinstallation, où il n'y a encore rien à rattraper.
 *
 * C'est l'inverse d'un modèle qui attendrait le serveur. Une recherche qui
 * s'ouvre sur « Domicile » puis le remplace deux secondes plus tard par la même
 * chose venue d'ailleurs aurait le clignotement en plus et rien en moins.
 *
 * ## Une synchronisation ratée n'est pas un incident
 *
 * Elle est journalisée et rien d'autre : les favoris locaux sont déjà là, et un
 * bandeau rouge sur une liste correcte n'apprendrait rien à personne. C'est la
 * même règle que le géocodeur muet dans la recherche.
 *
 * ⚠️ **La fusion précède l'écriture, toujours.** Écrire d'abord le local puis
 * pousser écraserait ce que l'autre appareil a enregistré entre-temps. C'est
 * [mergeSavedPlaces] qui tranche, à l'horodatage, et lui seul.
 */
internal class SavedPlacesModel(
    private val store: SavedPlacesStore?,
    private val repository: SavedPlaceRepository?,
    private val session: () -> AuthSession?,
    private val dispatchers: AuleDispatchers,
    private val scope: CoroutineScope,
    private val logger: AuleLogger,
    private val now: () -> Instant = { Instant.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * À qui appartient ce qui est en mémoire.
     *
     * Un téléphone de service passe de main en main : sans cette vérification,
     * les favoris du collègue précédent seraient restés à l'écran, puis poussés
     * sur le compte du suivant à la première synchronisation.
     */
    private var owner: String? = session()?.user?.id

    /**
     * Tout ce qui est écrit, pierres tombales comprises. Ce n'est pas ce que la
     * vue lit — [places] filtre et ordonne — mais c'est ce qui part au serveur :
     * une suppression ne se propage que si elle voyage.
     */
    private var all: List<SavedPlace> = store?.read(owner).orEmpty()

    /** Les raccourcis, dans l'ordre où ils se lisent. */
    var places by mutableStateOf(all.visibleSavedPlaces())
        private set

    private var syncJob: Job? = null
    private var lastSync: Instant? = null

    fun at(slot: SavedPlaceSlot): SavedPlace? {
        rebindOwner()
        return all.savedPlaceAt(slot)
    }

    /**
     * Relit le dépôt si le compte a changé depuis la dernière fois.
     *
     * Appelée avant toute lecture et toute écriture, plutôt que par un
     * abonnement à la session : c'est une comparaison de deux chaînes, elle
     * coûte moins qu'un collecteur, et surtout elle ne peut pas être **oubliée**
     * — un abonnement branché à un seul endroit laisserait passer le chemin
     * qu'on n'aurait pas pensé à couvrir.
     */
    private fun rebindOwner() {
        val current = session()?.user?.id
        if (current == owner) return
        owner = current
        all = store?.read(current).orEmpty()
        places = all.visibleSavedPlaces()
        // Le compte a changé : ce qui avait été synchronisé ne dit plus rien de
        // celui-ci.
        lastSync = null
        syncJob?.cancel()
    }

    /**
     * Enregistre ou modifie une adresse.
     *
     * L'écriture locale est **immédiate et synchrone** : le volet se referme sur
     * une liste déjà à jour. La poussée suit, et son échec ne défait rien — la
     * prochaine synchronisation la reprendra, puisque c'est l'horodatage local
     * qui gagnera.
     */
    fun save(edit: SavedPlaceEdit) {
        rebindOwner()
        val instant = now()
        val existing = edit.id?.let { id -> all.firstOrNull { it.id == id && !it.isDeleted } }
        val place = SavedPlace(
            id = existing?.id ?: newId(),
            // Un emplacement nommé porte le nom de son emplacement, pas une
            // phrase enregistrée : « Domicile » se traduit, la donnée non
            // (ADR-011).
            name = if (edit.slot == SavedPlaceSlot.CUSTOM) {
                edit.name.trim().ifEmpty { shortPlaceName(edit.place.label) }
            } else {
                ""
            },
            slot = edit.slot,
            icon = edit.icon,
            label = edit.place.label,
            coordinate = edit.place.coordinate,
            stopMode = edit.place.stopMode,
            // La date d'enregistrement d'origine survit à la modification :
            // c'est elle qui fixe la place dans la liste, et corriger un nom ne
            // doit pas faire sauter le raccourci en tête.
            createdAt = existing?.createdAt ?: instant,
            updatedAt = instant,
        )
        commit(upsertSavedPlace(place, all))
        logger.info(LogDomain.APP, "Adresse favorite enregistrée (${place.slot.wire}).")
    }

    /** Supprime une adresse — la pierre tombale part au serveur avec le reste. */
    fun remove(id: String) {
        rebindOwner()
        commit(removeSavedPlace(id, all, now()))
        logger.info(LogDomain.APP, "Adresse favorite supprimée.")
    }

    /**
     * Rapproche l'appareil et le compte.
     *
     * Appelée à l'ouverture de la recherche, et forcée après chaque écriture.
     * Le débrayage n'est pas une économie de confort : la recherche s'ouvre et
     * se referme des dizaines de fois par service, et une requête à chaque
     * ouverture ferait payer un aller-retour à un geste qui n'attend rien.
     */
    fun sync(force: Boolean = false) {
        rebindOwner()
        val repository = repository ?: return
        val session = session() ?: return
        if (syncJob?.isActive == true) return
        val since = lastSync
        if (!force && since != null && since.plusMillis(SYNC_INTERVAL_MS).isAfter(now())) return

        val issued = owner
        syncJob = scope.launch {
            val outcome = runCatching {
                val remote = withContext(dispatchers.io) { repository.fetch(session) }
                // La fusion d'abord : pousser le local tel quel écraserait ce
                // que l'autre appareil a enregistré pendant qu'on était hors
                // ligne.
                val merged = mergeSavedPlaces(all, remote)
                withContext(dispatchers.io) { repository.push(session, merged) }
                // Le serveur a pris acte des suppressions : elles n'ont plus
                // rien à apprendre à personne, et les garder ferait grossir sans
                // fin un fichier de préférences pour y écrire des absences.
                val acknowledged = merged.filter { it.isDeleted }.map { it.id }.toSet()
                pruneSavedTombstones(merged, acknowledged)
            }
            outcome.onSuccess { synced ->
                // Le compte a pu changer pendant l'aller-retour : écrire ces
                // favoris sous le nouveau propriétaire les lui donnerait.
                if (issued != owner) return@onSuccess
                lastSync = now()
                commit(synced, push = false)
            }.onFailure { failure ->
                if (failure is CancellationException) throw failure
                // Les favoris locaux sont déjà à l'écran : il n'y a rien à
                // annoncer, et rien à défaire.
                logger.warn(LogDomain.NET, "Favoris non synchronisés.", failure)
            }
        }
    }

    private fun commit(next: List<SavedPlace>, push: Boolean = true) {
        all = next
        places = next.visibleSavedPlaces()
        store?.write(owner, next)
        if (push) sync(force = true)
    }

    private companion object {
        /**
         * Cinq minutes. Un favori change quelques fois par an ; ce qui justifie
         * de redemander, c'est l'autre appareil — et un quart d'heure de retard
         * sur une adresse enregistrée ailleurs ne se remarque pas, là où une
         * requête par ouverture de la recherche se remarquerait au forfait.
         */
        const val SYNC_INTERVAL_MS = 5 * 60 * 1000L
    }
}
