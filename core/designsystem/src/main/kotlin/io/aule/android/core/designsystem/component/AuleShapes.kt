package io.aule.android.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

/**
 * Les formes expressives d'Aule.
 *
 * Material 3 Expressive livre une trentaine de polygones arrondis —
 * [MaterialShapes] — qui ne sont pas des rectangles à coins ronds : ce sont de
 * vraies silhouettes, dessinées comme telles, et c'est le seul endroit du kit
 * où une forme peut porter du sens à elle seule.
 *
 * ## Pourquoi une liste courte
 *
 * Le kit en propose trente ; Aule en retient **trois**. Une application qui les
 * emploierait toutes ressemblerait à une planche de gabarits : la silhouette
 * cesse de signifier dès qu'elle est partout. Chacune de celles-ci a un emploi
 * et un seul, et un quatrième emploi devra d'abord montrer pourquoi les trois
 * existantes ne conviennent pas.
 *
 * ## Le coût, qu'il faut connaître
 *
 * Un polygone arrondi n'est pas gratuit comme l'est un `RoundedCornerShape` :
 * chaque forme construit un `Path`, et le découpage d'une surface sur un `Path`
 * arbitraire est plus cher que sur un rectangle. C'est sans conséquence sur une
 * pastille de 40 dp affichée quelques dizaines de fois ; ça en aurait sur une
 * liste qui défile vite. Les trois formes ci-dessous sont donc réservées aux
 * éléments **ponctuels** — jamais au conteneur d'une rangée de liste.
 */
object AuleShape {

    /**
     * La pastille d'un mode de transport.
     *
     * Neuf lobes doux : de loin c'est un rond, de près c'est une fleur. C'est
     * exactement le registre visé — la liste « Autour de vous » ne doit pas
     * avoir l'air décorée, elle doit avoir l'air dessinée. Le rond parfait
     * qu'elle portait avant est ce qu'on obtient quand personne n'a tranché.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun modeAvatar(): Shape = MaterialShapes.Cookie9Sided.toShape()

    /**
     * Ce qui signale un état vivant : le véhicule suivi, la position tenue.
     *
     * Le soleil est la seule forme du kit qui a une **direction** sans avoir de
     * pointe. Elle dit « ça émet » là où un rond dirait « ça existe ».
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun live(): Shape = MaterialShapes.Sunny.toShape()

    /**
     * La forme d'un chiffre qui domine son écran.
     *
     * Un trèfle à quatre feuilles pour un compteur, ce serait ridicule ; pour le
     * cartouche qui porte le temps restant d'une tournée, à l'endroit exact où
     * l'œil du conducteur revient toutes les vingt secondes, c'est le repère
     * qu'on retrouve sans lire.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun hero(): Shape = MaterialShapes.Clover4Leaf.toShape()
}
