package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Les adresses qu'on garde : le domicile, le travail, et tout ce qu'on y ajoute.
 *
 * ## Ce que ce fichier remplace
 *
 * L'historique des destinations ([rememberPlace]) répond déjà à « où alliez-vous
 * hier ». Il ne répond pas à « où allez-vous tous les jours » : huit entrées qui
 * tournent, et le domicile disparaît de la liste dès qu'on a fait cinq courses.
 * Un favori ne tourne pas — c'est sa seule différence, et elle suffit à en faire
 * une autre chose.
 *
 * ## Ce qui est ici, et ce qui n'y est pas
 *
 * Les règles — remplacer, supprimer, ordonner, fusionner — sont **pures**, et
 * c'est ce qui les rend vérifiables sans disque ni réseau. Ce qui les persiste
 * vit dans `:app` (préférences) et ce qui les synchronise dans `:data`
 * (PostgREST), sur le patron de [SearchHistoryStore].
 *
 * Aucun libellé : « Domicile » et « Travail » sont des phrases, elles vivent
 * dans les ressources (ADR-011). Le modèle porte l'**emplacement**, la vue le
 * formule — c'est ce qui permet de lire ses favoris en anglais sans que la
 * donnée écrite hier change de sens.
 */

/**
 * Combien d'adresses on garde.
 *
 * Vingt. Le plafond ne protège pas la place — vingt entrées pèsent deux
 * kilo-octets — il protège la liste : au-delà, on ne reconnaît plus un
 * raccourci d'un coup d'œil, et chercher dans ses favoris coûte autant que de
 * retaper l'adresse. C'est la même raison qui borne l'historique à huit, à
 * l'échelle près : un favori se pose une fois et se relit cent.
 */
const val SAVED_PLACES_LIMIT = 20

/**
 * Distance en deçà de laquelle deux enregistrements désignent la même porte.
 *
 * Vingt-cinq mètres, comme côté Flutter (`kSamePlaceMeters`) : la largeur d'une
 * rue avec ses trottoirs. Le géocodeur ne rend pas deux fois exactement le même
 * point pour une même adresse — il vise le bâtiment, la voie ou le centroïde
 * selon ce qu'il a trouvé —, et une égalité stricte ferait passer deux réponses
 * pour deux endroits.
 *
 * Plus serré que [SAME_PLACE_METERS], et pour une raison opposée : la recherche
 * fusionne des **arrêts homonymes** dans toute une agglomération, ici on
 * reconnaît une porte qu'on a déjà enregistrée.
 */
const val SAME_SAVED_PLACE_METERS = 25.0

/**
 * Où va un lieu enregistré.
 *
 * Deux emplacements nommés d'avance, et le reste. Ce n'est pas un classement :
 * c'est ce qui permet d'afficher « Domicile » avant qu'aucune adresse n'y soit,
 * donc de proposer le geste avant que la donnée existe. Un raccourci vide qui
 * invite à le remplir vaut mieux qu'un raccourci absent que personne ne
 * découvre.
 */
enum class SavedPlaceSlot(val wire: String) {
    HOME("home"),
    WORK("work"),
    CUSTOM("custom"),
    ;

    companion object {
        fun of(value: String?): SavedPlaceSlot =
            entries.firstOrNull { it.wire == value } ?: CUSTOM
    }
}

/**
 * L'icône d'un lieu enregistré — une **intention**, pas un dessin.
 *
 * Le dessin vit dans le design system, comme pour [io.aule.android.core.model.TransportMode] :
 * le modèle dit « crèche », la vue choisit le symbole. Écrire ici le nom d'une
 * icône Material lierait une donnée persistée à une bibliothèque graphique, et
 * le jour où le symbole change de nom, ce sont les favoris de tout le monde qui
 * deviennent illisibles.
 *
 * La liste est courte et fermée : un choix d'icône se fait dans une grille qu'on
 * embrasse d'un regard. Une valeur inconnue — écrite par une version plus
 * récente — retombe sur [PIN] plutôt que d'échouer au décodage.
 */
enum class SavedPlaceIcon(val wire: String) {
    HOME("home"),
    WORK("work"),
    SCHOOL("school"),
    GYM("gym"),
    FAMILY("family"),
    SHOPPING("shopping"),
    HEALTH("health"),
    DEPOT("depot"),
    STAR("star"),
    PIN("pin"),
    ;

    companion object {
        fun of(value: String?): SavedPlaceIcon =
            entries.firstOrNull { it.wire == value } ?: PIN

        /** Ce qu'on propose d'emblée pour un emplacement, avant tout choix. */
        fun forSlot(slot: SavedPlaceSlot): SavedPlaceIcon = when (slot) {
            SavedPlaceSlot.HOME -> HOME
            SavedPlaceSlot.WORK -> WORK
            SavedPlaceSlot.CUSTOM -> PIN
        }
    }
}

/**
 * Une adresse gardée.
 *
 * ## Pourquoi deux horodatages
 *
 * [createdAt] fixe l'**ordre**, [updatedAt] arbitre la **fusion**. Les
 * confondre casserait l'un des deux : trier sur la date de modification ferait
 * sauter un favori en tête de liste parce qu'on vient d'en corriger l'icône, et
 * fusionner sur la date de création ferait gagner l'appareil qui l'a enregistré
 * le premier — c'est-à-dire l'ancienne adresse, exactement celle qu'on venait de
 * corriger ailleurs.
 *
 * ## Pourquoi une pierre tombale
 *
 * [deletedAt] marque une suppression au lieu de retirer la ligne. Sans elle, un
 * favori effacé sur le téléphone revient au prochain démarrage : le serveur, qui
 * n'a rien appris, le renvoie comme une nouveauté. La pierre tombale voyage,
 * gagne la fusion, et n'est purgée qu'une fois la suppression connue des deux
 * côtés — voir [pruneSavedTombstones].
 *
 * @param name le nom donné, vide pour [SavedPlaceSlot.HOME] et
 *   [SavedPlaceSlot.WORK] dont le nom est celui de l'emplacement (ADR-011).
 * @param label l'adresse telle que le géocodeur l'a rendue, avec sa commune.
 * @param stopMode renseigné quand le lieu **est** un arrêt du réseau. C'est lui,
 *   jamais le libellé, qui dira qu'on peut en demander les passages — une
 *   adresse peut porter le nom d'un arrêt.
 */
data class SavedPlace(
    val id: String,
    val name: String,
    val slot: SavedPlaceSlot,
    val icon: SavedPlaceIcon,
    val label: String,
    val coordinate: Coordinate,
    val stopMode: TransportMode? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** Ce qu'on envoie au calcul d'itinéraire, et à l'historique. */
    fun toPlace(): Place = Place(
        label = label,
        coordinate = coordinate,
        stopMode = stopMode,
    )

    /**
     * Le même endroit, au nom près ou à la porte près.
     *
     * Les deux critères comptent, et pour deux raisons différentes : un arrêt
     * choisi puis re-choisi porte le même nom à un accent près, et une adresse
     * cherchée deux fois porte le même point à quelques mètres près.
     */
    fun sameAs(other: SavedPlace): Boolean {
        if (id == other.id) return true
        if (normalizeStopName(label) == normalizeStopName(other.label)) return true
        return GeoMath.distance(coordinate, other.coordinate) <= SAME_SAVED_PLACE_METERS
    }
}

/**
 * Les lieux visibles, dans l'ordre où ils se lisent.
 *
 * Domicile, Travail, puis le reste par ordre d'enregistrement. **L'ordre est
 * stable, et c'est tout ce qu'on lui demande** : une liste de raccourcis se
 * touche de mémoire, et la réordonner à chaque usage — comme le fait
 * l'historique, où c'est justement l'intérêt — ferait rater la cible à qui vise
 * sans lire.
 */
fun List<SavedPlace>.visibleSavedPlaces(): List<SavedPlace> = asSequence()
    .filterNot { it.isDeleted }
    .sortedWith(compareBy({ it.slot.ordinal }, { it.createdAt }, { it.id }))
    .take(SAVED_PLACES_LIMIT)
    .toList()

/** Le lieu enregistré à cet emplacement, ou `null` s'il est encore à remplir. */
fun List<SavedPlace>.savedPlaceAt(slot: SavedPlaceSlot): SavedPlace? =
    if (slot == SavedPlaceSlot.CUSTOM) null else visibleSavedPlaces().firstOrNull { it.slot == slot }

/**
 * Enregistre ou remplace une adresse.
 *
 * Trois règles, et chacune répare un défaut qu'on verrait à l'écran :
 *
 * - même identifiant : c'est une **modification** — on remplace, sans bouger de
 *   place dans la liste, [SavedPlace.createdAt] étant celui de l'entrée d'origine ;
 * - même emplacement nommé : Domicile ne peut désigner qu'une porte. Le
 *   précédent est **remplacé**, pas doublé — deux « Domicile » dans la liste ne
 *   se distinguent que par leur sous-titre, et il faudrait les lire ;
 * - même porte sous un autre nom : on remplace aussi. Enregistrer « Crèche »
 *   puis « École » sur la même adresse laisse une entrée, la dernière — sans
 *   quoi la liste garde deux raccourcis qui mènent au même endroit.
 *
 * Au-delà de [SAVED_PLACES_LIMIT] entrées vivantes, la plus ancienne des
 * personnalisées cède la place. Domicile et Travail ne sont jamais évincés :
 * ils sont l'objet même de la liste.
 */
fun upsertSavedPlace(
    place: SavedPlace,
    into: List<SavedPlace>,
    limit: Int = SAVED_PLACES_LIMIT,
): List<SavedPlace> {
    if (limit <= 0) return emptyList()
    val replaced = into.filterNot { existing ->
        if (existing.isDeleted) return@filterNot false
        existing.id == place.id ||
            (place.slot != SavedPlaceSlot.CUSTOM && existing.slot == place.slot) ||
            existing.sameAs(place)
    }
    val next = replaced + place

    val living = next.filterNot { it.isDeleted }
    if (living.size <= limit) return next
    // On n'évince que des personnalisés, et le plus ancien d'abord : ce qui
    // dépasse le plafond est ce qu'on a enregistré et jamais retouché.
    val evicted = living
        .filter { it.slot == SavedPlaceSlot.CUSTOM && it.id != place.id }
        .sortedBy { it.createdAt }
        .take(living.size - limit)
        .map { it.id }
        .toSet()
    return next.filterNot { it.id in evicted }
}

/**
 * Supprime une adresse — en laissant la trace de sa suppression.
 *
 * L'entrée reste, vidée de ce qui l'identifiait. Une pierre tombale n'a besoin
 * que d'un identifiant et d'une date : garder l'adresse d'un domicile qu'on
 * vient d'effacer serait garder précisément ce qu'on a demandé d'oublier.
 */
fun removeSavedPlace(
    id: String,
    from: List<SavedPlace>,
    at: Instant,
): List<SavedPlace> = from.map { existing ->
    if (existing.id != id || existing.isDeleted) {
        existing
    } else {
        existing.copy(
            name = "",
            label = "",
            coordinate = Coordinate(latitude = 0.0, longitude = 0.0),
            stopMode = null,
            updatedAt = at,
            deletedAt = at,
        )
    }
}

/**
 * Fusionne ce qu'on a sur l'appareil et ce que le compte a retenu.
 *
 * La règle tient en une phrase — **la version la plus récente gagne** — et deux
 * précisions qui font tout le travail :
 *
 * - à horodatage égal, la **suppression** l'emporte. Un ex æquo est le signe de
 *   deux écritures indépendantes, et faire revivre un favori qu'on a effacé se
 *   remarque bien plus qu'un favori qu'il faut réenregistrer ;
 * - deux appareils ont pu nommer chacun leur Domicile hors ligne. Le plus récent
 *   garde l'emplacement, l'autre **devient un lieu personnalisé** portant le nom
 *   court de son adresse. Écarter le perdant perdrait une adresse que quelqu'un
 *   a saisie ; le garder en double laisserait deux « Domicile » à départager à
 *   la lecture.
 */
fun mergeSavedPlaces(local: List<SavedPlace>, remote: List<SavedPlace>): List<SavedPlace> {
    val byId = LinkedHashMap<String, SavedPlace>()
    for (place in local + remote) {
        val known = byId[place.id]
        byId[place.id] = when {
            known == null -> place
            place.updatedAt.isAfter(known.updatedAt) -> place
            known.updatedAt.isAfter(place.updatedAt) -> known
            // Ex æquo : la suppression tranche.
            place.isDeleted -> place
            else -> known
        }
    }
    return byId.values.toList().resolveSlotConflicts()
}

/**
 * Un emplacement nommé ne désigne qu'une porte. Les prétendants les moins
 * récents redeviennent des lieux personnalisés.
 */
private fun List<SavedPlace>.resolveSlotConflicts(): List<SavedPlace> {
    val winners = filterNot { it.isDeleted }
        .filter { it.slot != SavedPlaceSlot.CUSTOM }
        .groupBy { it.slot }
        .mapValues { (_, claims) -> claims.maxWith(compareBy({ it.updatedAt }, { it.id })).id }
    return map { place ->
        if (place.isDeleted || place.slot == SavedPlaceSlot.CUSTOM) return@map place
        if (winners[place.slot] == place.id) return@map place
        place.copy(
            slot = SavedPlaceSlot.CUSTOM,
            name = place.name.ifBlank { shortPlaceName(place.label) },
        )
    }
}

/**
 * Efface les pierres tombales dont le serveur a pris acte.
 *
 * Une suppression connue des deux côtés n'a plus rien à apprendre à personne :
 * la garder ferait grossir sans fin un fichier de préférences pour y écrire des
 * absences. Tant que la synchronisation n'a pas eu lieu, elle reste — c'est elle
 * qui empêchera la résurrection.
 */
fun pruneSavedTombstones(places: List<SavedPlace>, acknowledged: Set<String>): List<SavedPlace> =
    places.filterNot { it.isDeleted && it.id in acknowledged }

/**
 * Les favoris, tels qu'ils se posent sur le disque.
 *
 * Un tableau JSON, sur le patron de [List.encodeHistory] : le modèle sait
 * s'écrire et se relire, le dépôt de `:app` ne sait que ranger une chaîne.
 *
 * Les clés sont écrites en toutes lettres, contrairement au catalogue d'arrêts :
 * vingt entrées ne pèsent rien, et un fichier de préférences se relit parfois à
 * l'œil quand quelque chose cloche chez quelqu'un.
 */
fun List<SavedPlace>.encodeSavedPlaces(): String = buildJsonArray {
    forEach { place ->
        addJsonObject {
            put("id", place.id)
            put("slot", place.slot.wire)
            put("icon", place.icon.wire)
            put("created_at", place.createdAt.toEpochMilli())
            put("updated_at", place.updatedAt.toEpochMilli())
            val tombstone = place.deletedAt
            if (tombstone != null) {
                put("deleted_at", tombstone.toEpochMilli())
            } else {
                if (place.name.isNotEmpty()) put("name", place.name)
                put("label", place.label)
                put("lat", place.coordinate.latitude)
                put("lng", place.coordinate.longitude)
                place.stopMode?.let { put("stop_mode", it.name) }
            }
        }
    }
}.toString()

/**
 * Relit les favoris. Une entrée illisible est **sautée**, pas fatale.
 *
 * Même indulgence que l'historique, et elle compte davantage ici : un favori se
 * saisit à la main, une fois, et personne ne le ressaisit deux fois. Vider la
 * liste entière parce qu'une entrée d'une version future porte un champ inconnu
 * effacerait le domicile de quelqu'un qui vient de mettre à jour.
 */
fun decodeSavedPlaces(raw: String?): List<SavedPlace> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val created = obj["created_at"]?.jsonPrimitive?.longOrNull ?: return@runCatching null
            val updated = obj["updated_at"]?.jsonPrimitive?.longOrNull ?: created
            val deleted = obj["deleted_at"]?.jsonPrimitive?.longOrNull
            val slot = SavedPlaceSlot.of(obj["slot"]?.jsonPrimitive?.contentOrNull)
            val icon = SavedPlaceIcon.of(obj["icon"]?.jsonPrimitive?.contentOrNull)
            if (deleted != null) {
                return@runCatching SavedPlace(
                    id = id,
                    name = "",
                    slot = slot,
                    icon = icon,
                    label = "",
                    coordinate = Coordinate(latitude = 0.0, longitude = 0.0),
                    createdAt = Instant.ofEpochMilli(created),
                    updatedAt = Instant.ofEpochMilli(updated),
                    deletedAt = Instant.ofEpochMilli(deleted),
                )
            }
            val label = obj["label"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
            val lng = obj["lng"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
            val mode = obj["stop_mode"]?.jsonPrimitive?.contentOrNull
            SavedPlace(
                id = id,
                name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                slot = slot,
                icon = icon,
                label = label,
                coordinate = Coordinate(latitude = lat, longitude = lng),
                stopMode = TransportMode.entries.firstOrNull { it.name == mode },
                createdAt = Instant.ofEpochMilli(created),
                updatedAt = Instant.ofEpochMilli(updated),
            )
        }.getOrNull()
    }
}

/**
 * Ce que la table `user_saved_places` attend, une ligne par favori.
 *
 * Les pierres tombales partent aussi : c'est par elles que l'autre appareil
 * apprendra la suppression. `user_id` n'y figure pas — la RLS n'accepte que
 * `auth.uid()`, et le laisser au client promettrait plus que la base ne permet.
 */
fun SavedPlace.toRemoteRow(): Map<String, Any?> = linkedMapOf(
    "id" to id,
    "slot" to slot.wire,
    // La colonne s'appelle `symbol`, pas `icon` : c'est le schéma qui fait foi.
    "symbol" to icon.wire,
    // `name`, `label`, `lat` et `lng` sont NOT NULL. Une pierre tombale part
    // donc vidée plutôt qu'en nuls — un `null` ferait échouer l'écriture, et
    // c'est précisément la suppression qui ne passerait plus.
    "name" to name,
    "label" to label,
    "lat" to coordinate.latitude,
    "lng" to coordinate.longitude,
    // Le CHECK de la colonne n'accepte que le vocabulaire de l'API — « bus »,
    // « tram », « boat », en minuscules. Envoyer le nom de la constante Kotlin
    // ferait rejeter la ligne entière en 400.
    "stop_mode" to stopMode?.name?.lowercase(),
    "created_at" to createdAt.toString(),
    "updated_at" to updatedAt.toString(),
    "deleted_at" to deletedAt?.toString(),
)

/**
 * Relit une ligne de `user_saved_places`.
 *
 * `null` sur ce qui n'a pas de sens — pas d'identifiant, ou une adresse vivante
 * sans coordonnées. Même indulgence qu'au décodage local, et pour la même
 * raison : une ligne abîmée ne doit pas emporter les dix-neuf autres.
 */
fun savedPlaceFromRemote(
    id: String?,
    slot: String?,
    symbol: String?,
    name: String?,
    label: String?,
    lat: Double?,
    lng: Double?,
    stopMode: String?,
    createdAt: String?,
    updatedAt: String?,
    deletedAt: String?,
): SavedPlace? {
    if (id.isNullOrBlank()) return null
    val created = parseRemoteInstant(createdAt) ?: return null
    val updated = parseRemoteInstant(updatedAt) ?: created
    val deleted = parseRemoteInstant(deletedAt)
    val slotValue = SavedPlaceSlot.of(slot)
    val iconValue = SavedPlaceIcon.of(symbol)
    if (deleted != null) {
        return SavedPlace(
            id = id,
            name = "",
            slot = slotValue,
            icon = iconValue,
            label = "",
            coordinate = Coordinate(latitude = 0.0, longitude = 0.0),
            createdAt = created,
            updatedAt = updated,
            deletedAt = deleted,
        )
    }
    if (label.isNullOrBlank() || lat == null || lng == null) return null
    return SavedPlace(
        id = id,
        name = name.orEmpty(),
        slot = slotValue,
        icon = iconValue,
        label = label,
        coordinate = Coordinate(latitude = lat, longitude = lng),
        // `fromApiValue` et non une comparaison de nom : la colonne parle le
        // vocabulaire de l'API, en minuscules.
        stopMode = TransportMode.fromApiValue(stopMode),
        createdAt = created,
        updatedAt = updated,
    )
}

/**
 * PostgREST rend `2026-08-26T09:12:44.318+00:00`, que [Instant.parse] refuse :
 * il attend un `Z`. Le décalage explicite est donc ramené avant l'analyse — et
 * une date illisible rend `null` plutôt que de lever, comme tout le reste ici.
 */
private fun parseRemoteInstant(raw: String?): Instant? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(trimmed) }
        .recoverCatching { java.time.OffsetDateTime.parse(trimmed).toInstant() }
        .recoverCatching {
            java.time.LocalDateTime.parse(trimmed.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC)
        }
        .getOrNull()
}
