package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

class ProRegistrationTest {

    @Test
    fun `produit le payload web v2 pour un conducteur Naolib`() {
        val draft = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.CONDUCTEUR),
            networkKey = "naolib",
            fullName = "  Camille Martin  ",
            employeeId = " 48271 ",
            transportMode = ProfessionalTransportMode.BUSTRAM,
            email = "camille@example.com",
            termsAccepted = true,
        )

        assertTrue(draft.professionalDataComplete)
        assertEquals(
            buildJsonObject {
                put("role", "driver")
                put("display_name", "Camille Martin")
                put("requested_access", "pro")
                put("onboarding_profile", "conducteur")
                put("onboarding_profiles", buildJsonArray { add(JsonPrimitive("conducteur")) })
                put(
                    "onboarding_data",
                    buildJsonObject {
                        put("reseau", "naolib")
                        put("mode", "bustram")
                        put("habilitation", "")
                        put("fonction", "")
                    },
                )
                put(
                    "onboarding_identity",
                    buildJsonObject {
                        put("full_name", "Camille Martin")
                        put("employee_id", "48271")
                    },
                )
                put("onboarding_version", 2)
            },
            draft.toAuthMetadata(),
        )
    }

    @Test
    fun `inclut la demande de reseau personnalise`() {
        val draft = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.MAITRISE),
            networkKey = "custom",
            customNetworkName = " Réseau Astuce ",
            customNetworkOperator = " Métropole Mobilités ",
            customNetworkTerritory = " Rouen ",
            fullName = "Alex Durand",
            employeeId = "EXP42",
        )

        assertTrue(draft.professionalDataComplete)
        assertEquals(
            buildJsonObject {
                put("name", "Réseau Astuce")
                put("operator", "Métropole Mobilités")
                put("territory", "Rouen")
                put("status", "active")
            },
            draft.toAuthMetadata()["onboarding_network_request"]?.jsonObject,
        )
    }

    @Test
    fun `exige le mode de conduite, seule donnee de branche restante`() {
        val conductor = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.CONDUCTEUR),
            networkKey = "naolib",
            fullName = "Camille Martin",
            employeeId = "48271",
        )
        val controller = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.CONTROLEUR),
            networkKey = "naolib",
            fullName = "Sam Dupont",
            employeeId = "MSR21",
        )

        assertFalse(conductor.professionalDataComplete)
        assertTrue(
            conductor.copy(transportMode = ProfessionalTransportMode.BUS).professionalDataComplete,
        )
        assertTrue(controller.professionalDataComplete)
        assertEquals(ProfessionalHabilitation.CONTROLE, controller.habilitation)
    }

    @Test
    fun `la V1 relit un brouillon de reseau personnalise sans reseau`() {
        val draft = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.MAITRISE),
            networkKey = "custom",
            customNetworkName = "Réseau Astuce",
            customNetworkOperator = "Métropole Mobilités",
            customNetworkTerritory = "Rouen",
            fullName = "Alex Durand",
            employeeId = "EXP42",
        )

        val restored = ProRegistrationDraft.decode(draft.encode())

        assertFalse(restored.usesCustomNetwork)
        assertTrue(restored.networkKey.isEmpty())
        assertTrue(restored.customNetworkName.isEmpty())
        assertFalse(restored.networkComplete)
        assertEquals(setOf(ProfessionalProfile.MAITRISE), restored.profiles)
        assertEquals("Alex Durand", restored.fullName)
    }

    @Test
    fun `l habilitation se deduit des metiers de terrain`() {
        val base = ProRegistrationDraft(networkKey = "naolib")
        fun habilitationFor(profiles: Set<ProfessionalProfile>) =
            base.copy(profiles = profiles).habilitation

        assertEquals(
            listOf("controle", "intervention", "controleIntervention"),
            ProfessionalHabilitation.entries.map { it.key },
        )
        assertEquals(
            ProfessionalHabilitation.CONTROLE,
            habilitationFor(setOf(ProfessionalProfile.CONTROLEUR)),
        )
        assertEquals(
            ProfessionalHabilitation.INTERVENTION,
            habilitationFor(setOf(ProfessionalProfile.INTERVENTION)),
        )
        assertEquals(
            ProfessionalHabilitation.CONTROLE_INTERVENTION,
            habilitationFor(
                setOf(ProfessionalProfile.CONTROLEUR, ProfessionalProfile.INTERVENTION),
            ),
        )
        assertNull(habilitationFor(setOf(ProfessionalProfile.CONDUCTEUR)))
        assertNull(habilitationFor(setOf(ProfessionalProfile.MAITRISE)))
    }

    @Test
    fun `restaure le brouillon sans secret`() {
        val original = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.CONTROLEUR),
            networkKey = "naolib",
            fullName = "Sam Dupont",
            employeeId = "MSR21",
            email = "sam@example.com",
            termsAccepted = true,
        )

        val restored = ProRegistrationDraft.decode(original.encode())

        assertEquals(setOf(ProfessionalProfile.CONTROLEUR), restored.profiles)
        assertEquals(ProfessionalHabilitation.CONTROLE, restored.habilitation)
        assertEquals("sam@example.com", restored.email)
        assertTrue(restored.termsAccepted)
        assertFalse("password" in original.encode())
        assertFalse("confirm" in original.encode())
    }

    @Test
    fun `conducteur et controleur se cumulent dans les deux sens`() {
        val fromDriver = naolib
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)
            .toggleProfile(ProfessionalProfile.CONTROLEUR)
        val fromController = naolib
            .toggleProfile(ProfessionalProfile.CONTROLEUR)
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)

        val both = setOf(ProfessionalProfile.CONDUCTEUR, ProfessionalProfile.CONTROLEUR)
        assertEquals(both, fromDriver.profiles)
        assertEquals(both, fromController.profiles)
        assertTrue(fromDriver.asksTransportMode)
        assertEquals(ProfessionalHabilitation.CONTROLE, fromDriver.habilitation)
    }

    @Test
    fun `le cumul demande le role le plus large et liste les metiers`() {
        val draft = naolib
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)
            .toggleProfile(ProfessionalProfile.CONTROLEUR)
            .toggleProfile(ProfessionalProfile.INTERVENTION)
            .copy(transportMode = ProfessionalTransportMode.BUS)

        assertTrue(draft.professionalDataComplete)
        val metadata = draft.toAuthMetadata()
        assertEquals(JsonPrimitive("msr_agent"), metadata["role"])
        assertEquals(JsonPrimitive("controleur"), metadata["onboarding_profile"])
        assertEquals(
            buildJsonArray {
                add(JsonPrimitive("conducteur"))
                add(JsonPrimitive("controleur"))
                add(JsonPrimitive("intervention"))
            },
            metadata["onboarding_profiles"],
        )
        assertEquals(
            buildJsonObject {
                put("reseau", "naolib")
                put("mode", "bus")
                put("habilitation", "controleIntervention")
                put("fonction", "")
            },
            metadata["onboarding_data"],
        )
    }

    @Test
    fun `un metier exclusif remplace la selection, et reciproquement`() {
        val afterSupervisor = naolib
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)
            .toggleProfile(ProfessionalProfile.CONTROLEUR)
            .copy(transportMode = ProfessionalTransportMode.BUS)
            .toggleProfile(ProfessionalProfile.MAITRISE)

        assertEquals(setOf(ProfessionalProfile.MAITRISE), afterSupervisor.profiles)
        assertNull(afterSupervisor.transportMode)
        assertNull(afterSupervisor.habilitation)
        assertEquals("naolib", afterSupervisor.networkKey)
        assertEquals("Camille Martin", afterSupervisor.fullName)

        val backToDriver = afterSupervisor.toggleProfile(ProfessionalProfile.CONDUCTEUR)
        assertEquals(setOf(ProfessionalProfile.CONDUCTEUR), backToDriver.profiles)
    }

    @Test
    fun `un second tap deselectionne le metier`() {
        val draft = naolib
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)
            .copy(transportMode = ProfessionalTransportMode.TRAM)
            .toggleProfile(ProfessionalProfile.CONDUCTEUR)

        assertTrue(draft.profiles.isEmpty())
        assertNull(draft.transportMode)
        assertFalse(draft.professionalDataComplete)
    }

    @Test
    fun `la V1 ne propose ni la regulation ni l exploitation`() {
        assertEquals(
            listOf(
                ProfessionalProfile.CONDUCTEUR,
                ProfessionalProfile.CONTROLEUR,
                ProfessionalProfile.INTERVENTION,
                ProfessionalProfile.MAITRISE,
            ),
            SIGNUP_PROFILES,
        )
        assertFalse(ProfessionalProfile.REGULATEUR.isOpenForSignup)
        assertFalse(ProfessionalProfile.EXPLOITATION.isOpenForSignup)
    }

    @Test
    fun `relit les brouillons anterieurs au cumul`() {
        val legacy = ProRegistrationDraft.decode(
            """{"profile":"controleur","networkKey":"naolib"}""",
        )
        assertEquals(setOf(ProfessionalProfile.CONTROLEUR), legacy.profiles)

        val retired = ProRegistrationDraft.decode(
            """{"profiles":["regulateur","conducteur"],"networkKey":"naolib"}""",
        )
        assertEquals(setOf(ProfessionalProfile.CONDUCTEUR), retired.profiles)
    }

    @Test
    fun `encode un mode de conduite absent comme null`() {
        val encoded = ProRegistrationDraft(
            profiles = setOf(ProfessionalProfile.CONTROLEUR),
            networkKey = "naolib",
        ).encode()
        assertTrue("\"transportMode\":null" in encoded)
        assertEquals(JsonNull, kotlinx.serialization.json.Json.parseToJsonElement(encoded).jsonObject["transportMode"])
    }

    private companion object {
        val naolib = ProRegistrationDraft(
            networkKey = "naolib",
            fullName = "Camille Martin",
            employeeId = "48271",
        )
    }
}
