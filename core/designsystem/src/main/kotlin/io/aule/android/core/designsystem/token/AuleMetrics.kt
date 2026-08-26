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

/**
 * Les rayons, sur cinq crans.
 *
 * Ils alimentent directement les cinq formes de Material 3 — `extraSmall` à
 * `extraLarge` — dans l'ordre. Material sait déjà quelle forme va à quel
 * composant : le menu prend le plus petit cran, le chip le suivant, la carte le
 * médian, le volet et le dialogue les deux derniers. Renseigner l'échelle
 * suffit donc à arrondir l'application entière.
 *
 * L'échelle a été **ouverte** au passage à Material 3 Expressive. Une carte à
 * 18 dp de rayon est une carte correcte ; à 22 dp elle devient une forme, et
 * c'est cette différence-là qu'on lit comme du soin. Expressive pousse la
 * même logique : ses composants sont plus ronds que ceux de Material 3
 * classique, et une application qui garde les anciens rayons sous un thème
 * expressif se retrouve à mi-chemin — le pire endroit.
 */
object AuleRadius {
    val sm = 10.dp
    val md = 14.dp
    val lg = 22.dp
    val xl = 28.dp

    /** Le cran des dialogues et des volets. */
    val xxl = 34.dp

    val pill = 999.dp
}

object AuleTouch {
    /**
     * Le plancher tactile de la maison, tenu partout dans le dépôt.
     *
     * 48 dp : la valeur de Material, et celle que retiennent les
     * recommandations d'accessibilité Android. Aule tenait 44 dp, hérité des
     * autres surfaces ; c'est quatre points de moins sous le pouce sur chaque
     * bouton d'une application qu'on utilise debout, en mouvement, dans un
     * véhicule. Material fixe par ailleurs ses propres cibles à 48 dp, donc
     * descendre en dessous ne gagnait même pas la compacité recherchée.
     */
    val minimum = 48.dp
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
 * Ce qui flotte au-dessus de la carte, un cran sous les contrôles d'un écran.
 *
 * [AuleControl] mesure des contrôles posés **dans** une page : un bouton de
 * formulaire, un champ, une case. Ils ont la page pour eux, et leur générosité
 * est ce qui rend la page confortable. Le chrome de la carte est l'inverse : il
 * est posé **sur** le contenu, et chaque point qu'il prend est un point de ville
 * qu'on ne voit plus. Aux tailles de [AuleControl], six entrées de menu et une
 * barre de recherche mangeaient la moitié de l'écran — un fond de carte réduit à
 * une bande entre deux empilements de pastilles.
 *
 * D'où une deuxième échelle, plus serrée d'un cran à chaque niveau. Elle ne
 * descend pas sous le plancher tactile pour autant : [bar] **est** le plancher,
 * et [pill] s'appuie sur l'agrandissement de cible que Material pose lui-même
 * autour des surfaces cliquables — la pastille se voit à 32 dp et se touche à
 * 48. C'est la seule façon d'être à la fois plus petit et pas moins atteignable.
 */
object AuleChrome {
    /**
     * Une barre, une entrée de menu déplié, le bouton qui le déplie.
     *
     * 48 dp, soit le plancher tactile exactement : la valeur la plus basse qui
     * ne coûte rien au doigt. Material pose les siens à 56 — la barre de
     * recherche, l'entrée de menu flottant, le bouton d'action — et ces huit
     * points, répétés sur sept surfaces empilées dans le même écran, faisaient
     * cinquante-six points de carte en moins.
     */
    val bar = 48.dp

    /**
     * Un bouton de vue, qui commande la carte sans rien engager.
     *
     * Il reste sous [bar] à dessein : le cadrage n'est pas une action au même
     * rang que celles du menu, et la différence de taille est ce qui le dit
     * avant la couleur. C'est aussi la taille du *small FAB* de Material, donc
     * celle que le composant prend déjà tout seul.
     */
    val button = 40.dp

    /**
     * Une pastille : un état qu'on lit, une mention qu'on atteint.
     *
     * Elle ne porte jamais d'action irréversible — au pire elle ouvre un volet —
     * et c'est ce qui autorise à la dessiner sous le plancher : la cible, elle,
     * reste à 48 dp, agrandie par Material autour d'une surface cliquable.
     */
    val pill = 32.dp

    /**
     * Le glyphe d'une pastille.
     *
     * La grille de 24 de [AuleControl.icon] remplirait les 32 dp bord à bord ;
     * ce qui reste ici est un glyphe **dans** une pastille, pas une icône à sa
     * taille de famille.
     */
    val pillGlyph = 18.dp
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
 *
 * [RESTING] a été **abaissé** de 8 à 6 dp au passage : il porte ce qui s'appuie
 * sur un bord, et une ombre trop haute sur une barre collée en bas se lit comme
 * un décollement raté. Ce que l'ancienne échelle cherchait à 8 dp — que la
 * barre se détache — vient maintenant de la couleur, pas de l'ombre.
 */
enum class AuleElevation(private val light: Dp, private val dark: Dp) {
    /** Posé à même la surface. */
    NONE(0.dp, 0.dp),

    /** Ce qui s'appuie sur un bord : la barre d'arrivée. */
    RESTING(6.dp, 8.dp),

    /** Ce qui flotte au-dessus de la carte : pastilles, boutons, recherche. */
    FLOATING(12.dp, 16.dp),

    /** Le volet, qui recouvre une part de l'écran. */
    LIFTED(18.dp, 24.dp),

    /** Ce qui prend l'écran : la carte de connexion. */
    OVERLAY(26.dp, 32.dp);

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

    /** Le lavis de marque sous un glyphe. */
    const val WASH = 0.14f

    /** Le contour d'un aplat teinté. */
    const val OUTLINE = 0.30f

    /** Un fond posé sur la carte, qui la laisse deviner. */
    const val VEIL = 0.72f

    /** La poignée du volet : présente, jamais lue en premier. */
    const val SUBDUED = 0.35f

    /**
     * Ce qui recule sous un menu déployé.
     *
     * Le voile ne cache pas la carte — elle reste le repère de l'écran — mais
     * il éteint ce qui n'est plus touchable tant que le menu est ouvert, et il
     * donne au doigt une cible pour refermer ailleurs que sur le bouton. En
     * dessous de 0,30, les pastilles du HUD continuent de tirer l'œil vers
     * elles alors qu'elles ne répondent plus.
     *
     * Il porte un nom à lui plutôt que de reprendre [SCRIM] : celui-là est un
     * fond de dégradé sous du texte, deux fois plus dense, et le confondre
     * avec ce voile-ci noierait la carte pour trois boutons.
     */
    const val SHADE = 0.32f

    /** Le lavis d'ambiance, de jour. */
    const val GLOW = 0.16f

    /** Le lavis d'ambiance, de nuit. */
    const val GLOW_STRONG = 0.34f

    /** Un halo d'angle, de jour. */
    const val HALO = 0.10f

    /** Un halo d'angle, de nuit. */
    const val HALO_STRONG = 0.16f

    /** Le halo le plus lointain. */
    const val HALO_SOFT = 0.08f

    /**
     * Le verre posé sur la carte.
     *
     * Assez opaque pour qu'un texte s'y lise sur n'importe quel fond de tuile —
     * un bâtiment sombre, un plan d'eau, une place blanche — et assez
     * transparent pour qu'on sache qu'il y a une carte dessous. En dessous de
     * 0,88, un nom d'arrêt posé au-dessus d'un toit foncé décroche.
     */
    const val GLASS = 0.92f

    /**
     * Le reflet haut d'une surface de marque.
     *
     * Ce qui fait qu'un aplat teal a l'air éclairé plutôt que peint. Il ne se
     * remarque que par son absence.
     *
     * Il a été **divisé par deux**. À 0,22 posé sur le tiers haut, le voile ne
     * se lisait plus comme de la lumière mais comme une deuxième couleur : le
     * bouton avait une bande claire en haut, une bande sombre en bas, et une
     * frontière entre les deux là où le voile s'arrêtait. À 0,11 étalé sur la
     * moitié de la hauteur, il ne reste que ce qu'on cherchait — le haut de la
     * surface prend la lumière.
     */
    const val SHEEN = 0.11f

    /**
     * Le liseré qui borde une surface de marque, en haut.
     *
     * Le détail qui distingue une surface d'un rectangle colorié, et le moins
     * cher de tous : un trait d'un point, à l'encre de la surface, qui s'éteint
     * avant d'avoir atteint le bas. C'est l'arête que prendrait un objet réel
     * éclairé par le haut — et comme il épouse la forme, il redit l'arrondi que
     * l'aplat seul laisse deviner.
     */
    const val RIM = 0.24f

    /**
     * Le balayage de lumière, à son passage.
     *
     * Plus discret que le reflet fixe qu'il traverse : un éclat qu'on remarque
     * en tant qu'éclat est un éclat raté. Il passe une fois, et on ne doit
     * retenir que le fait que la surface *est arrivée*.
     */
    const val GLINT = 0.16f

    /**
     * Le voile d'un fond de dégradé sous un contenu clair.
     *
     * Sert aux surfaces où le dégradé de marque passe **derrière** du texte
     * plutôt que sous lui.
     */
    const val SCRIM = 0.55f
}

/**
 * Les durées d'animation.
 *
 * Elles viennent de `dashboard/docs/carte-immersive/08-style-graphique.md` et
 * sont communes aux trois plateformes : c'est ce qui fait qu'Aule bouge pareil
 * partout.
 *
 * Ces durées restent celles des mouvements **cartographiques** — la caméra, la
 * pulsation, le volet. Tout ce qui bouge dans l'interface Compose passe
 * désormais par le `MotionScheme` expressif de Material, qui raisonne en
 * ressorts et non en millisecondes : un ressort s'interrompt et repart d'où il
 * est, une durée recommence du début. Sur un écran où l'on annule un geste sur
 * deux, la différence se voit.
 */
object AuleMotion {
    /** Un déplacement ordinaire : volet, apparition de carte. */
    const val GLIDE_MS = 300

    /** Une réponse au doigt : sélection, bascule. */
    const val POP_MS = 220

    /**
     * Un rattrapage de caméra : on remet d'aplomb ce qui a glissé.
     *
     * C'est le mouvement le plus court du lot, et il doit l'être : la carte
     * corrige quelque chose que l'utilisateur n'a pas demandé, et une
     * correction qui s'étire se lit comme une reprise en main.
     */
    const val CAMERA_NUDGE_MS = 400

    /**
     * Un changement d'échelle : un vol vers un lieu, un tracé qu'on cadre.
     *
     * Plus long qu'un rattrapage, parce qu'on change de **contexte** et non
     * de réglage : l'œil doit pouvoir suivre le trajet entre l'endroit d'où
     * l'on part et celui où l'on arrive, sinon il se retrouve ailleurs sans
     * savoir comment.
     */
    const val CAMERA_FLY_MS = 650

    /**
     * L'entrée dans un mode de caméra : d'exploration à navigation.
     *
     * Le plus long, presque une seconde. Zoom, cap et inclinaison changent
     * **ensemble** : joué en un demi-temps, le mouvement combiné donne
     * l'impression que la carte bascule. Étalé, il se lit comme un
     * décollage. Au-delà, on ne l'anime plus — le suivi écrit alors la
     * caméra image par image.
     */
    const val CAMERA_MODE_MS = 900

    /** La pulsation du point temps réel. */
    const val PULSE_MS = 1800

    /**
     * Le décalage entre deux éléments d'une même liste qui apparaissent.
     *
     * Une liste dont toutes les rangées arrivent ensemble arrive comme un bloc ;
     * la même décalée de 40 ms par rangée se déroule. Au-delà de six rangées le
     * décalage cesse de croître — sinon la dernière arriverait une seconde
     * après la première, et l'attente se verrait.
     */
    const val STAGGER_MS = 40

    /** Le nombre de rangées au-delà duquel la cascade cesse de s'allonger. */
    const val STAGGER_CAP = 6

    /** Le balayage lumineux d'une surface de marque, quand elle se pose. */
    const val SHEEN_MS = 1400

    /**
     * Le temps mort avant le balayage.
     *
     * La surface entre d'abord — translation et opacité, comme le reste de
     * l'écran — et l'éclat ne la traverse qu'ensuite. Joués ensemble, les deux
     * mouvements se disputent : on voit une chose bouger et briller sans savoir
     * laquelle des deux on regarde.
     */
    const val SHEEN_DELAY_MS = 260L

    /**
     * Ce que le doigt enfonce.
     *
     * Trois centièmes : c'est la limite basse de ce qui se **sent** sans se
     * voir. À 0,90 le bouton recule visiblement, ce qui est le geste d'un jouet ;
     * à 0,99 il ne se passe rien, et l'écran redevient une image. Entre les
     * deux, on a la seule chose qu'on demande à un contrôle tactile : accuser
     * réception.
     */
    const val PRESS_SCALE = 0.97f
}
