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
    val geocode = "$root/api/geocode"

    /**
     * Le calcul d'itinéraire. `from` et `to` s'écrivent en **`lng,lat`** —
     * inversés, le serveur répond 404 sans rien expliquer.
     */
    val route = "$root/api/route"
}
