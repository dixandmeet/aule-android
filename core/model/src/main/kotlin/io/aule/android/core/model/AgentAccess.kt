package io.aule.android.core.model

enum class AgentRole { CONDUCTEUR, CONTROLE }

enum class AccountModes { MIXTE, CONDUCTEUR, CONTROLE }

data class AgentAccess(
    val modes: AccountModes,
    val initialRole: AgentRole,
)

/**
 * Traduit les habilitations serveur en surfaces accessibles dans Aule Pro.
 *
 * `null` pour un compte voyageur ou une fiche métier incomplète — l'app
 * ferme alors la session. Port de `SAE/lib/models/agent_access.dart`.
 */
fun resolveAgentAccess(
    staffRole: String?,
    hasDriverProfile: Boolean,
    msrControl: Boolean,
    msrIntervention: Boolean,
): AgentAccess? {
    val canDrive = hasDriverProfile || staffRole == "driver"
    val canControl = msrControl ||
        msrIntervention ||
        staffRole in CONTROL_ROLES
    return when {
        canDrive && canControl -> AgentAccess(
            modes = AccountModes.MIXTE,
            initialRole = AgentRole.CONDUCTEUR,
        )
        canDrive -> AgentAccess(
            modes = AccountModes.CONDUCTEUR,
            initialRole = AgentRole.CONDUCTEUR,
        )
        canControl -> AgentAccess(
            modes = AccountModes.CONTROLE,
            initialRole = AgentRole.CONTROLE,
        )
        else -> null
    }
}

private val CONTROL_ROLES = setOf(
    "msr_agent",
    "msr_supervisor",
    "regulator",
    "admin",
)
