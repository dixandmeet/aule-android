package io.aule.android.core.designsystem.token

/**
 * Les rampes tonales d'Aule.
 *
 * Material 3 ne raisonne pas en « une couleur par rôle » mais en **familles de
 * tons** : un même ton sert d'aplat de jour et d'encre de nuit, et c'est cette
 * symétrie qui permet d'écrire un thème sombre sans dupliquer le thème clair.
 * Les rampes ci-dessous sont donc la seule source de couleur du produit ; les
 * rôles M3 s'y servent, ils n'inventent rien.
 *
 * Le nombre suit la convention Material : 0 est noir, 100 est blanc, et le ton
 * dit la **luminosité**, pas l'importance. Un ton 40 est lisible sur du blanc,
 * un ton 80 est lisible sur du noir ; c'est tout ce qu'il faut retenir pour s'en
 * servir.
 *
 * ## Comment ces valeurs ont été trouvées
 *
 * Chaque ton est posé en **OKLCH** — clarté, chroma, teinte — puis converti en
 * sRGB. C'est ce qui distingue cette palette d'une liste de hex choisis à vue :
 * dans OKLCH, deux tons de même clarté *paraissent* aussi clairs l'un que
 * l'autre, quelle que soit leur teinte. Un vert et un rouge posés à L = 0,50 se
 * lisent au même niveau ; les mêmes choisis à l'œil ne le font jamais.
 *
 * Trois règles ont guidé la refonte, et elles se voient dans les chiffres :
 *
 * 1. **La chroma suit une cloche.** Une famille dont la chroma reste constante
 *    donne des tons clairs fluorescents — c'est ce que faisait l'ancien
 *    `Teal.T90` (`#B8F0F6`), un cyan de piscine posé en aplat plein sur les
 *    cartes sélectionnées. La chroma culmine désormais au milieu de la rampe,
 *    là où vit l'accent, et retombe aux extrémités, là où vivent les aplats.
 *
 * 2. **Les neutres partagent la teinte de la marque.** Ils étaient verdis
 *    (H ≈ 170°) sous un primaire cyan (H ≈ 202°) : trente degrés d'écart, assez
 *    pour que les gris paraissent sales à côté du vert. Ils sont maintenant à
 *    H = 199°, la teinte d'Aule, avec une chroma d'un centième — présente, mais
 *    jamais nommable.
 *
 * 3. **L'échelle de surfaces descend pour de bon.** Les cinq crans de conteneur
 *    tenaient dans six centièmes de clarté : à l'écran, cinq blancs. Ils
 *    couvrent maintenant neuf centièmes, et le cran le plus haut est un gris
 *    franc. C'est ce qui fait qu'une carte se voit sans qu'on lui dessine un
 *    contour.
 */
internal object AulePalette {

    /**
     * Le teal Aule — la teinte de marque, celle de la carte en production.
     *
     * [Teal.T30] est le teal exact d'`AuleBrand.teal` : il n'a pas bougé, et il
     * ne bougera pas — c'est l'identité, et elle tourne sur les véhicules. Ce
     * qui a changé, c'est ce qu'il y a **autour** de lui.
     *
     * L'ancienne rampe était plate : chroma d'un vingtième du haut en bas, ce
     * qui est presque un gris teinté. Une marque qui ne sature jamais ne se
     * remarque jamais. La chroma monte maintenant jusqu'à 0,10 en [T60] — le
     * teal vif qui porte les états actifs et les lueurs — et redescend à 0,04
     * en [T90], pour que les aplats clairs restent des fonds et non des néons.
     */
    object Teal {
        val T0 = AuleRgba(0x000000)
        val T10 = AuleRgba(0x002327)
        val T20 = AuleRgba(0x003E43)
        val T25 = AuleRgba(0x004C50)

        /** Le teal Aule. Inchangé depuis la première carte. */
        val T30 = AuleRgba(0x0D595E)
        val T35 = AuleRgba(0x06696E)
        val T40 = AuleRgba(0x05797E)
        val T50 = AuleRgba(0x21979B)

        /** Le sommet de chroma : l'accent vivant, celui des lueurs et des états. */
        val T60 = AuleRgba(0x44B4B6)
        val T70 = AuleRgba(0x73CDCE)
        val T80 = AuleRgba(0xA3E0E1)
        val T90 = AuleRgba(0xC9EFF0)
        val T95 = AuleRgba(0xE2F7F7)
        val T100 = AuleRgba(0xFFFFFF)
    }

    /**
     * Le vert du temps réel.
     *
     * [Green.T60] est la couleur du point qui pulse quand une donnée vient du
     * véhicule et non de l'horaire théorique. Elle porte la famille secondaire :
     * dans Aule, « secondaire » veut dire « vivant ».
     *
     * Sa teinte — 159° — la tient à quarante degrés du teal de marque. C'est
     * délibéré et c'est le maximum utile : plus près, le temps réel se
     * confondrait avec l'accent ; plus loin, il quitterait la famille des verts
     * et cesserait de dire « ça va bien ».
     */
    object Green {
        val T10 = AuleRgba(0x002716)
        val T20 = AuleRgba(0x004128)
        val T30 = AuleRgba(0x005C39)
        val T40 = AuleRgba(0x067A4C)
        val T50 = AuleRgba(0x1D9761)

        /** Le vert temps réel. */
        val T60 = AuleRgba(0x2EB67A)
        val T70 = AuleRgba(0x60CF97)
        val T80 = AuleRgba(0x92E5B7)
        val T90 = AuleRgba(0xC2F2D6)
        val T95 = AuleRgba(0xDFF9E9)
    }

    /**
     * L'ambre du retard.
     *
     * [Amber.T70] est la couleur d'un passage annoncé en retard. Elle porte la
     * famille tertiaire, que Material réserve aux accents de contrepoint : dans
     * une application d'horaires, le contrepoint du temps réel est le retard.
     *
     * C'est aussi la seule famille **chaude** du produit, et c'est ce qui donne
     * à l'ensemble sa profondeur : une palette entièrement froide paraît
     * clinique, quelle que soit la qualité de ses gris.
     */
    object Amber {
        val T10 = AuleRgba(0x2D1800)
        val T20 = AuleRgba(0x4B2B00)
        val T30 = AuleRgba(0x6A4000)
        val T40 = AuleRgba(0x8B5700)
        val T50 = AuleRgba(0xAD6F00)
        val T60 = AuleRgba(0xCE8919)

        /** L'ambre du retard. */
        val T70 = AuleRgba(0xEAA53F)
        val T80 = AuleRgba(0xFDC373)
        val T90 = AuleRgba(0xFFDEB2)
        val T95 = AuleRgba(0xFFEFD8)
    }

    /**
     * Le rouge de l'alerte.
     *
     * [Red.T50] est la couleur d'alerte historique. Elle ne tient pas le
     * contraste en aplat sous du texte blanc — donc le rôle `error` de jour
     * prend [Red.T40] et la marque reste disponible pour ce qui n'est pas du
     * texte.
     */
    object Red {
        val T10 = AuleRgba(0x370509)
        val T20 = AuleRgba(0x5D0D13)
        val T30 = AuleRgba(0x85191F)
        val T40 = AuleRgba(0xAD2C2E)

        /** Le rouge d'alerte. */
        val T50 = AuleRgba(0xD44543)
        val T60 = AuleRgba(0xEF6860)
        val T70 = AuleRgba(0xFF9186)
        val T80 = AuleRgba(0xFFBBB1)
        val T90 = AuleRgba(0xFFDAD4)
        val T95 = AuleRgba(0xFFECE9)
    }

    /**
     * Les neutres, teintés de la marque.
     *
     * Un gris parfaitement neutre posé à côté du teal Aule paraît violacé ; un
     * centième de chroma à la teinte de la marque suffit à faire tenir
     * l'ensemble. C'est la même raison qui fait que Material teinte ses neutres
     * avec la couleur primaire — sauf qu'ici la teinte est bien celle du
     * primaire, ce qui n'était pas le cas avant.
     *
     * L'escalier des tons clairs a été **écarté**. [T96] à [T87] descendent
     * maintenant de 0,978 à 0,882 en clarté, contre 0,983 à 0,936 avant : le
     * même nombre de crans, sur le double de course. C'est ce qui rend une
     * carte visible sur son volet sans lui dessiner de contour, et c'est la
     * moitié du remède à l'écran fade.
     *
     * ## Puis **remonté**, et à moitié désaturé
     *
     * L'écartement avait un prix qu'on n'avait pas vu : à `#E3ECEC`, le
     * cartouche des passages est un gris-vert franc, et sur un volet blanc il
     * ne se lit plus comme un fond mais comme un **bloc de couleur**. Ajoutez-y
     * un chiffre vert par rangée et une plaque teal en tête, et l'écran compte
     * trois verts qui n'ont rien à se dire.
     *
     * Les six crans clairs remontent donc de deux à trois centièmes de clarté,
     * et leur chroma tombe de moitié — d'un centième à un demi. La hiérarchie
     * survit entière : l'écart d'un cran au suivant est inchangé, c'est
     * l'escalier tout entier qui a monté d'une marche. Ce qui disparaît est la
     * teinte, pas la structure — le gris cesse d'être nommable, ce qui est
     * exactement ce qu'on demande à un gris.
     */
    object Neutral {
        val T0 = AuleRgba(0x000000)
        val T6 = AuleRgba(0x050C0D)
        val T8 = AuleRgba(0x0A1313)
        val T10 = AuleRgba(0x0E1818)
        val T12 = AuleRgba(0x131E1E)
        val T17 = AuleRgba(0x1A2626)
        val T20 = AuleRgba(0x222D2E)
        val T22 = AuleRgba(0x283334)
        val T24 = AuleRgba(0x2E3A3A)
        val T30 = AuleRgba(0x3D4B4B)
        val T50 = AuleRgba(0x6E7D7E)
        val T60 = AuleRgba(0x889798)
        val T80 = AuleRgba(0xC9D3D4)
        val T87 = AuleRgba(0xD7DFE0)
        val T90 = AuleRgba(0xE0E7E8)
        val T92 = AuleRgba(0xE8EEEF)
        val T94 = AuleRgba(0xEFF4F5)
        val T96 = AuleRgba(0xF5F8F9)
        val T100 = AuleRgba(0xFFFFFF)

        /**
         * L'encre du produit : un noir qui n'est pas tout à fait noir.
         *
         * Elle a **remonté** de quatre centièmes de clarté. À `#1F2829`, elle
         * rendait 15:1 sur le blanc — le contraste d'un texte de loi, pas d'une
         * interface — et cette dureté se payait partout à la fois : chaque
         * titre, chaque nom d'arrêt, chaque chiffre découpé au rasoir sur son
         * fond. Additionnée au gras généralisé de l'échelle appuyée, elle
         * donnait un écran qui *cogne* au lieu de se lire.
         *
         * À `#2B3536`, il reste 12,6:1 — deux fois et demie le plancher AA, et
         * bien au-delà de ce qu'exige une lecture au soleil. Ce qu'on perd est
         * une brutalité dont personne n'avait besoin ; ce qu'on gagne se voit
         * sur l'écran entier, parce que c'est l'encre de l'écran entier.
         */
        val ink = AuleRgba(0x2B3536)

        /**
         * L'encre secondaire, pour ce qui se lit après.
         *
         * Adoucie du même geste, et sous la même contrainte : elle tient 5,9:1
         * sur le blanc et 4,9:1 sur le cartouche le plus soutenu du jour, donc
         * elle reste un texte, pas une nuance. Un cran plus clair et elle
         * passait sous le plancher AA sur les cartouches — c'est la surface qui
         * fixe la limite, pas le goût.
         */
        val inkMuted = AuleRgba(0x5B6768)

        /** L'encre de nuit. */
        val inkNight = AuleRgba(0xEBF3F3)

        /**
         * L'encre secondaire de nuit.
         *
         * Plus claire que son homologue de jour ne le laisserait croire : le
         * brief exige 10:1 sur la surface nocturne, contre 4,5:1 le jour. Un
         * texte secondaire lu de nuit dans un véhicule qui bouge n'a pas droit
         * au même minimum qu'un texte lu à l'arrêt en plein jour.
         */
        val inkMutedNight = AuleRgba(0xB6C2C3)
    }

    /**
     * Les ancres HUD qui ne sont pas un cran de rampe.
     *
     * La rampe décrit une famille de tons ; le HUD pose des **rôles**. Certains
     * coïncident avec un cran — le teal Aule, le temps réel de jour, l'ambre du
     * retard. D'autres sont des teintes de marque mesurées à part.
     *
     * La nuit a été recalée. Son aplat primaire était un vert d'aplat à 168° et
     * son encre primaire un vert menthe : l'application était teal de jour et
     * verte de nuit, deux marques pour un produit. Les deux sont revenues dans
     * la famille — [nightFill] et [nightOnSurface] partagent maintenant la
     * teinte du teal Aule.
     */
    object Hud {
        val nightFill = AuleRgba(0x005255)
        val nightOnFill = AuleRgba(0xE8F6F6)
        val nightOnSurface = AuleRgba(0x74D0D2)
        val nightError = AuleRgba(0xE5635B)
        val nightOnError = AuleRgba(0x370509)

        val realtimeContainerDay = AuleRgba(0xC8EFD8)
        val realtimeOnContainerDay = AuleRgba(0x004A2D)
        val realtimeNight = AuleRgba(0x52CC90)
        val realtimeOnNight = AuleRgba(0x002F1B)
        val realtimeContainerNight = AuleRgba(0x004D2F)
        val realtimeOnContainerNight = AuleRgba(0x93EABA)

        val delayContainerDay = AuleRgba(0xFFE3BE)
        val delayOnContainerDay = AuleRgba(0x5B3700)
        val delayNight = AuleRgba(0xF0B058)
        val delayOnNight = AuleRgba(0x3E2300)
        val delayContainerNight = AuleRgba(0x643D00)
        val delayOnContainerNight = AuleRgba(0xFDD298)
    }
}
