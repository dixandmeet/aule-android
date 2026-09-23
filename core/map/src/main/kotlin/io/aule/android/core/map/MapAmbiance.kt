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
 * ⚠️ **Ils l'ont été deux fois, et la seconde s'est payée le 29/08/2026.** Cette
 * note affirmait que la palette du cockpit était remontée dans les jetons du
 * générateur et que ces fichiers en étaient redevenus une simple sortie. C'était
 * faux : ils portaient encore, *à la main*, une palette entière — 37 couches
 * repeintes — et **quatre couches que le générateur ne sait pas produire**,
 * `road-busway-casing`, `road-busway`, `road-busway-marking` et `cycleway`, soit
 * précisément les « voies réservées et pistes cyclables sorties des couches où
 * elles étaient noyées » que la note donnait pour acquises. Le générateur, lui,
 * range toujours `busway` avec les primaires et les cycleways avec les
 * cheminements.
 *
 * La régénération demandée les a donc effacées, en connaissance de cause. **Ce
 * travail n'est pas perdu, il est dans l'historique** : `git show 16099b8` rend
 * les deux JSON tels qu'ils étaient. Le remonter suppose de le porter dans
 * `build-style.ts`, et d'accepter qu'il s'applique aussi à l'iOS, au Flutter et
 * au web — les quatre cibles partagent la variante `cockpit`.
 *
 * Ces fichiers sont **maintenant** une sortie, pour de bon :
 *
 *     node --experimental-strip-types scripts/build-map-style.mjs \
 *       --out ../Kotlin/app/src/main/assets/map --pretty
 *
 * les régénère à l'identique. Rien ne le vérifie encore côté Android :
 * `map-style-assets.test.mjs` ne compare que les assets du Flutter. Une retouche
 * à la main repasserait donc en silence — c'est ainsi que la précédente a duré.
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

    /**
     * Le seuil où un **nom** d'arrêt cesse d'être ambigu.
     *
     * Une pastille apparaît bien avant son nom, et c'est délibéré : un point
     * n'appartient qu'à lui-même, un nom doit désigner un point et un seul.
     * Écrit trop tôt, il tombe à mi-chemin de deux arrêts, et le plan se met à
     * mentir — « St-Donatien » lu sur la pastille de Chalâtres. Signalé à
     * l'écran sur la C1, et c'est ce qui a fixé cette constante.
     *
     * ## La mesure
     *
     * Le nom se pose à 1,25 em de sa pastille et occupe environ 1,2 em de haut :
     * son centre est donc à ~1,85 em, et il faut le double d'espacement entre
     * deux arrêts pour qu'il reste plus près du sien que du suivant. À la
     * latitude de Nantes, ça donne :
     *
     * | zoom | il faut, entre deux arrêts |
     * |---|---|
     * | 12 | 1 020 m |
     * | 13 | 540 m |
     * | 14 | 285 m |
     * | **14,5** | **207 m** |
     * | 15 | 150 m |
     *
     * Un arrêt de bus urbain succède au précédent tous les 250 à 400 mètres —
     * 330 en moyenne sur la C1. Quatorze et demi est donc le premier palier qui
     * passe, y compris sur les enfilades du centre-ville ; à quatorze, une
     * portion serrée sur deux reste douteuse.
     *
     * Les deux couches d'arrêts s'y tiennent — le catalogue et la desserte d'une
     * ligne — pour que fermer un volet ne fasse ni apparaître ni sauter un nom.
     */
    const val STOP_LABELS_FROM = 14.5

    const val VEHICLES_FROM = 12.0
    const val VEHICLE_ICONS_FROM = 14.0

    /**
     * Le volume des véhicules, sur la même rampe que celle des bâtiments.
     *
     * Le style lève le relief de la ville à quinze et le rend plein à quinze et
     * demi ; un bus en volume au-dessus d'une ville encore plate flotterait sans
     * sol. Les deux montent donc ensemble : c'est le **milieu** du fondu, qui
     * court d'un `BODY_FADE` de part et d'autre (14,9 → 15,5), si bien que les
     * caisses sont pleines exactement quand les bâtiments le sont.
     *
     * ⚠️ Il valait quinze et demi — le seuil du web — et le fondu se terminait
     * donc à 15,8. Au cadre du quartier ([NEIGHBOURHOOD], 15,5), la flotte
     * s'ouvrait **à moitié transparente**, mi-glyphe mi-volume.
     */
    const val VEHICLE_BODIES_FROM = 15.2

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
     * Le cadre du quartier : l'ouverture du Voyageur.
     *
     * Un niveau sous [OPENING], soit **deux fois plus large** — un peu moins
     * d'un demi-kilomètre au premier plan à Nantes, et bien davantage vers
     * l'horizon avec l'inclinaison. Aule Pro garde [OPENING] : un conducteur
     * regarde sa rue ; un voyageur qui ouvre la carte demande d'abord ce qui
     * passe autour de lui, les arrêts d'à côté et les véhicules qui y vont.
     * « Beaucoup trop proche », signalé à l'écran le 23/09/2026.
     *
     * C'est le plus bas qu'on puisse descendre sans perdre l'identité de la
     * carte : les bâtiments y sont pleins, les caisses aussi
     * ([VEHICLE_BODIES_FROM]), et l'inclinaison est entière
     * (`PITCH_FULL_ZOOM`). Un cran plus bas, la ville s'aplatit.
     */
    const val NEIGHBOURHOOD = 15.5

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
