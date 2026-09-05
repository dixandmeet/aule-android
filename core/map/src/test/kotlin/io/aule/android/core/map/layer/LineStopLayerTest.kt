package io.aule.android.core.map.layer

import io.aule.android.core.geo.Coordinate
import io.aule.android.core.model.LineJourneyStop
import io.aule.android.core.model.buildLineStopMarkers
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LineStopLayerTest {

    @Test
    fun `l identifiant de la couche est conforme`() {
        val layer = LineStopLayer()
        assertEquals("aule.line-stop", layer.id)
    }

    @Test
    fun `les arrets se posent avant le montage sans lever`() {
        val layer = LineStopLayer()

        // Publier dans le vide est le mode d'échec de cette couche : la desserte
        // arrive, la source n'existe pas encore, et c'est `mount` qui republie.
        layer.setStops(markers, colorHex = "#F3A11C", focusedStopId = markers.first().id)
    }

    /**
     * Un arrêt désigné dans le parcours précédent n'existe plus dans celui-ci :
     * le garder laisserait la pastille pleine posée sur un point que plus rien
     * n'occupe, et le reste de la desserte assourdi pour personne.
     */
    @Test
    fun `un arret vise qui n est plus dans la desserte ne survit pas`() {
        val layer = LineStopLayer()
        layer.setStops(markers, focusedStopId = "un-autre-parcours#0")
    }

    @Test
    fun `une ligne sans couleur se peint quand meme`() {
        val layer = LineStopLayer()
        layer.setStops(markers, colorHex = null)
    }

    @Test
    fun `oublier le style reinitialise les references de source sans lever`() {
        val layer = LineStopLayer()
        layer.forgetStyle()
    }

    private companion object {
        val markers = buildLineStopMarkers(
            "C3-3",
            listOf(
                LineJourneyStop("s1", "Beaujoire", Coordinate(47.25, -1.53)),
                LineJourneyStop("s2", "Halvèque", Coordinate(47.24, -1.53)),
                LineJourneyStop("s3", "Sans position", null),
            ),
        )
    }
}
