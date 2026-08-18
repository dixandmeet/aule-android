package io.aule.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une ligne du catalogue, réduite à ce que le nuancier demande.
 *
 * Deux colonnes plutôt que la rangée entière de [GtfsRouteDto] : les cent
 * trente-huit lignes du réseau tiennent alors dans six kilo-octets, et rien de
 * ce qu'on ne lit pas ne traverse le réseau.
 */
@Serializable
internal data class GtfsRouteColorDto(
    @SerialName("route_id") val routeId: String,
    @SerialName("route_color") val routeColor: String? = null,
)
