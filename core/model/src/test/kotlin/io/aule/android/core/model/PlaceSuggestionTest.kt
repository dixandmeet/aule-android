package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Ce qu'une prédiction du géocodeur devient une fois touchée, et ce qu'on en écarte avant.
 *
 * ⚠️ **Les prédictions ci-dessous sont relevées sur `www.aule.fr` le 24/09/2026**
 * (`/api/geocode?q=…`), et chaque adresse est la réponse de `?placeId=` qui la situe ; les
 * variantes construites pour une épreuve le disent là où elles servent. Les réponses complètes
 * sont les fixtures de `:data` (`data/src/test/resources/fixtures/geocode-*.json`).
 */
class PlaceSuggestionTest {

    private val gare = PlaceSuggestion.Prediction(
        placeId = "ChIJkXDHRLnuBUgRlpH_NZEcZ1s",
        label = "Gare de Nantes, Boulevard de Stalingrad, Nantes, France",
        details = "Boulevard de Stalingrad, Nantes, France",
        isTransitStop = true,
    )

    private val rue = PlaceSuggestion.Prediction(
        placeId = "EiFSdWUgZGUgU3RyYXNib3VyZywgTmFudGVzLCBGcmFuY2UiLiosChQKEglBfPGKpO4FSBHeFcsyI5pCphIUChIJra6o8IHuBUgRMO0NHlI3DQQ",
        label = "Rue de Strasbourg, Nantes, France",
        details = "Nantes, France",
    )

    private val commerce = PlaceSuggestion.Prediction(
        placeId = "ChIJ-dFxE6buBUgRFa9LIcUym8w",
        label = "Commerce, Nantes, France",
        details = "Nantes, France",
        isTransitStop = true,
    )

    private val placeDuCommerce = PlaceSuggestion.Prediction(
        placeId = "EiFQbGFjZSBkdSBDb21tZXJjZSwgTmFudGVzLCBGcmFuY2UiLiosChQKEgkDmbp5qO4FSBF4wNGXtV3yPBIUChIJra6o8IHuBUgRMO0NHlI3DQQ",
        label = "Place du Commerce, Nantes, France",
        details = "Nantes, France",
    )

    // --- Le nom qu'on a touché ---

    @Test
    fun `le nom est le libelle moins la precision du fournisseur`() {
        assertEquals("Gare de Nantes", gare.name)
        assertEquals("Rue de Strasbourg", rue.name)
    }

    /**
     * La précision dit où finit le nom, et une coupe à la première virgule ne le sait pas :
     * elle aurait réduit cet hôtel à « The Originals City ».
     */
    @Test
    fun `un nom qui porte des virgules reste entier`() {
        val hotel = PlaceSuggestion.Prediction(
            placeId = "ChIJEQecNk_uBUgR0gfcAGn4XyE",
            label = "The Originals City, Hôtel le Beaujoire, Nantes, Rue des Pays de la Loire, Nantes, France",
            details = "Rue des Pays de la Loire, Nantes, France",
        )

        assertEquals("The Originals City, Hôtel le Beaujoire, Nantes", hotel.name)
    }

    /** Variantes construites de la rue relevée : sans précision, puis avec une précision étrangère. */
    @Test
    fun `sans precision le nom s arrete a la premiere virgule`() {
        assertEquals("Rue de Strasbourg", rue.copy(details = null).name)
        // Une précision qui ne termine pas le libellé ne dit rien de lui.
        assertEquals("Rue de Strasbourg", rue.copy(details = "Rezé, France").name)
    }

    // --- Le libellé du lieu situé ---

    /**
     * Le défaut qu'on écarte, dans sa forme exacte : `?placeId=` rend pour la gare la fiche
     * « Nantes, 27 Bd de Stalingrad… », et le trajet se serait intitulé « Nantes ».
     */
    @Test
    fun `le lieu situe garde le nom touche devant l adresse`() {
        val place = gare.locatedAt(
            coordinate = Coordinate(latitude = 47.2174401, longitude = -1.5426706),
            address = "27 Bd de Stalingrad, 44041 Nantes, France",
        )

        assertEquals("Gare de Nantes, 27 Bd de Stalingrad, 44041 Nantes, France", place.label)
        assertEquals("Gare de Nantes", place.shortLabel())
        // Google annonce une gare ; seul le catalogue dit ce qu'on peut interroger en passages.
        assertNull(place.stopMode)
    }

    @Test
    fun `une adresse qui porte deja le nom ne le repete pas`() {
        assertEquals(
            "Rue de Strasbourg, 44000 Nantes, France",
            rue.locatedLabel("Rue de Strasbourg, 44000 Nantes, France"),
        )
    }

    @Test
    fun `sans adresse le libelle touche reste`() {
        assertEquals("Gare de Nantes, Boulevard de Stalingrad, Nantes, France", gare.locatedLabel(null))
        assertEquals("Gare de Nantes, Boulevard de Stalingrad, Nantes, France", gare.locatedLabel("  "))
    }

    @Test
    fun `une prediction n a ni point ni mode`() {
        assertNull(gare.coordinate)
        assertNull(gare.stopMode)
        assertEquals("Gare de Nantes", gare.shortLabel())
        assertEquals("Boulevard de Stalingrad, Nantes, France", gare.contextLabel())
    }

    // --- Le jeton ---

    /** Hors de ce que le BFF laisse passer, le jeton serait ignoré en silence : la facture double. */
    @Test
    fun `un jeton neuf tient dans ce que le BFF laisse passer`() {
        val first = PlaceSearchSession.start()
        val second = PlaceSearchSession.start()

        assertTrue(Regex("[A-Za-z0-9_-]{8,64}").matches(first.token), first.token)
        assertNotEquals(first, second)
    }

    @Test
    fun `un jeton que le BFF ignorerait ne se construit pas`() {
        assertThrows<IllegalArgumentException> { PlaceSearchSession("court") }
        assertThrows<IllegalArgumentException> { PlaceSearchSession("jeton avec des espaces") }
    }

    // --- Les jumeaux d'arrêts ---

    /** « commerce », tel que la production le rend : l'arrêt, puis la place du même nom. */
    @Test
    fun `une prediction annoncee comme arret s ecarte devant l arret montre`() {
        val found = listOf(commerce, placeDuCommerce).withoutStopTwins(listOf("Commerce"))

        assertEquals(listOf(placeDuCommerce), found)
    }

    /**
     * Accents, casse et tirets ne comptent pas, comme partout où l'on compare des noms d'arrêts :
     * le référentiel écrit `Haluchère - Batignolles` ou `Haluchère-Batignolles` selon le quai.
     * Prédiction construite pour l'épreuve, sur le modèle de « Commerce ».
     */
    @Test
    fun `le nom se compare sans accent ni casse`() {
        val haluchere = commerce.copy(label = "Haluchere Batignolles, Nantes, France")

        assertTrue(listOf(haluchere).withoutStopTwins(listOf("Haluchère - Batignolles")).isEmpty())
    }

    /**
     * C'est l'annonce du fournisseur qui décide, pas le nom seul : la même prédiction, si Google
     * ne la donnait pas pour un arrêt — une rue, une commune homonymes —, resterait. Les écarter
     * ferait disparaître une destination sans que rien le dise.
     */
    @Test
    fun `un homonyme qui n est pas annonce comme arret reste`() {
        val homonyme = commerce.copy(isTransitStop = false)

        assertEquals(listOf(homonyme), listOf(homonyme).withoutStopTwins(listOf("Commerce")))
    }

    /**
     * La gare n'a pas d'homonyme parmi les arrêts montrés : la prédiction reste alors le seul
     * chemin vers elle, et l'écarter la rendrait introuvable.
     */
    @Test
    fun `un arret que la liste ne montre pas ne fait rien disparaitre`() {
        assertEquals(listOf(gare), listOf(gare).withoutStopTwins(listOf("Gare Sud")))
        assertEquals(listOf(commerce), listOf(commerce).withoutStopTwins(emptyList()))
    }

    /** Un lieu situé a un point : c'est à l'appelant de dire à quelle distance il cesse d'être l'arrêt. */
    @Test
    fun `un lieu deja situe n est pas juge ici`() {
        val situe = PlaceSuggestion.Located(
            Place(label = "Commerce, 44000 Nantes", coordinate = Coordinate(47.21358, -1.55600)),
        )

        assertEquals(listOf(situe), listOf(situe).withoutStopTwins(listOf("Commerce")))
    }
}
