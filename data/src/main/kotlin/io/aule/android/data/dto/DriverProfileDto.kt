package io.aule.android.data.dto

import io.aule.android.core.model.Depot
import io.aule.android.core.model.DriverProfile
import io.aule.android.core.model.TransportNetwork
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DriverProfileDto(
    val id: String,
    val email: String,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val phone: String? = null,
    @SerialName("driver_number") val driverNumber: String? = null,
    @SerialName("depot_id") val depotId: String? = null,
    @SerialName("network_id") val networkId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("msr_control") val msrControl: Boolean = false,
    @SerialName("msr_intervention") val msrIntervention: Boolean = false,
) {
    fun toDomain(): DriverProfile = DriverProfile(
        id = id,
        email = email,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
        driverNumber = driverNumber,
        depotId = depotId,
        networkId = networkId,
        avatarUrl = avatarUrl,
        msrControl = msrControl,
        msrIntervention = msrIntervention,
    )
}

@Serializable
internal data class DepotDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("network_id") val networkId: String? = null,
) {
    fun toDomain(): Depot = Depot(
        id = id,
        code = code,
        name = name,
        networkId = networkId,
    )
}

@Serializable
internal data class NetworkDto(
    val id: String,
    val code: String,
    val name: String,
) {
    fun toDomain(): TransportNetwork = TransportNetwork(
        id = id,
        code = code,
        name = name,
    )
}

@Serializable
internal data class UserProfileDto(
    val role: String? = null,
)
