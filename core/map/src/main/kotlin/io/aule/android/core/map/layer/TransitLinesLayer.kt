package io.aule.android.core.map.layer

import io.aule.android.core.common.log.AuleLogger
import io.aule.android.core.common.log.LogDomain
import io.aule.android.core.common.log.NoopLogger
import io.aule.android.core.map.MapLayer
import io.aule.android.core.map.MapStyleAnchors
import io.aule.android.core.map.TransitTiles
import io.aule.android.core.model.canonicalLineName
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
 * Port de `Native/Aule/Core/Map/Layers/TransitLinesLayer.swift`, lui-même port du
 * Flutter et du web. Les seuils, les opacités et les largeurs sont **repris tels
 * quels** : ce sont eux qui font que les trois cartes d'Aule se ressemblent, et
 * les réécrire « en mieux » serait la première divergence.
 *
 * ## Trois paliers, parce que 138 lignes d'un bloc ne se lisent pas
 *
 * À l'échelle de l'agglomération, on ne lit que la **structure** : tram, busway,
 * navibus. Le reste arrive en approchant — les Chronobus et l'interurbain après
 * l'ouverture, le bus ordinaire un palier plus loin. Le rang qui décide est
 * calculé au build des tuiles ; le style se contente de le lire.
 *
 * ## Masqués par défaut, et c'est une décision
 *
 * Les tracés décrivent ce qui **existe**, pas ce qui se passe. Peints en
 * permanence, ils recouvrent le territoire d'un lacis coloré au milieu duquel les
 * véhicules, l'arrêt qu'on vient de toucher et l'itinéraire qu'on vient de
 * calculer — tout ce qui répond à une question qu'on vient de poser — deviennent
 * difficiles à distinguer. Le réseau est donc quelque chose qu'on **demande** :
 * il est peint tant que le volet « Lignes du réseau » est ouvert, et il s'efface
 * avec lui.
 *
 * ## Une ligne désignée, par-dessus
 *
 * Toucher un rang met la ligne en avant — halo et trait plein — et assourdit le
 * réseau autour d'elle. Il n'y a **rien à charger** : la ligne est déjà dans les
 * tuiles, ce qui change est un filtre. C'est ce qui rend la désignation
 * instantanée, y compris hors réseau.
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
     * dans celui du premier montage : sans ça, passer en mode sombre éteindrait
     * le réseau qu'on vient d'allumer.
     */
    var isVisible: Boolean = false
        private set

    /**
     * La ligne mise en avant, sous sa forme canonique — ou `null`.
     *
     * **Séparée de la visibilité du réseau**, et ce n'est pas un raffinement :
     * les deux répondent à deux questions différentes — « où passent les lignes
     * ici ? » et « où passe celle-ci ? » —, et la seconde doit pouvoir se poser
     * sur une carte nue.
     */
    var focusedLine: String? = null
        private set

    private var source: VectorSource? = null
    private var tiers: List<PaintedTier> = emptyList()
    private var halo: LineLayer? = null
    private var highlight: LineLayer? = null

    private class PaintedTier(val layer: LineLayer, val tier: Tier)

    fun setVisible(value: Boolean) {
        if (value == isVisible) return
        isVisible = value
        // La source reste en place : la démonter puis la remonter à chaque
        // bascule relancerait le chargement des tuiles, alors que la visibilité
        // est immédiate.
        tiers.forEach { it.layer.setProperties(PropertyFactory.visibility(visibility(value))) }
        logger.info(LogDomain.MAP, "Tracés du réseau : ${if (value) "affichés" else "masqués"}.")
    }

    fun setFocus(line: String?) {
        val canonical = line?.let(::canonicalLineName)
        if (canonical == focusedLine) return
        focusedLine = canonical
        applyFocus()
        logger.info(LogDomain.MAP, "Ligne mise en avant : ${canonical ?: "aucune"}.")
    }

    override fun mount(style: Style, map: MapLibreMap) {
        val posed = VectorSource(SOURCE, archiveUrl)
        style.addSource(posed)
        source = posed

        tiers = Tier.ALL.map { tier ->
            val layer = LineLayer(tier.id, SOURCE).apply {
                sourceLayer = TransitTiles.LINES_SOURCE_LAYER
                minZoom = tier.minimumZoom.toFloat()
                setProperties(
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineJoin("round"),
                    // La couleur voyage **dans la donnée** : chaque tronçon porte
                    // celle de sa ligne.
                    PropertyFactory.lineColor(Expression.get(PROP_COLOR)),
                    PropertyFactory.lineOpacity(tier.opacity()),
                    PropertyFactory.lineWidth(tier.width()),
                    PropertyFactory.lineOffset(bundleOffset()),
                    PropertyFactory.visibility(visibility(isVisible)),
                )
                // Le rang trie les tracés en trois paliers de densité ; il est
                // calculé au build des tuiles, le style se contente de le lire.
                setFilter(Expression.eq(Expression.get(PROP_RANK), Expression.literal(tier.rank)))
            }
            insertBelowLabels(style, layer)
            PaintedTier(layer, tier)
        }

        // Le halo d'abord, le trait par-dessus — et les deux **après** les
        // paliers, donc au-dessus d'eux : une ligne désignée passe devant le
        // réseau qu'elle traverse.
        halo = highlightLayer(style, HALO, haloWidth(), HIGHLIGHT_HALO_OPACITY, blur = 2f)
        highlight = highlightLayer(style, HIGHLIGHT, highlightWidth(), HIGHLIGHT_LINE_OPACITY, blur = null)

        // Un rechargement de style remonte tout : la ligne désignée doit revenir
        // avec, sans quoi passer en mode sombre l'effacerait sans rien dire.
        applyFocus()
    }

    override fun unmount(style: Style) {
        listOf(HIGHLIGHT, HALO).forEach { style.removeLayer(it) }
        tiers.asReversed().forEach { style.removeLayer(it.layer.id) }
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ **Ici, ce ne sont pas que des sources.** Les `LineLayer` gardées servent
     * à `setVisible` et à `setFocus`, que le volet des lignes appelle **hors du
     * registre** : gardées après la mort du style, elles laissent ces deux gestes
     * repeindre des couches qui ne sont plus dans aucun style.
     *
     * La visibilité et la ligne désignée, elles, restent : c'est [mount] qui les
     * réapplique — un rechargement de style ne doit pas effacer la ligne qu'on
     * vient de désigner.
     */
    override fun forgetStyle() {
        halo = null
        highlight = null
        tiers = emptyList()
        source = null
    }

    /**
     * Une couche de surbrillance : même source, même couleur portée par la
     * donnée, **sans seuil de zoom** — une ligne qu'on vient de désigner reste
     * lisible à tous les paliers, y compris sous le zoom où son palier de densité
     * s'allume.
     */
    private fun highlightLayer(
        style: Style,
        identifier: String,
        width: Expression,
        opacity: Float,
        blur: Float?,
    ): LineLayer {
        val layer = LineLayer(identifier, SOURCE).apply {
            sourceLayer = TransitTiles.LINES_SOURCE_LAYER
            setProperties(
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineColor(Expression.get(PROP_COLOR)),
                PropertyFactory.lineOpacity(opacity),
                PropertyFactory.lineWidth(width),
                PropertyFactory.lineOffset(bundleOffset()),
            )
            // Le fondu vaut pour l'opacité seule : le filtre, lui, est sec.
            // C'est ce que fait le web, à la même durée. Android pose les
            // transitions par un setter dédié et non par `PropertyFactory`.
            lineOpacityTransition = TransitionOptions(SELECTION_FADE_MS, 0)
            if (blur != null) setProperties(PropertyFactory.lineBlur(blur))
        }
        insertBelowLabels(style, layer)
        return layer
    }

    /**
     * Sous les étiquettes du fond, et non par-dessus : un tracé de 5 points posé
     * au-dessus des noms de rue les barre, et c'est justement le quartier qu'on
     * est en train de lire.
     */
    private fun insertBelowLabels(style: Style, layer: LineLayer) {
        if (style.getLayer(MapStyleAnchors.BELOW_LABELS) != null) {
            style.addLayerBelow(layer, MapStyleAnchors.BELOW_LABELS)
        } else {
            style.addLayer(layer)
        }
    }

    /**
     * Pose l'état de désignation sur les couches déjà montées. Sans effet avant
     * le montage — [focusedLine] est alors la seule vérité, et [mount] la rejoue.
     */
    private fun applyFocus() {
        val designated = focusedLine

        tiers.forEach { painted ->
            // Le réseau s'assourdit **par constante** quand une ligne est
            // désignée, et retrouve sinon la rampe de fondu de son palier : c'est
            // elle qui fait entrer chaque palier au lieu de le faire surgir.
            painted.layer.setProperties(
                if (designated == null) {
                    PropertyFactory.lineOpacity(painted.tier.opacity())
                } else {
                    PropertyFactory.lineOpacity(DIMMED_NETWORK_OPACITY)
                },
            )
        }

        listOfNotNull(halo, highlight).forEach { layer ->
            layer.setFilter(matchFilter(designated))
            layer.setProperties(PropertyFactory.visibility(visibility(designated != null)))
        }
    }

    /**
     * Le filtre d'une ligne désignée — ou celui qui ne retient rien.
     *
     * ⚠️ **Le filtre « rien » désigne une espace**, comme sur le web
     * (`MATCHES_NOTHING`) : `match` porte toujours un nom de ligne, donc jamais
     * une espace. Un filtre constamment faux serait plus direct et n'a pas de
     * traduction garantie en expression de style.
     */
    private fun matchFilter(line: String?): Expression =
        Expression.eq(Expression.get(PROP_MATCH), Expression.literal(line ?: " "))

    /**
     * Un palier de densité du réseau.
     *
     * ```
     *   structurant — tram, busway, navibus       → dès z10
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
                opacity = {
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(ORDINARY_FROM, 0f),
                        Expression.stop(ORDINARY_FROM + 1, 0.3f),
                        Expression.stop(ORDINARY_FROM + 2, 0.42f),
                    )
                },
                width = {
                    Expression.interpolate(
                        Expression.exponential(1.4f),
                        Expression.zoom(),
                        Expression.stop(14, 1.4f),
                        Expression.stop(15, 2.6f),
                        Expression.stop(17, 3.4f),
                        Expression.stop(18, 3f),
                    )
                },
            )

            private val STRONG = Tier(
                id = "aule-transit-strong",
                rank = 1,
                minimumZoom = STRONG_FROM,
                opacity = {
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(STRONG_FROM, 0f),
                        Expression.stop(STRONG_FROM + 1, 0.4f),
                        Expression.stop(STRONG_FROM + 2, 0.58f),
                    )
                },
                width = {
                    Expression.interpolate(
                        Expression.exponential(1.4f),
                        Expression.zoom(),
                        Expression.stop(12, 1.8f),
                        Expression.stop(14, 3f),
                        Expression.stop(16, 4.5f),
                        Expression.stop(18, 3.5f),
                    )
                },
            )

            private val STRUCTURING = Tier(
                id = "aule-transit-structuring",
                rank = 0,
                minimumZoom = 10.0,
                opacity = {
                    Expression.interpolate(
                        Expression.linear(),
                        Expression.zoom(),
                        Expression.stop(10, 0.42f),
                        Expression.stop(12, 0.64f),
                        Expression.stop(15, 0.74f),
                    )
                },
                width = {
                    Expression.interpolate(
                        Expression.exponential(1.4f),
                        Expression.zoom(),
                        Expression.stop(10, 2f),
                        Expression.stop(13, 3.6f),
                        Expression.stop(15, 5f),
                        Expression.stop(18, 4f),
                    )
                },
            )
        }
    }

    companion object {
        const val ID = "aule.transit-lines"

        internal const val SOURCE = "aule-transit-lines-source"
        internal const val HALO = "aule-transit-highlight-halo"
        internal const val HIGHLIGHT = "aule-transit-highlight"

        /** Les propriétés que les tuiles portent sur chaque tronçon. */
        internal const val PROP_COLOR = "color"
        internal const val PROP_RANK = "rank"
        internal const val PROP_MATCH = "match"

        /** La place signée d'un tronçon dans son faisceau, calculée au build. */
        internal const val PROP_BUNDLE_SLOT = "bs"

        /**
         * Le zoom d'ouverture du réseau urbain (`AFTER_OPENING` de la carte web).
         *
         * La vue nationale et la densité à l'échelle d'une ville sont deux
         * décisions indépendantes : ce seuil ne suit pas le zoom d'ouverture de
         * la caméra.
         */
        const val NETWORK_AFTER_OPENING = 14.0

        /**
         * Le zoom en deçà duquel l'archive cesse de porter la ligne qu'on regarde.
         *
         * C'est le plancher du cadrage d'une ligne : sous ce palier, le cadrage
         * serait juste — la boîte est la bonne — sur une carte où la ligne
         * n'existe pas. Le défaut ne se lit nulle part ailleurs : ni erreur, ni
         * tuile manquante, seulement une carte nue.
         */
        const val NETWORK_LEGIBLE_ZOOM = 10.0

        private const val STRONG_FROM = NETWORK_AFTER_OPENING
        private const val ORDINARY_FROM = NETWORK_AFTER_OPENING + 1.5

        // Les trois opacités et la durée du fondu sont **celles du web**
        // (`transit-selection.ts`), reprises telles quelles : une ligne désignée
        // doit se reconnaître d'une carte à l'autre.

        /** L'opacité du halo d'une ligne mise en avant. */
        private const val HIGHLIGHT_HALO_OPACITY = 0.25f

        /** Celle de son trait plein. */
        private const val HIGHLIGHT_LINE_OPACITY = 1.0f

        /** Celle du réseau autour d'elle, quand il est peint. */
        private const val DIMMED_NETWORK_OPACITY = 0.12f

        /**
         * Le fondu d'apparition et de disparition d'une ligne désignée. Assez
         * court pour que le geste reste direct, assez long pour que le tracé ne
         * surgisse pas d'un coup.
         */
        private const val SELECTION_FADE_MS = 200L

        private fun visibility(value: Boolean): String =
            if (value) Property.VISIBLE else Property.NONE

        /**
         * L'écartement d'un faisceau, en points.
         *
         * Chaque tronçon porte sa place signée dans le corridor
         * ([PROP_BUNDLE_SLOT], calculée au build des tuiles). L'écartement est
         * **nul jusqu'à z14** : à l'échelle de l'agglomération, un faisceau doit
         * se lire comme une seule artère épaisse, et l'écarter le ferait
         * ressembler à dix rues parallèles qui n'existent pas. Le seuil est z14 et
         * non z13 parce que c'est là que les tuiles cessent de porter le tracé
         * entier pour porter ses segments de faisceau (`BUNDLE_FROM`,
         * `build-transit.mjs`) : ancré plus bas, l'écartement sauterait d'un coup
         * au changement de tuile. Il s'annule de nouveau à z18, où la géométrie
         * est exacte au mètre et où dix points de décalage feraient courir la
         * ligne à côté de sa propre chaussée.
         *
         * ⚠️ **Le produit est dans les paliers, et il n'a pas le choix d'y être.**
         * La forme factorisée — `bs × interpolate(zoom …)` — donnerait la même
         * valeur et refuse de se monter : MapLibre lève « "zoom" expression may
         * only be used as input to a top-level "step" or "interpolate"
         * expression ». Une expression de zoom ne peut pas être un sous-terme ;
         * c'est l'interpolation qui doit être en tête, et le produit descend dans
         * ses paliers. Le web et l'iOS l'écrivent déjà ainsi.
         */
        private fun bundleOffset(): Expression {
            val slot = Expression.coalesce(
                Expression.get(PROP_BUNDLE_SLOT),
                Expression.literal(0f),
            )
            return Expression.interpolate(
                Expression.linear(),
                Expression.zoom(),
                Expression.stop(14, Expression.literal(0f)),
                Expression.stop(15, Expression.product(slot, Expression.literal(1.6f))),
                Expression.stop(17, Expression.product(slot, Expression.literal(3.4f))),
                Expression.stop(18, Expression.literal(0f)),
            )
        }

        /**
         * Le halo d'une ligne désignée : large, flou, translucide. C'est lui qui
         * la fait sortir du fond sans épaissir le trait, donc sans mentir sur le
         * tracé.
         */
        private fun haloWidth(): Expression = Expression.interpolate(
            Expression.exponential(1.4f),
            Expression.zoom(),
            Expression.stop(10, 8f),
            Expression.stop(14, 14f),
            Expression.stop(18, 20f),
        )

        /**
         * Le trait plein, posé dans le halo. Plus épais que le palier structurant
         * à tous les zooms — sinon la ligne désignée aurait la même épaisseur que
         * celles qu'elle croise.
         */
        private fun highlightWidth(): Expression = Expression.interpolate(
            Expression.exponential(1.4f),
            Expression.zoom(),
            Expression.stop(10, 3f),
            Expression.stop(13, 5f),
            Expression.stop(15, 7f),
            Expression.stop(18, 6f),
        )
    }
}
