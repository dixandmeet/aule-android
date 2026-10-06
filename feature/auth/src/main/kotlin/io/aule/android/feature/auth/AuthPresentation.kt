package io.aule.android.feature.auth

/** Une session seule ne suffit pas : le succès attend une habilitation résolue. */
internal fun authButtonPhase(state: AuthUiState): AuthButtonPhase = when {
    state.isCheckingAccess -> AuthButtonPhase.CHECKING
    state.isSubmitting -> AuthButtonPhase.CONNECTING
    state.isSignedIn && !state.isResettingPassword && state.access != null -> AuthButtonPhase.GRANTED
    else -> AuthButtonPhase.READY
}
