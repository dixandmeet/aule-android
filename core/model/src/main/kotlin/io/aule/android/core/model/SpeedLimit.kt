package io.aule.android.core.model

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.PolylineProjection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Les limitations de vitesse qu'une note de service localise, telles que le
 * fichier les porte.
 *
 * ## Pourquoi ce type existe, et ce qu'il refuse de faire
 *
 * Les deux compteurs d'Aule réservent depuis leur écriture la place d'un panneau
 * réglementaire, et refusent de l'occuper : « la limitation n'est pas dans les
 * données ». Elle ne l'est toujours pas **en général** — ni dans les tuiles, ni
 * dans le GTFS, ni dans aucun endpoint. Ce qui suit n'est pas une couverture du
 * réseau : ce sont les tronçons qu'une note de service décrit explicitement.
 *
 * D'où la règle qui gouverne tout le fichier : **hors d'un tronçon décrit, on ne
 * rend rien**, et le panneau reste éteint. Un afficheur qui annonce 50 là où il
 * est 30 est pire que pas d'afficheur, parce qu'un conducteur qui s'y fie une
 * fois s'y fie ensuite.
 *
 * Port de `Native/Aule/Models/SpeedLimit.swift`.
 */
data class SpeedLimits(
    /** Distance perpendiculaire maximale à un tronçon pour qu'il compte, en mètres. */
    val corridorMeters: Double,
    /** La marge d'incertitude autour d'une borne, en mètres. */
    val boundaryToleranceMeters: Double,
    val lines: List<LineLimits> = emptyList(),
) {
    data class LineLimits(
        /** L'indice public — « 1 ». */
        val line: String,
        /**
         * D'où vient la donnée : une note de service, nommée. C'est ce qui permet
         * à l'écran de dire *pourquoi* un chiffre s'affiche.
         */
        val source: Source,
        val sections: List<Section> = emptyList(),
    ) {
        data class Source(
            val reference: String,
            val issuedOn: String,
            val effectiveOn: String,
            /** « Gare Maritime — Médiathèque » : l'étendue, en toutes lettres. */
            val extent: String,
        )
    }

    data class Section(
        val limitKmh: Int,
        /** La longueur mesurée par le générateur. Informative. */
        val meters: Int = 0,
        /** Le tracé, en **`[lng, lat]`** — l'ordre GeoJSON, comme le reste du contrat. */
        val path: List<List<Double>> = emptyList(),
    )
}

/**
 * Les limitations **préparées** : chaque tronçon porte ses longueurs cumulées.
 *
 * ## Deux gardes, et aucune n'est de la prudence gratuite
 *
 * **Le couloir** écarte ce qui n'est pas sur la voie. Les limitations de la note
 * 26/639 sont celles de la **plate-forme tramway** ; la chaussée qui la longe n'a
 * pas les mêmes, et un panneau qui suivrait une voiture au Quai de la Fosse
 * afficherait 30 là où la route est à 50. C'est aussi pourquoi [limitFor] exige
 * l'indice de la ligne **qu'on assure**.
 *
 * **La tolérance de borne** dit ce qu'on ne sait pas. Les bornes viennent d'un
 * schéma mesuré au pixel ; leur position réelle est connue à quelques dizaines de
 * mètres près. Dans cette marge, deux tronçons répondent, et c'est **le plus
 * restrictif** qui gagne : un chiffre trop bas fait ralentir, un chiffre trop haut
 * fait accélérer là où il ne fallait pas.
 */
class SpeedLimitTable(limits: SpeedLimits) {

    private class PreparedSection(
        val limitKmh: Int,
        val points: List<Coordinate>,
        val total: Double,
    )

    private class PreparedLine(
        val source: SpeedLimits.LineLimits.Source,
        val sections: List<PreparedSection>,
    )

    private val corridorMeters = limits.corridorMeters
    private val boundaryToleranceMeters = limits.boundaryToleranceMeters
    private val byLine: Map<String, PreparedLine> = buildMap {
        limits.lines.forEach { entry ->
            val sections = entry.sections.mapNotNull { section ->
                val points = section.path.mapNotNull(Coordinate::fromGeoJsonPair)
                if (points.size < 2) return@mapNotNull null
                PreparedSection(section.limitKmh, points, PolylineProjection.length(points))
            }
            if (sections.isNotEmpty()) {
                putIfAbsent(canonicalLineName(entry.line), PreparedLine(entry.source, sections))
            }
        }
    }

    /**
     * La limitation qui s'applique à cette position, sur cette ligne.
     *
     * @param line l'indice public de la ligne **qu'on assure**. Ce n'est pas une
     *   commodité : c'est la garde qui empêche d'afficher une limitation de
     *   tramway à qui roule sur la chaussée voisine.
     * @return `null` hors des tronçons décrits — et c'est la réponse la plus
     *   fréquente. Le réseau fait cent kilomètres, une note en décrit un.
     */
    fun limitFor(line: String, position: Coordinate): Int? {
        val entry = byLine[canonicalLineName(line)] ?: return null

        var best: Int? = null
        for (section in entry.sections) {
            // Sans fenêtre : on ne suit pas un avancement, on demande « où suis-je »,
            // et un conducteur entre sur le tronçon par le bout que son service lui donne.
            val match = PolylineProjection.projectWithin(position, section.points) ?: continue

            val along = match.t * section.total
            // Projection **clampée à une extrémité** : la distance rendue n'est
            // plus latérale, elle mêle l'écart à la voie et le dépassement du
            // tronçon. On l'accepte dans la seule marge d'incertitude de la borne
            // — c'est ce qui fait répondre le tronçon voisin juste avant une
            // transition, et lui seul.
            val atEnd = along <= 0.5 || along >= section.total - 0.5
            val reach = if (atEnd) boundaryToleranceMeters else corridorMeters
            if (match.deviationMeters > reach) continue

            // ⚠️ **La plus restrictive gagne.** Dans la marge d'une borne, deux
            // tronçons répondent ; en retenir un au hasard afficherait une fois
            // sur deux la limitation qu'on vient de quitter.
            best = minOf(best ?: section.limitKmh, section.limitKmh)
        }
        return best
    }

    /** La note dont une ligne tient ses limitations, s'il y en a une. */
    fun sourceFor(line: String): SpeedLimits.LineLimits.Source? =
        byLine[canonicalLineName(line)]?.source

    companion object {
        /**
         * Une table vide : aucune ligne, donc aucune réponse. C'est l'état d'une
         * application dont l'asset manque, et il est **indistinguable** d'un
         * réseau sans note — délibérément : dans les deux cas il n'y a rien à
         * afficher.
         */
        val EMPTY = SpeedLimitTable(SpeedLimits(0.0, 0.0))
    }
}

/**
 * Décode le fichier, ou rend une table vide.
 *
 * ## Écrit à la main, comme l'index des lignes
 *
 * `:core:model` n'applique pas le greffon de sérialisation, et c'est une chance
 * ici : un décodage strict perdrait **tout le fichier** pour un tronçon abîmé,
 * là où chaque entrée se rattrape séparément. Un panneau qui s'éteint sur un
 * kilomètre vaut mieux qu'un panneau qui s'éteint partout.
 *
 * Ne lève jamais : sans limitations le panneau reste éteint et l'application
 * reste juste. C'est le test qui tient la présence de l'asset, pas l'exécution.
 */
fun decodeSpeedLimits(text: String?): SpeedLimitTable {
    if (text.isNullOrBlank()) return SpeedLimitTable.EMPTY
    val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        ?: return SpeedLimitTable.EMPTY

    val corridor = root["corridorMeters"]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val tolerance = root["boundaryToleranceMeters"]?.jsonPrimitive?.doubleOrNull ?: 0.0
    val lines = runCatching { root["lines"]?.jsonArray }.getOrNull().orEmpty()
        .mapNotNull { element ->
            val entry = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = entry["line"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val source = runCatching { entry["source"]!!.jsonObject }.getOrNull()
                ?.let { obj ->
                    SpeedLimits.LineLimits.Source(
                        reference = obj.text("reference").orEmpty(),
                        issuedOn = obj.text("issuedOn").orEmpty(),
                        effectiveOn = obj.text("effectiveOn").orEmpty(),
                        extent = obj.text("extent").orEmpty(),
                    )
                } ?: return@mapNotNull null
            val sections = runCatching { entry["sections"]?.jsonArray }.getOrNull().orEmpty()
                .mapNotNull { raw ->
                    val obj = runCatching { raw.jsonObject }.getOrNull() ?: return@mapNotNull null
                    // Une limitation absente n'est pas un zéro : sans elle le tronçon
                    // n'a rien à dire, et le poser à 0 afficherait un panneau « 0 ».
                    val limit = obj["limitKmh"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val path = runCatching { obj["path"]?.jsonArray }.getOrNull().orEmpty()
                        .mapNotNull { pair ->
                            runCatching {
                                pair.jsonArray.mapNotNull { it.jsonPrimitive.doubleOrNull }
                            }.getOrNull()?.takeIf { it.size == 2 }
                        }
                    SpeedLimits.Section(
                        limitKmh = limit,
                        meters = obj["meters"]?.jsonPrimitive?.intOrNull ?: 0,
                        path = path,
                    )
                }
            SpeedLimits.LineLimits(line = name, source = source, sections = sections)
        }

    return SpeedLimitTable(SpeedLimits(corridor, tolerance, lines))
}

private fun kotlinx.serialization.json.JsonObject.text(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
