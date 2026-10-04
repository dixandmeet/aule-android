package io.aule.android.feature.auth

import io.aule.android.core.model.AccountModes
import io.aule.android.core.model.AgentAccess
import io.aule.android.core.model.AgentRole
import io.aule.android.core.model.AuthFailureKind
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AuthPresentationTest {
    private val access = AgentAccess(AccountModes.CONDUCTEUR, AgentRole.CONDUCTEUR)

    @Test
    fun `une session ouverte sans habilitation ne donne jamais un succes visuel`() {
        assertEquals(AuthButtonPhase.READY, authButtonPhase(AuthUiState(isSignedIn = true)))
        assertEquals(AuthButtonPhase.CHECKING, authButtonPhase(AuthUiState(isSignedIn = true, isCheckingAccess = true, access = access)))
    }

    @Test
    fun `la session de recuperation ne declenche pas l entree professionnelle`() {
        assertEquals(AuthButtonPhase.READY, authButtonPhase(AuthUiState(isSignedIn = true, isResettingPassword = true, access = access)))
    }

    @Test
    fun `l acces accorde declenche le succes mais le refus rend la main`() {
        assertEquals(AuthButtonPhase.GRANTED, authButtonPhase(AuthUiState(isSignedIn = true, access = access)))
        assertEquals(AuthButtonPhase.READY, authButtonPhase(AuthUiState(failure = AuthFailureKind.NO_HABILITATION)))
    }
}
