package io.aule.android.core.map

import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.geo.SolarPosition

/**
 * Ce qu'il faut écrire dans le bloc `light` du style pour que la ville soit
 * éclairée comme elle l'est dehors.
 *
 * ⚠️ **Ce ne sont pas des ombres.** Le nuanceur des extrusions de MapLibre
 * calcule `dot(normal, u_lightpos)` face par face : une façade tournée vers la
 * lumière s'éclaire, celle d'en face retombe sur un plancher d'ambiant. Aucun
 * bâtiment n'assombrit son voisin ni le trottoir — le moteur n'a pas de tampon
 * de profondeur pour ça. Ce qu'on gagne est le **volume** : la même rue ne se
 * lit pas pareil à huit heures et à midi, et c'est ce qui la rend reconnaissable.
 */
data class SunlightSetting(
    /** D'où vient la lumière, en degrés depuis le nord — `a` du style. */
    val azimuthDegrees: Double,

    /**
     * De quelle hauteur, comptée **depuis le zénith** — `p` du style.
     *
     * C'est le complément de l'élévation, et l'inversion se paie : zéro veut
     * dire « à la verticale », quatre-vingt-dix « au ras de l'horizon ». La
     * confondre avec une hauteur donne une carte éclairée par en dessous à midi.
     */
    val polarDegrees: Double,

    val color: AuleRgba,

    /** De 0 à 1 : combien la direction pèse, face au plancher d'ambiant. */
    val intensity: Double,
)

/**
 * Du soleil réel à la lumière du style.
 *
 * La règle tient en une phrase : **la direction est de la physique, la couleur
 * et la force sont du produit.** L'endroit d'où vient la lumière ne se négocie
 * pas — c'est là qu'est le soleil, et c'est ce qui donne le relief. Ce qu'on
 * dose, c'est ce que l'écran a le droit de renvoyer : l'ambiance sombre est un
 * choix de luminosité d'écran, pas une affirmation sur l'heure qu'il est, et
 * une ville qui flamberait en plein mode nuit trahirait ce choix.
 */
object MapSunlight {

    fun of(sun: SolarPosition, ambiance: MapAmbiance): SunlightSetting {
        val elevation = sun.elevationDegrees
        val risen = ramp(elevation, DIRECTION_FROM, DIRECTION_TO)
        val daylight = ramp(elevation, DAYLIGHT_FROM, DAYLIGHT_TO)

        // ⚠️ **L'écrêtage au ras de l'horizon n'est pas une précaution de
        // style.** Sous l'horizon, le complément de l'élévation dépasse 90° et
        // la lumière passerait sous la ville : les toits s'éteindraient, les
        // façades s'allumeraient par le bas, et toute la carte prendrait cet
        // air de masque de fête foraine qu'on ne s'explique pas au premier
        // regard.
        val sunPolar = (90.0 - elevation).coerceIn(MIN_POLAR, HORIZON_POLAR)
        // Le soleil couché, on remonte la lumière vers le haut plutôt que de la
        // laisser raser : à cette heure, elle ne représente plus un astre mais
        // un ciel, et un ciel n'a pas de direction.
        val polar = mix(NIGHT_POLAR, sunPolar, risen)

        val zenithColor = when (ambiance) {
            MapAmbiance.LIGHT -> DAY_WHITE
            MapAmbiance.DARK -> NIGHT_BLUE
        }
        val dayIntensity = when (ambiance) {
            MapAmbiance.LIGHT -> DAY_INTENSITY
            MapAmbiance.DARK -> DARK_DAY_INTENSITY
        }

        // La hauteur commande deux choses à la fois, et pour la même raison :
        // l'épaisseur d'atmosphère traversée. Elle fait la couleur — l'or du
        // ras de l'horizon fond dans les premiers degrés — et elle fait la
        // **part** que le soleil prend dans l'éclairage.
        val climbed = ramp(elevation, 0.0, NEUTRAL_FROM)
        val dayColor = mix(GOLDEN, zenithColor, climbed)

        // ⚠️ **La force suit la hauteur, et pas seulement le jour.** Le
        // nuanceur des extrusions n'a pas de terme de ciel : ce qu'une face ne
        // reçoit pas du soleil, elle le prend sur un plancher qui vaut
        // `1 − force`. Une force de plein midi appliquée à un soleil rasant
        // éteint donc les toits, qui sont horizontaux et ne reçoivent presque
        // rien — la ville prend des toitures brunes à huit heures du matin,
        // ce qu'on a vu sur le S21 avant d'écrire ces lignes. C'est aussi le
        // sens physique : au ras de l'horizon le rayon direct est affaibli et
        // c'est le ciel qui éclaire, donc la part directionnelle doit baisser.
        val direct = mix(dayIntensity * GRAZING_SHARE, dayIntensity, climbed)

        return SunlightSetting(
            azimuthDegrees = sun.azimuthDegrees,
            polarDegrees = polar,
            color = mix(NIGHT_BLUE, dayColor, daylight),
            intensity = mix(NIGHT_INTENSITY, direct, daylight),
        )
    }

    /**
     * Les hauteurs entre lesquelles la lumière **prend une direction**.
     *
     * Elles encadrent l'horizon au plus près : dès que le soleil l'a franchi,
     * c'est lui qui décide d'où vient la lumière, et le rasant de l'aube est
     * précisément ce qu'on veut voir. Distinctes de [DAYLIGHT_FROM] et
     * [DAYLIGHT_TO] pour cette raison — la direction devient vraie d'un coup,
     * là où la clarté, elle, continue de monter longtemps après.
     */
    const val DIRECTION_FROM = -4.0
    const val DIRECTION_TO = 2.0

    /**
     * Celles entre lesquelles le jour se lève, pour la couleur et la force.
     *
     * Le bas est le **crépuscule civil** : sous six degrés d'horizon, on ne lit
     * plus un journal dehors, et la carte n'a plus de raison de faire comme s'il
     * faisait jour.
     */
    const val DAYLIGHT_FROM = -6.0
    const val DAYLIGHT_TO = 6.0

    /**
     * L'angle polaire de la nuit : haut, donc sans direction marquée.
     *
     * C'est la valeur que le style sombre porte déjà dans son JSON. La reprendre
     * n'est pas de la superstition : elle a été réglée à l'œil sur cette
     * palette, et une carte de nuit qui repart de ce point exact est une carte
     * dont la nuit n'a pas changé.
     */
    const val NIGHT_POLAR = 32.0

    /**
     * Le plus haut qu'on laisse monter la lumière.
     *
     * Sous les tropiques, le soleil de midi passe au zénith : la lumière tombe
     * alors d'aplomb, toutes les façades reçoivent la même chose et la ville
     * s'aplatit d'un coup. Douze degrés suffisent à garder un côté éclairé et
     * un côté à l'ombre.
     */
    const val MIN_POLAR = 12.0

    /** Le ras de l'horizon : la lumière ne descend jamais plus bas. */
    const val HORIZON_POLAR = 90.0

    /** La hauteur au-delà de laquelle la lumière a fini de blanchir. */
    const val NEUTRAL_FROM = 25.0

    /** L'or du ras de l'horizon. */
    val GOLDEN = AuleRgba(0xFFD2A0)

    /** Le blanc chaud du plein jour — celui du style clair. */
    val DAY_WHITE = AuleRgba(0xFFF6E8)

    /** Le bleu pâle de la nuit — celui du style sombre. */
    val NIGHT_BLUE = AuleRgba(0xDCE6F5)

    /**
     * Ce qu'il reste de force au ras de l'horizon, en part du plein jour.
     *
     * Trois cinquièmes : assez pour que le rasant se voie sur les façades,
     * assez peu pour que le plancher d'ambiant remonte et garde les toits
     * lisibles. C'est le réglage qui décide si une ville du matin se lit
     * encore, et il vaut mieux le régler à l'œil qu'à la physique — le rendu
     * n'a pas de ciel à simuler, seulement un plancher à doser.
     */
    const val GRAZING_SHARE = 0.6

    const val DAY_INTENSITY = 0.55
    const val DARK_DAY_INTENSITY = 0.30
    const val NIGHT_INTENSITY = 0.20

    /** Où [value] se situe entre [from] et [to], borné à `[0, 1]`. */
    private fun ramp(value: Double, from: Double, to: Double): Double =
        ((value - from) / (to - from)).coerceIn(0.0, 1.0)

    private fun mix(from: Double, to: Double, t: Double): Double = from + (to - from) * t

    private fun mix(from: AuleRgba, to: AuleRgba, t: Double): AuleRgba = AuleRgba(
        red = mix(from.red, to.red, t),
        green = mix(from.green, to.green, t),
        blue = mix(from.blue, to.blue, t),
    )
}
