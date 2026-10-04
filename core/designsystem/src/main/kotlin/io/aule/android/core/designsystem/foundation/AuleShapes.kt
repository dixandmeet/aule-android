package io.aule.android.core.designsystem.foundation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Les rayons de la langue Aule : 12 · 16 · 24 · 28 · 40, et la capsule.
 *
 * Ils alimentent les cinq formes Material dans l'ordre — un menu prend `extraSmall`, une puce
 * `small`, une carte `medium`, un volet `large`, un dialogue `extraLarge` — et les écrans
 * n'écrivent jamais de `RoundedCornerShape` à eux.
 */
object AuleRadius {
    /**
     * Le coin **intérieur** d'une liste segmentée : entre deux rangées d'un même groupe.
     *
     * Hors des cinq crans du brief, et pour une seule raison : Material 3 Expressive sépare les
     * rangées d'un groupe par un coin à peine arrondi, et c'est ce coin-là qui fait lire le
     * groupe comme un bloc. Il ne va nulle part ailleurs — voir [AuleSegmentShapes].
     */
    val xs = 4.dp

    /** Anneau de focus, coin d'une pastille de ligne. */
    val s = 12.dp

    /** Champs, puces, petites cartes. */
    val m = 16.dp

    /** Cartes, panneaux flottants. */
    val l = 24.dp

    /** Volets. */
    val xl = 28.dp

    /** La carte du héros, la scène d'un onboarding. */
    val xxl = 40.dp
}

internal fun auleShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(AuleRadius.s),
    small = RoundedCornerShape(AuleRadius.m),
    medium = RoundedCornerShape(AuleRadius.l),
    large = RoundedCornerShape(AuleRadius.xl),
    extraLarge = RoundedCornerShape(AuleRadius.xxl),
)

/**
 * Les coins d'une liste segmentée — le groupe de rangées de Material 3 Expressive.
 *
 * Chaque rangée est sa propre surface, et le groupe se lit par ses coins : larges aux deux
 * bouts, serrés entre deux rangées. Une rangée seule est une carte. C'est la forme que prennent
 * les réglages d'Android 16, et celle de la carte d'information du web (`infoCard`), au rayon
 * des cartes.
 */
object AuleSegmentShapes {
    val single: Shape = RoundedCornerShape(AuleRadius.l)

    val first: Shape = RoundedCornerShape(
        topStart = AuleRadius.l,
        topEnd = AuleRadius.l,
        bottomStart = AuleRadius.xs,
        bottomEnd = AuleRadius.xs,
    )

    val middle: Shape = RoundedCornerShape(AuleRadius.xs)

    val last: Shape = RoundedCornerShape(
        topStart = AuleRadius.xs,
        topEnd = AuleRadius.xs,
        bottomStart = AuleRadius.l,
        bottomEnd = AuleRadius.l,
    )

    /** La forme de la rangée [index] d'un groupe qui en compte [count]. */
    fun at(index: Int, count: Int): Shape = when {
        count <= 1 -> single
        index == 0 -> first
        index == count - 1 -> last
        else -> middle
    }
}

/**
 * Les silhouettes expressives d'Aule, et leur emploi.
 *
 * Sept formes du kit, chacune pour un registre — celui que le brief leur donne. Elles ne vont
 * **jamais** sur une rangée de liste : chaque instance construit un `Path`, ce qui ne coûte rien
 * sur un médaillon d'état vide et coûterait sur trente rangées qui défilent.
 *
 * Elles sont réservées au niveau 3 d'expressivité : états vides, succès, illustrations,
 * indicateurs, moments importants. Une pastille de mode ou un bouton reste rond.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object AuleExpressiveShapes {
    /** Réseau, missions : ce qui est en mouvement. */
    val cookie7: Shape @Composable get() = MaterialShapes.Cookie7Sided.toShape()

    /** Véhicules en ligne, ce qui compte. */
    val cookie12: Shape @Composable get() = MaterialShapes.Cookie12Sided.toShape()

    /** Proximité, lieux. */
    val cookie9: Shape @Composable get() = MaterialShapes.Cookie9Sided.toShape()

    /** Confidentialité, ce qu'on garde pour soi. */
    val cookie4: Shape @Composable get() = MaterialShapes.Cookie4Sided.toShape()

    /** Ponctualité, équipe : ce qui est réussi. */
    val clover4: Shape @Composable get() = MaterialShapes.Clover4Leaf.toShape()

    /** Communauté : ce qui se partage. */
    val clover8: Shape @Composable get() = MaterialShapes.Clover8Leaf.toShape()

    /** Voyageurs, alertes actives : ce qui émet. */
    val sunny: Shape @Composable get() = MaterialShapes.Sunny.toShape()

    /** La capsule, pour mémoire : la forme des boutons et des pastilles. */
    val capsule: Shape get() = CircleShape
}
