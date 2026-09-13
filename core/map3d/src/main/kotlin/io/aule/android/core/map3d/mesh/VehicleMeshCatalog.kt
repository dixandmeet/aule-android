package io.aule.android.core.map3d.mesh

/**
 * Le catalogue des modèles 3D.
 *
 * **Une entrée par mode qui en a un, et aucune erreur pour les autres.** Un mode
 * sans modèle n'est pas un défaut : c'est un véhicule qui garde son volume
 * extrudé, comme le navibus — le pack n'en contient pas de bateau.
 */
internal object VehicleMeshCatalog {

    /**
     * Un modèle du pack, et de quoi l'orienter.
     *
     * [forwardIsPositiveZ] dit si le nez pointe vers **+Z** dans le fichier,
     * après le redressement des modèles exportés en largeur.
     *
     * ⚠️ **Les deux modèles ne suivent pas la même convention**, et cela ne se
     * voit qu'en mouvement — un véhicule à l'envers recule sagement le long de sa
     * voie. Ce qui tranche est dans les fichiers, pas à l'œil : constaté côté
     * iOS, qui a payé la découverte.
     */
    data class Model(
        val asset: String,
        val dimensions: MeshDimensions,
        val forwardIsPositiveZ: Boolean,
        /**
         * La teinte de la carrosserie, en plein jour.
         *
         * ⚠️ **Ce n'est pas `markerColor` du design system, et c'est délibéré.**
         * La pastille plate est un aplat : elle peut être sombre sans rien
         * perdre. Un modèle, lui, ne se lit que par le **contraste entre sa
         * caisse et ses pièces** — vitrage et châssis presque noirs.
         * Peint du teal `0x0D595E` de la pastille tram, l'ensemble devient un
         * bloc noir où ni vitre ni roue n'apparaît : tout le détail qu'on est
         * allé chercher disparaît.
         *
         * Ces valeurs sont donc celles du web (`partColor` de `vehicles-3d.ts`),
         * qui a tranché la même chose à l'écran : « une livrée blanche
         * *réaliste* disparaissait sur la chaussée claire — la lisibilité prime
         * sur la fidélité de flotte ».
         *
         * La nuit ne les assombrit plus : c'est la lumière de la scène qui
         * baisse, avec celle des façades — voir `VehicleLighting`.
         */
        val bodyColor: Int,
        val materialParts: Map<String, MeshPart> = emptyMap(),
    )

    /** Le répertoire des `.glb` dans les assets. */
    const val ASSET_DIR = "models/vehicles"

    /**
     * Les cotes sont les gabarits **réels**, repris du web qui les tient de sa
     * V0 : un bus articulé nantais mesure onze mètres, un Incentro vingt-huit.
     * C'est ce qui rend la 3D juste là où la 2D ne pouvait pas l'être — un objet
     * posé au sol en mètres garde sa taille physique quand on zoome, alors
     * qu'une silhouette en points d'écran rétrécit relativement à la rue.
     */
    val BUS = Model(
        asset = "bus.glb",
        dimensions = MeshDimensions(widthMeters = 2.55, heightMeters = 3.2, lengthMeters = 11.0),
        // Les roues sont deux maillages nommés, `FrontWheels` centré sur x = 0,34
        // et `BackWheels` sur x = 3,19 : l'avant est du côté des x faibles, qui
        // devient −Z après redressement.
        forwardIsPositiveZ = false,
        bodyColor = 0x45C299,
        // ⚠️ **Deux matériaux que leur nom fait mentir**, et c'est mesuré dans le
        // fichier, pas supposé :
        //
        // | matériau | hauteur | longueur | aire |
        // |---|---|---|---|
        // | `Bottom` | 0,23 → 1,65 m | 0,2 → 10,9 m | 3,9 |
        // | `Bumper` | 0,23 → 2,73 m | 0,1 → 11,0 m | 8,1 |
        // | `Top` | 0,96 → 3,20 m | 0,2 → 10,9 m | 12,4 |
        //
        // `Bottom` n'est pas un bas de caisse mais **le panneau latéral inférieur
        // sur toute la longueur** ; `Bumper` n'est pas un pare-chocs mais la
        // deuxième surface du modèle. Les laisser au châssis peint **la moitié du
        // bus en presque noir** : une coque sombre surmontée d'une verrière, ce
        // qu'on a vu à l'écran le 12/09. Le web fait cette erreur aussi ; elle s'y
        // remarque moins parce que sa flotte est plus transparente et son ombrage
        // plus plat.
        materialParts = mapOf(
            "Bottom" to MeshPart.BODY,
            "Bumper" to MeshPart.BODY,
        ),
    )

    val TRAM = Model(
        asset = "tram.glb",
        dimensions = MeshDimensions(widthMeters = 2.65, heightMeters = 3.35, lengthMeters = 28.0),
        // Le vitrage atteint z = 1,55 quand la caisse s'arrête à 1,52 — un
        // pare-brise affleurant à la pointe. À l'autre bout, les six derniers
        // décimètres sont de la caisse pleine, sans une vitre : la cabine est
        // donc en +Z. Le tram, plus long, est d'un ton plus profond que le bus.
        forwardIsPositiveZ = true,
        bodyColor = 0x2F9D80,
    )

    val ALL = listOf(BUS, TRAM)
}
