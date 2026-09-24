package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.util.UUID

/**
 * Ce qu'une recherche de lieux propose : un lieu déjà situé, ou un lieu seulement **nommé**,
 * dont le point reste à demander.
 *
 * ## ⚠️ Pourquoi deux cas, et pas un [Place] pour tout
 *
 * Depuis le passage du BFF à Google Places, `/api/geocode?q=` répond en deux temps. La frappe
 * rend des prédictions — un libellé et une clé, jamais un point :
 *
 *     GET /api/geocode?q=rue de Strasbourg                      (www.aule.fr, 24/09/2026)
 *     {"results":[{"placeId":"EiFSdWUg…","label":"Rue de Strasbourg, Nantes, France",
 *       "details":"Nantes, France","kind":{"category":"address","icon":"pin"}}, …],
 *      "provider":"google"}
 *
 * et le point se demande à part, par `?placeId=`, au prix d'un second appel que le fournisseur
 * facture à l'unité.
 *
 * Un [Place] exige un point, et le socle le fabriquait donc tout de suite : il situait les cinq
 * premières suggestions de **chaque** recherche (`686d1d3`). Cinq résolutions facturées par
 * frappe posée, pour des lieux que personne ne touchera — quand le BFF écrit lui-même « un seul
 * appel, au moment du clic, et jamais pour les suggestions non retenues »
 * (`dashboard/app/api/geocode/route.ts`). Une prédiction reste donc une prédiction jusqu'au
 * toucher, et c'est [repository.PlaceSearchRepository.resolve] qui la situe — une fois, pour le
 * seul lieu retenu. C'est la règle du web (`de5f90c`) et celle d'iOS.
 *
 * Le géocodeur de repli, l'IGN, rend encore le point d'emblée : ses résultats arrivent en
 * [Located], comme les arrêts du catalogue.
 */
sealed interface PlaceSuggestion {

    /** Ce que la liste affiche : « Rue de Strasbourg, Nantes, France ». */
    val label: String

    /**
     * Renseigné pour un arrêt du réseau, et pour lui seul — jamais pour une prédiction, même
     * quand le fournisseur l'annonce comme un arrêt : seul le catalogue sait ce que le réseau
     * dessert.
     */
    val stopMode: TransportMode?

    /**
     * Le point, quand on l'a.
     *
     * ⚠️ **`null` pour une prédiction, et jamais un point approché à la place.** Une coordonnée
     * par défaut se lit comme une vraie jusqu'au bout de la chaîne : (0, 0) est au large du golfe
     * de Guinée, et un itinéraire s'y calcule sans la moindre erreur.
     */
    val coordinate: Coordinate?

    /** Un lieu situé : un arrêt du catalogue, une adresse du géocodeur de repli. */
    data class Located(val place: Place) : PlaceSuggestion {
        override val label: String get() = place.label
        override val stopMode: TransportMode? get() = place.stopMode
        override val coordinate: Coordinate get() = place.coordinate
    }

    /**
     * Un lieu que le fournisseur nomme sans le situer. Il ne le sera qu'au choix.
     *
     * @property placeId la clé qui le situera, par `/api/geocode?placeId=`.
     * @property details la seconde ligne du fournisseur — « Nantes, France ». C'est elle qui dit
     *   où finit le nom.
     * @property isTransitStop le fournisseur l'annonce comme un arrêt de transport
     *   (`kind.category == "stop"`). Sans point, c'est le seul indice qui reconnaisse le jumeau
     *   d'un arrêt que le catalogue vient de trouver — voir [withoutStopTwins].
     */
    data class Prediction(
        val placeId: String,
        override val label: String,
        val details: String? = null,
        val isTransitStop: Boolean = false,
    ) : PlaceSuggestion {
        override val stopMode: TransportMode? get() = null
        override val coordinate: Coordinate? get() = null

        /**
         * Le nom du lieu, sans sa localisation : « Rue de Strasbourg », « Gare de Nantes ».
         *
         * Le libellé est ce nom suivi de [details] : les retirer rend le nom **entier**, là où
         * une coupe à la première virgule tronquerait « The Originals City, Hôtel le Beaujoire,
         * Nantes » (prédiction relevée sur `www.aule.fr` le 24/09/2026). Sans précision, on coupe
         * à la première virgule, comme le reste de l'application.
         */
        val name: String
            get() {
                val whole = label.trim()
                val precision = details?.trim().orEmpty()
                if (precision.isNotEmpty() && whole.length > precision.length && whole.endsWith(precision)) {
                    val name = whole.dropLast(precision.length).trim { it == ',' || it.isWhitespace() }
                    if (name.isNotEmpty()) return name
                }
                return shortPlaceName(whole)
            }

        /**
         * Le libellé du lieu une fois situé : le nom **qu'on a touché**, puis l'adresse postale.
         *
         * ⚠️ **Pas le libellé de la fiche.** Google annonce « Gare de Nantes » dans sa prédiction
         * et nomme le lieu « Nantes » dans sa fiche : `?placeId=` rend alors `"label": "Nantes,
         * 27 Bd de Stalingrad, 44041 Nantes, France"` (relevé le 24/09/2026), et le trajet se
         * serait intitulé « Nantes ». Le BFF rend l'adresse à part pour cette raison même. C'est
         * le jumeau de `composePlaceLabel` (`dashboard/lib/place-label.ts`) : le nom devant
         * l'adresse, sauf quand l'adresse le porte déjà — « Rue de Strasbourg, 44000 Nantes,
         * France » ne se répète pas.
         *
         * Sans adresse, le libellé touché reste : il porte au moins la commune.
         */
        fun locatedLabel(address: String?): String {
            val postal = address?.trim().orEmpty()
            if (postal.isEmpty()) return label
            val title = name
            if (title.isEmpty()) return postal
            return if (postal.startsWith(title)) postal else "$title, $postal"
        }

        /**
         * Le lieu que cette prédiction désigne, une fois situé.
         *
         * Jamais un arrêt : [Place.stopMode] reste vide, même quand le fournisseur annonce une
         * gare — c'est le catalogue, et lui seul, qui dit ce qu'on peut interroger en passages.
         */
        fun locatedAt(coordinate: Coordinate, address: String?): Place =
            Place(label = locatedLabel(address), coordinate = coordinate)
    }
}

/** Le nom court d'une suggestion — voir [shortPlaceName]. */
fun PlaceSuggestion.shortLabel(): String = shortPlaceName(label)

/** Ce qui reste d'une suggestion sans son nom court — voir [placeContext]. */
fun PlaceSuggestion.contextLabel(): String = placeContext(label)

/**
 * Une recherche, de la première frappe au lieu retenu.
 *
 * Le jeton part avec chaque frappe **et** avec la résolution qui les conclut : Google facture
 * alors l'ensemble comme une seule recherche, et comme autant de requêtes isolées sans lui. Il
 * appartient au modèle de recherche, seul à savoir quand une recherche commence et quand elle
 * finit — la même décision que le web (`newPlacesSession`, `dashboard/lib/geocode.ts`) et
 * qu'iOS.
 *
 * ⚠️ **Le BFF ignore en silence tout jeton hors de `[A-Za-z0-9_-]{8,64}`** : il part dans l'URL
 * d'un tiers. Rien ne casse alors — la facture double, et personne ne le voit. D'où la garde à
 * la construction : un UUID y tient, et un jeton bricolé ailleurs échoue ici plutôt qu'en
 * silence chez Google.
 */
data class PlaceSearchSession(val token: String) {
    init {
        require(TOKEN_SHAPE.matches(token)) { "Jeton de session que le BFF ignorerait : « $token »" }
    }

    companion object {
        private val TOKEN_SHAPE = Regex("[A-Za-z0-9_-]{8,64}")

        /** Une session neuve — à la première frappe d'une recherche, jamais avant. */
        fun start(): PlaceSearchSession = PlaceSearchSession(UUID.randomUUID().toString())
    }
}

/**
 * Les suggestions du géocodeur, moins les prédictions qui redisent un arrêt déjà montré.
 *
 * Le géocodeur connaît les pôles du réseau, lui aussi : « commerce » rend « Commerce, Nantes,
 * France » juste avant la place et la rue du même nom. Sans ce retrait, la liste portait deux
 * rangs pour le même endroit, qui ne faisaient pas la même chose sous le doigt — l'arrêt ouvre
 * ses passages, la prédiction calcule un itinéraire —, et le second coûtait en plus une
 * résolution à qui le touchait.
 *
 * ⚠️ **Une prédiction n'a pas de point, et la distance ne peut donc rien trancher.** C'est le
 * fournisseur qui la remplace : Google annonce lui-même « Commerce, Nantes, France » et
 * « Beaujoire, Nantes, France » comme des arrêts de tram (`kind.category == "stop"`, relevé sur
 * `www.aule.fr` le 24/09/2026), et seuls ceux-là s'écartent, devant un arrêt montré du même nom.
 * Une rue, une place ou un commerce homonymes restent : ce ne sont pas des arrêts, et les écarter
 * ferait disparaître une destination sans que rien le dise.
 *
 * Les lieux déjà situés ne sont pas touchés ici : ils ont un point, et c'est à l'appelant de dire
 * à quelle distance un homonyme cesse d'être le même arrêt.
 *
 * @param stopNames les noms des arrêts **montrés** pour cette frappe. Un arrêt du catalogue que
 *   la liste ne porte pas ne fait rien disparaître : la prédiction serait alors le seul chemin
 *   vers ce lieu.
 */
fun List<PlaceSuggestion>.withoutStopTwins(stopNames: Collection<String>): List<PlaceSuggestion> {
    val shown = stopNames.map(::normalizeStopName).filterTo(HashSet()) { it.isNotEmpty() }
    if (shown.isEmpty()) return this
    return filterNot { suggestion ->
        suggestion is PlaceSuggestion.Prediction &&
            suggestion.isTransitStop &&
            normalizeStopName(suggestion.name) in shown
    }
}
