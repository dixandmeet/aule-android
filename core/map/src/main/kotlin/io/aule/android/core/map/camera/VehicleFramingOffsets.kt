package io.aule.android.core.map.camera

import io.aule.android.core.geo.GeoMath

/**
 * L'écart entre le cadrage que la vue GPS d'un véhicule demande et celui que la main a posé.
 *
 * Le zoom du suivi vient de l'allure du véhicule, son cap de sa trajectoire : à chaque battement la
 * caméra les réécrit. Sans mémoire, un pincement ne durerait que jusqu'au battement suivant — ou il
 * faudrait que la vue GPS cesse pour qu'il dure. On retient **l'écart**, pas la valeur : le véhicule
 * qui accélère recule toujours la caméra, et un doigt qui l'a rapprochée la garde rapprochée d'autant.
 *
 * Jumeau de `VehicleFramingOffsets` (`MapController.swift`).
 */
data class VehicleFramingOffsets(
    val zoom: Double,
    val pitch: Double,
    val bearing: Double,
) {
    /** Le cadrage du suivi, avec l'écart de la main par-dessus. */
    fun applied(target: CameraTarget, maxPitch: Double): CameraTarget = target.copy(
        zoom = target.zoom + zoom,
        pitch = (target.pitch + pitch).coerceIn(0.0, maxPitch),
        bearing = GeoMath.normalizeHeading(target.bearing + bearing),
    )

    companion object {
        val ZERO = VehicleFramingOffsets(zoom = 0.0, pitch = 0.0, bearing = 0.0)

        /** L'écart entre la caméra vivante et ce que le suivi demandait à cet instant. */
        fun reading(zoom: Double, tilt: Double, bearing: Double, base: CameraTarget) = VehicleFramingOffsets(
            zoom = zoom - base.zoom,
            pitch = tilt - base.pitch,
            bearing = GeoMath.shortestHeadingDelta(from = base.bearing, to = bearing),
        )
    }
}
