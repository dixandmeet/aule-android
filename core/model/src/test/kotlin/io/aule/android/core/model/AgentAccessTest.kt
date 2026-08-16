package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class AgentAccessTest {

    @Test
    fun `un voyageur sans fiche metier est refuse`() {
        assertNull(
            resolveAgentAccess(
                staffRole = "passenger",
                hasDriverProfile = false,
                msrControl = false,
                msrIntervention = false,
            ),
        )
    }

    @Test
    fun `une fiche conducteur simple ouvre uniquement la conduite`() {
        val access = resolveAgentAccess(
            staffRole = "driver",
            hasDriverProfile = true,
            msrControl = false,
            msrIntervention = false,
        )
        assertEquals(AccountModes.CONDUCTEUR, access?.modes)
        assertEquals(AgentRole.CONDUCTEUR, access?.initialRole)
    }

    @Test
    fun `une habilitation MSR cumulee ouvre le mode mixte`() {
        val access = resolveAgentAccess(
            staffRole = "driver",
            hasDriverProfile = true,
            msrControl = true,
            msrIntervention = false,
        )
        assertEquals(AccountModes.MIXTE, access?.modes)
    }

    @Test
    fun `un agent MSR non conducteur ouvre uniquement le controle`() {
        val access = resolveAgentAccess(
            staffRole = "msr_agent",
            hasDriverProfile = false,
            msrControl = false,
            msrIntervention = false,
        )
        assertEquals(AccountModes.CONTROLE, access?.modes)
        assertEquals(AgentRole.CONTROLE, access?.initialRole)
    }

    @Test
    fun `une fiche sans role staff suffit a conduire`() {
        val access = resolveAgentAccess(
            staffRole = null,
            hasDriverProfile = true,
            msrControl = false,
            msrIntervention = false,
        )
        assertEquals(AccountModes.CONDUCTEUR, access?.modes)
    }
}
