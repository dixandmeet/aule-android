package io.aule.android.core.map.camera

/**
 * Ce que les bâtiments ont le droit de prendre à l'écran.
 *
 * La 3D n'est pas un décor : c'est elle qui fait reconnaître un endroit où
 * l'on est déjà passé, et elle reste allumée en toute circonstance. Mais
 * elle est **seconde**. Une façade proche, à l'inclinaison de la
 * navigation, occupe le quart de l'écran et masque exactement ce qu'on est
 * venu chercher : la rue qui vient, le carrefour d'après, l'arrêt sur le
 * trottoir d'en face.
 *
 * La réponse n'est pas de couper les volumes — une carte à plat perd le
 * repère qui sert le plus — mais de **doser leur présence** selon ce que
 * l'utilisateur est en train de faire. Trois niveaux suffisent, et le
 * moteur anime lui-même le passage de l'un à l'autre.
 */
object BuildingEmphasis {

    /**
     * L'exploration : les volumes sont pleins, à un voile près.
     *
     * Ce voile n'est pas de la coquetterie. Un aplat parfaitement opaque
     * coupe net la rue qui passe derrière, alors qu'à 92 % on devine qu'elle
     * continue — et c'est ce « on devine » qui distingue une ville d'un
     * empilement de blocs.
     */
    const val FULL = 0.92

    /**
     * Le guidage : les volumes reculent d'un quart.
     *
     * On ne regarde plus la ville, on suit un trajet. Les bâtiments gardent
     * leur hauteur et leur ombre — donc le repère — mais laissent lire les
     * routes qu'ils croisaient.
     */
    const val GUIDED = 0.7

    /**
     * L'approche d'un carrefour : le minimum qu'on s'autorise.
     *
     * C'est le seul moment où la géométrie d'une intersection compte plus
     * que tout le reste. À la moitié, les volumes proches deviennent des
     * silhouettes : on voit à travers eux la branche qu'on doit prendre,
     * sans que la ville disparaisse pour autant.
     */
    const val JUNCTION = 0.45

    /** Le suivi d'un véhicule : à peine atténué, on regarde la rue avec lui. */
    const val VEHICLE = 0.85

    /**
     * L'écart en deçà duquel on ne réécrit pas la couche.
     *
     * L'imminence d'un carrefour est un nombre continu, réévalué quinze fois
     * par seconde : écrit tel quel, il repeindrait toutes les extrusions de
     * l'écran à chaque battement. On quantifie donc, et le moteur interpole
     * entre deux valeurs écrites.
     */
    const val STEP = 0.04

    /**
     * Le niveau qui convient à la situation.
     *
     * ⚠️ **L'atténuation suit le guidage, pas le mode de caméra.** Un doigt
     * posé sur la carte fait passer la caméra en exploration libre sans rien
     * annuler du trajet : rendre les bâtiments pleins à cet instant
     * masquerait la route qu'on est justement en train de vérifier.
     *
     * @param guiding vrai tant qu'un trajet est engagé, geste ou pas.
     * @param followingVehicle vrai quand la caméra est accrochée à un véhicule.
     * @param maneuverFocus l'imminence de la prochaine manœuvre, de 0 à 1.
     */
    fun of(
        guiding: Boolean,
        followingVehicle: Boolean = false,
        maneuverFocus: Double = 0.0,
    ): Double {
        val focus = maneuverFocus.coerceIn(0.0, 1.0)
        return when {
            guiding -> GUIDED + (JUNCTION - GUIDED) * focus
            followingVehicle -> VEHICLE
            else -> FULL
        }
    }
}
