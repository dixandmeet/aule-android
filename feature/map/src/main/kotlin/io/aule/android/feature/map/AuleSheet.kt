package io.aule.android.feature.map

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.auleShadow
import io.aule.android.core.designsystem.component.AuleSheetHandle
import io.aule.android.core.designsystem.token.AuleElevation
import io.aule.android.core.designsystem.token.AuleMotion
import io.aule.android.core.designsystem.token.AuleRadius
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Les paliers du volet. Trois suffisent : entrouvert, à mi-hauteur, déployé.
 *
 * Ce que le volet masque réellement, en pixels, est publié à la caméra : elle
 * cadre sur la bande visible plutôt que sur l'écran entier.
 */
internal object SheetStop {
    const val PEEK = 0.30f
    const val HALF = 0.55f
    const val FULL = 0.90f

    private val all = floatArrayOf(PEEK, HALF, FULL)

    /** `null` : on est assez bas pour fermer. */
    fun snap(fraction: Float): Float? {
        if (fraction < PEEK * 0.5f) return null
        return all.minBy { abs(it - fraction) }
    }
}

/**
 * Un volet posé sur la carte, sans Material.
 *
 * La carte reste vivante au-dessus de lui : le volet n'occupe que le bas de
 * l'écran, et les touches hors de sa boîte vont à MapLibre. C'est ce qui
 * distingue une app de mobilité d'un tableau de bord.
 */
@Composable
internal fun BoxScope.AuleSheet(
    parentHeightPx: Int,
    handleDescription: String,
    paneTitle: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onHeightPx: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = AuleTheme.tokens
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val fraction = remember { Animatable(SheetStop.PEEK) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        onHeightPx(parentHeightPx * fraction.value)
    }

    PredictiveBackHandler { progress ->
        val start = fraction.value
        try {
            progress.collect { event ->
                fraction.snapTo((start * (1f - event.progress)).coerceAtLeast(0f))
            }
            onDismiss()
        } catch (cancelled: CancellationException) {
            fraction.animateTo(
                start.coerceAtLeast(SheetStop.PEEK),
                tween(AuleMotion.GLIDE_MS),
            )
            throw cancelled
        }
    }

    val heightDp = with(density) { (parentHeightPx * fraction.value).toDp() }
    val shape = RoundedCornerShape(topStart = AuleRadius.xl, topEnd = AuleRadius.xl)

    Column(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(heightDp)
            .auleShadow(AuleElevation.LIFTED, shape)
            .clip(shape)
            .background(tokens.surface.color)
            .navigationBarsPadding()
            .focusRequester(focusRequester)
            .focusable()
            .onSizeChanged { onHeightPx(it.height.toFloat()) }
            .semantics(mergeDescendants = false) {
                this.paneTitle = paneTitle
                isTraversalGroup = true
                traversalIndex = -2f
                customActions = listOf(
                    CustomAccessibilityAction(dismissLabel) {
                        onDismiss()
                        true
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = handleDescription }
                .pointerInput(parentHeightPx) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (parentHeightPx <= 0) return@detectVerticalDragGestures
                            val next = (fraction.value - dragAmount / parentHeightPx)
                                .coerceIn(0f, SheetStop.FULL)
                            scope.launch { fraction.snapTo(next) }
                        },
                        onDragEnd = {
                            val target = SheetStop.snap(fraction.value)
                            if (target == null) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    fraction.animateTo(target, tween(AuleMotion.GLIDE_MS))
                                }
                            }
                        },
                    )
                },
        ) {
            AuleSheetHandle()
        }
        content()
    }
}
