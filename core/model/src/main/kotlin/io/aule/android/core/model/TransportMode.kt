package io.aule.android.core.model

/**
 * Les modes que le réseau nantais fait circuler.
 *
 * L'énumération est fermée à dessein. Le backend peut nommer un mode qu'on ne
 * connaît pas ; dans ce cas [fromApiValue] rend `null` et l'appelant **écarte
 * l'enregistrement** sans faire échouer le lot — un véhicule inconnu ne doit pas
 * vider la carte des autres.
 */
enum class TransportMode {
    BUS,
    TRAM,
    BOAT;

    companion object {
        /**
         * Le vocabulaire de l'API, y compris le nom local du bateau.
         *
         * « Navibus » est le nom nantais des navettes fluviales ; il arrive tel
         * quel dans les données et ne se devine pas.
         */
        fun fromApiValue(value: String?): TransportMode? =
            when (value?.trim()?.lowercase()) {
                "bus" -> BUS
                "tram", "tramway" -> TRAM
                "boat", "navibus", "ferry" -> BOAT
                else -> null
            }

        /**
         * Le `route_type` GTFS. 0 = tram, 4 = ferry ; tout le reste
         * (bus, métro, train) se lit comme un bus sur ce réseau.
         */
        fun fromGtfsRouteType(type: Int): TransportMode = when (type) {
            0 -> TRAM
            4 -> BOAT
            else -> BUS
        }
    }
}
