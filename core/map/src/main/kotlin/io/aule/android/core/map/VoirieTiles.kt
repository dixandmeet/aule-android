package io.aule.android.core.map

/**
 * D'où vient le référentiel de voirie de Nantes Métropole, et comment il s'appelle
 * dans l'archive.
 *
 * **Une archive embarquée, comme les tracés.** Les 40 496 tronçons de la métropole
 * tiennent dans 1,6 Mo de tuiles vectorielles produites une fois pour toutes
 * (`dashboard/tools/tiles/build-voirie.sh`) : largeur mesurée, famille et statut y
 * sont figés au build. C'est le même fichier que la carte web et l'application iOS,
 * **au bit près** — voir `app/src/main/assets/tiles/README.md`.
 *
 * ## Ce que ce jeu apporte, et ce qu'il ne remplace pas
 *
 * Le fond OpenMapTiles peint les rues à des largeurs choisies pour la lisibilité,
 * et il couvre la France entière. Le référentiel, lui, s'arrête aux limites de la
 * métropole mais donne la largeur **mesurée** de chaque tronçon, sa nature exacte
 * et son statut — public ou privé. Il vient donc **par-dessus**, sans jamais se
 * substituer au fond : hors emprise, la source est simplement vide et rien ne
 * change. C'est ce qui permet de l'embarquer sans conditionner la carte à lui.
 *
 * L'URL se forme avec [TransitTiles.pmtilesUrl] : le piège du `pmtiles://` suivi
 * d'un `file://` percent-encodé est le même ici, et il n'a pas à être décrit deux
 * fois.
 */
object VoirieTiles {

    /**
     * Le nom de la couche dans l'archive (`--named-layer` de tippecanoe). Il est
     * partagé avec le web (`VOIRIE_SOURCE_LAYER` de `style/tile-source.ts`) et
     * l'iOS : il change des trois côtés ou d'aucun.
     */
    const val SOURCE_LAYER = "voirie"

    /** Le chemin de l'archive dans les assets. */
    const val ASSET_PATH = "tiles/voirie.pmtiles"

    /** Le nom sous lequel elle est recopiée — le même, pour qu'on la reconnaisse. */
    const val CACHED_FILE_NAME = "voirie.pmtiles"
}
