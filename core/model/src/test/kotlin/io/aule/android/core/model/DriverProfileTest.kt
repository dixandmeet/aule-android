package io.aule.android.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class DriverProfileTest {

    @Test
    fun `le nom affiche joint prenom et nom, et ignore les vides`() {
        assertEquals(
            "Kevin Getbu",
            profile(firstName = "Kevin", lastName = "Getbu").displayName(),
        )
        assertEquals("Kevin", profile(firstName = "Kevin", lastName = "  ").displayName())
        assertEquals("Getbu", profile(firstName = null, lastName = "Getbu").displayName())
        assertNull(profile(firstName = " ", lastName = null).displayName())
    }

    @Test
    fun `les initiales viennent du nom affiche`() {
        assertEquals("KG", initialsOf("Kevin Getbu"))
        assertEquals("GE", initialsOf("getbu.kevin@gmail.com"))
        assertEquals("SL", initialsOf("Session locale"))
        assertEquals("?", initialsOf("   "))
    }

    @Test
    fun `le depot et le reseau portent le libelle du menu et du profil`() {
        assertEquals(
            "BLX · Dépôt Haluchère",
            Depot(id = "d1", code = "BLX", name = "Dépôt Haluchère").label,
        )
        assertEquals(
            "Dépôt Haluchère (BLX)",
            Depot(id = "d1", code = "BLX", name = "Dépôt Haluchère").directoryLabel,
        )
        assertEquals(
            "Nantes (NAN)",
            TransportNetwork(id = "n1", code = "NAN", name = "Nantes").label,
        )
    }

    @Test
    fun `un reseau filtre les depots qui ne lui appartiennent pas`() {
        val blx = Depot("d1", "BLX", "Haluchère", "net-a")
        val ttx = Depot("d2", "TTX", "Trentemoult", "net-b")
        val all = listOf(blx, ttx)
        assertEquals(listOf(blx), all.forNetwork("net-a"))
        assertEquals(all, all.forNetwork(null))
    }

    private fun profile(
        firstName: String? = null,
        lastName: String? = null,
    ) = DriverProfile(
        id = "drv-1",
        email = "agent@aule.fr",
        firstName = firstName,
        lastName = lastName,
    )
}
