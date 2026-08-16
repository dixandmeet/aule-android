package io.aule.android.core.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import io.aule.android.core.designsystem.token.AuleBrand
import io.aule.android.core.designsystem.token.AuleRgba
import io.aule.android.core.designsystem.token.AuleTokens
import io.aule.android.core.designsystem.token.markerColor
import io.aule.android.core.model.TransportMode
import org.maplibre.android.maps.Style

/**
 * Les icônes de la carte, dessinées à l'exécution.
 *
 * Le style n'a **aucun sprite** : tout ce qui se pose sur la carte est peint ici,
 * au `Canvas`, puis enregistré dans le style. C'est ce qui permet aux marqueurs
 * de suivre l'ambiance sans qu'on maintienne deux jeux d'images, et de rester
 * nets à n'importe quelle densité.
 *
 * Elles sont **ré-enregistrées à chaque changement d'ambiance** : un rechargement
 * de style vide aussi le registre d'images, et un `iconImage` qui pointe sur une
 * image absente ne dessine rien — sans erreur.
 */
internal object MapIcons {

    private const val DENSITY_SCALE = 3f

    fun stopPlaceName(mode: TransportMode) = "stop-place-${mode.name.lowercase()}"
    fun stopQuayName(mode: TransportMode) = "stop-quay-${mode.name.lowercase()}"
    fun vehicleName(mode: TransportMode) = "vehicle-${mode.name.lowercase()}"
    const val STOP_SELECTED = "stop-selected"
    const val DESTINATION = "destination"
    const val VEHICLE_HEADING = "vehicle-heading"
    const val PUCK = "user-puck"
    const val PUCK_HEADING = "user-puck-heading"
    const val HANDOVER_VEHICLE = "handover-vehicle"
    const val HANDOVER_VEHICLE_STALE = "handover-vehicle-stale"
    const val HANDOVER_STOP = "handover-stop"
    const val HANDOVER_STOP_ARRIVED = "handover-stop-arrived"

    /** Pose (ou repose) toutes les images dans le style. */
    fun register(style: Style, night: Boolean) {
        val tokens = AuleTokens.of(night)
        for (mode in TransportMode.entries) {
            style.addImage(stopPlaceName(mode), placeDot(mode.markerColor(night), tokens))
            style.addImage(stopQuayName(mode), quayDot(mode.markerColor(night), tokens))
            style.addImage(vehicleName(mode), vehicleDot(mode.markerColor(night), tokens))
        }
        style.addImage(STOP_SELECTED, selectionRing(tokens))
        style.addImage(DESTINATION, destinationPin(tokens))
        style.addImage(VEHICLE_HEADING, headingChevron(tokens))
        style.addImage(PUCK, puckDot())
        style.addImage(PUCK_HEADING, puckCone())
        style.addImage(HANDOVER_VEHICLE, handoverVehicle(filled = true, tokens))
        style.addImage(HANDOVER_VEHICLE_STALE, handoverVehicle(filled = false, tokens))
        style.addImage(HANDOVER_STOP, handoverStop(arrived = false, tokens))
        style.addImage(HANDOVER_STOP_ARRIVED, handoverStop(arrived = true, tokens))
    }

    /** Le véhicule : plus gros qu'un arrêt, cerclé de blanc pour se lire sur n'importe quoi. */
    private fun vehicleDot(fill: AuleRgba, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 22f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2.5f * DENSITY_SCALE, paint(fill.argb))
        }

    /**
     * Le chevron de cap.
     *
     * Dessiné pointe en haut : MapLibre le fera tourner de `heading` degrés, et
     * un cap de 0° veut dire « vers le nord ».
     */
    private fun headingChevron(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 34f) { canvas, size ->
            val center = size / 2f
            val path = android.graphics.Path().apply {
                moveTo(center, 1f * DENSITY_SCALE)
                lineTo(center + 4.5f * DENSITY_SCALE, 8f * DENSITY_SCALE)
                lineTo(center, 6f * DENSITY_SCALE)
                lineTo(center - 4.5f * DENSITY_SCALE, 8f * DENSITY_SCALE)
                close()
            }
            canvas.drawPath(path, paint(tokens.onSurface.argb))
        }

    /**
     * Le véhicule du collègue relevé : teal Aule, plus gros que la flotte,
     * pour qu'on le trouve d'un coup d'œil. Ouvert quand le dernier point
     * n'est plus une mesure à l'instant.
     */
    private fun handoverVehicle(filled: Boolean, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 28f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2.5f * DENSITY_SCALE, paint(AuleBrand.teal.argb))
            if (!filled) {
                canvas.drawCircle(
                    center,
                    center,
                    center * 0.38f,
                    paint(tokens.surfaceSolid.argb),
                )
            }
        }

    /**
     * Le point de relève : un anneau teal, distinct du véhicule. Plein
     * seulement une fois arrivé — ici on n'a pas encore le moteur d'ETA,
     * l'icône ouverte suffit tant que le collègue n'est pas au rendez-vous.
     */
    private fun handoverStop(arrived: Boolean, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 32f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2.5f * DENSITY_SCALE, paint(AuleBrand.teal.argb))
            if (!arrived) {
                canvas.drawCircle(
                    center,
                    center,
                    center * 0.42f,
                    paint(tokens.surfaceSolid.argb),
                )
            }
        }

    /**
     * Le lieu : un disque plein cerclé de la surface, pour qu'il se détache aussi
     * bien d'un parc que d'un bâtiment.
     */
    private fun placeDot(fill: AuleRgba, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 18f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2f * DENSITY_SCALE, paint(fill.argb))
        }

    /** Le quai : plus grand, avec un cœur clair — on le vise du doigt. */
    private fun quayDot(fill: AuleRgba, tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 26f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(center, center, center, paint(tokens.surfaceSolid.argb))
            canvas.drawCircle(center, center, center - 2f * DENSITY_SCALE, paint(fill.argb))
            canvas.drawCircle(center, center, center * 0.34f, paint(tokens.surfaceSolid.argb))
        }

    /**
     * La position de l'utilisateur : un disque net, cerclé de blanc, en vert Aule.
     *
     * Pas le bleu de Google Maps — le brief l'exclut, et un puck vert au
     * milieu d'une carte claire se repère aussi bien.
     */
    private fun puckDot(): Bitmap =
        bitmap(sizeDp = 26f) { canvas, size ->
            val center = size / 2f
            val ring = paint(0xFFFFFFFF.toInt()).apply {
                setShadowLayer(4f * DENSITY_SCALE / 3f, 0f, 1f * DENSITY_SCALE / 3f, 0x4D000000)
            }
            canvas.drawCircle(center, center, 9f * DENSITY_SCALE, ring)
            canvas.drawCircle(
                center,
                center,
                6.5f * DENSITY_SCALE,
                paint(AuleBrand.teal.argb),
            )
        }

    /**
     * Le cône de direction, en dégradé — il indique un cap, pas une certitude.
     *
     * Dessiné pointe en haut : MapLibre le fera tourner de `heading` degrés.
     */
    private fun puckCone(): Bitmap =
        bitmap(sizeDp = 60f) { canvas, size ->
            val center = size / 2f
            val radius = 28f * DENSITY_SCALE
            val wedge = Path().apply {
                moveTo(center, center)
                arcTo(
                    RectF(
                        center - radius,
                        center - radius,
                        center + radius,
                        center + radius,
                    ),
                    -90f - 24f,
                    48f,
                )
                close()
            }
            val teal = AuleBrand.teal
            val shader = RadialGradient(
                center,
                center,
                radius,
                intArrayOf(teal.opacity(0.42).argb, teal.opacity(0.0).argb),
                floatArrayOf(4f / 28f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.save()
            canvas.clipPath(wedge)
            canvas.drawCircle(center, center, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
            canvas.restore()
        }

    /**
     * L'épingle d'une adresse. Ancrée en bas : la pointe pose sur le point,
     * pas le centre du bitmap — sinon le lieu visé flotterait d'une dizaine
     * de mètres.
     */
    private fun destinationPin(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 36f) { canvas, size ->
            val cx = size / 2f
            val tipY = size - 1f * DENSITY_SCALE
            val headR = 8f * DENSITY_SCALE
            val headCy = 11f * DENSITY_SCALE
            val path = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx + headR * 0.78f, headCy + headR * 0.45f)
                arcTo(
                    RectF(cx - headR, headCy - headR, cx + headR, headCy + headR),
                    25f,
                    310f,
                    false,
                )
                close()
            }
            canvas.drawPath(path, paint(tokens.accent.argb))
            canvas.drawCircle(cx, headCy, headR * 0.38f, paint(tokens.onAccent.argb))
        }

    /** L'anneau de sélection, posé sous le marqueur choisi. */
    private fun selectionRing(tokens: AuleTokens): Bitmap =
        bitmap(sizeDp = 46f) { canvas, size ->
            val center = size / 2f
            canvas.drawCircle(
                center,
                center,
                center - 2f * DENSITY_SCALE,
                paint(tokens.accentOnSurface.opacity(0.22).argb),
            )
            canvas.drawCircle(
                center,
                center,
                center - 2f * DENSITY_SCALE,
                paint(tokens.accentOnSurface.argb).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * DENSITY_SCALE
                },
            )
        }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private inline fun bitmap(sizeDp: Float, draw: (Canvas, Float) -> Unit): Bitmap {
        val size = sizeDp * DENSITY_SCALE
        val bitmap = createBitmap(size.toInt())
        draw(Canvas(bitmap), size)
        return bitmap
    }

    private fun createBitmap(size: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
}
