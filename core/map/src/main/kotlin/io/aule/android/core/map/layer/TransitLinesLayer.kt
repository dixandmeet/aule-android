package io.aule.android.core.map.layer

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapLayer
import io.aule.android.core.map.MapStyleAnchors
import io.aule.android.core.map.TransitTiles
import io.aule.android.core.model.normalizeTransitLineKey
import io.aule.android.core.model.splitTransitLineKey
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.VectorSource

/**
 * Les tracés du réseau.
 *
 * Port de `dashboard/components/carte-immersive/next/layers/transit-lines-layer.ts`
 * (refonte du tracé, chapitre 14 de la carte web) et de son pendant iOS
 * (`Native/Aule/Core/Map/Layers/TransitLinesLayer.swift`), dans le **profil
 * « filtres »** : MapLibre Native n'a ni `line-layer-opacity` ni états de tracé
 * exposés, si bien que ce qui est piloté par état sur le web l'est ici par filtre.
 * Les épaisseurs, les couleurs et la grammaire sont **reprises telles quelles** :
 * ce sont elles qui font que les cartes d'Aule se ressemblent.
 *
 * ## La géométrie Aule
 *
 * L'archive est `transit-v2.pmtiles` ([TransitTiles]) : un axe par ligne jusqu'à
 * z15, où l'aller et le retour qui partagent la rue ne font qu'un trait ; le
 * tracé exact de chaque sens au-delà, pour que véhicules et itinéraires — calés
 * sur les mêmes tracés en base — tombent dessus ; branches et occasionnels
 * réduits à leur partie propre ; couleurs normalisées au build pour le jour et
 * la nuit (`stroke_day`, `casing_night`…).
 *
 * ## Trois paliers, parce que 149 lignes d'un bloc ne se lisent pas
 *
 * À l'échelle de l'agglomération, on ne lit que la **structure** : tram, busway,
 * navibus, TER. Le reste arrive en approchant. Le réseau lit toujours l'**axe
 * fusionné** : un trait par ligne et par rue, sans faisceau décalé.
 *
 * ## Masqués par défaut, et c'est une décision
 *
 * Les tracés décrivent ce qui **existe**, pas ce qui se passe. Le réseau est
 * quelque chose qu'on **demande** : il est peint tant que le volet « Lignes du
 * réseau » est ouvert, et il s'efface avec lui.
 *
 * ## Une ligne désignée, au-dessus
 *
 * Toucher une ligne la dessine **nette** : un trait bordé d'un liseré (blanc le
 * jour, presque noir la nuit), plus de halo flou. Branches et occasionnels à
 * 60 %, occasionnels tiretés. Quand un sens est donné, il est plein et l'autre
 * estompé. Le réseau, s'il est peint, passe en gris neutre dessous. Il n'y a
 * **rien à charger** : la ligne est déjà dans les tuiles, ce qui change est un
 * filtre.
 *
 * @param archiveUrl l'URL `pmtiles://…` de l'archive, telle que
 *   [TransitTiles.pmtilesUrl] la forme. Passée plutôt que calculée ici : ce
 *   module ne connaît pas le `Context` qui sait où le fichier a été recopié.
 */
class TransitLinesLayer(
    private val archiveUrl: String,
    private val logger: AuleLogger = NoopLogger,
) : MapLayer {

    override val id: String = ID

    /**
     * Vrai quand les tracés sont demandés.
     *
     * **L'état vit ici et non dans la vue.** Un rechargement de style remonte
     * toutes les couches, et elles doivent revenir dans l'état demandé plutôt que
     * dans celui du premier montage.
     */
    var isVisible: Boolean = false
        private set

    /**
     * La ligne mise en avant, sous sa **clé** canonique (`naolib:C6`, `aleop:C6`) — ou `null`.
     *
     * ⚠️ **La clé, pas l'indice.** C6 est un Chronobus et un tram-train : désigner
     * « C6 » seul allumait les deux tracés. Une clé nue reste acceptée — elle
     * allume toutes les lignes qui portent ce `match`, comme sur le web.
     */
    var focusedLine: String? = null
        private set

    /**
     * Le sens affiché de la ligne mise en avant (`direction_id` GTFS : "0", "1") —
     * ou `null`, et alors tous ses sens sont pleins.
     */
    var focusedDirection: String? = null
        private set

    private var ambiance: MapAmbiance = MapAmbiance.LIGHT
    private var source: VectorSource? = null
    private var tiers: List<PaintedTier> = emptyList()
    private var focus: List<FocusLayer> = emptyList()

    private class PaintedTier(val layer: LineLayer, val tier: Tier)

    private enum class Family(val key: String) { FUSED("fused"), DIRECTIONAL("directional") }

    private enum class Kind(val key: String) { OTHER("other"), CASING("casing"), STROKE("stroke"), DASHED("dashed") }

    private class FocusLayer(val layer: LineLayer, val family: Family, val kind: Kind)

    fun setVisible(value: Boolean) {
        if (value == isVisible) return
        isVisible = value
        // La source reste en place : la démonter puis la remonter à chaque
        // bascule relancerait le chargement des tuiles, alors que la visibilité
        // est immédiate.
        tiers.forEach { it.layer.setProperties(PropertyFactory.visibility(visibility(value))) }
        logger.info(LogDomain.MAP, "Tracés du réseau : ${if (value) "affichés" else "masqués"}.")
    }

    /**
     * Met une ligne en avant, ou n'en met aucune.
     *
     * @param direction le sens montré par le plan de ligne, ou `null` pour les
     *   montrer tous.
     */
    fun setFocus(line: String?, direction: String? = null) {
        val canonical = normalizeTransitLineKey(line)
        val sense = if (canonical == null) null else direction
        if (canonical == focusedLine && sense == focusedDirection) return
        focusedLine = canonical
        focusedDirection = sense
        applyFocus()
        logger.info(LogDomain.MAP, "Ligne mise en avant : ${canonical ?: "aucune"} (sens ${sense ?: "tous"}).")
    }

    override fun mount(style: Style, map: MapLibreMap) {
        val posed = VectorSource(SOURCE, archiveUrl)
        style.addSource(posed)
        source = posed

        // Du moins structurant au plus structurant : le tram passe au-dessus du
        // bus quand ils partagent un couloir, et MapLibre peint dans l'ordre d'ajout.
        tiers = Tier.ALL.map { tier ->
            val layer = LineLayer(tier.id, SOURCE).apply {
                sourceLayer = TransitTiles.ROUTES_SOURCE_LAYER
                minZoom = tier.minimumZoom.toFloat()
                setProperties(
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.visibility(visibility(isVisible)),
                )
                lineOpacityTransition = TransitionOptions(SELECTION_FADE_MS, 0)
                setFilter(
                    Expression.all(
                        Expression.eq(Expression.get(PROP_RANK), Expression.literal(tier.rank)),
                        bandFilter(Family.FUSED),
                    ),
                )
            }
            insertBelowLabels(style, layer)
            PaintedTier(layer, tier)
        }

        // La ligne désignée : l'autre sens estompé dessous, puis liseré, trait, tireté.
        focus = Kind.entries.flatMap { kind ->
            Family.entries.map { family -> FocusLayer(focusLayer(kind, family), family, kind) }
        }
        focus.forEach { insertBelowLabels(style, it.layer) }

        // Un rechargement de style remonte tout : la ligne désignée doit revenir
        // avec, sans quoi passer en mode sombre l'effacerait sans rien dire.
        applyFocus()
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        this.ambiance = ambiance
        applyFocus()
    }

    override fun unmount(style: Style) {
        focus.asReversed().forEach { style.removeLayer(it.layer.id) }
        tiers.asReversed().forEach { style.removeLayer(it.layer.id) }
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ **Ici, ce ne sont pas que des sources.** Les `LineLayer` gardées servent
     * à `setVisible` et à `setFocus`, que le volet des lignes appelle **hors du
     * registre** : gardées après la mort du style, elles laissent ces deux gestes
     * repeindre des couches qui ne sont plus dans aucun style.
     */
    override fun forgetStyle() {
        focus = emptyList()
        tiers = emptyList()
        source = null
    }

    private fun focusLayer(kind: Kind, family: Family): LineLayer =
        LineLayer("aule-transit-line-${family.key}-${kind.key}", SOURCE).apply {
            sourceLayer = TransitTiles.ROUTES_SOURCE_LAYER
            when (family) {
                Family.FUSED -> maxZoom = DIRECTIONAL_FROM_ZOOM.toFloat()
                Family.DIRECTIONAL -> minZoom = DIRECTIONAL_FROM_ZOOM.toFloat()
            }
            setProperties(
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                // Des extrémités rondes mangeraient les trous du tireté.
                PropertyFactory.lineCap(if (kind == Kind.DASHED) Property.LINE_CAP_BUTT else Property.LINE_CAP_ROUND),
                PropertyFactory.visibility(Property.NONE),
            )
            when (kind) {
                Kind.OTHER -> setProperties(
                    PropertyFactory.lineWidth(width(OTHER_DIRECTION_WIDTH)),
                    PropertyFactory.lineOpacity(OTHER_DIRECTION_OPACITY),
                )
                Kind.CASING -> setProperties(PropertyFactory.lineWidth(width(LINE_WIDTH, border = true)))
                Kind.STROKE -> setProperties(PropertyFactory.lineWidth(width(LINE_WIDTH)))
                Kind.DASHED -> setProperties(
                    PropertyFactory.lineWidth(width(LINE_WIDTH)),
                    PropertyFactory.lineDasharray(arrayOf(2f, 1.5f)),
                )
            }
        }

    /**
     * Sous les étiquettes du fond, et non par-dessus : un tracé posé au-dessus des
     * noms de rue les barre, et c'est justement le quartier qu'on est en train de lire.
     */
    private fun insertBelowLabels(style: Style, layer: LineLayer) {
        if (style.getLayer(MapStyleAnchors.BELOW_LABELS) != null) {
            style.addLayerBelow(layer, MapStyleAnchors.BELOW_LABELS)
        } else {
            style.addLayer(layer)
        }
    }

    /**
     * Pose l'état de désignation et les couleurs de l'ambiance sur les couches
     * déjà montées. Sans effet avant le montage — [focusedLine] est alors la seule
     * vérité, et [mount] la rejoue.
     */
    private fun applyFocus() {
        val designated = focusedLine
        val suffix = if (ambiance == MapAmbiance.DARK) "night" else "day"

        tiers.forEach { painted ->
            // Sous une ligne désignée, le réseau passe en gris neutre et s'affine :
            // il reste un contexte, et ne dispute plus la couleur à la ligne qu'on regarde.
            painted.layer.setProperties(
                *if (designated == null) {
                    arrayOf(
                        PropertyFactory.lineColor(Expression.get("stroke_$suffix")),
                        PropertyFactory.lineWidth(painted.tier.width()),
                        PropertyFactory.lineOpacity(painted.tier.opacity()),
                    )
                } else {
                    arrayOf(
                        PropertyFactory.lineColor(if (ambiance == MapAmbiance.DARK) CONTEXT_COLOR_NIGHT else CONTEXT_COLOR_DAY),
                        PropertyFactory.lineWidth(width(CONTEXT_WIDTH, byRole = false)),
                        PropertyFactory.lineOpacity(1f),
                    )
                },
            )
        }

        focus.forEach { painted ->
            painted.layer.setProperties(
                PropertyFactory.lineColor(
                    Expression.get(if (painted.kind == Kind.CASING) "casing_$suffix" else "stroke_$suffix"),
                ),
            )
            val filter = designated?.let { focusFilter(it, painted) }
            painted.layer.setFilter(filter ?: NOTHING)
            painted.layer.setProperties(PropertyFactory.visibility(visibility(filter != null)))
        }
    }

    /** Le filtre d'une couche de la ligne désignée — `null` quand elle n'a rien à peindre. */
    private fun focusFilter(line: String, painted: FocusLayer): Expression? {
        val direction = focusedDirection
        val parts = mutableListOf(matchFilter(line), bandFilter(painted.family))
        val shown = direction?.let {
            Expression.any(
                Expression.eq(Expression.get(PROP_DIRS), Expression.literal("both")),
                Expression.eq(Expression.get(PROP_DIRS), Expression.literal(it)),
            )
        }
        val dashed = Expression.eq(Expression.get(PROP_ROLE), Expression.literal("occasionnel"))
        when (painted.kind) {
            // Sans sens désigné, il n'y a pas d'« autre » : tout est plein.
            Kind.OTHER -> parts += Expression.not(shown ?: return null)
            Kind.CASING, Kind.STROKE -> {
                parts += Expression.not(dashed)
                shown?.let { parts += it }
            }
            Kind.DASHED -> {
                parts += dashed
                shown?.let { parts += it }
            }
        }
        return Expression.all(*parts.toTypedArray())
    }

    /**
     * Le filtre des tronçons d'**une** ligne.
     *
     * Une clé qualifiée compare **les deux** propriétés du tronçon :
     * `network == "aleop" && match == "C6"`. Comparer `match` seul allumait le
     * Chronobus C6 avec le tram-train. Une clé nue compare `match` seul.
     */
    private fun matchFilter(line: String): Expression {
        val (network, match) = splitTransitLineKey(line) ?: (null to " ")
        val sameMatch = Expression.eq(Expression.get(PROP_MATCH), Expression.literal(match))
        if (network == null) return sameMatch
        return Expression.all(
            Expression.eq(Expression.get(PROP_NETWORK), Expression.literal(network)),
            sameMatch,
        )
    }

    /**
     * Un palier de densité du réseau.
     *
     * ```
     *   structurant — tram, busway, navibus, TER  → dès z10
     *   fort        — Chronobus, Express, Aléop   → à l'ouverture du réseau urbain
     *   ordinaire   — le reste du réseau bus      → un palier et demi plus loin
     * ```
     */
    private class Tier(
        val id: String,
        val rank: Int,
        val minimumZoom: Double,
        val opacity: () -> Expression,
        val width: () -> Expression,
    ) {
        companion object {
            /**
             * Du moins structurant au plus structurant : le tram doit passer
             * **au-dessus** du bus quand ils partagent un couloir, et MapLibre
             * peint dans l'ordre d'ajout.
             */
            val ALL: List<Tier> get() = listOf(ORDINARY, STRONG, STRUCTURING)

            private val ORDINARY = Tier(
                id = "aule-transit-ordinary",
                rank = 2,
                minimumZoom = ORDINARY_FROM,
                // Chaque palier démarre à son seuil avec une opacité nulle : il
                // entre en fondu plutôt qu'en à-coup.
                opacity = { ramp(ORDINARY_FROM to 0f, ORDINARY_FROM + 1 to 0.55f, ORDINARY_FROM + 2 to 0.7f) },
                width = { widthRamp(14.0 to 1.4f, 15.0 to 2.2f, 17.0 to 3f, 19.0 to 4f) },
            )

            private val STRONG = Tier(
                id = "aule-transit-strong",
                rank = 1,
                minimumZoom = STRONG_FROM,
                opacity = { ramp(STRONG_FROM to 0f, STRONG_FROM + 1 to 0.65f, STRONG_FROM + 2 to 0.8f) },
                width = { widthRamp(12.0 to 1.6f, 14.0 to 2.6f, 16.0 to 3.6f, 19.0 to 5f) },
            )

            private val STRUCTURING = Tier(
                id = "aule-transit-structuring",
                rank = 0,
                minimumZoom = 10.0,
                opacity = { ramp(10.0 to 0.7f, 12.0 to 0.85f, 15.0 to 0.9f) },
                width = { widthRamp(10.0 to 2f, 13.0 to 3f, 15.0 to 4.2f, 19.0 to 6f) },
            )

            private fun ramp(vararg stops: Pair<Double, Float>): Expression = Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                *stops.map { (zoom, value) -> Expression.stop(zoom, value) }.toTypedArray(),
            )

            private fun widthRamp(vararg stops: Pair<Double, Float>): Expression = Expression.interpolate(
                Expression.exponential(1.4f),
                Expression.zoom(),
                *stops.map { (zoom, value) -> Expression.stop(zoom, value) }.toTypedArray(),
            )
        }
    }

    companion object {
        const val ID = "aule.transit-lines"

        internal const val SOURCE = "aule-transit-lines-source"

        /** Les propriétés que la géométrie Aule porte sur chaque tronçon. */
        internal const val PROP_RANK = "rank"
        internal const val PROP_MATCH = "match"
        internal const val PROP_NETWORK = "network"
        internal const val PROP_BAND = "band"
        internal const val PROP_ROLE = "role"
        internal const val PROP_DIRS = "dirs"

        /** Au-delà de ce zoom, on quitte l'axe fusionné pour le tracé exact de chaque sens. */
        const val DIRECTIONAL_FROM_ZOOM = 16.0

        /** Les bandes de l'axe fusionné dans `transit-v2.pmtiles`, chacune à son zoom. */
        private val FUSED_BANDS = listOf("fused-11", "fused-12", "fused-13", "fused-14")

        /**
         * Le zoom d'ouverture du réseau urbain (`AFTER_OPENING` de la carte web).
         *
         * La vue nationale et la densité à l'échelle d'une ville sont deux
         * décisions indépendantes : ce seuil ne suit pas le zoom d'ouverture de
         * la caméra.
         */
        const val NETWORK_AFTER_OPENING = 14.0

        /**
         * Le zoom en deçà duquel la carte cesse de lire un réseau.
         *
         * C'est le plancher du cadrage d'une ligne. `transit-v2.pmtiles` porte
         * l'axe fusionné dès z2 ; le plancher dit seulement où la carte commence
         * à lire un réseau.
         */
        const val NETWORK_LEGIBLE_ZOOM = 10.0

        private const val STRONG_FROM = NETWORK_AFTER_OPENING
        private const val ORDINARY_FROM = NETWORK_AFTER_OPENING + 1.5

        // Les épaisseurs, les couleurs et la durée du fondu sont **celles du web**
        // (`lib/carte-immersive/line-rendering.ts`, `transit-selection.ts`),
        // reprises telles quelles : une ligne désignée doit se reconnaître d'une
        // carte à l'autre.

        /** Le trait d'une ligne désignée, par zoom. */
        private val LINE_WIDTH = listOf(10.0 to 2.5f, 13.0 to 3.5f, 15.0 to 5f, 17.0 to 7f, 19.0 to 10f)

        /** Son liseré, de chaque côté. */
        private val LINE_BORDER = mapOf(10.0 to 0.75f, 13.0 to 1f, 15.0 to 1.25f, 17.0 to 1.5f, 19.0 to 2f)

        /** Le trait de l'autre sens, estompé. */
        private val OTHER_DIRECTION_WIDTH = listOf(10.0 to 1.5f, 13.0 to 2f, 15.0 to 2.5f, 17.0 to 3.5f, 19.0 to 5f)
        private const val OTHER_DIRECTION_OPACITY = 0.45f

        /** Branches et occasionnels, relativement au tronc. */
        private const val BRANCH_FACTOR = 0.6f

        /** Le réseau en gris sous une ligne désignée. */
        private val CONTEXT_WIDTH = listOf(10.0 to 1f, 13.0 to 1.5f, 15.0 to 2f, 17.0 to 2.5f, 19.0 to 3f)
        private const val CONTEXT_COLOR_DAY = "#7F8C89"
        private const val CONTEXT_COLOR_NIGHT = "#5E746E"

        /**
         * Le fondu d'apparition et de disparition d'une ligne désignée. Assez
         * court pour que le geste reste direct, assez long pour que le tracé ne
         * surgisse pas d'un coup.
         */
        private const val SELECTION_FADE_MS = 200L

        /**
         * Le filtre qui ne retient rien : une clé vide, que `key` ne porte jamais.
         * Un filtre constamment faux serait plus direct et n'a pas de traduction
         * garantie en expression de style.
         */
        private val NOTHING: Expression = Expression.eq(Expression.get("key"), Expression.literal(""))

        private fun visibility(value: Boolean): String =
            if (value) Property.VISIBLE else Property.NONE

        private fun bandFilter(family: Family): Expression = when (family) {
            Family.FUSED -> Expression.any(
                *FUSED_BANDS.map { Expression.eq(Expression.get(PROP_BAND), Expression.literal(it)) }.toTypedArray(),
            )
            Family.DIRECTIONAL -> Expression.eq(Expression.get(PROP_BAND), Expression.literal("directional"))
        }

        /**
         * Une épaisseur au format d'une expression MapLibre : branches et
         * occasionnels à 60 % du tronc, le liseré ajouté des deux côtés quand
         * [border] l'est. Le facteur descend dans chaque palier : une expression
         * de zoom ne peut pas être un sous-terme.
         */
        private fun width(
            stops: List<Pair<Double, Float>>,
            border: Boolean = false,
            byRole: Boolean = true,
        ): Expression {
            val factor = Expression.switchCase(
                Expression.eq(Expression.get(PROP_ROLE), Expression.literal("principal")),
                Expression.literal(1f),
                Expression.literal(BRANCH_FACTOR),
            )
            return Expression.interpolate(
                Expression.exponential(1.5f),
                Expression.zoom(),
                *stops.map { (zoom, value) ->
                    val core = if (byRole) Expression.product(Expression.literal(value), factor) else Expression.literal(value)
                    val edge = LINE_BORDER[zoom] ?: 1f
                    Expression.stop(zoom, if (border) Expression.sum(core, Expression.literal(2 * edge)) else core)
                }.toTypedArray(),
            )
        }
    }
}
