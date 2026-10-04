package io.aule.android.feature.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import io.aule.android.core.designsystem.reduceMotionEnabled
import kotlin.math.max
import io.aule.android.core.designsystem.token.AuleAlpha
import io.aule.android.core.designsystem.foundation.AuleVoyageurPalette

/** Géométrie de dashboard/lib/auth/scene-network.ts, calculée une fois par scène. */
@Composable
internal fun RegistrationNetwork(modifier: Modifier, wide: Boolean, animate: Boolean) {
    val colors = MaterialTheme.colorScheme
    val lines = remember { registrationNetworkLines() }
    val reduced = reduceMotionEnabled()
    val phase = remember { mutableFloatStateOf(0f) }
    val clock = if (reduced || !animate) null else {
        val transition = rememberInfiniteTransition(label = "registration-network")
        transition.animateFloat(0f, NETWORK_CYCLE_SECONDS,
            infiniteRepeatable(tween(NETWORK_CYCLE_MILLIS, easing = LinearEasing)), label = "network-time")
    }
    // La boucle est supprimée hors écran ; sa phase reprend sans saut au retour.
    DisposableEffect(clock) {
        onDispose {
            if (clock != null) phase.floatValue = (phase.floatValue + clock.value) % NETWORK_CYCLE_SECONDS
        }
    }
    val sheen = remember(colors.inverseOnSurface) { Brush.verticalGradient(listOf(colors.inverseOnSurface.copy(alpha = AuleAlpha.HALO_SOFT), Color.Transparent)) }
    val tones = listOf(AuleVoyageurPalette.PrimaryBright.color, colors.primary, lerp(colors.inverseSurface, colors.primary, 0.55f))
    Canvas(modifier) {
        // Le SVG mobile est carré, haut comme le bandeau et décalé de 10 % à droite.
        val side = if (wide) max(size.width, size.height) else size.height
        val left = if (wide) (size.width - side) / 2 else size.width * 1.1f - side
        withTransform({ translate(left, 0f); scale(side / NETWORK_SIZE, side / NETWORK_SIZE, Offset.Zero) }) {
            lines.indices.reversed().forEach { i ->
                drawPath(lines[i].path, tones[i], style = LINE_STROKE)
            }
            lines.forEachIndexed { i, line ->
                line.stopPoints.forEach { point ->
                    drawCircle(colors.inverseSurface, 12f, point)
                    drawCircle(tones[i], 12f, point, style = STOP_STROKE)
                }
            }
            // Les deux correspondances du dessin web ; aucun calcul d'intersection par frame.
            HUBS.forEach { point ->
                drawCircle(colors.inverseSurface, 19f, point)
                drawCircle(colors.inverseOnSurface, 19f, point, style = HUB_STROKE)
            }
            val elapsed = phase.floatValue + (clock?.value ?: 0f)
            lines.forEach { line ->
                val fraction = networkVehicleFraction(elapsed + line.offset, line.duration, line.stops)
                val point = line.measure.getPosition(line.measure.length * fraction)
                drawCircle(colors.inversePrimary.copy(alpha = AuleAlpha.GLOW), 26f, point)
                drawCircle(colors.inversePrimary, 10f, point)
            }
        }
        drawRect(sheen)
    }
}

/** Vitesse constante entre les arrêts, pause de 1,6 s à chacun comme sur le web. */
internal fun networkVehicleFraction(elapsed: Float, duration: Float, stops: List<Float>): Float {
    var remaining = ((elapsed % duration) + duration) % duration
    val travelTime = duration - stops.size * STOP_PAUSE_SECONDS
    var previous = 0f
    for (stop in stops) {
        val segmentTime = (stop - previous) * travelTime
        if (remaining < segmentTime) return previous + remaining / travelTime
        remaining -= segmentTime
        if (remaining < STOP_PAUSE_SECONDS) return stop
        remaining -= STOP_PAUSE_SECONDS
        previous = stop
    }
    return (previous + remaining / travelTime).coerceIn(0f, 1f)
}

private class NetworkLine(val path: Path, val stops: List<Float>, val duration: Float, val offset: Float) {
    val measure = PathMeasure().apply { setPath(path, false) }
    val stopPoints = stops.map { measure.getPosition(measure.length * it) }
}

private fun registrationNetworkLines() = listOf(
    NetworkLine(Path().apply {
        moveTo(-700f, 300f); cubicTo(220f, 300f, 380f, 256f, 600f, 256f)
        cubicTo(800f, 256f, 900f, 110f, 1040f, 0f)
    }, listOf(0.45f, 0.6f, 0.93f), 38f, 21f),
    NetworkLine(Path().apply {
        moveTo(520f, -80f); cubicTo(520f, 90f, 548f, 206f, 600f, 256f)
        cubicTo(690f, 342f, 880f, 320f, 905f, 430f); cubicTo(930f, 528f, 980f, 590f, 1040f, 660f)
    }, listOf(0.18f, 0.6f, 0.82f), 24f, 4f),
    NetworkLine(Path().apply {
        moveTo(1040f, 390f); cubicTo(900f, 390f, 866f, 200f, 720f, 110f)
        cubicTo(640f, 60f, 560f, 60f, 470f, -80f)
    }, listOf(0.16f, 0.66f), 22f, 14f),
)

private const val NETWORK_SIZE = 1000f
private const val STOP_PAUSE_SECONDS = 1.6f
// PPCM des durées : pas de saut de phase lors du redémarrage de l'horloge.
private const val NETWORK_CYCLE_SECONDS = 5016f
private const val NETWORK_CYCLE_MILLIS = 5_016_000


private val LINE_STROKE = Stroke(18f, cap = StrokeCap.Round)
private val STOP_STROKE = Stroke(6f)
private val HUB_STROKE = Stroke(7f)
private val HUBS = listOf(Offset(600f, 256f), Offset(811.802f, 191.033f))
