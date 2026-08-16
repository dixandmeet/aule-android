package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath

/** Combien d'entrées au plus. Au-delà, une liste lue à voix haute cesse d'être une réponse. */
const val NEARBY_LIMIT = 12

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

    data class StopEntry(val stop: TransitStop, val distanceMeters: Double)
    data class VehicleEntry(val vehicle: TransportVehicle, val distanceMeters: Double)
}

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
