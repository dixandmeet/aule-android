package io.aule.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Métier demandé à l'inscription.
 *
 * Les libellés vivent dans l'UI (ADR-011). [key] est la valeur envoyée au
 * back-office, identique au `name` Dart.
 */
enum class ProfessionalProfile(
    val key: String,
    val requestedRole: String,
    val asksTransportMode: Boolean,
    val isCombinable: Boolean,
) {
    CONDUCTEUR("conducteur", "driver", true, true),
    CONTROLEUR("controleur", "msr_agent", false, true),
    INTERVENTION("intervention", "msr_agent", false, true),
    REGULATEUR("regulateur", "regulator", false, false),
    EXPLOITATION("exploitation", "regulator", false, false),
    MAITRISE("maitrise", "msr_supervisor", false, false),
    ;

    val isOpenForSignup: Boolean
        get() = this in SIGNUP_PROFILES

    companion object {
        fun fromKey(key: String?): ProfessionalProfile? =
            entries.find { it.key == key }
    }
}

enum class ProfessionalTransportMode(val key: String) {
    BUS("bus"),
    TRAM("tram"),
    BUSTRAM("bustram"),
    ;

    companion object {
        fun fromKey(key: String?): ProfessionalTransportMode? =
            entries.find { it.key == key }
    }
}

/**
 * Habilitation terrain, déduite des métiers choisis.
 *
 * Ce n'est plus une question posée : le contrôle et l'intervention sont deux
 * métiers cumulables, dont ces trois valeurs sont l'expression — exactement
 * les deux booléens `msr_control` / `msr_intervention` de la table `drivers`.
 */
enum class ProfessionalHabilitation(val key: String) {
    CONTROLE("controle"),
    INTERVENTION("intervention"),
    CONTROLE_INTERVENTION("controleIntervention"),
}

/** Métiers proposés à l'inscription en V1. */
val SIGNUP_PROFILES: List<ProfessionalProfile> = listOf(
    ProfessionalProfile.CONDUCTEUR,
    ProfessionalProfile.CONTROLEUR,
    ProfessionalProfile.INTERVENTION,
    ProfessionalProfile.MAITRISE,
)

/**
 * Ordre de priorité du rôle demandé quand plusieurs métiers sont cumulés : le
 * plus large l'emporte, la validation du réseau tranchera de toute façon.
 */
private val ROLE_PRIORITY: List<ProfessionalProfile> = listOf(
    ProfessionalProfile.MAITRISE,
    ProfessionalProfile.REGULATEUR,
    ProfessionalProfile.EXPLOITATION,
    ProfessionalProfile.CONTROLEUR,
    ProfessionalProfile.INTERVENTION,
    ProfessionalProfile.CONDUCTEUR,
)

/**
 * Brouillon d'inscription Aule Pro, aligné sur l'onboarding web v2.
 *
 * Le mot de passe et sa confirmation ne font volontairement pas partie de ce
 * modèle : ils ne doivent jamais être conservés dans le stockage local.
 *
 * Port de `SAE/lib/models/pro_registration.dart`.
 */
data class ProRegistrationDraft(
    val profiles: Set<ProfessionalProfile> = emptySet(),
    val networkKey: String = "",
    val customNetworkName: String = "",
    val customNetworkOperator: String = "",
    val customNetworkTerritory: String = "",
    val fullName: String = "",
    val employeeId: String = "",
    val transportMode: ProfessionalTransportMode? = null,
    val email: String = "",
    val termsAccepted: Boolean = false,
) {
    /**
     * Métiers choisis, dans l'ordre de déclaration de l'énumération, pour que
     * l'affichage et le payload ne dépendent pas de l'ordre des taps.
     */
    val orderedProfiles: List<ProfessionalProfile>
        get() = ProfessionalProfile.entries.filter { it in profiles }

    /** Métier porteur du rôle demandé quand plusieurs sont cumulés. */
    val primaryProfile: ProfessionalProfile?
        get() = ROLE_PRIORITY.firstOrNull { it in profiles }

    val asksTransportMode: Boolean
        get() = profiles.any { it.asksTransportMode }

    val habilitation: ProfessionalHabilitation?
        get() {
            val controle = ProfessionalProfile.CONTROLEUR in profiles
            val intervention = ProfessionalProfile.INTERVENTION in profiles
            return when {
                controle && intervention -> ProfessionalHabilitation.CONTROLE_INTERVENTION
                controle -> ProfessionalHabilitation.CONTROLE
                intervention -> ProfessionalHabilitation.INTERVENTION
                else -> null
            }
        }

    /**
     * Ajoute ou retire un métier en respectant la règle de cumul : les métiers
     * de terrain se cumulent, tout autre métier remplace la sélection. Le mode
     * de conduite devenu sans objet est effacé au passage.
     */
    fun toggleProfile(profile: ProfessionalProfile): ProRegistrationDraft {
        val next = profiles.toMutableSet()
        if (!next.remove(profile)) {
            next.removeAll { !profile.isCombinable || !it.isCombinable }
            next.add(profile)
        }
        return copy(
            profiles = next,
            transportMode = if (next.any { it.asksTransportMode }) transportMode else null,
        )
    }

    val usesCustomNetwork: Boolean
        get() = networkKey == CUSTOM_NETWORK_KEY

    val networkComplete: Boolean
        get() = when {
            networkKey.isEmpty() -> false
            !usesCustomNetwork -> true
            else ->
                customNetworkName.trim().length >= 2 &&
                    customNetworkOperator.trim().length >= 2 &&
                    customNetworkTerritory.trim().length >= 2
        }

    val identityComplete: Boolean
        get() = fullName.trim().length >= 3 && employeeId.trim().length >= 2

    val professionalDataComplete: Boolean
        get() {
            if (profiles.isEmpty() || !networkComplete || !identityComplete) return false
            if (asksTransportMode && transportMode == null) return false
            return true
        }

    /**
     * Métadonnées envoyées à Supabase Auth.
     *
     * Le rôle est une demande informative. L'autorisation effective est
     * attribuée côté serveur après validation du réseau.
     */
    fun toAuthMetadata(): JsonObject {
        val primary = checkNotNull(primaryProfile) { "primary profile required" }
        return buildJsonObject {
            put("role", primary.requestedRole)
            put("display_name", fullName.trim())
            put("requested_access", "pro")
            put("onboarding_profile", primary.key)
            put("onboarding_profiles", buildJsonArray {
                orderedProfiles.forEach { add(JsonPrimitive(it.key)) }
            })
            put(
                "onboarding_data",
                buildJsonObject {
                    put("reseau", networkKey)
                    put("mode", transportMode?.key.orEmpty())
                    put("habilitation", habilitation?.key.orEmpty())
                    put("fonction", "")
                },
            )
            put(
                "onboarding_identity",
                buildJsonObject {
                    put("full_name", fullName.trim())
                    put("employee_id", employeeId.trim())
                },
            )
            if (usesCustomNetwork) {
                put(
                    "onboarding_network_request",
                    buildJsonObject {
                        put("name", customNetworkName.trim())
                        put("operator", customNetworkOperator.trim())
                        put("territory", customNetworkTerritory.trim())
                        put("status", "active")
                    },
                )
            }
            put("onboarding_version", 2)
        }
    }

    fun encode(): String = buildJsonObject {
        put("profiles", buildJsonArray {
            orderedProfiles.forEach { add(JsonPrimitive(it.key)) }
        })
        put("networkKey", networkKey)
        put("customNetworkName", customNetworkName)
        put("customNetworkOperator", customNetworkOperator)
        put("customNetworkTerritory", customNetworkTerritory)
        put("fullName", fullName)
        put("employeeId", employeeId)
        if (transportMode == null) {
            put("transportMode", JsonNull)
        } else {
            put("transportMode", transportMode.key)
        }
        put("email", email)
        put("termsAccepted", termsAccepted)
    }.toString()

    companion object {
        const val NAOLIB_NETWORK_KEY = "naolib"
        const val CUSTOM_NETWORK_KEY = "custom"

        private val json = Json { ignoreUnknownKeys = true }

        fun decode(source: String): ProRegistrationDraft {
            val root = runCatching { json.parseToJsonElement(source) }.getOrNull()
            val obj = root as? JsonObject
                ?: throw IllegalArgumentException("invalid registration draft")
            val draft = ProRegistrationDraft(
                profiles = profilesOf(obj),
                networkKey = stringOf(obj, "networkKey"),
                customNetworkName = stringOf(obj, "customNetworkName"),
                customNetworkOperator = stringOf(obj, "customNetworkOperator"),
                customNetworkTerritory = stringOf(obj, "customNetworkTerritory"),
                fullName = stringOf(obj, "fullName"),
                employeeId = stringOf(obj, "employeeId"),
                transportMode = ProfessionalTransportMode.fromKey(
                    obj["transportMode"]?.jsonPrimitive?.contentOrNull,
                ),
                email = stringOf(obj, "email"),
                termsAccepted = obj["termsAccepted"]?.jsonPrimitive?.booleanOrNull == true,
            )
            // V1 : l'ajout d'un réseau n'est plus proposé, un brouillon qui en
            // portait un repart donc sans réseau — sa demande ne serait ni
            // affichée ni modifiable.
            return if (draft.usesCustomNetwork) {
                draft.copy(
                    networkKey = "",
                    customNetworkName = "",
                    customNetworkOperator = "",
                    customNetworkTerritory = "",
                )
            } else {
                draft
            }
        }

        /**
         * Relit les métiers du brouillon.
         *
         * Les brouillons d'avant le cumul ne portent qu'un `profile`, et un
         * métier retiré de l'inscription V1 ne doit pas revenir par la porte
         * du stockage local : il ne serait plus ni affiché ni modifiable.
         */
        private fun profilesOf(json: JsonObject): Set<ProfessionalProfile> {
            val keys = json["profiles"]
            val decoded = mutableSetOf<ProfessionalProfile>()
            if (keys is JsonArray) {
                keys.forEach { element ->
                    ProfessionalProfile.fromKey(element.jsonPrimitive.contentOrNull)
                        ?.let(decoded::add)
                }
            } else {
                ProfessionalProfile.fromKey(json["profile"]?.jsonPrimitive?.contentOrNull)
                    ?.let(decoded::add)
            }
            return decoded.filter { it.isOpenForSignup }.toSet()
        }

        private fun stringOf(json: JsonObject, key: String): String {
            val value = json[key] ?: return ""
            val primitive = value as? JsonPrimitive ?: return ""
            return primitive.contentOrNull.orEmpty()
        }
    }
}
