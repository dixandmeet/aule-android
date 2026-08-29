package io.aule.android.core.map.layer

import io.aule.android.core.map.MapAmbiance
import io.aule.android.core.map.MapLayer
import io.aule.android.core.map.MapStyleAnchors
import io.aule.android.core.map.VoirieTiles
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.VectorSource

/**
 * Le référentiel de voirie de Nantes Métropole.
 *
 * Port de `dashboard/components/carte-immersive/next/layers/voirie-layer.ts`, et
 * mêmes choix : les seuils et le motif du pointillé sont **repris tels quels**, comme
 * pour les tracés.
 *
 * ## Ce que cette couche peint, et ce qu'elle ne peint pas
 *
 * Elle **ne repeint pas les rues**. Le fond les peint déjà, à des largeurs choisies
 * pour la lisibilité, et deux référentiels dont les axes s'écartent de un à trois
 * mètres ne se superposent pas sans bavure : doubler les chaussées aurait épaissi
 * chaque rue d'un liseré décalé, pour un gain nul.
 *
 * Elle porte le **statut privé**, que le fond ne dit pas. Une allée de lotissement,
 * une cour d'immeuble, une desserte fermée y sont peintes exactement comme la rue
 * publique qui les dessert — et l'on s'y engage. Un pointillé les distingue, à partir
 * du palier où l'on choisit son chemin à pied.
 *
 * ## Sa teinte est empruntée, et choisie sur mesure
 *
 * Le pointillé ne porte pas de couleur à soi : il doit se lire comme une nuance de la
 * chaussée. Il emprunte donc au style — mais **au plus contrasté** de deux jetons
 * voisins, le marquage axial et le casing de chaussée, mesuré contre la couleur de la
 * chaussée elle-même.
 *
 * ⚠️ Deux teintes fixes ont été essayées avant, et chacune a disparu quelque part.
 *
 * Le *casing piéton* d'abord, ce qui paraissait le plus juste — c'est bien un bord, pas
 * un axe. Invisible en sombre sur l'iOS : ce jeton borde une surface piétonne **claire**.
 *
 * Le *marquage axial* ensuite, qui tient sur l'iOS (×1,92 en clair, ×2,47 en sombre) et
 * sur l'Android sombre (×1,79) — et s'efface ici même, **en clair** : ×1,44, #D9D7D3 sur
 * une chaussée #FFFFFF. Relevé sur le S21, le pointillé y était un fantôme. Le casing y
 * monte à ×2,19. Les quatre styles embarqués n'ayant pas la même palette, aucun jeton
 * unique ne gagne partout — d'où le choix au moment du rendu.
 *
 * ## Sa largeur suit le ruban dessiné, pas la chaussée réelle
 *
 * Elle a elle aussi commencé par la largeur mesurée du référentiel, divisée par les
 * mètres par pixel du zoom. C'était un contresens : le style dessine les rues **plus
 * larges que nature** aux échelles moyennes, et un pointillé à l'emprise réelle tombait
 * à 1,6 px au milieu d'un ruban qui en fait 6. La largeur mesurée sert au moteur de rue
 * de la carte web, qui en a le bon usage ; ici c'est le ruban peint qu'on marque.
 *
 * @param archiveUrl l'URL `pmtiles://…` de l'archive, telle que
 *   [io.aule.android.core.map.TransitTiles.pmtilesUrl] la forme. Passée plutôt que
 *   calculée ici : ce module ne connaît pas le `Context` qui sait où le fichier a été
 *   recopié.
 */
class VoirieLayer(private val archiveUrl: String) : MapLayer {

    override val id: String = ID

    private var source: VectorSource? = null
    private var privateWays: LineLayer? = null

    override fun mount(style: Style, map: MapLibreMap) {
        val posed = VectorSource(SOURCE, archiveUrl)
        style.addSource(posed)
        source = posed

        val layer = LineLayer(PRIVATE, SOURCE).apply {
            sourceLayer = VoirieTiles.SOURCE_LAYER
            minZoom = PRIVATE_FROM_ZOOM.toFloat()
            setProperties(
                PropertyFactory.lineCap("butt"),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineColor(stripeColor(style)),
                PropertyFactory.lineDasharray(arrayOf(1f, 2f)),
                PropertyFactory.lineOpacity(PRIVATE_OPACITY),
                PropertyFactory.lineWidth(privateWidth()),
            )
            setFilter(Expression.eq(Expression.get(PROP_PRIVATE), Expression.literal(1)))
        }
        insertBelowLabels(style, layer)
        privateWays = layer
    }

    override fun unmount(style: Style) {
        style.removeLayer(PRIVATE)
        style.removeSource(SOURCE)
        forgetStyle()
    }

    /**
     * ⚠️ Sans cet oubli, la `VectorSource` gardée reste un objet JVM vivant après la
     * mort du style, et [onAmbianceChange] repeindrait une couche qui n'est plus posée
     * nulle part. MapLibre Android l'ignorerait en silence ; l'iOS, lui, lève.
     */
    override fun forgetStyle() {
        privateWays = null
        source = null
    }

    override fun onAmbianceChange(ambiance: MapAmbiance, style: Style) {
        privateWays?.setProperties(PropertyFactory.lineColor(stripeColor(style)))
    }

    /**
     * La teinte du pointillé, **lue dans le style qui vient d'être chargé**.
     *
     * Elle n'est écrite nulle part ici, et c'est le point : les couleurs de la carte
     * vivent dans les jetons du générateur (`tokens.ts`), d'où sortent les deux JSON
     * embarqués. Les recopier en Kotlin en ferait une troisième source de vérité, que
     * la prochaine retouche de palette laisserait derrière — le mode sombre garderait
     * un pointillé couleur du jour, et rien ne le dirait.
     *
     * Le repli n'est pas une couleur choisie mais un gris neutre : il ne sert que si
     * le fond n'a pas de cheminements peints, cas où le pointillé n'a de toute façon
     * personne à côté de qui se fondre.
     */
    private fun stripeColor(style: Style): Int {
        val candidats = listOfNotNull(colorOf(style, ROAD_MARKING), colorOf(style, ROAD_CASING))
        if (candidats.isEmpty()) return FALLBACK_MARKING
        val chaussee = colorOf(style, ROAD_SURFACE) ?: return candidats.first()
        return candidats.maxBy { contrast(it, chaussee) }
    }

    private fun colorOf(style: Style, layer: String): Int? = try {
        (style.getLayer(layer) as? LineLayer)?.lineColorAsInt
    } catch (_: RuntimeException) {
        // `lineColorAsInt` lève quand la couleur est une expression et non une
        // constante. Ce n'est pas le cas aujourd'hui ; ça peut le devenir.
        null
    }

    /**
     * Rapport de contraste WCAG entre deux couleurs, de 1 (identiques) à 21.
     *
     * Quelques lignes d'arithmétique plutôt qu'une dépendance : c'est le seul endroit de
     * la carte qui compare deux teintes, et les trois plateformes en portent chacune leur
     * copie — un module partagé entre du Kotlin, du Swift et du TypeScript n'existe pas.
     */
    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Int): Double {
        fun canal(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * canal(16) + 0.7152 * canal(8) + 0.0722 * canal(0)
    }

    /**
     * Deux points en pixels, calés sur le ruban que le style dessine : une rue mineure y
     * fait 6 px à z16 et 18 px à z19, le pointillé en prend le tiers. Assez pour se lire,
     * assez peu pour rester une nuance.
     */
    private fun privateWidth(): Expression = Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        Expression.stop(PRIVATE_FROM_ZOOM, 2f),
        Expression.stop(19, 6f),
    )

    /** Sous les étiquettes du fond — même raison que pour les tracés. */
    private fun insertBelowLabels(style: Style, layer: LineLayer) {
        if (style.getLayer(MapStyleAnchors.BELOW_LABELS) != null) {
            style.addLayerBelow(layer, MapStyleAnchors.BELOW_LABELS)
        } else {
            style.addLayer(layer)
        }
    }

    companion object {
        const val ID: String = "voirie"

        private const val SOURCE = "voirie"
        private const val PRIVATE = "voirie-private"

        /** L'attribut du statut privé, tel que `build-voirie.mjs` l'écrit. */
        private const val PROP_PRIVATE = "p"

        /**
         * Palier d'apparition du pointillé.
         *
         * z16 : l'échelle où la carte cesse de montrer un quartier pour montrer des
         * rues, et où savoir qu'une voie est privée change ce qu'on en fait.
         */
        private const val PRIVATE_FROM_ZOOM = 16

        /** Le motif suffit à la discrétion : le diluer par-dessus l'effaçait. */
        private const val PRIVATE_OPACITY = 0.9f

        /** Les couches du fond dont le pointillé emprunte sa teinte, et celle qu'il tranche. */
        private const val ROAD_MARKING = "road-minor-marking"
        private const val ROAD_CASING = "road-minor-casing"
        private const val ROAD_SURFACE = "road-minor"

        /** Gris neutre, si le fond ne peint ni marquage ni casing. */
        private const val FALLBACK_MARKING = 0xFF8A8A8A.toInt()
    }
}
