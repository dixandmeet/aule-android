package io.aule.android.core.designsystem.token

/**
 * Cinq rôles typographiques. Pas six.
 *
 * Le rapport d'environ 1,27 entre paliers est ce qui les rend distinguables
 * **sans les comparer côte à côte**. C'est aussi lui qui a décidé que le palier
 * haut valait 28 et non 30 : 28/22 = 1,273 passe, 30/22 = 1,364 non. Un test
 * garde ce rapport dans [1,2 ; 1,35].
 *
 * La graisse n'est pas dans le rôle : elle se passe à part, parce qu'un même
 * palier s'écrit tantôt en normal, tantôt en demi-gras, sans changer de rôle.
 */
enum class AuleRole(
    val sizeSp: Float,
    val lineHeightRatio: Float,
    val trackingSp: Float,
    val usesTabularFigures: Boolean,
) {
    /** Sur-titre, capitales espacées. */
    KICKER(sizeSp = 11f, lineHeightRatio = 1.20f, trackingSp = 0.44f, usesTabularFigures = false),

    /** Le corps de texte. */
    BODY(sizeSp = 14f, lineHeightRatio = 1.40f, trackingSp = 0f, usesTabularFigures = false),

    /** Titre de volet, nom d'arrêt. */
    TITLE(sizeSp = 18f, lineHeightRatio = 1.25f, trackingSp = -0.18f, usesTabularFigures = false),

    /** Un chiffre qu'on lit d'un coup d'œil : minutes d'attente, vitesse. */
    DATA(sizeSp = 22f, lineHeightRatio = 1.10f, trackingSp = -0.22f, usesTabularFigures = true),

    /** Le chiffre qui domine l'écran. */
    HERO(sizeSp = 28f, lineHeightRatio = 1.05f, trackingSp = -0.40f, usesTabularFigures = true);

    /** Interligne absolu, en sp. */
    val lineHeightSp: Float get() = sizeSp * lineHeightRatio

    companion object {
        /** L'échelle, du plus petit au plus grand. L'ordre porte le rapport. */
        val ladder: List<AuleRole> = listOf(KICKER, BODY, TITLE, DATA, HERO)
    }
}
