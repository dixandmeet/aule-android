package io.aule.android.core.map.layer

import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.LinePalette
import io.aule.android.core.model.TransportMode
import io.aule.android.core.model.TransportVehicle
import kotlin.math.PI
import kotlin.math.sin

/**
 * La livrée « neutre + accent » des véhicules, et tout ce qui en dépend : palette, accent, état.
 *
 * ## La règle
 *
 * La couleur de ligne dit **la ligne**. La carrosserie et les effets du véhicule disent **son
 * état**. Spec commune iOS / Android / web : `voyageur/docs/vehicules-livree-neutre-accent.md`.
 * ⚠️ **Les nombres de ce fichier sont ceux des trois plateformes** : deux jeux de valeurs feraient
 * deux réseaux différents au même endroit. Les changer ici sans changer la spec, c'est les faire
 * diverger.
 *
 * Le fichier est du calcul pur — ni MapLibre, ni `Canvas`, ni `Context` — pour se vérifier sur la
 * JVM. Les couleurs sont des `0xRRGGBB` ; l'alpha est l'affaire de celui qui peint.
 */
internal object VehicleLivery {

    // ------------------------------------------------------------------ palette neutre

    /** La carrosserie, le contour qui la détache du fond, et l'encre (vitres, joints). */
    data class Neutral(val body: Int, val outline: Int, val ink: Int)

    val DAY = Neutral(body = 0xF4F6F8, outline = 0x5E6874, ink = 0x222A33)
    val NIGHT = Neutral(body = 0x343C48, outline = 0xB8C0CB, ink = 0x0B0F14)

    fun neutral(night: Boolean): Neutral = if (night) NIGHT else DAY

    /**
     * Tout aplat d'accent est cerné d'un trait de contour neutre **à 55 %**, pour qu'un accent
     * blanc sur carrosserie blanche — la ligne NC —, ou noir sur anthracite, reste une forme.
     */
    const val OUTLINE_ALPHA = 0.55

    // -------------------------------------------------------------------- turquoise Aule

    /** Le trait de l'anneau et du contour d'un véhicule choisi. Jamais la couleur de ligne. */
    fun turquoiseStroke(night: Boolean): Int = if (night) 0x5FD0D3 else 0x17A2A5

    /** La lueur du halo — celle du halo du puck. */
    fun turquoiseGlow(night: Boolean): Int = if (night) 0x74D0D2 else 0x44B4B6

    /** L'épaisseur du contour turquoise d'une silhouette choisie, en points. */
    const val SELECTED_STROKE_PT = 1.6

    // -------------------------------------------------------------------------- l'accent

    /**
     * La couleur d'accent d'un véhicule : celle de sa ligne **telle quelle**, sans l'éclaircir ni
     * la saturer ; à défaut, la couleur de son **mode** — jamais le gris « ligne inconnue ».
     *
     * Le repli couvre aussi les couleurs illisibles : sur une carte, un véhicule gris se lit comme
     * une panne.
     */
    fun accent(lineColorHex: String?, mode: TransportMode, night: Boolean): Int =
        parseHex(lineColorHex) ?: rgb(mode.markerColor(night).let { Triple(it.red, it.green, it.blue) })

    /**
     * Ce que la flotte sait de la couleur d'une ligne : la clé du BFF d'abord, puis le `route_id`
     * avec le mode, puis le nom montré — l'ordre que suit déjà le tracé du véhicule suivi.
     *
     * `null` est une réponse : la ligne est hors index, [accent] prend alors le mode.
     */
    fun lineColorHex(vehicle: TransportVehicle, palette: LinePalette): String? =
        palette.colorOf(vehicle.lineKey)
            ?: palette.colorOf(vehicle.lineId, vehicle.mode)
            ?: palette.colorOf(vehicle.lineName, vehicle.mode)

    /**
     * `#RRGGBB`, `RRGGBB` ou `#RGB`, entourés ou non d'espaces — les formes du GTFS. Rend `null`
     * pour tout le reste, **et non un gris de repli** : c'est l'appelant qui choisit le sien.
     */
    fun parseHex(raw: String?): Int? {
        val cleaned = raw?.trim()?.removePrefix("#") ?: return null
        val full = when (cleaned.length) {
            6 -> cleaned
            3 -> cleaned.map { "$it$it" }.joinToString("")
            else -> return null
        }
        if (full.any { it !in HEX_DIGITS }) return null
        return full.toInt(radix = 16)
    }

    /** `0xRRGGBB` → `#RRGGBB`, pour une propriété de feature que MapLibre lit comme couleur. */
    fun css(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

    /** Le nom d'une couleur dans un nom d'image : six chiffres, majuscules. */
    fun key(rgb: Int): String = "%06X".format(rgb and 0xFFFFFF)

    private const val HEX_DIGITS = "0123456789abcdefABCDEF"

    private fun rgb(channels: Triple<Double, Double, Double>): Int {
        fun byte(value: Double) = (value * 255).toInt().coerceIn(0, 255)
        return (byte(channels.first) shl 16) or (byte(channels.second) shl 8) or byte(channels.third)
    }

    // ------------------------------------------------------------------------- l'état

    /**
     * Où en est un véhicule vis-à-vis de ce que l'utilisateur regarde.
     *
     * - [REST] : rien n'est choisi, aucun véhicule ne recule ;
     * - [SELECTED] : sa fiche est ouverte ;
     * - [FOLLOWED] : la caméra le suit (vue GPS) ;
     * - [RECEDED] : un **autre** est choisi ou suivi, celui-ci passe à l'arrière-plan.
     */
    enum class Role { REST, SELECTED, FOLLOWED, RECEDED }

    fun role(isSelected: Boolean, isFollowed: Boolean, anyChosen: Boolean): Role = when {
        isSelected && isFollowed -> Role.FOLLOWED
        isSelected -> Role.SELECTED
        anyChosen -> Role.RECEDED
        else -> Role.REST
    }

    /** Le contour turquoise : le véhicule choisi, suivi ou non. */
    fun isContoured(role: Role): Boolean = role == Role.SELECTED || role == Role.FOLLOWED

    /** L'échelle de la silhouette : « élévation » du choisi, retrait des autres. */
    fun scale(role: Role): Double = when (role) {
        Role.REST -> 1.0
        Role.SELECTED, Role.FOLLOWED -> SELECTED_SCALE
        Role.RECEDED -> RECEDED_SCALE
    }

    /**
     * L'échelle d'un **volume** : les autres reculent, mais le choisi **n'ajoute pas** son
     * « élévation » à l'exagération qu'il a déjà — la caméra de suivi le grossit de 80 % (voir
     * `VehicleBody.FOLLOWED_SCALE`), et ×1,06 par-dessus ne se verrait pas.
     */
    fun volumeScale(role: Role): Double = if (role == Role.RECEDED) RECEDED_SCALE else 1.0

    /** L'opacité d'un véhicule : celle des autres baisse quand l'un d'eux est choisi. */
    fun opacity(role: Role): Double = if (role == Role.RECEDED) RECEDED_OPACITY else 1.0

    /** Le facteur de l'ombre de contact : le véhicule suivi pèse davantage sur la chaussée. */
    fun contactShadow(role: Role): Double = if (role == Role.FOLLOWED) FOLLOWED_SHADOW else 1.0

    /** Le rang de dessin : le choisi passe **au-dessus** des autres, jamais en dessous. */
    fun drawRank(role: Role): Int = when (role) {
        Role.FOLLOWED -> 2
        Role.SELECTED -> 1
        else -> 0
    }

    const val SELECTED_SCALE = 1.06
    const val RECEDED_SCALE = 0.92
    const val RECEDED_OPACITY = 0.82
    const val FOLLOWED_SHADOW = 1.5

    // -------------------------------------------------------------------- la pastille

    /** Le disque de loin : Ø 11, cœur d'accent Ø 6, aucune silhouette. */
    const val DOT_DIAMETER_PT = 11.0
    const val DOT_CORE_DIAMETER_PT = 6.0

    // -------------------------------------------------------------------- la silhouette

    /**
     * Les cotes de la silhouette compacte, en unités du canevas web (48 pour la boîte).
     *
     * Elles servent au dessin **et** au calcul de la longueur d'écran du véhicule, donc au halo :
     * deux copies finiraient par ne plus se suivre.
     */
    object Silhouette {
        /** Les 48 unités du canevas web, pour [VEHICLE_BOX_DP] points dessinés. */
        const val UNITS = 48f
        const val VEHICLE_BOX_DP = 32f

        fun noseUnits(mode: TransportMode): Float = when (mode) {
            TransportMode.BOAT -> 14f
            TransportMode.TER -> 18f
            else -> 16f
        }

        const val TAIL_UNITS = 12f

        fun wingUnits(mode: TransportMode): Float = when (mode) {
            TransportMode.BOAT -> 12f
            TransportMode.TER -> 10f
            else -> 11f
        }

        /**
         * La largeur maximale de la coque, en unités : la courbe quadratique de la caisse culmine
         * à 0,769 de l'aile (dérivée nulle à t = 0,769), de chaque côté.
         */
        fun hullWidthUnits(mode: TransportMode): Float = 2f * HULL_PEAK * wingUnits(mode)

        /** La largeur de la bande de toit : ≈ 24 % de la largeur de la caisse vue du ciel. */
        fun roofStripeUnits(mode: TransportMode): Float = ROOF_STRIPE_RATIO * hullWidthUnits(mode)

        /** Du nez au talon de la coque, en points dessinés. */
        fun lengthDp(mode: TransportMode): Float =
            (noseUnits(mode) + TAIL_UNITS) * VEHICLE_BOX_DP / UNITS

        const val ROOF_STRIPE_RATIO = 0.24f
        private const val HULL_PEAK = 0.769f
    }

    // ----------------------------------------------------------------- les images

    /**
     * Une silhouette à dessiner : ce qui la distingue des autres, donc ce qui entre dans le nom.
     *
     * Une image par **couleur** et non par ligne : le réseau nantais compte 138 lignes pour 41
     * teintes, et mémoïser par ligne fabriquerait trois fois plus d'images identiques.
     */
    data class IconSpec(
        val mode: TransportMode,
        val accent: Int,
        val live: Boolean,
        val contoured: Boolean,
        val night: Boolean,
    ) {
        val name: String
            get() = "vehicle-${mode.name.lowercase()}-${key(accent)}" +
                (if (live) "-live" else "-scheduled") +
                (if (contoured) "-chosen" else "") +
                (if (night) "-night" else "-day")
    }
}

/**
 * Le halo d'un véhicule choisi : un anneau fin posé au sol, et, pour le véhicule suivi, une lueur
 * qui pulse. **Transparent au centre** : jamais un disque plein, jamais un cône de cap — ce serait
 * le puck de l'utilisateur, qui est précisément un disque et un cône.
 *
 * Valeurs de la spec commune ; voir [VehicleLivery].
 */
internal object VehicleHalo {

    /** Ce qu'il faut peindre à une image : le diamètre de l'anneau, et son opacité. */
    data class Paint(
        val diameterPt: Double,
        val opacity: Double,
        /** Vrai pour la lueur diffuse du véhicule suivi : le choisi seul n'a que l'anneau. */
        val glow: Boolean,
    )

    /**
     * Le halo d'un rôle, à l'instant [seconds] de l'horloge de la couche.
     *
     * Rend `null` quand le véhicule n'a pas de halo — normal ou à l'arrière-plan.
     *
     * @param screenLengthPt la longueur du véhicule **à l'écran**, en points : celle de sa
     *   silhouette de loin, celle de son volume de près (voir [screenLengthPt]).
     * @param reduceMotion « réduire les animations » : pas de pulsation, l'anneau reste fixe.
     */
    fun paint(
        role: VehicleLivery.Role,
        screenLengthPt: Double,
        seconds: Double,
        reduceMotion: Boolean,
    ): Paint? = when (role) {
        VehicleLivery.Role.SELECTED -> Paint(
            diameterPt = SELECTED_DIAMETER_RATIO * screenLengthPt,
            opacity = SELECTED_OPACITY,
            glow = false,
        )
        VehicleLivery.Role.FOLLOWED -> {
            val base = (FOLLOWED_DIAMETER_RATIO * screenLengthPt).coerceIn(FOLLOWED_MIN_PT, FOLLOWED_MAX_PT)
            if (reduceMotion) {
                Paint(diameterPt = base, opacity = SELECTED_OPACITY, glow = true)
            } else {
                val wave = wave(seconds)
                Paint(
                    diameterPt = base * (SCALE_MID + SCALE_SWING * wave),
                    opacity = OPACITY_MID + OPACITY_SWING * wave,
                    glow = true,
                )
            }
        }
        else -> null
    }

    /** Le sinus de la pulsation, de −1 à 1, de période [PERIOD_SECONDS]. */
    fun wave(seconds: Double): Double = sin(2 * PI * seconds / PERIOD_SECONDS)

    /**
     * La longueur du véhicule à l'écran, en points.
     *
     * De loin, c'est celle de sa silhouette ; de près, celle de son volume — exagération comprise ;
     * entre les deux, au fil du fondu qui les croise ([volumeFade], de 0 à 1).
     *
     * @param meters sa longueur réelle.
     * @param volumeScale l'exagération appliquée au volume.
     * @param metersPerPoint ce que couvre un point d'écran, à sa latitude et à ce zoom.
     */
    fun screenLengthPt(
        silhouettePt: Double,
        meters: Double,
        volumeScale: Double,
        metersPerPoint: Double,
        volumeFade: Double,
    ): Double {
        val volume = if (metersPerPoint.isFinite() && metersPerPoint > 0) {
            meters * volumeScale / metersPerPoint
        } else {
            silhouettePt
        }
        val fade = volumeFade.coerceIn(0.0, 1.0)
        return silhouettePt + (volume - silhouettePt) * fade
    }

    /** Anneau fixe du véhicule choisi : Ø = 1,3 × longueur d'écran, opacité 0,9. */
    const val SELECTED_DIAMETER_RATIO = 1.3
    const val SELECTED_OPACITY = 0.9

    /** Véhicule suivi : Ø = 1,55 × longueur d'écran, **borné** à 44–140 points. */
    const val FOLLOWED_DIAMETER_RATIO = 1.55
    const val FOLLOWED_MIN_PT = 44.0
    const val FOLLOWED_MAX_PT = 140.0

    /** La pulsation : 2,8 s, échelle 0,94 → 1,06, opacité 0,55 → 0,95. */
    const val PERIOD_SECONDS = 2.8
    const val SCALE_MIN = 0.94
    const val SCALE_MAX = 1.06
    const val OPACITY_MIN = 0.55
    const val OPACITY_MAX = 0.95

    private const val SCALE_MID = (SCALE_MIN + SCALE_MAX) / 2
    private const val SCALE_SWING = (SCALE_MAX - SCALE_MIN) / 2
    private const val OPACITY_MID = (OPACITY_MIN + OPACITY_MAX) / 2
    private const val OPACITY_SWING = (OPACITY_MAX - OPACITY_MIN) / 2
}
