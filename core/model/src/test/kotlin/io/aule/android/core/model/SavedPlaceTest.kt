package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Les adresses favorites : les règles, sans disque ni réseau.
 *
 * Ce qui est vérifié ici est exactement ce qui se voit à l'écran quand ça
 * casse — un « Domicile » en double, un favori effacé qui revient au
 * lancement, une liste dont l'ordre change sous le doigt.
 */
class SavedPlaceTest {

    private val t0: Instant = Instant.parse("2026-08-26T08:00:00Z")

    private fun place(
        id: String,
        name: String = "",
        slot: SavedPlaceSlot = SavedPlaceSlot.CUSTOM,
        label: String = name.ifEmpty { id },
        lat: Double = 47.21,
        lng: Double = -1.55,
        created: Instant = t0,
        updated: Instant = created,
    ) = SavedPlace(
        id = id,
        name = name,
        slot = slot,
        icon = SavedPlaceIcon.forSlot(slot),
        label = label,
        coordinate = Coordinate(latitude = lat, longitude = lng),
        createdAt = created,
        updatedAt = updated,
    )

    // ------------------------------------------------------------ enregistrer

    @Test
    fun `un emplacement nomme ne designe qu'une porte`() {
        val first = place("a", slot = SavedPlaceSlot.HOME, label = "12 rue Paul Bellamy", lat = 47.22)
        val second = place("b", slot = SavedPlaceSlot.HOME, label = "3 quai Ceineray", lat = 47.23)

        val places = upsertSavedPlace(second, upsertSavedPlace(first, emptyList()))

        // Deux « Domicile » ne se distingueraient que par leur sous-titre : il
        // faudrait les lire pour choisir, ce qui est exactement ce qu'un
        // raccourci évite.
        assertEquals(listOf("b"), places.map { it.id })
    }

    @Test
    fun `la meme porte sous un autre nom remplace, elle ne double pas`() {
        val creche = place("a", name = "Crèche", label = "5 rue de Bel Air", lat = 47.2100, lng = -1.5500)
        // Vingt mètres plus loin : le géocodeur vise tantôt le bâtiment, tantôt
        // la voie. C'est la même porte.
        val ecole = place("b", name = "École", label = "5 rue de Bel Air, Nantes", lat = 47.21015, lng = -1.5500)

        val places = upsertSavedPlace(ecole, upsertSavedPlace(creche, emptyList()))

        assertEquals(listOf("b"), places.map { it.id })
        assertEquals("École", places.single().name)
    }

    @Test
    fun `modifier un favori le laisse a sa place`() {
        var places = upsertSavedPlace(place("a", name = "Crèche", lat = 47.21, created = t0), emptyList())
        places = upsertSavedPlace(place("b", name = "Sport", lat = 47.30, created = t0.plusSeconds(60)), places)

        val renamed = places.first { it.id == "a" }
            .copy(name = "Halte-garderie", updatedAt = t0.plusSeconds(600))
        places = upsertSavedPlace(renamed, places)

        // L'ordre suit la date d'enregistrement, pas celle de la retouche :
        // corriger un nom ne doit pas faire sauter le raccourci en tête.
        assertEquals(listOf("a", "b"), places.visibleSavedPlaces().map { it.id })
    }

    @Test
    fun `le plafond n'evince jamais le domicile`() {
        var places = upsertSavedPlace(
            place("home", slot = SavedPlaceSlot.HOME, label = "Domicile", lat = 47.0),
            emptyList(),
        )
        repeat(SAVED_PLACES_LIMIT + 3) { index ->
            places = upsertSavedPlace(
                place(
                    id = "c$index",
                    name = "Lieu $index",
                    label = "Lieu $index",
                    lat = 47.5 + index,
                    created = t0.plusSeconds(index.toLong()),
                ),
                places,
            )
        }

        val visible = places.visibleSavedPlaces()
        assertEquals(SAVED_PLACES_LIMIT, visible.size)
        assertNotNull(places.savedPlaceAt(SavedPlaceSlot.HOME))
        // Ce qui cède est le plus ancien des personnalisés, jamais un
        // emplacement nommé : ils sont l'objet même de la liste.
        assertTrue(visible.none { it.id == "c0" })
    }

    // --------------------------------------------------------------- ordonner

    @Test
    fun `domicile et travail passent devant, le reste garde son ordre`() {
        var places = upsertSavedPlace(place("c1", name = "Crèche", lat = 47.30, created = t0), emptyList())
        places = upsertSavedPlace(
            place("w", slot = SavedPlaceSlot.WORK, label = "Dépôt", lat = 47.40, created = t0.plusSeconds(10)),
            places,
        )
        places = upsertSavedPlace(place("c2", name = "Sport", lat = 47.50, created = t0.plusSeconds(20)), places)
        places = upsertSavedPlace(
            place("h", slot = SavedPlaceSlot.HOME, label = "Maison", lat = 47.60, created = t0.plusSeconds(30)),
            places,
        )

        assertEquals(listOf("h", "w", "c1", "c2"), places.visibleSavedPlaces().map { it.id })
    }

    // -------------------------------------------------------------- supprimer

    @Test
    fun `supprimer laisse une pierre tombale, et oublie l'adresse`() {
        val places = removeSavedPlace(
            id = "a",
            from = upsertSavedPlace(place("a", name = "Crèche", label = "5 rue de Bel Air"), emptyList()),
            at = t0.plusSeconds(60),
        )

        val tombstone = places.single()
        assertTrue(tombstone.isDeleted)
        // Garder l'adresse d'un domicile qu'on vient d'effacer serait garder
        // précisément ce qu'on a demandé d'oublier.
        assertEquals("", tombstone.label)
        assertEquals("", tombstone.name)
        assertTrue(places.visibleSavedPlaces().isEmpty())
    }

    // ---------------------------------------------------------------- fusion

    @Test
    fun `la version la plus recente gagne`() {
        val local = listOf(place("a", name = "Crèche", updated = t0.plusSeconds(600)))
        val remote = listOf(place("a", name = "Halte-garderie", updated = t0))

        assertEquals("Crèche", mergeSavedPlaces(local, remote).single().name)
        assertEquals("Crèche", mergeSavedPlaces(remote, local).single().name)
    }

    @Test
    fun `un favori supprime ne revient pas du serveur`() {
        val deleted = removeSavedPlace(
            id = "a",
            from = listOf(place("a", name = "Crèche", updated = t0)),
            at = t0.plusSeconds(60),
        )
        val remote = listOf(place("a", name = "Crèche", updated = t0))

        // Sans la pierre tombale, le serveur — qui n'a rien appris — renverrait
        // le favori comme une nouveauté au prochain démarrage.
        assertTrue(mergeSavedPlaces(deleted, remote).single().isDeleted)
    }

    @Test
    fun `a horodatage egal, la suppression tranche`() {
        val deleted = listOf(
            place("a", name = "Crèche", updated = t0).copy(deletedAt = t0, label = "", name = ""),
        )
        val alive = listOf(place("a", name = "Crèche", updated = t0))

        assertTrue(mergeSavedPlaces(alive, deleted).single().isDeleted)
        assertTrue(mergeSavedPlaces(deleted, alive).single().isDeleted)
    }

    @Test
    fun `deux domiciles enregistres hors ligne ne se perdent pas`() {
        val mine = listOf(
            place("a", slot = SavedPlaceSlot.HOME, label = "12 rue Paul Bellamy, Nantes", lat = 47.22, updated = t0),
        )
        val theirs = listOf(
            place(
                "b",
                slot = SavedPlaceSlot.HOME,
                label = "3 quai Ceineray, Nantes",
                lat = 47.23,
                updated = t0.plusSeconds(600),
            ),
        )

        val merged = mergeSavedPlaces(mine, theirs)

        assertEquals("b", merged.savedPlaceAt(SavedPlaceSlot.HOME)?.id)
        // Le perdant n'est pas écarté : quelqu'un a saisi cette adresse à la
        // main, et elle redevient un lieu personnalisé plutôt que de disparaître.
        val loser = merged.single { it.id == "a" }
        assertEquals(SavedPlaceSlot.CUSTOM, loser.slot)
        assertEquals("12 rue Paul Bellamy", loser.name)
    }

    @Test
    fun `une pierre tombale connue des deux cotes s'efface`() {
        val places = removeSavedPlace("a", listOf(place("a", name = "Crèche")), t0.plusSeconds(60))

        assertEquals(1, places.size)
        assertTrue(pruneSavedTombstones(places, acknowledged = setOf("a")).isEmpty())
        // Tant que le serveur n'en a pas pris acte, elle reste : c'est elle qui
        // empêche la résurrection.
        assertEquals(1, pruneSavedTombstones(places, acknowledged = emptySet()).size)
    }

    // ------------------------------------------------------------- persistance

    @Test
    fun `ce qui est ecrit se relit`() {
        val places = listOf(
            place("h", slot = SavedPlaceSlot.HOME, label = "12 rue Paul Bellamy, Nantes", lat = 47.22),
            place("c", name = "Crèche", label = "5 rue de Bel Air, Nantes", lat = 47.21)
                .copy(icon = SavedPlaceIcon.SCHOOL, stopMode = TransportMode.TRAM),
        ) + removeSavedPlace("x", listOf(place("x", name = "Ancien")), t0.plusSeconds(60))

        val reread = decodeSavedPlaces(places.encodeSavedPlaces())

        assertEquals(places.map { it.id }, reread.map { it.id })
        assertEquals(SavedPlaceIcon.SCHOOL, reread[1].icon)
        // Le mode d'arrêt survit : c'est lui, et jamais le libellé, qui dira
        // qu'on peut demander les passages de ce lieu.
        assertEquals(TransportMode.TRAM, reread[1].stopMode)
        assertTrue(reread[2].isDeleted)
    }

    @Test
    fun `une entree illisible est sautee, pas fatale`() {
        val raw = """[{"id":"a"},{"nope":1},"texte",{"id":"b","label":"Bel Air",""" +
            """"lat":47.2,"lng":-1.5,"created_at":1,"updated_at":2}]"""

        val places = decodeSavedPlaces(raw)

        // Un favori se saisit à la main, une fois. Vider la liste entière parce
        // qu'une entrée est abîmée effacerait le domicile de quelqu'un.
        assertEquals(listOf("b"), places.map { it.id })
    }

    @Test
    fun `un JSON vide ou casse ne rend rien, et ne leve pas`() {
        assertTrue(decodeSavedPlaces(null).isEmpty())
        assertTrue(decodeSavedPlaces("").isEmpty())
        assertTrue(decodeSavedPlaces("{pas du json").isEmpty())
    }

    @Test
    fun `une icone inconnue retombe sur l'epingle`() {
        val raw = """[{"id":"a","icon":"licorne","slot":"custom","label":"Bel Air",""" +
            """"lat":47.2,"lng":-1.5,"created_at":1,"updated_at":2}]"""

        // Écrite par une version plus récente : elle ne doit pas coûter l'entrée.
        assertEquals(SavedPlaceIcon.PIN, decodeSavedPlaces(raw).single().icon)
    }

    // ----------------------------------------------------------------- serveur

    @Test
    fun `une ligne PostgREST se relit, decalage horaire compris`() {
        val place = savedPlaceFromRemote(
            id = "a",
            slot = "home",
            symbol = "home",
            name = null,
            label = "12 rue Paul Bellamy, Nantes",
            lat = 47.22,
            lng = -1.55,
            stopMode = null,
            // PostgREST rend un décalage explicite, qu'Instant.parse refuse.
            createdAt = "2026-08-26T08:00:00.318+00:00",
            updatedAt = "2026-08-26T09:12:44.318+00:00",
            deletedAt = null,
        )

        assertNotNull(place)
        assertEquals(SavedPlaceSlot.HOME, place.slot)
        assertEquals(Instant.parse("2026-08-26T09:12:44.318Z"), place.updatedAt)
    }

    @Test
    fun `une pierre tombale part sans nul, et le mode en minuscules`() {
        val tombstone = removeSavedPlace("a", listOf(place("a", name = "Crèche")), t0.plusSeconds(60))
            .single()

        val row = tombstone.toRemoteRow()

        // `name`, `label`, `lat` et `lng` sont NOT NULL dans
        // `user_saved_places`. Un nul ferait rejeter l'écriture en 400 — et
        // c'est précisément la suppression qui ne se propagerait plus.
        assertEquals("", row["name"])
        assertEquals("", row["label"])
        assertNotNull(row["lat"])
        assertNotNull(row["lng"])
        assertNotNull(row["deleted_at"])
    }

    @Test
    fun `le mode d'arret part dans le vocabulaire de la colonne`() {
        val row = place("a", name = "Commerce")
            .copy(stopMode = TransportMode.TRAM)
            .toRemoteRow()

        // CHECK (stop_mode IN ('bus','tram','boat')). Envoyer le nom de la
        // constante Kotlin — « TRAM » — ferait rejeter la ligne entière.
        assertEquals("tram", row["stop_mode"])
        // Et la colonne s'appelle `symbol`, pas `icon`.
        assertTrue("symbol" in row)
    }

    @Test
    fun `une ligne vivante sans coordonnees est ecartee`() {
        val place = savedPlaceFromRemote(
            id = "a", slot = "custom", symbol = "pin", name = "Crèche",
            label = "5 rue de Bel Air", lat = null, lng = null, stopMode = null,
            createdAt = "2026-08-26T08:00:00Z", updatedAt = "2026-08-26T08:00:00Z", deletedAt = null,
        )

        assertNull(place)
    }
}
