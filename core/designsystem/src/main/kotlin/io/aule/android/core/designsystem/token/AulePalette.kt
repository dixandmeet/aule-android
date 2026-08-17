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
 * Chaque rampe est ancrée sur une couleur de marque déjà en production, dont le
 * ton d'origine est signalé en commentaire. Les autres tons sont sa
 * déclinaison : on éclaircit et on assombrit la marque, on ne la remplace pas.
 *
 * Le nombre suit la convention Material : 0 est noir, 100 est blanc, et le ton
 * dit la **luminosité**, pas l'importance. Un ton 40 est lisible sur du blanc,
 * un ton 80 est lisible sur du noir ; c'est tout ce qu'il faut retenir pour s'en
 * servir.
 */
internal object AulePalette {

    /**
     * Le vert Aule — la teinte de marque, celle de la carte en production.
     *
     * [Teal.T30] est le vert exact d'`AuleBrand.teal`. Toute la famille primaire
     * en descend, de jour comme de nuit : c'est ce qui fait qu'Aule reste Aule
     * quand l'appareil bascule en sombre.
     */
    object Teal {
        val T0 = AuleRgba(0x000000)
        val T10 = AuleRgba(0x00201F)
        val T20 = AuleRgba(0x003739)
        val T25 = AuleRgba(0x004346)

        /** Le vert Aule. */
        val T30 = AuleRgba(0x0D595E)
        val T35 = AuleRgba(0x16666B)
        val T40 = AuleRgba(0x227279)
        val T50 = AuleRgba(0x3D8D94)
        val T60 = AuleRgba(0x5AA8AF)
        val T70 = AuleRgba(0x78C4CB)
        val T80 = AuleRgba(0x96E0E7)
        val T90 = AuleRgba(0xB8F0F6)
        val T95 = AuleRgba(0xDCF8FB)
        val T100 = AuleRgba(0xFFFFFF)
    }

    /**
     * Le vert du temps réel.
     *
     * [Green.T60] est la couleur du point qui pulse quand une donnée vient du
     * véhicule et non de l'horaire théorique. Elle porte la famille secondaire :
     * dans Aule, « secondaire » veut dire « vivant ».
     */
    object Green {
        val T10 = AuleRgba(0x00210F)
        val T20 = AuleRgba(0x00391D)
        val T30 = AuleRgba(0x00522B)
        val T40 = AuleRgba(0x006D3B)
        val T50 = AuleRgba(0x00894C)

        /** Le vert temps réel. */
        val T60 = AuleRgba(0x19B37B)
        val T70 = AuleRgba(0x3FCF97)
        val T80 = AuleRgba(0x62ECB3)
        val T90 = AuleRgba(0xA9F2CE)
        val T95 = AuleRgba(0xCBF8DF)
    }

    /**
     * L'ambre du retard.
     *
     * [Amber.T70] est la couleur d'un passage annoncé en retard. Elle porte la
     * famille tertiaire, que Material réserve aux accents de contrepoint : dans
     * une application d'horaires, le contrepoint du temps réel est le retard.
     */
    object Amber {
        val T10 = AuleRgba(0x2B1700)
        val T20 = AuleRgba(0x482A00)
        val T30 = AuleRgba(0x663E00)
        val T40 = AuleRgba(0x875300)
        val T50 = AuleRgba(0xA96A00)
        val T60 = AuleRgba(0xCC8200)

        /** L'ambre du retard. */
        val T70 = AuleRgba(0xE8A13C)
        val T80 = AuleRgba(0xFFBF74)
        val T90 = AuleRgba(0xFFDDB8)
        val T95 = AuleRgba(0xFFEEDD)
    }

    /**
     * Le rouge de l'alerte.
     *
     * [Red.T50] est la couleur d'alerte historique. Elle ne tient pas le
     * contraste en aplat sous du texte blanc — 4,35:1 pour 4,5 exigés — donc le
     * rôle `error` de jour prend [Red.T40] et la marque reste disponible pour ce
     * qui n'est pas du texte.
     */
    object Red {
        val T10 = AuleRgba(0x410008)
        val T20 = AuleRgba(0x690013)
        val T30 = AuleRgba(0x8C1D24)
        val T40 = AuleRgba(0xB03038)

        /** Le rouge d'alerte. */
        val T50 = AuleRgba(0xD64545)
        val T60 = AuleRgba(0xF2605C)
        val T70 = AuleRgba(0xFF8A83)
        val T80 = AuleRgba(0xFFB4AB)
        val T90 = AuleRgba(0xFFDAD6)
        val T95 = AuleRgba(0xFFEDEA)
    }

    /**
     * Les neutres, très légèrement verdis.
     *
     * Un gris parfaitement neutre posé à côté du vert Aule paraît violacé ; deux
     * points de vert dans les neutres suffisent à faire tenir l'ensemble. C'est
     * la même raison qui fait que Material teinte ses neutres avec la couleur
     * primaire.
     */
    object Neutral {
        val T0 = AuleRgba(0x000000)
        val T6 = AuleRgba(0x070D0B)
        val T8 = AuleRgba(0x0D1512)
        val T10 = AuleRgba(0x141C19)
        val T12 = AuleRgba(0x18201D)
        val T17 = AuleRgba(0x222A27)
        val T20 = AuleRgba(0x1A211F)
        val T22 = AuleRgba(0x2D3532)
        val T24 = AuleRgba(0x333B38)
        val T30 = AuleRgba(0x3F4946)
        val T50 = AuleRgba(0x75807D)
        val T60 = AuleRgba(0x899390)
        val T80 = AuleRgba(0xC5CFCC)
        val T87 = AuleRgba(0xDCE4E1)
        val T90 = AuleRgba(0xE6EBE9)
        val T92 = AuleRgba(0xECF1EF)
        val T94 = AuleRgba(0xF2F6F4)
        val T96 = AuleRgba(0xF7FAF9)
        val T100 = AuleRgba(0xFFFFFF)

        /** L'encre du produit : un noir qui n'est pas tout à fait noir. */
        val ink = AuleRgba(0x171717)

        /** L'encre secondaire, pour ce qui se lit après. */
        val inkMuted = AuleRgba(0x4A4A4A)

        /** L'encre de nuit. */
        val inkNight = AuleRgba(0xF3F5F7)

        /** L'encre secondaire de nuit. */
        val inkMutedNight = AuleRgba(0xBFC7C3)
    }

    /**
     * Les ancres HUD qui ne sont pas un cran de rampe.
     *
     * La rampe décrit une famille de tons ; le HUD pose des **rôles**. Certains
     * coïncident avec un cran — le vert Aule, le temps réel de jour, l'ambre
     * du retard. D'autres sont des teintes de marque mesurées à part. La nuit
     * surtout : son aplat primaire n'est pas le ton 80 inversé, c'est un vert
     * d'aplat (H:163°) qui reste un fond, pas une encre.
     */
    object Hud {
        val nightFill = AuleRgba(0x1A5C47)
        val nightOnFill = AuleRgba(0xF1F6F3)
        val nightOnSurface = AuleRgba(0x8AC79B)
        val nightError = AuleRgba(0xE86060)
        val nightOnError = AuleRgba(0x601410)

        val realtimeContainerDay = AuleRgba(0xD1F4E6)
        val realtimeOnContainerDay = AuleRgba(0x005132)
        val realtimeNight = AuleRgba(0x41C895)
        val realtimeOnNight = AuleRgba(0x003822)
        val realtimeContainerNight = AuleRgba(0x005234)
        val realtimeOnContainerNight = AuleRgba(0x67E5B0)

        val delayContainerDay = AuleRgba(0xFFE3C2)
        val delayOnContainerDay = AuleRgba(0x593600)
        val delayNight = AuleRgba(0xF0B45C)
        val delayOnNight = AuleRgba(0x432C00)
        val delayContainerNight = AuleRgba(0x5E4000)
        val delayOnContainerNight = AuleRgba(0xFFDCB3)
    }
}
