package io.aule.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AvatarFailureKind

@Composable
fun AuthFailureKind.message(): String = when (this) {
    AuthFailureKind.INVALID_CREDENTIALS -> stringResource(R.string.auth_error_invalid_credentials)
    AuthFailureKind.EMAIL_NOT_CONFIRMED -> stringResource(R.string.auth_error_email_not_confirmed)
    AuthFailureKind.RATE_LIMITED -> stringResource(R.string.auth_error_rate_limited)
    AuthFailureKind.WEAK_PASSWORD -> stringResource(R.string.auth_error_weak_password)
    AuthFailureKind.INVALID_EMAIL -> stringResource(R.string.auth_error_invalid_email)
    AuthFailureKind.NOT_CONFIGURED -> stringResource(R.string.auth_error_not_configured)
    AuthFailureKind.NETWORK -> stringResource(R.string.auth_error_network)
    AuthFailureKind.NO_HABILITATION -> stringResource(R.string.auth_error_no_habilitation)
    AuthFailureKind.HABILITATION_UNVERIFIED -> stringResource(R.string.auth_error_habilitation_unverified)
    AuthFailureKind.USER_ALREADY_EXISTS -> stringResource(R.string.auth_error_user_already_exists)
    AuthFailureKind.UNKNOWN -> stringResource(R.string.auth_error_unknown)
}

@Composable
fun AvatarFailureKind.message(): String = when (this) {
    AvatarFailureKind.EMPTY -> stringResource(R.string.profile_avatar_error_empty)
    AvatarFailureKind.NOT_CONFIGURED -> stringResource(R.string.profile_avatar_error_not_configured)
    AvatarFailureKind.DENIED -> stringResource(R.string.profile_avatar_error_denied)
    AvatarFailureKind.UNSUPPORTED -> stringResource(R.string.profile_avatar_error_unsupported)
    AvatarFailureKind.NETWORK, AvatarFailureKind.UNKNOWN ->
        stringResource(R.string.profile_avatar_error_send)
}
