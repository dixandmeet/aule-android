package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Combien d'entrées au plus. Au-delà, une liste lue à voix haute cesse d'être une réponse. */
const val NEARBY_LIMIT = 12

/**
 * Le rapport entre la corde et le trottoir réellement parcouru.
 *
 * La distance est à vol d'oiseau : personne ne marche en ligne droite entre
 * deux immeubles. Un cinquième de rallonge est l'ordre de grandeur admis en
 * tissu urbain — assez pour ne pas promettre plus court que la réalité, pas
 * assez pour décourager d'un arrêt qui est en face.
 */
private const val WALK_DETOUR = 1.2

/** Un piéton qui traverse des rues, en mètres par seconde — pas un marcheur sportif. */
private const val WALK_SPEED_MPS = 1.35

/**
 * Ce qu'il y a autour d'un point, en une liste ordonnée.
 *
 * La carte n'a aucun chemin TalkBack : la sélection passe par un hit-test de
 * 22 dp sur un tampon opaque. Cette liste répond à la vraie question —
 * « qu'est-ce qu'il y a autour de moi ? » — et sert aussi à qui voit l'écran.
 */
data class NearbyDigest(
    val stops: List<StopEntry> = emptyList(),
    val vehicles: List<VehicleEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = stops.isEmpty() && vehicles.isEmpty()

    data class StopEntry(val stop: TransitStop, val distanceMeters: Double) {
        /** Voir [walkMinutesOver]. */
        val walkMinutes: Int get() = walkMinutesOver(distanceMeters)
    }

    data class VehicleEntry(val vehicle: TransportVehicle, val distanceMeters: Double)
}

/**
 * Combien de minutes de marche pour couvrir cette distance à vol d'oiseau.
 *
 * Une distance répond « où », une durée répond « est-ce que j'y suis à
 * temps » — et c'est la seconde question qu'on se pose devant une liste
 * d'arrêts. La recherche la pose autant que « autour de vous » : le calcul vit
 * donc ici, une fois, plutôt que dans chacune des deux listes.
 *
 * **Jamais zéro.** « 0 min à pied » se lit comme une panne d'affichage, pas
 * comme « vous y êtes ».
 */
fun walkMinutesOver(distanceMeters: Double): Int =
    ceil(distanceMeters * WALK_DETOUR / WALK_SPEED_MPS / 60.0)
        .toInt()
        .coerceAtLeast(1)

/**
 * La même marche, **en secondes**.
 *
 * [walkMinutesOver] arrondit au supérieur et ne descend jamais sous une minute :
 * c'est ce qu'il faut pour l'écrire dans une liste, et c'est faux pour calculer.
 * Le Guet soustrait cette durée d'une heure de passage ; un plancher d'une minute
 * ferait partir soixante secondes trop tôt de l'arrêt sous ses pieds, et
 * l'arrondi ferait dériver la marge de quai qu'on lui a demandé de tenir.
 *
 * Mêmes constantes — même détour, même vitesse : les deux fonctions décrivent la
 * même marche, elles n'en donnent que deux lectures.
 */
fun walkSecondsOver(distanceMeters: Double): Int =
    (distanceMeters.coerceAtLeast(0.0) * WALK_DETOUR / WALK_SPEED_MPS)
        .roundToInt()

object NearbyDigestBuilder {

    fun build(
        stops: List<TransitStop>,
        vehicles: List<TransportVehicle>,
        around: Coordinate,
        limit: Int = NEARBY_LIMIT,
    ): NearbyDigest = NearbyDigest(
        stops = nearestStops(stops, around, limit),
        vehicles = nearestVehicles(vehicles, around, limit),
    )

    /**
     * **Un lieu, une entrée.** Un pôle d'échange compte jusqu'à sept quais du
     * même nom : les énumérer remplirait la liste de « Commerce, Commerce,
     * Commerce » sans rien apprendre.
     */
    private fun nearestStops(
        stops: List<TransitStop>,
        around: Coordinate,
        limit: Int,
    ): List<NearbyDigest.StopEntry> {
        val closestByPlace = LinkedHashMap<String, NearbyDigest.StopEntry>()
        for (stop in stops) {
            val entry = NearbyDigest.StopEntry(stop, GeoMath.distance(around, stop.coordinate))
            val existing = closestByPlace[stop.departuresKey]
            if (existing != null && existing.distanceMeters <= entry.distanceMeters) continue
            closestByPlace[stop.departuresKey] = entry
        }
        return closestByPlace.values
            .sortedWith(compareBy({ it.distanceMeters }, { it.stop.id }))
            .take(limit)
    }

    private fun nearestVehicles(
        vehicles: List<TransportVehicle>,
        around: Coordinate,
        limit: Int,
    ): List<NearbyDigest.VehicleEntry> =
        vehicles
            .map { NearbyDigest.VehicleEntry(it, GeoMath.distance(around, it.coordinate)) }
            .sortedWith(compareBy({ it.distanceMeters }, { it.vehicle.id }))
            .take(limit)
}
