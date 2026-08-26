package io.aule.android.core.map

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * La conversion mètres → pixels, dont dépend l'anneau d'incertitude du puck.
 *
 * Elle mérite des tests parce qu'elle est **fausse de façon crédible** : la
 * confondre avec la formule à 256 pixels donne un anneau deux fois trop
 * large, ce qui reste un anneau parfaitement plausible à l'œil.
 */
class MapScaleTest {

    /** Nantes, dont la latitude sert de repère à toutes les vérifications. */
    private val nantes = 47.2136

    /**
     * L'ancre du calcul : au zoom zéro, le monde fait 512 pixels de large pour
     * quarante mille kilomètres. Un pixel vaut donc environ 78 km à
     * l'équateur — et s'y tromper d'un facteur deux ne se voit nulle part
     * ailleurs que dans ce test.
     */
    @Test
    fun `au zoom zero, un pixel couvre soixante-dix-huit kilometres a l equateur`() {
        val meters = MapScale.metersPerPixel(latitude = 0.0, zoom = 0.0)
        assertEquals(78_271.5, meters, 1.0)
    }

    /** Mercator étire les distances vers les pôles : un pixel y couvre moins. */
    @Test
    fun `la latitude resserre l echelle`() {
        val equator = MapScale.metersPerPixel(latitude = 0.0, zoom = 16.0)
        val nantesMeters = MapScale.metersPerPixel(latitude = nantes, zoom = 16.0)
        assertTrue(nantesMeters < equator, "un pixel doit couvrir moins de sol à 47° qu'à 0°")

        // cos(47,2136°) ≈ 0,6793 — c'est tout le facteur.
        assertEquals(0.6793, nantesMeters / equator, 1e-3)
    }

    /** Un niveau de zoom, un facteur deux. C'est ce sur quoi l'anneau compte. */
    @Test
    fun `chaque niveau de zoom double l echelle`() {
        var zoom = 4.0
        while (zoom < 22.0) {
            val here = MapScale.pixelsPerMeter(nantes, zoom)
            val next = MapScale.pixelsPerMeter(nantes, zoom + 1)
            assertEquals(2.0, next / here, 1e-9, "au zoom $zoom")
            zoom += 1.0
        }
    }

    /** Les deux sens doivent se répondre exactement. */
    @Test
    fun `mètres par pixel et pixels par mètre s inversent`() {
        val zoom = 17.0
        val product = MapScale.metersPerPixel(nantes, zoom) * MapScale.pixelsPerMeter(nantes, zoom)
        assertEquals(1.0, product, 1e-12)
    }

    /**
     * Le rayon du disque du puck, **en pixels de style**.
     *
     * `circle-radius` se compte en pixels logiques ; le bitmap du puck, lui,
     * est peint en pixels physiques — son anneau blanc fait neuf points de
     * rayon, soit vingt-sept pixels sur un écran de densité trois, donc neuf
     * pixels de style. Confondre les deux échelles fait raisonner sur un puck
     * trois fois trop gros et rend tous les seuils qui suivent inoffensifs.
     */
    private val puckRadiusPx = 9.0

    /**
     * Le cas qui a motivé la correction : une incertitude ordinaire doit se
     * voir. Dix-sept mètres — la précision relevée sur le S21 en extérieur —
     * doivent franchement dépasser le puck.
     */
    @Test
    fun `une incertitude ordinaire sort du puck au zoom de suivi`() {
        val radiusPx = 17.0 * MapScale.pixelsPerMeter(nantes, zoom = 17.0)
        assertTrue(radiusPx > 3 * puckRadiusPx, "l'anneau doit dépasser le puck : $radiusPx px")
        assertTrue(radiusPx < 100.0, "sans pour autant manger l'écran : $radiusPx px")
    }

    /**
     * Et le cas symétrique : au plancher, l'anneau ne dit presque rien.
     *
     * Cinq mètres au zoom d'ouverture tombent sur le rayon du puck **à un
     * pixel près** — ni un anneau qui s'impose, ni une couche morte. C'est le
     * plancher de [UserPuckLayer] : en dessous, un GPS annonce une précision
     * qu'il ne tient pas.
     */
    @Test
    fun `le plancher tient dans l ombre du puck`() {
        val radiusPx = 5.0 * MapScale.pixelsPerMeter(nantes, zoom = 16.5)
        assertTrue(
            abs(radiusPx - puckRadiusPx) < 1.5,
            "attendu ~$puckRadiusPx px, obtenu $radiusPx px",
        )
    }

    /**
     * **Le halo ne doit pas reprendre la place qu'on vient de lui retirer.**
     *
     * Les deux objets se disputent le même disque autour du puck, et le halo
     * gagne toujours : il est peint, il est dense au centre, et il ne dit rien
     * qu'on ne sache déjà. L'anneau, lui, porte la seule information que rien
     * d'autre ne donne — à quel point la position est sûre. À trente-cinq
     * points, le halo noyait tout ce qui annonçait moins de **vingt** mètres,
     * c'est-à-dire une réception ordinaire de ville ; à vingt-deux, il laisse
     * passer dès une douzaine.
     *
     * Le seuil se lit sur l'appareil de référence, où [MapIcons.PUCK_HALO_DP]
     * — une taille de dessin, multipliée par trois au `Canvas` — retombe sur
     * autant de pixels de style, la densité du S21 valant elle aussi trois.
     */
    @Test
    fun `le halo laisse sortir une incertitude ordinaire`() {
        val ringPx = 15.0 * MapScale.pixelsPerMeter(nantes, zoom = 16.5)
        val haloPx = MapIcons.PUCK_HALO_DP / 2.0
        assertTrue(
            ringPx > haloPx + 2.0,
            "quinze mètres font $ringPx px, le halo en occupe $haloPx : l'anneau s'y noie",
        )
    }

    /**
     * **L'oracle : ce que MapLibre répond lui-même.**
     *
     * Les six chiffres ci-dessous ont été relevés sur le S21 en interrogeant
     * `Projection.getMetersPerPixelAtLatitude` pendant que la carte suivait
     * l'utilisateur — c'est-à-dire la fonction que le moteur applique
     * réellement pour poser un `circle-radius` au sol. La formule d'ici et la
     * sienne coïncident à la onzième décimale.
     *
     * Ce test existe parce que rien d'autre ne rattraperait un retour à la
     * convention 256 : l'anneau ferait le double, ce qui reste un anneau
     * plausible, et aucun des autres tests ne le verrait.
     */
    @Test
    fun `la formule repond ce que MapLibre repond`() {
        // Relevé le 26 août 2026, zoom 16,5, au nord de Nantes.
        val observed = 0.5728887982228275
        val computed = MapScale.metersPerPixel(latitude = 47.2842243, zoom = 16.5)
        assertEquals(observed, computed, 1e-9)
    }

    /**
     * Le rayon publié dans la source est exprimé au zoom de référence, et la
     * couche le redescend par une interpolation exponentielle de base deux sur
     * dix niveaux. Les deux doivent se répondre : le facteur 1/1024 du stop bas
     * est exactement dix doublements.
     */
    @Test
    fun `le facteur du stop bas correspond a dix niveaux de zoom`() {
        val reference = MapScale.pixelsPerMeter(nantes, MapScale.REFERENCE_ZOOM.toDouble())
        val tenBelow = MapScale.pixelsPerMeter(nantes, MapScale.REFERENCE_ZOOM - 10.0)
        assertTrue(abs(reference / tenBelow - 1024.0) < 1e-6, "attendu 1024, obtenu ${reference / tenBelow}")
    }
}
