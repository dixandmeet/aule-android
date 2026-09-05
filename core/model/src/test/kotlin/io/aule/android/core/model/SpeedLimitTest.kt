package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Les limitations de vitesse : les deux gardes qui décident d'un chiffre.
 *
 * Les défauts tenus ici sont **muets à l'exécution**. Un couloir trop large
 * allume le panneau à côté de la voie, une borne mal arbitrée affiche 40 là où il
 * est 30 — et ni l'un ni l'autre ne se signale à l'écran.
 *
 * Mêmes cas que `Native/AuleTests/SpeedLimitTests.swift` : la règle est commune
 * aux deux clients, et deux implémentations qui divergeraient afficheraient deux
 * chiffres différents au même endroit.
 */
class SpeedLimitTest {

    /** Un degré de longitude à 47,2° de latitude, en mètres. */
    private val metersPerDegreeLon = 111_320.0 * cos(47.2 * PI / 180)

    /** Un tronçon droit d'est en ouest, à latitude constante. */
    private fun table(vararg sections: Triple<Int, Double, Double>): SpeedLimitTable =
        SpeedLimitTable(
            SpeedLimits(
                corridorMeters = 30.0,
                boundaryToleranceMeters = 25.0,
                lines = listOf(
                    SpeedLimits.LineLimits(
                        line = "1",
                        source = SpeedLimits.LineLimits.Source(
                            reference = "26/000",
                            issuedOn = "2026-08-26",
                            effectiveOn = "2026-08-31",
                            extent = "essai",
                        ),
                        sections = sections.map { (limit, fromLon, toLon) ->
                            SpeedLimits.Section(
                                limitKmh = limit,
                                path = listOf(listOf(fromLon, 47.2), listOf(toLon, 47.2)),
                            )
                        },
                    ),
                ),
            ),
        )

    /** Deux tronçons collés : 50 puis 30. La borne est à `lon = 0`. */
    private val twoSections = table(
        Triple(50, -0.01, 0.0),
        Triple(30, 0.0, 0.01),
    )

    @Test
    fun `au milieu d'un troncon, c'est sa limitation`() {
        assertEquals(50, twoSections.limitFor("1", Coordinate(47.2, -0.005)))
        assertEquals(30, twoSections.limitFor("1", Coordinate(47.2, 0.005)))
    }

    /**
     * ⚠️ Les bornes viennent d'un schéma mesuré au pixel, et leur position réelle
     * est connue à quelques dizaines de mètres près. Dans cette marge, deux
     * tronçons répondent — et c'est **le plus restrictif** qui doit gagner.
     */
    @Test
    fun `dans la marge d'une borne, la plus restrictive gagne`() {
        val justBefore = Coordinate(47.2, -10 / metersPerDegreeLon)

        assertEquals(30, twoSections.limitFor("1", justBefore))
    }

    @Test
    fun `passe la marge, le troncon voisin ne repond plus`() {
        val wellBefore = Coordinate(47.2, -40 / metersPerDegreeLon)

        assertEquals(50, twoSections.limitFor("1", wellBefore))
    }

    /**
     * ⚠️ **La garde qui compte le plus.** Les limitations de la note 26/639 sont
     * celles de la plate-forme tramway ; la chaussée qui la longe n'a pas les
     * mêmes. Un panneau qui suivrait une voiture y annoncerait 30 là où la route
     * est à 50.
     */
    @Test
    fun `hors du couloir, aucun troncon ne repond`() {
        val aside = Coordinate(47.2 + 60 / 111_320.0, -0.005)

        assertNull(twoSections.limitFor("1", aside))
    }

    @Test
    fun `dans le couloir, un ecart de dix metres ne change rien`() {
        val nearby = Coordinate(47.2 + 10 / 111_320.0, -0.005)

        assertEquals(50, twoSections.limitFor("1", nearby))
    }

    @Test
    fun `une ligne qu'on n'assure pas n'a pas de limitation`() {
        assertNull(twoSections.limitFor("C3", Coordinate(47.2, -0.005)))
        assertNull(twoSections.limitFor("", Coordinate(47.2, -0.005)))
    }

    @Test
    fun `une table vide ne repond jamais`() {
        assertNull(SpeedLimitTable.EMPTY.limitFor("1", Coordinate(47.2, 0.0)))
    }

    /**
     * Un tronçon réduit à un point ne décrit aucune voie : le garder ferait
     * répondre un « tronçon » sans direction, dont la projection n'a pas de sens.
     */
    @Test
    fun `un troncon d'un seul point est ecarte au chargement`() {
        val degenerate = SpeedLimitTable(
            SpeedLimits(
                corridorMeters = 30.0,
                boundaryToleranceMeters = 25.0,
                lines = listOf(
                    SpeedLimits.LineLimits(
                        line = "1",
                        source = SpeedLimits.LineLimits.Source("26/000", "", "", ""),
                        sections = listOf(
                            SpeedLimits.Section(limitKmh = 30, path = listOf(listOf(0.0, 47.2))),
                        ),
                    ),
                ),
            ),
        )

        assertNull(degenerate.limitFor("1", Coordinate(47.2, 0.0)))
    }

    // ------------------------------------------------------------------ décodage

    @Test
    fun `un fichier illisible rend une table vide plutot que de lever`() {
        assertNull(decodeSpeedLimits("{ pas du json").limitFor("1", Coordinate(47.2, 0.0)))
        assertNull(decodeSpeedLimits(null).limitFor("1", Coordinate(47.2, 0.0)))
        assertNull(decodeSpeedLimits("").limitFor("1", Coordinate(47.2, 0.0)))
    }

    /**
     * Une limitation absente n'est pas un zéro : sans elle le tronçon n'a rien à
     * dire, et le poser à 0 afficherait un panneau « 0 » — le pire des chiffres.
     */
    @Test
    fun `un troncon sans limitation est ecarte, sans emporter les autres`() {
        val table = decodeSpeedLimits(
            """
            {"corridorMeters":30,"boundaryToleranceMeters":25,"lines":[
              {"line":"1","source":{"reference":"26/000","issuedOn":"","effectiveOn":"","extent":""},
               "sections":[
                 {"meters":10,"path":[[-0.01,47.2],[0.0,47.2]]},
                 {"limitKmh":30,"meters":10,"path":[[0.0,47.2],[0.01,47.2]]}]}]}
            """.trimIndent(),
        )

        // Le tronçon ouest n'a pas de limitation : rien à l'ouest, 30 à l'est.
        assertNull(table.limitFor("1", Coordinate(47.2, -0.005)))
        assertEquals(30, table.limitFor("1", Coordinate(47.2, 0.005)))
    }
}
