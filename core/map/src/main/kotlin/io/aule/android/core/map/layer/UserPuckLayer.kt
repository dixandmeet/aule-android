package io.aule.android.core.map.layer

import com.google.gson.JsonObject
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.geo.Coordinate
import io.aule.android.core.geo.GeoMath
import io.aule.android.core.location.LocationFix
import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapIcons
import io.aule.android.core.map.MapLayer
import io.aule.android.core.map.MapScale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * La position de l'utilisateur.
 *
 * On dessine notre propre puck plutôt que d'utiliser celui de MapLibre : le
 * brief exclut le bleu de Google Maps, et surtout le puck natif se recale
 * par sauts alors que celui-ci glisse.
 *
 * Trois éléments, du plus incertain au plus sûr : l'anneau de précision, le
 * cône de direction, le marqueur.
 *
 * **Deux caps, et ils ne répondent pas à la même question.** Le cône dit
 * *vers où l'on est tourné* — cap fusionné boussole + route, il tient donc
 * même à l'arrêt ; le marqueur dit *vers où l'on va* — cap de route seul, et
 * il change de forme avec lui : **disque à l'arrêt, flèche en mouvement**.
 * Le disque répond « je suis là », la flèche répond « je suis là et je vais
 * par là », qui est la question qu'on se pose en marchant ; le cône, lui,
 * répond à celle qu'on se pose en sortant du métro, immobile sur le
 * trottoir, avant même d'avoir fait un pas.
 *
 * Chacun disparaît quand son cap n'existe plus — afficher une direction
 * qu'on ne connaît plus est pire que de n'en afficher aucune.
 */
class UserPuckLayer : MapLayer {

    override val id: String = ID
    override val isAnimated: Boolean = true

    private var source: GeoJsonSource? = null

    // Référence **forte** sur la couche du halo : c'est elle qu'on écrit à
    // chaque battement, et la retrouver par son identifiant trente fois par
    // seconde coûterait une recherche dans le style à chaque fois.
    private var halo: SymbolLayer? = null
    private var lastBreathAt = 0.0

    private var target: Target? = null
    private var displayed: Displayed? = null

    /**
     * La position affichée, qui court après la mesure. C'est elle que suit
     * la caméra : la faire suivre la mesure brute ferait sursauter l'écran
     * à chaque point GPS.
     */
    val displayedCoordinate: Coordinate? get() = displayed?.coordinate
    val displayedHeading: Double? get() = displayed?.heading

    /**
     * [movementHeading] est le cap de **route**, `null` dès qu'il est gelé.
     * [facingHeading] est celui du cône, déjà fusionné et lissé par
     * `HeadingFusion` — ici on ne fait que combler les soixante-six
     * millisecondes qui séparent deux battements du ticker.
     */
    fun update(fix: LocationFix?, movementHeading: Double?, facingHeading: Double?) {
        if (fix == null) {
            target = null
            return
        }
        val hadHeading = target?.heading != null
        val hadFacing = target?.facing != null
        target = Target(fix.coordinate, movementHeading, facingHeading, fix.accuracyMeters)
        if (displayed == null) {
            // Première position : on se pose dessus, sans glisser depuis nulle part.
            displayed = Displayed(
                fix.coordinate,
                movementHeading ?: facingHeading ?: 0.0,
                facingHeading ?: movementHeading ?: 0.0,
            )
            redraw()
            return
        }
        // ⚠️ **L'apparition et le gel d'un cap doivent être republiés tout de
        // suite.** Ils choisissent entre le disque et la flèche, et décident si
        // le cône existe ; or ils basculent précisément au moment où plus rien
        // ne bouge : [onFrame] sort alors sans dessiner — le puck a rattrapé sa
        // cible, les caps sont figés — et la flèche resterait à pointer une
        // direction qu'on n'a plus. On ne republie qu'au basculement, pas à
        // chaque point : le reste du temps, l'interpolation fait le travail.
        if (hadHeading != (movementHeading != null) || hadFacing != (facingHeading != null)) {
            redraw()
        }
    }

    override fun onFrame(elapsedSeconds: Double) {
        breathe(elapsedSeconds)

        val target = target ?: return
        val current = displayed ?: return

        val distance = GeoMath.distance(current.coordinate, target.coordinate)
        val headingDelta = abs(
            GeoMath.shortestHeadingDelta(current.heading, target.heading ?: current.heading),
        )
        // ⚠️ **Le cône doit peser dans cette garde.** À l'arrêt, la position ne
        // bouge plus et le cap de route est gelé : sans ce troisième terme, on
        // sortirait à chaque image, et le cône resterait cloué sur son premier
        // cap pendant que l'utilisateur pivote sur place — c'est-à-dire dans le
        // seul cas où il sert vraiment.
        val facingDelta = abs(
            GeoMath.shortestHeadingDelta(current.facing, target.facing ?: current.facing),
        )
        if (distance <= 0.15 && headingDelta <= 0.3 && facingDelta <= 0.3) return

        val factor = if (distance > 40) 0.5 else 0.12
        val nextCoordinate = GeoMath.interpolate(current.coordinate, target.coordinate, factor)
        val nextHeading = target.heading?.let { GeoMath.interpolateHeading(current.heading, it, 0.2) }
            ?: current.heading
        val nextFacing = target.facing?.let { GeoMath.interpolateHeading(current.facing, it, 0.25) }
            ?: current.facing
        displayed = Displayed(nextCoordinate, nextHeading, nextFacing)
        redraw()
    }

    override fun mount(style: Style, map: MapLibreMap) {
        source = GeoJsonSource(
            SOURCE,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withSynchronousUpdate(true).withBuffer(0).withTolerance(0f),
        ).also { style.addSource(it) }

        // L'anneau d'incertitude — **le rayon que le GPS annonce**, et non un
        // rayon décoratif.
        //
        // ⚠️ Il ne l'a pas toujours été. Les stops [12 → 0,5] / [22 → 512]
        // hérités du proto iOS faisaient bien grandir l'anneau au dézoom, mais
        // à partir d'une constante : quelle que soit la précision mesurée, il
        // dessinait les mêmes six mètres et demi. La couche publiait pourtant
        // `accuracy` à chaque battement — personne ne la lisait. Et comme six
        // mètres et demi tiennent sous le puck à tout zoom utile, l'anneau
        // n'était jamais visible : un objet qui ne dit rien et qu'on ne voit
        // pas ne se signale pas non plus comme cassé.
        //
        // Le rayon vient maintenant de la source, déjà converti en pixels au
        // zoom de référence ([MapScale]) parce que la conversion a besoin de
        // la latitude, que l'expression n'a pas. Il ne reste ici qu'à le
        // laisser suivre l'échelle : un facteur deux par niveau de zoom, ce
        // qui est exactement ce que fait une distance réelle.
        //
        // Conséquence voulue : **il ne parle que quand il a quelque chose à
        // dire.** Au plancher de cinq mètres et au zoom d'ouverture, il tient
        // dans l'ombre du puck au pixel près ; il s'ouvre à mesure que
        // l'incertitude grandit, et une réception ordinaire de dix-sept mètres
        // lui fait déjà trois fois le rayon du disque.
        style.addLayer(
            CircleLayer(ACCURACY_LAYER, SOURCE).withProperties(
                // ⚠️ **`zoom()` ne se met pas où l'on veut.** MapLibre refuse
                // l'expression si elle n'est pas l'entrée directe d'un
                // `interpolate` ou d'un `step` de **premier niveau** : glissée
                // dans un `product`, elle échoue à la pose avec « zoom
                // expression may only be used as input to a top-level step or
                // interpolate », la propriété retombe sur son défaut — cinq
                // pixels — et l'anneau redevient invisible sous le puck. La
                // multiplication descend donc **dans les sorties** des paliers,
                // ce que `circle-radius` accepte puisqu'il est data-driven.
                PropertyFactory.circleRadius(
                    Expression.interpolate(
                        Expression.exponential(2f),
                        Expression.zoom(),
                        Expression.stop(
                            MapScale.REFERENCE_ZOOM - ZOOM_SPAN,
                            Expression.product(
                                Expression.get(PROP_ACCURACY_PX),
                                Expression.literal(1f / ZOOM_FACTOR),
                            ),
                        ),
                        Expression.stop(
                            MapScale.REFERENCE_ZOOM,
                            Expression.get(PROP_ACCURACY_PX),
                        ),
                    ),
                ),
                // La teinte de jour, que [onAmbianceChange] corrigera dans la
                // même trame. La poser quand même : le défaut de MapLibre pour
                // `circle-color` est le **noir**, et une couche qui passerait
                // entre les mailles du registre dessinerait une tache d'encre
                // sous le puck plutôt que rien du tout.
                PropertyFactory.circleColor(AuleTokens.of(night = false).accentGlow.argb),
                PropertyFactory.circleStrokeColor(AuleTokens.of(night = false).accentGlow.argb),
                PropertyFactory.circleOpacity(0.12f),
                PropertyFactory.circleStrokeWidth(1.5f),
                PropertyFactory.circleStrokeOpacity(0.35f),
                PropertyFactory.circlePitchAlignment(ALIGNMENT_MAP),
            ),
        )

        // Le halo, **sous** tout le reste : il entoure le puck, il ne le voile
        // pas. Face à l'écran comme le puck lui-même — couché au sol par
        // l'inclinaison de la navigation, il deviendrait une ellipse traînant
        // devant soi.
        halo = SymbolLayer(HALO_LAYER, SOURCE).withProperties(
            PropertyFactory.iconImage(MapIcons.PUCK_HALO),
            PropertyFactory.iconSize(HALO_REST),
            PropertyFactory.iconOpacity(HALO_OPACITY_REST),
            PropertyFactory.iconPitchAlignment(ALIGNMENT_VIEWPORT),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).also { style.addLayer(it) }

        // Le cône de direction, **couché au sol** : ni `rotationAlignment` ni
        // `pitchAlignment` ne sont mis à `viewport`, contrairement au marqueur.
        //
        // Ce n'est pas un oubli, c'est l'inverse du raisonnement. Le marqueur
        // est petit et sa pointe *est* l'information : couché par
        // l'inclinaison de la navigation, il s'écraserait jusqu'à ne plus rien
        // désigner. Le cône, lui, est un faisceau posé sur la chaussée — la
        // perspective qui l'étire vers l'horizon est précisément ce qui le
        // rend lisible, et c'est ce que fait tout guidage à l'écran incliné.
        //
        // Il porte son propre cap ([PROP_FACING]) et son propre filtre : à
        // l'arrêt, la boussole le tient debout alors que le marqueur est
        // retombé au disque.
        style.addLayer(
            SymbolLayer(CONE_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(MapIcons.PUCK_HEADING),
                PropertyFactory.iconRotate(Expression.get(PROP_FACING)),
                PropertyFactory.iconRotationAlignment(ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ).also {
                it.setFilter(Expression.eq(Expression.get(PROP_HAS_FACING), Expression.literal(1)))
            },
        )

        // Le marqueur lui-même : disque à l'arrêt, **flèche en mouvement**.
        //
        // C'est la même couche et la même source dans les deux cas — une
        // seconde couche ferait apparaître les deux marqueurs le temps d'une
        // image, à chaque démarrage et à chaque feu rouge. L'image est donc
        // choisie par expression, sur la propriété qui dit déjà si le cap
        // vaut quelque chose.
        //
        // Deux alignements, et ils ne disent pas la même chose : la flèche
        // **tourne avec la carte** — c'est une direction géographique — mais
        // reste **face à l'écran**. Couchée au sol par l'inclinaison de la
        // navigation, elle s'écraserait jusqu'à ne plus désigner que du vide.
        //
        // `ignorePlacement` la met hors du jeu d'évitement des étiquettes :
        // sa position n'est pas négociable, et un nom d'arrêt n'a pas à la
        // faire disparaître.
        style.addLayer(
            SymbolLayer(DOT_LAYER, SOURCE).withProperties(
                PropertyFactory.iconImage(
                    Expression.switchCase(
                        Expression.eq(Expression.get(PROP_HAS_HEADING), Expression.literal(1)),
                        Expression.literal(MapIcons.PUCK_MOVING),
                        Expression.literal(MapIcons.PUCK),
                    ),
                ),
                PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
                PropertyFactory.iconRotationAlignment(ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )

        // ⚠️ La source qu'on vient de poser est vide, et **personne ne
        // viendra la remplir**. `update` ne dessine qu'à la première
        // position, et `onFrame` sort avant de dessiner dès que le puck a
        // rattrapé sa cible. À l'arrêt, les deux se taisent : sans cette
        // ligne, le puck disparaît au premier rechargement de style — donc
        // au premier passage en mode sombre — et ne revient qu'au prochain
        // déplacement réel.
        redraw()
    }

    /**
     * L'anneau est le seul élément du puck peint par **propriété de couche** et
     * non par image : les trois autres passent par [MapIcons], que le contrôleur
     * ré-enregistre à chaque bascule. Celui-ci doit donc être repeint ici.
     *
     * Même encre que le halo et le cône, et pour la même raison : c'est un objet
     * translucide, et le teal de marque en transparence est une ombre.
     */
    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        val tokens = AuleTokens.of(ambiance == MapAmbiance.DARK)
        (style.getLayer(ACCURACY_LAYER) as? CircleLayer)?.setProperties(
            PropertyFactory.circleColor(tokens.accentGlow.argb),
            PropertyFactory.circleStrokeColor(tokens.accentGlow.argb),
        )
    }

    override fun unmount(style: Style) {
        style.removeLayer(DOT_LAYER)
        style.removeLayer(CONE_LAYER)
        style.removeLayer(HALO_LAYER)
        style.removeLayer(ACCURACY_LAYER)
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ **La couche la plus exposée du lot.** Le ticker caméra l'écrit toutes les
     * 66 ms par `update`, sans rien demander au registre : entre le moment où un
     * style meurt et celui où le suivant est chargé, une dizaine de battements
     * tombent sur une source qui n'est plus posée nulle part.
     *
     * La position visée et la position affichée, elles, restent : [mount] les
     * republie, et le puck ne disparaît pas le temps d'un rechargement.
     */
    override fun forgetStyle() {
        source = null
        halo = null
    }

    /**
     * Le battement du halo, repris du web : deux secondes et demie, de la taille
     * du puck à un tiers de plus, en s'effaçant à mesure qu'il s'élargit.
     *
     * **Trente fois par seconde, et non cent vingt.** L'écran du S21 en offre
     * cent vingt, mais une courbe qui met deux secondes et demie à faire son
     * tour ne se raconte pas plus finement en quatre-vingt-dix pas de plus :
     * c'est trois écritures de propriété sur quatre en moins, sur la seule
     * couche que rien n'oblige à se redessiner.
     *
     * Une sinusoïde plutôt qu'un aller-retour linéaire — c'est l'`ease-in-out`
     * du web, et c'est ce qui sépare une respiration d'un clignotant.
     */
    private fun breathe(elapsedSeconds: Double) {
        val halo = halo ?: return
        if (elapsedSeconds - lastBreathAt < BREATH_STEP) return
        lastBreathAt = elapsedSeconds

        val phase = (elapsedSeconds % HALO_PERIOD) / HALO_PERIOD
        val wave = (1.0 - cos(2.0 * PI * phase)) / 2.0
        halo.setProperties(
            PropertyFactory.iconSize((HALO_REST + (1f - HALO_REST) * wave.toFloat())),
            PropertyFactory.iconOpacity(
                HALO_OPACITY_REST + (HALO_OPACITY_WIDE - HALO_OPACITY_REST) * wave.toFloat(),
            ),
        )
    }

    private fun redraw() {
        val source = source ?: return
        val displayed = displayed ?: return
        val target = target ?: return

        val props = JsonObject().apply {
            addProperty(PROP_HEADING, displayed.heading)
            addProperty(PROP_HAS_HEADING, if (target.heading != null) 1 else 0)
            addProperty(PROP_FACING, displayed.facing)
            addProperty(PROP_HAS_FACING, if (target.facing != null) 1 else 0)
            // Le plancher n'est pas de la coquetterie : un GPS qui annonce un
            // mètre ne les tient pas, et un anneau plus petit que le puck se
            // lirait comme un liseré du puck plutôt que comme une incertitude.
            addProperty(
                PROP_ACCURACY_PX,
                max(target.accuracy, MIN_ACCURACY_METERS) * MapScale.pixelsPerMeter(
                    latitude = displayed.coordinate.latitude,
                    zoom = MapScale.REFERENCE_ZOOM.toDouble(),
                ),
            )
        }
        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(displayed.coordinate.longitude, displayed.coordinate.latitude),
                props,
            ),
        )
    }

    private data class Target(
        val coordinate: Coordinate,
        /** Cap de route — le marqueur. `null` quand il est gelé. */
        val heading: Double?,
        /** Cap fusionné — le cône. Tient à l'arrêt tant que la boussole répond. */
        val facing: Double?,
        val accuracy: Double,
    )

    private data class Displayed(
        val coordinate: Coordinate,
        val heading: Double,
        val facing: Double,
    )

    private companion object {
        const val ID = "aule.puck"
        const val SOURCE = "aule.puck.source"
        const val ACCURACY_LAYER = "aule.puck.accuracy"
        const val CONE_LAYER = "aule.puck.cone"
        const val HALO_LAYER = "aule.puck.halo"
        const val DOT_LAYER = "aule.puck.dot"

        const val PROP_HEADING = "heading"
        const val PROP_HAS_HEADING = "hasHeading"
        const val PROP_FACING = "facing"
        const val PROP_HAS_FACING = "hasFacing"

        /** Le rayon d'incertitude, **en pixels au zoom de référence**. */
        const val PROP_ACCURACY_PX = "accuracyPx"

        /** En dessous, un GPS annonce une précision qu'il ne tient pas. */
        const val MIN_ACCURACY_METERS = 5.0

        /**
         * La plage sur laquelle l'anneau suit l'échelle, et son rapport.
         *
         * Dix niveaux de zoom, donc un facteur mille vingt-quatre. En dehors,
         * MapLibre retient la valeur du stop le plus proche : sous le zoom
         * douze, l'anneau cesse de rétrécir — mais il y vaut déjà une fraction
         * de pixel, et personne ne verra la différence.
         */
        const val ZOOM_SPAN = 10
        const val ZOOM_FACTOR = 1024f

        /**
         * La respiration du halo, et son pas.
         *
         * L'image est peinte à son ampleur maximale : [HALO_REST] est donc la
         * part qu'elle occupe au repos, et le sommet vaut un. Les valeurs
         * viennent du web — un tiers de plus en largeur, et l'opacité qui
         * tombe des trois quarts pendant qu'il s'étale.
         */
        const val HALO_PERIOD = 2.6
        const val BREATH_STEP = 1.0 / 30.0
        const val HALO_REST = 0.62f
        const val HALO_OPACITY_REST = 0.70f
        const val HALO_OPACITY_WIDE = 0.18f

        const val ALIGNMENT_MAP = "map"

        /** Face à l'écran, quelle que soit l'inclinaison de la caméra. */
        const val ALIGNMENT_VIEWPORT = "viewport"
    }
}
