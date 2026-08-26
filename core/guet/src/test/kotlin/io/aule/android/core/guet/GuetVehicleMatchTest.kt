package io.aule.android.core.guet

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.FleetSnapshot
import io.aule.android.core.model.HeadsignMatch
import io.aule.android.core.model.TransitStop
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import io.aule.android.core.model.VehicleFeed
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * L'appariement passage ↔ véhicule.
 *
 * Le test qui compte est `deux candidats donc aucun` : désigner le **mauvais**
 * véhicule ferait suivre à la caméra un bus qui va ailleurs, pendant que
 * l'utilisateur décide de courir vers son quai. Un titre pauvre vaut mieux qu'un
 * titre faux.
 *
 * Port de `GuetVehicleMatchTests.swift`.
 */
class GuetVehicleMatchTest {

    private val reference: Instant = Instant.ofEpochSecond(1_755_792_000)
    private val here = Coordinate(latitude = 47.2136, longitude = -1.5601)

    private val stop = TransitStop(
        id = "COMM",
        name = "Commerce",
        coordinate = here,
        mode = TransportMode.TRAM,
        stationName = "Commerce",
    )

    private fun candidate(
        line: String = "1",
        destination: String = "Beaujoire",
        inSeconds: Long = 300,
    ) = GuetCandidate(
        key = PassageKey.make("Commerce", line, destination, reference.plusSeconds(inSeconds)),
        place = "Commerce",
        stop = stop,
        line = line,
        destination = destination,
        mode = TransportMode.TRAM,
        isRealtime = true,
        timing = GuetTiming.of(
            expectedAt = reference.plusSeconds(inSeconds),
            walkSeconds = 120,
            platformSeconds = 0,
        ),
        level = GuetLevel.UNDECIDED,
        score = GuetScore(emptyMap()),
    )

    private fun vehicle(
        id: String = "v1",
        lineName: String = "1",
        lineId: String = "1",
        destination: String? = "Beaujoire",
        etaSeconds: Double? = 300.0,
        nextStop: String? = null,
    ) = TransportVehicle(
        id = id,
        mode = TransportMode.TRAM,
        feed = VehicleFeed.LIVE,
        lineId = lineId,
        lineName = lineName,
        destination = destination,
        coordinate = here,
        etaSeconds = etaSeconds,
        nextStop = nextStop,
    )

    private fun fleet(vararg vehicles: TransportVehicle) =
        FleetSnapshot(vehicles = vehicles.toList(), generatedAt = reference)

    /**
     * ⚠️ **La règle, et le test qui l'empêche de devenir `firstOrNull`.** Deux
     * véhicules de la même ligne vers la même destination à la même heure : rien
     * ne permet de trancher, donc on ne tranche pas.
     */
    @Test
    fun `deux candidats donc aucun`() {
        val found = GuetVehicleMatch.vehicle(
            candidate(),
            fleet(vehicle(id = "a"), vehicle(id = "b")),
            reference,
        )

        assertNull(found)
    }

    @Test
    fun `un seul candidat est retenu`() {
        val found = GuetVehicleMatch.vehicle(
            candidate(),
            fleet(vehicle(id = "a"), vehicle(id = "b", lineName = "2", lineId = "2")),
            reference,
        )

        assertEquals("a", found?.id)
    }

    @Test
    fun `aucun candidat rend null sans lever`() {
        assertNull(GuetVehicleMatch.vehicle(candidate(), fleet(), reference))
    }

    /**
     * ⚠️ Le théorique porte `ALEOP:300`, le suivi porte `300`. Comparer les
     * identifiants bruts n'apparie **jamais** rien, et l'écran reste vide sans
     * qu'aucune erreur ne soit levée.
     */
    @Test
    fun `le prefixe de reseau ne casse pas l appariement`() {
        val found = GuetVehicleMatch.vehicle(
            candidate(line = "ALEOP:300", destination = "Nantes"),
            fleet(vehicle(lineName = "300", lineId = "300", destination = "Nantes")),
            reference,
        )

        assertEquals("v1", found?.id)
        assertEquals(HeadsignMatch.routeKey("ALEOP:300"), HeadsignMatch.routeKey("300"))
    }

    @Test
    fun `l accent et la casse du terminus ne comptent pas`() {
        assertTrue(
            GuetVehicleMatch.serves(
                candidate(destination = "Hôtel Dieu"),
                vehicle(destination = "HOTEL DIEU"),
                reference,
            ),
        )
    }

    /**
     * La ligne 1 à Bouffay annonce `direction: "Beaujoire / Babinière"` et
     * `destination: "Babinière"` : l'un nomme la branche, l'autre les réunit. Le
     * repli par inclusion est ce qui les fait se reconnaître.
     */
    @Test
    fun `une branche se reconnait dans le libelle qui la reunit`() {
        assertTrue(
            GuetVehicleMatch.serves(
                candidate(destination = "Beaujoire / Babinière"),
                vehicle(destination = "Babinière"),
                reference,
            ),
        )
    }

    @Test
    fun `une autre ligne ou une autre destination n apparie pas`() {
        assertFalse(
            GuetVehicleMatch.serves(candidate(), vehicle(lineName = "2", lineId = "2"), reference),
        )
        assertFalse(
            GuetVehicleMatch.serves(candidate(), vehicle(destination = "Orvault"), reference),
        )
    }

    @Test
    fun `une heure trop eloignee n apparie pas`() {
        // Le passage est dans 300 s, le véhicule annonce 300 + tolérance + 1.
        val far = vehicle(etaSeconds = 300.0 + GuetVehicleMatch.ARRIVAL_TOLERANCE + 1)

        assertFalse(GuetVehicleMatch.serves(candidate(), far, reference))
    }

    @Test
    fun `une derive dans la tolerance apparie encore`() {
        // Mesuré côté iOS : le passage annonçait 13:03, la course disait 13:04.
        val drifted = vehicle(etaSeconds = 360.0)

        assertTrue(GuetVehicleMatch.serves(candidate(inSeconds = 300), drifted, reference))
    }

    /**
     * ⚠️ **Sans ETA, on n'invente pas d'heure.** Retenir un véhicule muet
     * reviendrait à apparier sur la seule ligne, ce qui apparie n'importe quel
     * véhicule de la ligne — y compris celui qui vient de passer.
     */
    @Test
    fun `un vehicule muet sur son attente est ecarte`() {
        val silent = vehicle(etaSeconds = null, nextStop = null)

        assertFalse(GuetVehicleMatch.serves(candidate(), silent, reference))
    }

    /** Le repli : le véhicule annonce comme prochain arrêt le lieu même du passage. */
    @Test
    fun `un prochain arret qui est le lieu du passage sauve l appariement`() {
        val silent = vehicle(etaSeconds = null, nextStop = "COMMERCE")

        assertTrue(GuetVehicleMatch.serves(candidate(), silent, reference))
    }

    @Test
    fun `la tolerance d arrivee est celle du reappariement`() {
        // Deux constantes séparées auraient divergé au premier ajustement.
        assertEquals(GuetLedger.REMATCH_TOLERANCE, GuetVehicleMatch.ARRIVAL_TOLERANCE)
    }
}
