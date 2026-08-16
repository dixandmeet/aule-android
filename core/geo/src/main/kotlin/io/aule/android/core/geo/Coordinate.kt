package io.aule.android.core.geo

/**
 * Un point sur la Terre.
 *
 * On n'utilise pas le `LatLng` de MapLibre comme type de domaine : il attacherait
 * tout le projet au moteur cartographique, jusqu'aux modules qui n'affichent
 * rien. Et surtout, il ne dit pas dans quel ordre l'API écrit ses paires — or le
 * backend Aule attend `lng,lat` (ordre GeoJSON), et une inversion donne un
 * **404 silencieux**, pas une erreur.
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
) {

    /** La forme que le backend attend en paramètre de requête : `lng,lat`. */
    val apiPair: String get() = "$longitude,$latitude"

    /**
     * Un point est valide s'il est sur Terre.
     *
     * Sert de garde-fou contre l'inversion lat/lng, qui produit sinon une
     * position au large de l'Afrique sans rien signaler. Le point (0, 0) est
     * rejeté pour la même raison : c'est ce que rend un enregistrement vide.
     */
    val isValid: Boolean
        get() = latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    companion object {
        val NANTES = Coordinate(latitude = 47.2184, longitude = -1.5536)

        /** Décode une paire GeoJSON `[lng, lat]`. Rend `null` si la paire est incomplète. */
        fun fromGeoJsonPair(pair: List<Double>): Coordinate? =
            if (pair.size >= 2) Coordinate(latitude = pair[1], longitude = pair[0]) else null
    }
}
