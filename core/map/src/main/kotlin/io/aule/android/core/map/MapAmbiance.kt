package io.aule.android.core.map

/**
 * L'ambiance de la carte.
 *
 * Les deux styles sont **embarqués** dans les assets et jamais téléchargés : la
 * carte doit se peindre même sans réseau, et un style servi par un serveur plus
 * ancien repeindrait la carte conducteur avec les mauvaises couleurs. Ils sont
 * générés par `dashboard/lib/carte-immersive/style/build-style.ts` et copiés tels
 * quels — on ne les retouche pas à la main.
 *
 * ⚠️ **Ils l'ont été, et ça se paie.** La palette du cockpit a longtemps vécu
 * ici seule, repeinte directement dans ces JSON : le générateur ne savait plus
 * les produire, et une régénération aurait effacé le travail. Elle est
 * désormais **dans les jetons** du générateur (`COCKPIT_DAY_BASEMAP`,
 * `COCKPIT_NIGHT_BASEMAP`), avec la refonte de lisibilité qui l'accompagne —
 * chaussées élargies aux échelles où la caméra travaille, voies réservées et
 * pistes cyclables sorties des couches où elles étaient noyées, volumes dosés.
 * Ces fichiers sont donc, à nouveau, une **sortie** :
 *
 *     node --experimental-strip-types scripts/build-map-style.mjs --pretty
 *
 * les régénère à l'identique, et un test du tableau de bord échoue si l'un des
 * deux dérive.
 */
enum class MapAmbiance(val assetPath: String) {
    LIGHT("asset://map/style-light.json"),
    DARK("asset://map/style-dark.json");

    companion object {
        fun of(night: Boolean): MapAmbiance = if (night) DARK else LIGHT
    }
}

object MapStyleAnchors {
    /**
     * Les tracés s'insèrent **sous** les étiquettes : une ligne de bus peinte
     * par-dessus le nom des rues rend la carte illisible là où elle doit l'être
     * le plus.
     */
    const val BELOW_LABELS = "label-water"
}

/** Les seuils de zoom d'apparition, repris du proto iOS. */
object MapZoom {
    /** En dessous, les arrêts encombrent plus qu'ils n'informent. */
    const val STOPS_FROM = 13.0

    /** Les quais n'apparaissent qu'une fois qu'on est à l'échelle du trottoir. */
    const val QUAYS_FROM = 17.5

    const val VEHICLES_FROM = 12.0
    const val VEHICLE_ICONS_FROM = 14.0

    /**
     * Le volume des véhicules, au même seuil que celui des bâtiments.
     *
     * Le style lève le relief de la ville à quinze et le rend plein à quinze et
     * demi ; un bus en volume au-dessus d'une ville encore plate flotterait sans
     * sol. Les deux montent donc ensemble — c'est aussi le seuil du web, pour que
     * les deux cartes basculent au même moment.
     */
    const val VEHICLE_BODIES_FROM = 15.5

    /**
     * L'ouverture : ce que la carte montre au premier regard.
     *
     * Elle a reculé d'un demi-niveau — de dix-sept à seize et demi — et ce
     * demi-niveau vaut **quarante pour cent de largeur en plus**. À dix-sept
     * on était à l'échelle du trottoir : deux façades, un bout de chaussée,
     * et rien pour dire dans quelle rue on se trouvait. La question à
     * laquelle une carte qui s'ouvre doit répondre n'est pas « où sont mes
     * pieds » mais « où suis-je » — il y faut plusieurs rues, les carrefours
     * d'à côté, les arrêts qui les bordent.
     *
     * Elle reste **au-dessus de [VEHICLE_BODIES_FROM]** : les volumes des
     * bâtiments comme ceux des véhicules sont pleins, et l'identité de la
     * carte tient.
     */
    const val OPENING = 16.5

    /**
     * L'inclinaison de la maison.
     *
     * Celle de l'ouverture, et celle de tout ce qui se pose sur un lieu : la
     * carte d'Aule se regarde en volume, et une arrivée à plat sur un écran qui
     * s'incline partout ailleurs se lit comme une autre application.
     *
     * Elle s'est **relevée de sept degrés**. À cinquante-neuf, la caméra
     * rasait le sol : les façades du premier plan montaient jusqu'au tiers
     * haut de l'écran et cachaient la rue suivante, et le réseau routier se
     * lisait en fuyante plutôt qu'en plan. Cinquante-deux garde tout le
     * volume — on voit les toits, les décrochés, les ombres — et rend le
     * tracé des rues.
     */
    const val PITCH_3D = 52.0

    /**
     * Le cadre le plus large qu'on s'autorise en désignant un lieu.
     *
     * Venu de l'échelle de l'agglomération — une adresse cherchée à l'autre
     * bout de la ville —, on ne redescend pas plus bas que ça : en dessous,
     * l'arrêt se perd dans le quartier. C'est l'ancien cran « quartier »,
     * devenu une **borne** plutôt qu'une destination : se poser toujours au
     * même niveau reculait la carte de quelqu'un qui était déjà au bon
     * endroit.
     */
    const val SELECTION_MIN = 16.2

    /**
     * Le plus serré, dans le même geste.
     *
     * Un lieu désigné depuis une carte déjà proche ne doit **pas** faire
     * plonger la caméra dessus : on garde le cadre courant tant qu'il tient
     * dans ces bornes. C'est ce qui fait qu'une sélection se lit comme un
     * rapprochement et non comme un saut.
     */
    const val SELECTION_MAX = 17.2
}

/**
 * Ce qu'un crédit de carte désigne.
 *
 * Quatre sources, quatre rôles distincts : sans ce genre, la liste se lirait
 * comme quatre noms empilés dont on ne sait pas ce qu'ils ont fait.
 */
enum class LegalNoticeKind {
    /** Les données géographiques elles-mêmes. */
    BASEMAP,

    /** Qui sert les tuiles. */
    TILES,

    /** Le schéma dont le style est dérivé. */
    SCHEMA,

    /** Les horaires, les lignes et les arrêts. */
    TRANSIT,
}

/**
 * Une mention à afficher.
 *
 * [credit] est le crédit **tel que la licence l'exige** : il ne se traduit pas
 * et ne s'abrège pas, et c'est pourquoi il vit ici et non dans les ressources
 * (ADR-011 vaut pour ce que nous formulons, pas pour ce qu'on nous impose).
 * Seul l'intitulé du genre est à nous.
 */
data class LegalNotice(
    val kind: LegalNoticeKind,
    val credit: String,
    val licence: String?,
    val url: String?,
)

/**
 * Ce que l'application doit créditer, et qui n'est pas facultatif.
 *
 * Le logo et l'attribution natifs de MapLibre sont éteints dans
 * [MapController.attach] pour que la carte reste le produit. **Les éteindre
 * oblige à porter la mention ailleurs** : la licence ODbL d'OpenStreetMap
 * demande un crédit visible ou atteignable en un geste, et c'est une obligation
 * de licence, pas une politesse. Ce geste est la pastille ⓘ du HUD.
 *
 * Le motif est celui de Plans et de Google Maps sur téléphone : sur un écran de
 * cette taille, une ligne de crédits permanente mange la carte — aux plus
 * grandes tailles d'accessibilité, elle en occupait un tiers côté iOS. Un ⓘ qui
 * ouvre la liste complète respecte la licence **et** l'écran.
 */
val MAP_LEGAL_NOTICES: List<LegalNotice> = listOf(
    LegalNotice(
        kind = LegalNoticeKind.BASEMAP,
        credit = "© les contributeurs OpenStreetMap",
        licence = "ODbL",
        url = "https://www.openstreetmap.org/copyright",
    ),
    LegalNotice(
        kind = LegalNoticeKind.TILES,
        credit = "OpenFreeMap",
        licence = null,
        url = "https://openfreemap.org",
    ),
    LegalNotice(
        kind = LegalNoticeKind.SCHEMA,
        credit = "OpenMapTiles",
        licence = "BSD 3-Clause",
        url = "https://openmaptiles.org",
    ),
    LegalNotice(
        kind = LegalNoticeKind.TRANSIT,
        credit = "Nantes Métropole — données ouvertes",
        licence = null,
        url = "https://data.nantesmetropole.fr",
    ),
)
