package io.aule.android.core.designsystem.token

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Espacements, sur une base de 4. */
object AuleSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object AuleRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 18.dp
    val xl = 24.dp
    val pill = 999.dp
}

object AuleTouch {
    /**
     * Le plancher tactile de la maison, tenu partout dans le dépôt.
     *
     * 44 dp et non les 48 dp de Material : c'est la valeur déjà éprouvée sur les
     * autres surfaces Aule, et un test balaie les écrans pour la vérifier.
     */
    val minimum = 44.dp
}

/**
 * Les hauteurs de contrôle.
 *
 * [minimum] est un **plancher**, pas une taille : un bouton principal posé à
 * 44 dp est atteignable et pourtant timide. Nommer les deux paliers évite
 * l'arbitrage au cas par cas, celui qui a donné 52 ici et 48 trois écrans plus
 * loin.
 */
object AuleControl {
    /** Bouton principal, barre de recherche. */
    val height = 52.dp

    /**
     * Champ à libellé flottant. Le libellé monte de 12 dp au-dessus de la
     * saisie ; en dessous de 60 dp, l'un des deux touche le bord.
     */
    val field = 60.dp

    /**
     * La grille d'icône, commune aux trois plateformes
     * (`dashboard/docs/carte-immersive/08-style-graphique.md`, § 6).
     */
    val icon = 24.dp

    /** La pastille d'identité : deux initiales, lisibles sans être un portrait. */
    val avatar = 52.dp

    /** La pastille caméra / crayon collée au portrait. */
    val avatarBadge = 22.dp

    /** La case à cocher des conditions d'utilisation. */
    val check = 22.dp
}

/**
 * Les épaisseurs de trait.
 *
 * [glyph] appartient à la grille d'icône : 1,75 dp sur 24 dp est ce qui rend la
 * famille reconnaissable. Le dessiner en **pixels** — ce que faisait la barre
 * de recherche — donne un trait deux fois trop fin sur un écran dense.
 */
object AuleStroke {
    /** Contour au repos, séparation. */
    val hairline = 1.dp

    /** Contour d'un champ actif ou en erreur : il doit se voir sans crier. */
    val emphasis = 1.4.dp

    /** Le trait de la famille d'icônes. */
    val glyph = 1.75.dp
}

/**
 * Cinq hauteurs d'ombre, pas plus.
 *
 * L'ombre dit à quelle distance de la carte flotte une surface ; cinq distances
 * suffisent à ranger tout ce que l'app pose au-dessus d'elle. La valeur de nuit
 * est plus haute à dessein : sur un fond sombre une ombre noire disparaît, et
 * c'est l'étalement qui redonne le décollement.
 */
enum class AuleElevation(private val light: Dp, private val dark: Dp) {
    /** Posé à même la surface. */
    NONE(0.dp, 0.dp),

    /** Ce qui s'appuie sur un bord : la barre d'arrivée. */
    RESTING(8.dp, 10.dp),

    /** Ce qui flotte au-dessus de la carte : pastilles, boutons, recherche. */
    FLOATING(10.dp, 14.dp),

    /** Le volet, qui recouvre une part de l'écran. */
    LIFTED(16.dp, 20.dp),

    /** Ce qui prend l'écran : la carte de connexion. */
    OVERLAY(22.dp, 28.dp);

    fun height(night: Boolean): Dp = if (night) dark else light

    companion object {
        /** L'échelle, du plus posé au plus haut. L'ordre porte la hiérarchie. */
        val ladder: List<AuleElevation> = listOf(NONE, RESTING, FLOATING, LIFTED, OVERLAY)
    }
}

/**
 * Les opacités nommées.
 *
 * Une opacité écrite à la main est une couleur inventée : `0,10` ici et `0,12`
 * trois lignes plus bas produisent deux aplats teintés différents que personne
 * ne saura départager en revue.
 */
object AuleAlpha {
    /** Un contrôle qui ne répond plus. */
    const val DISABLED = 0.45f

    /** L'aplat d'un bouton secondaire, d'un bandeau d'alerte. */
    const val TINT = 0.12f

    /** Le contour d'un aplat teinté. */
    const val OUTLINE = 0.30f

    /** Un fond posé sur la carte, qui la laisse deviner. */
    const val VEIL = 0.72f

    /** La poignée du volet, l'attribution : présent, jamais lu en premier. */
    const val SUBDUED = 0.35f
}

/**
 * Les durées d'animation.
 *
 * Elles viennent de `dashboard/docs/carte-immersive/08-style-graphique.md` et
 * sont communes aux trois plateformes : c'est ce qui fait qu'Aule bouge pareil
 * partout.
 */
object AuleMotion {
    /** Un déplacement ordinaire : volet, apparition de carte. */
    const val GLIDE_MS = 300

    /** Une réponse au doigt : sélection, bascule. */
    const val POP_MS = 220

    /** L'entrée dans un mode de caméra. Au-delà, on ne l'anime plus. */
    const val CAMERA_ENTRY_MS = 550

    /** La pulsation du point temps réel. */
    const val PULSE_MS = 1800

    /** Un tour du témoin d'attente. */
    const val SPIN_MS = 900
}
