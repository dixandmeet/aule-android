package io.aule.android.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.aule.android.core.model.AuthFailureKind
import io.aule.android.core.model.AvatarFailureKind
import io.aule.android.core.security.BiometricFailureKind
import io.aule.android.core.security.BiometricType

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

/**
 * Ce qu'on dit d'un refus biométrique — ou ce qu'on n'en dit pas.
 *
 * `null` pour une annulation, et c'est la moitié du propos de cette fonction :
 * fermer le dialogue est un geste délibéré, pas un incident. Un bandeau rouge
 * après un geste volontaire apprend surtout à ignorer les bandeaux rouges.
 *
 * `null` aussi pour l'absence d'enrôlement, qui n'est pas un message mais un
 * écran — celui qui propose d'ouvrir les réglages de l'appareil.
 */
@Composable
fun BiometricFailureKind.message(): String? = when (this) {
    BiometricFailureKind.USER_CANCELED -> null
    BiometricFailureKind.NOT_ENROLLED -> null
    BiometricFailureKind.KEY_INVALIDATED -> stringResource(R.string.auth_biometric_invalidated)
    BiometricFailureKind.TEMPORARY_LOCKOUT ->
        stringResource(R.string.auth_biometric_lockout_temporary)
    BiometricFailureKind.PERMANENT_LOCKOUT ->
        stringResource(R.string.auth_biometric_lockout_permanent)
    BiometricFailureKind.HARDWARE_UNAVAILABLE,
    BiometricFailureKind.TRANSIENT,
    BiometricFailureKind.UNKNOWN,
    -> stringResource(R.string.auth_biometric_unavailable)
}

/**
 * Tous les messages de refus, résolus d'avance.
 *
 * Un dialogue biométrique se termine **hors composition** — dans la coroutine
 * qu'un bouton a lancée — et `stringResource` ne s'y appelle pas. Les résoudre
 * en amont est ce qui permet de garder la table ci-dessus comme seule source,
 * au lieu d'en recopier une version « sans ressources » dans chaque écran.
 */
@Composable
fun biometricFailureMessages(): Map<BiometricFailureKind, String?> =
    BiometricFailureKind.entries.associateWith { it.message() }

/** « Activer l'empreinte », ou « Activer la biométrie ». */
@Composable
fun BiometricType.enableLabel(): String = stringResource(
    if (this == BiometricType.FINGERPRINT) {
        R.string.auth_biometric_enable_fingerprint
    } else {
        R.string.auth_biometric_enable_generic
    },
)

/** Le titre du dialogue système, accordé au capteur. */
@Composable
fun BiometricType.unlockTitle(): String = stringResource(
    if (this == BiometricType.FINGERPRINT) {
        R.string.auth_biometric_unlock_title_fingerprint
    } else {
        R.string.auth_biometric_unlock_title_generic
    },
)

/** L'intitulé de la rangée des réglages. */
@Composable
fun BiometricType.settingLabel(): String = stringResource(
    if (this == BiometricType.FINGERPRINT) {
        R.string.auth_biometric_setting_fingerprint
    } else {
        R.string.auth_biometric_setting_generic
    },
)

@Composable
fun AvatarFailureKind.message(): String = when (this) {
    AvatarFailureKind.EMPTY -> stringResource(R.string.profile_avatar_error_empty)
    AvatarFailureKind.NOT_CONFIGURED -> stringResource(R.string.profile_avatar_error_not_configured)
    AvatarFailureKind.DENIED -> stringResource(R.string.profile_avatar_error_denied)
    AvatarFailureKind.UNSUPPORTED -> stringResource(R.string.profile_avatar_error_unsupported)
    AvatarFailureKind.NETWORK, AvatarFailureKind.UNKNOWN ->
        stringResource(R.string.profile_avatar_error_send)
}
