package io.aule.android.data.dto

import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AuthSession
import io.aule.android.core.model.AuthUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GoTrueTokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3_600,
    @SerialName("token_type") val tokenType: String? = null,
    val user: GoTrueUserDto? = null,
)

@Serializable
internal data class GoTrueUserDto(
    val id: String,
    val email: String? = null,
)

@Serializable
internal data class GoTrueErrorDto(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    val message: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
    val code: String? = null,
)

internal fun GoTrueTokenResponseDto.toSession(nowEpochSeconds: Long): AuthSession? {
    val user = user ?: return null
    val email = user.email?.takeIf { it.isNotBlank() } ?: return null
    return AuthSession(
        user = AuthUser(id = user.id, email = email),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = nowEpochSeconds + expiresIn,
    )
}

/**
 * Mappe un corps d'erreur GoTrue vers un [AuthFailureKind].
 *
 * Port de `dashboard/lib/auth-errors.ts` : les codes connus d'abord, puis
 * le statut HTTP, puis l'inconnu.
 */
internal fun authFailureKindOf(status: Int, body: GoTrueErrorDto?): AuthFailureKind {
    val code = (body?.errorCode ?: body?.code ?: body?.error)
        ?.trim()
        ?.lowercase()
        .orEmpty()
    return when {
        code == "invalid_credentials" || code == "invalid_grant" ->
            AuthFailureKind.INVALID_CREDENTIALS
        code == "email_not_confirmed" -> AuthFailureKind.EMAIL_NOT_CONFIRMED
        code == "email_address_invalid" || code == "validation_failed" ->
            AuthFailureKind.INVALID_EMAIL
        code == "weak_password" -> AuthFailureKind.WEAK_PASSWORD
        code == "user_already_exists" -> AuthFailureKind.USER_ALREADY_EXISTS
        code == "over_email_send_rate_limit" || code == "over_request_rate_limit" ->
            AuthFailureKind.RATE_LIMITED
        status == 429 -> AuthFailureKind.RATE_LIMITED
        status in 500..599 -> AuthFailureKind.NETWORK
        else -> AuthFailureKind.UNKNOWN
    }
}

internal fun GoTrueErrorDto?.serverMessage(): String? =
    this?.msg?.takeIf { it.isNotBlank() }
        ?: this?.message?.takeIf { it.isNotBlank() }
        ?: this?.errorDescription?.takeIf { it.isNotBlank() }
