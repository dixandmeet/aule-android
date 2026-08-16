package io.aule.android.core.designsystem.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.aule.android.core.designsystem.AuleTheme
import io.aule.android.core.designsystem.token.AuleControl
import io.aule.android.core.designsystem.token.AuleStroke
import io.aule.android.core.designsystem.token.AuleTouch
import kotlin.math.min

/**
 * La famille d'icônes, unique et sans exception.
 *
 * Ni emoji, ni glyphe textuel (`✕`, `←`) : les premiers changent d'aspect d'un
 * appareil à l'autre, les seconds n'ont ni la graisse ni l'alignement de leurs
 * voisins. La règle vient du style graphique commun aux trois plateformes
 * (`dashboard/docs/carte-immersive/08-style-graphique.md`, § 6).
 *
 * Chaque tracé est écrit sur une **grille de 24**, puis mis à l'échelle de sa
 * boîte. C'est ce qui garantit qu'une enveloppe de 24 dp et une loupe de 20 dp
 * portent le même poids optique.
 */
enum class AuleGlyph { MAIL, LOCK, EYE, EYE_OFF, SHIELD, SEARCH, BACK, CLOSE, MENU, SIGN_OUT, HEADING, PERSON, CHEVRON, CAMERA, EDIT, IMAGE, TRASH, CHECK, BUS, TICKET, PIN, TRAM, SUN, MOON, AUTO, FLAG, ROUTE, PLAY, SWAP }

/**
 * Dessine un glyphe centré dans la boîte courante.
 *
 * Le dessin est plafonné à [AuleControl.icon] : une cible tactile de 44 dp
 * porte une icône de 24 dp entourée de vide, elle ne porte pas une icône de
 * 44 dp. C'est cette marge qui rend le bouton confortable sans le rendre
 * criard.
 *
 * [filled] est l'état sélectionné de la famille — un contour qui se remplit,
 * jamais une seconde icône.
 */
fun DrawScope.drawAuleGlyph(
    glyph: AuleGlyph,
    color: Color,
    filled: Boolean = false,
) {
    val extent = min(size.minDimension, AuleControl.icon.toPx())
    val unit = extent / GRID
    val originX = (size.width - extent) / 2f
    val originY = (size.height - extent) / 2f
    val stroke = Stroke(
        width = AuleStroke.glyph.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    fun at(x: Float, y: Float) = Offset(originX + x * unit, originY + y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
        color = color,
        start = at(x1, y1),
        end = at(x2, y2),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )

    when (glyph) {
        AuleGlyph.MAIL -> {
            drawRoundRect(
                color = color,
                topLeft = at(2.5f, 5f),
                size = Size(19f * unit, 14f * unit),
                cornerRadius = CornerRadius(2.5f * unit),
                style = stroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(at(2.5f, 6.5f).x, at(2.5f, 6.5f).y)
                    lineTo(at(12f, 13f).x, at(12f, 13f).y)
                    lineTo(at(21.5f, 6.5f).x, at(21.5f, 6.5f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.LOCK -> {
            drawRoundRect(
                color = color,
                topLeft = at(4f, 10.5f),
                size = Size(16f * unit, 10.5f * unit),
                cornerRadius = CornerRadius(2.5f * unit),
                style = stroke,
            )
            // L'anse rejoint le corps : arrêtée au-dessus, elle dessine un
            // cadenas ouvert, qui dit l'inverse d'un champ de mot de passe.
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = at(7f, 2.5f),
                size = Size(10f * unit, 10f * unit),
                style = stroke,
            )
            line(7f, 7.5f, 7f, 10.5f)
            line(17f, 7.5f, 17f, 10.5f)
            drawCircle(color = color, radius = 1.3f * unit, center = at(12f, 15.7f))
        }

        AuleGlyph.EYE, AuleGlyph.EYE_OFF -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(2f, 12f).x, at(2f, 12f).y)
                    quadraticTo(at(7f, 5f).x, at(7f, 5f).y, at(12f, 5f).x, at(12f, 5f).y)
                    quadraticTo(at(17f, 5f).x, at(17f, 5f).y, at(22f, 12f).x, at(22f, 12f).y)
                    quadraticTo(at(17f, 19f).x, at(17f, 19f).y, at(12f, 19f).x, at(12f, 19f).y)
                    quadraticTo(at(7f, 19f).x, at(7f, 19f).y, at(2f, 12f).x, at(2f, 12f).y)
                    close()
                },
                color = color,
                style = stroke,
            )
            drawCircle(color = color, radius = 3f * unit, center = at(12f, 12f), style = stroke)
            if (glyph == AuleGlyph.EYE_OFF) line(4f, 20f, 20f, 4f)
        }

        AuleGlyph.SHIELD -> drawPath(
            path = Path().apply {
                moveTo(at(12f, 2.5f).x, at(12f, 2.5f).y)
                lineTo(at(20f, 5.8f).x, at(20f, 5.8f).y)
                lineTo(at(20f, 11.5f).x, at(20f, 11.5f).y)
                quadraticTo(at(20f, 18f).x, at(20f, 18f).y, at(12f, 21.5f).x, at(12f, 21.5f).y)
                quadraticTo(at(4f, 18f).x, at(4f, 18f).y, at(4f, 11.5f).x, at(4f, 11.5f).y)
                lineTo(at(4f, 5.8f).x, at(4f, 5.8f).y)
                close()
            },
            color = color,
            style = if (filled) Fill else stroke,
        )

        AuleGlyph.SEARCH -> {
            drawCircle(color = color, radius = 6.5f * unit, center = at(10.5f, 10.5f), style = stroke)
            line(15.2f, 15.2f, 20.5f, 20.5f)
        }

        AuleGlyph.BACK -> {
            line(20f, 12f, 4f, 12f)
            drawPath(
                path = Path().apply {
                    moveTo(at(10f, 6f).x, at(10f, 6f).y)
                    lineTo(at(4f, 12f).x, at(4f, 12f).y)
                    lineTo(at(10f, 18f).x, at(10f, 18f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.CLOSE -> {
            line(6f, 6f, 18f, 18f)
            line(18f, 6f, 6f, 18f)
        }

        // La porte ouverte et la flèche qui sort : trois côtés seulement, le
        // quatrième est l'ouverture. Une porte fermée dirait l'inverse.
        AuleGlyph.SIGN_OUT -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(11f, 3.5f).x, at(11f, 3.5f).y)
                    lineTo(at(4f, 3.5f).x, at(4f, 3.5f).y)
                    lineTo(at(4f, 20.5f).x, at(4f, 20.5f).y)
                    lineTo(at(11f, 20.5f).x, at(11f, 20.5f).y)
                },
                color = color,
                style = stroke,
            )
            line(10f, 12f, 20.5f, 12f)
            drawPath(
                path = Path().apply {
                    moveTo(at(16f, 7.5f).x, at(16f, 7.5f).y)
                    lineTo(at(20.5f, 12f).x, at(20.5f, 12f).y)
                    lineTo(at(16f, 16.5f).x, at(16f, 16.5f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.MENU -> {
            line(4f, 7f, 20f, 7f)
            line(4f, 12f, 20f, 12f)
            line(4f, 17f, 20f, 17f)
        }

        AuleGlyph.HEADING -> drawPath(
            path = Path().apply {
                moveTo(at(12f, 2.8f).x, at(12f, 2.8f).y)
                lineTo(at(19.5f, 20.5f).x, at(19.5f, 20.5f).y)
                lineTo(at(12f, 16.6f).x, at(12f, 16.6f).y)
                lineTo(at(4.5f, 20.5f).x, at(4.5f, 20.5f).y)
                close()
            },
            color = color,
            style = if (filled) Fill else stroke,
        )

        AuleGlyph.PERSON -> {
            drawCircle(color = color, radius = 4f * unit, center = at(12f, 8f), style = stroke)
            drawPath(
                path = Path().apply {
                    moveTo(at(4.5f, 20.5f).x, at(4.5f, 20.5f).y)
                    quadraticTo(at(6f, 14.5f).x, at(6f, 14.5f).y, at(12f, 14.5f).x, at(12f, 14.5f).y)
                    quadraticTo(at(18f, 14.5f).x, at(18f, 14.5f).y, at(19.5f, 20.5f).x, at(19.5f, 20.5f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.CHEVRON -> {
            line(9f, 6f, 15f, 12f)
            line(15f, 12f, 9f, 18f)
        }

        AuleGlyph.CAMERA -> {
            drawRoundRect(
                color = color,
                topLeft = at(3.5f, 9f),
                size = Size(17f * unit, 11.5f * unit),
                cornerRadius = CornerRadius(2.5f * unit),
                style = stroke,
            )
            drawRoundRect(
                color = color,
                topLeft = at(8.5f, 5.5f),
                size = Size(7f * unit, 3.5f * unit),
                cornerRadius = CornerRadius(1.5f * unit),
                style = stroke,
            )
            drawCircle(color = color, radius = 3.2f * unit, center = at(12f, 14.6f), style = stroke)
        }

        AuleGlyph.EDIT -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(14.5f, 5.5f).x, at(14.5f, 5.5f).y)
                    lineTo(at(18.5f, 9.5f).x, at(18.5f, 9.5f).y)
                    lineTo(at(9f, 19f).x, at(9f, 19f).y)
                    lineTo(at(5f, 19f).x, at(5f, 19f).y)
                    lineTo(at(5f, 15f).x, at(5f, 15f).y)
                    close()
                },
                color = color,
                style = stroke,
            )
            line(13.2f, 6.8f, 17.2f, 10.8f)
        }

        AuleGlyph.IMAGE -> {
            drawRoundRect(
                color = color,
                topLeft = at(3.5f, 5f),
                size = Size(17f * unit, 14f * unit),
                cornerRadius = CornerRadius(2.5f * unit),
                style = stroke,
            )
            drawCircle(color = color, radius = 1.6f * unit, center = at(8.5f, 9.2f), style = stroke)
            drawPath(
                path = Path().apply {
                    moveTo(at(4.5f, 17.5f).x, at(4.5f, 17.5f).y)
                    lineTo(at(10f, 11.5f).x, at(10f, 11.5f).y)
                    lineTo(at(13.5f, 15f).x, at(13.5f, 15f).y)
                    lineTo(at(16f, 12.5f).x, at(16f, 12.5f).y)
                    lineTo(at(20.5f, 17.5f).x, at(20.5f, 17.5f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.TRASH -> {
            line(8f, 7f, 16f, 7f)
            line(10.5f, 5f, 13.5f, 5f)
            line(10.5f, 5f, 10.5f, 7f)
            line(13.5f, 5f, 13.5f, 7f)
            drawPath(
                path = Path().apply {
                    moveTo(at(8f, 7f).x, at(8f, 7f).y)
                    lineTo(at(9f, 19.5f).x, at(9f, 19.5f).y)
                    lineTo(at(15f, 19.5f).x, at(15f, 19.5f).y)
                    lineTo(at(16f, 7f).x, at(16f, 7f).y)
                },
                color = color,
                style = stroke,
            )
            line(11f, 10f, 11f, 16.5f)
            line(13f, 10f, 13f, 16.5f)
        }

        AuleGlyph.CHECK -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(5f, 12.5f).x, at(5f, 12.5f).y)
                    lineTo(at(10f, 17.5f).x, at(10f, 17.5f).y)
                    lineTo(at(19.5f, 6.5f).x, at(19.5f, 6.5f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.BUS -> {
            drawRoundRect(
                color = color,
                topLeft = at(3.5f, 6.5f),
                size = Size(17f * unit, 11f * unit),
                cornerRadius = CornerRadius(2.5f * unit),
                style = stroke,
            )
            line(3.5f, 12f, 20.5f, 12f)
            drawCircle(color = color, radius = 1.6f * unit, center = at(8f, 19f), style = stroke)
            drawCircle(color = color, radius = 1.6f * unit, center = at(16f, 19f), style = stroke)
        }

        AuleGlyph.TICKET -> {
            drawRoundRect(
                color = color,
                topLeft = at(3.5f, 7f),
                size = Size(17f * unit, 10f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = stroke,
            )
            line(12f, 7f, 12f, 17f)
            drawCircle(color = color, radius = 1.2f * unit, center = at(8f, 12f), style = stroke)
        }

        AuleGlyph.PIN -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(12f, 21f).x, at(12f, 21f).y)
                    quadraticTo(at(5f, 13f).x, at(5f, 13f).y, at(5f, 10f).x, at(5f, 10f).y)
                    quadraticTo(at(5f, 4.5f).x, at(4.5f, 4.5f).y, at(12f, 4.5f).x, at(12f, 4.5f).y)
                    quadraticTo(at(19f, 4.5f).x, at(19.5f, 4.5f).y, at(19f, 10f).x, at(19f, 10f).y)
                    quadraticTo(at(19f, 13f).x, at(19f, 13f).y, at(12f, 21f).x, at(12f, 21f).y)
                    close()
                },
                color = color,
                style = if (filled) Fill else stroke,
            )
            drawCircle(color = color, radius = 2.2f * unit, center = at(12f, 10f), style = stroke)
        }

        AuleGlyph.TRAM -> {
            drawRoundRect(
                color = color,
                topLeft = at(4f, 8f),
                size = Size(16f * unit, 9f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = stroke,
            )
            line(12f, 4.5f, 12f, 8f)
            line(8f, 4.5f, 16f, 4.5f)
            drawCircle(color = color, radius = 1.5f * unit, center = at(8.5f, 19.5f), style = stroke)
            drawCircle(color = color, radius = 1.5f * unit, center = at(15.5f, 19.5f), style = stroke)
        }

        AuleGlyph.SUN -> {
            drawCircle(color = color, radius = 3.5f * unit, center = at(12f, 12f), style = stroke)
            line(12f, 3.5f, 12f, 6f)
            line(12f, 18f, 12f, 20.5f)
            line(3.5f, 12f, 6f, 12f)
            line(18f, 12f, 20.5f, 12f)
            line(6.2f, 6.2f, 8f, 8f)
            line(16f, 16f, 17.8f, 17.8f)
            line(17.8f, 6.2f, 16f, 8f)
            line(8f, 16f, 6.2f, 17.8f)
        }

        AuleGlyph.MOON -> drawPath(
            path = Path().apply {
                moveTo(at(14.5f, 4.5f).x, at(14.5f, 4.5f).y)
                quadraticTo(at(8f, 6f).x, at(8f, 6f).y, at(8f, 12.5f).x, at(8f, 12.5f).y)
                quadraticTo(at(8f, 19f).x, at(8f, 19f).y, at(16f, 19.5f).x, at(16f, 19.5f).y)
                quadraticTo(at(12f, 16f).x, at(12f, 16f).y, at(12f, 11f).x, at(12f, 11f).y)
                quadraticTo(at(12f, 7f).x, at(12f, 7f).y, at(14.5f, 4.5f).x, at(14.5f, 4.5f).y)
                close()
            },
            color = color,
            style = if (filled) Fill else stroke,
        )

        AuleGlyph.AUTO -> {
            drawCircle(color = color, radius = 7f * unit, center = at(12f, 12f), style = stroke)
            drawPath(
                path = Path().apply {
                    moveTo(at(12f, 5f).x, at(12f, 5f).y)
                    lineTo(at(12f, 19f).x, at(12f, 19f).y)
                    quadraticTo(at(18.5f, 16f).x, at(18.5f, 16f).y, at(18.5f, 12f).x, at(18.5f, 12f).y)
                    quadraticTo(at(18.5f, 8f).x, at(18.5f, 8f).y, at(12f, 5f).x, at(12f, 5f).y)
                    close()
                },
                color = color,
                style = Fill,
            )
        }

        AuleGlyph.FLAG -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(12f, 3.5f).x, at(12f, 3.5f).y)
                    lineTo(at(21f, 20f).x, at(21f, 20f).y)
                    lineTo(at(3f, 20f).x, at(3f, 20f).y)
                    close()
                },
                color = color,
                style = if (filled) Fill else stroke,
            )
            line(12f, 10f, 12f, 14.5f)
            drawCircle(color = color, radius = 1.1f * unit, center = at(12f, 17.2f))
        }

        AuleGlyph.ROUTE -> {
            drawPath(
                path = Path().apply {
                    moveTo(at(6f, 19f).x, at(6f, 19f).y)
                    lineTo(at(6f, 12f).x, at(6f, 12f).y)
                    quadraticTo(at(6f, 6.5f).x, at(6f, 6.5f).y, at(12f, 6.5f).x, at(12f, 6.5f).y)
                    lineTo(at(18.5f, 6.5f).x, at(18.5f, 6.5f).y)
                },
                color = color,
                style = stroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(at(12f, 12f).x, at(12f, 12f).y)
                    quadraticTo(at(18.5f, 12f).x, at(18.5f, 12f).y, at(18.5f, 19f).x, at(18.5f, 19f).y)
                },
                color = color,
                style = stroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(at(15.5f, 4f).x, at(15.5f, 4f).y)
                    lineTo(at(19.5f, 6.5f).x, at(19.5f, 6.5f).y)
                    lineTo(at(15.5f, 9f).x, at(15.5f, 9f).y)
                },
                color = color,
                style = stroke,
            )
        }

        AuleGlyph.PLAY -> {
            drawCircle(color = color, radius = 9f * unit, center = at(12f, 12f), style = stroke)
            drawPath(
                path = Path().apply {
                    moveTo(at(10f, 7.5f).x, at(10f, 7.5f).y)
                    lineTo(at(17f, 12f).x, at(17f, 12f).y)
                    lineTo(at(10f, 16.5f).x, at(10f, 16.5f).y)
                    close()
                },
                color = color,
                style = if (filled) Fill else stroke,
            )
        }

        AuleGlyph.SWAP -> {
            line(5f, 8f, 15.5f, 8f)
            drawPath(
                path = Path().apply {
                    moveTo(at(13f, 5f).x, at(13f, 5f).y)
                    lineTo(at(19f, 8f).x, at(19f, 8f).y)
                    lineTo(at(13f, 11f).x, at(13f, 11f).y)
                },
                color = color,
                style = stroke,
            )
            line(8.5f, 16f, 19f, 16f)
            drawPath(
                path = Path().apply {
                    moveTo(at(11f, 13f).x, at(11f, 13f).y)
                    lineTo(at(5f, 16f).x, at(5f, 16f).y)
                    lineTo(at(11f, 19f).x, at(11f, 19f).y)
                },
                color = color,
                style = stroke,
            )
        }
    }
}

/**
 * Une icône décorative.
 *
 * Sans [contentDescription] elle sort de l'arbre d'accessibilité : dans un
 * champ ou un bouton, c'est le parent qui parle, et une enveloppe annoncée en
 * plus du libellé « E-mail » ne fait que rallonger la lecture.
 */
@Composable
fun AuleIcon(
    glyph: AuleGlyph,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = AuleControl.icon,
    filled: Boolean = false,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                if (contentDescription == null) {
                    hideFromAccessibility()
                } else {
                    this.contentDescription = contentDescription
                }
            }
            .drawBehind { drawAuleGlyph(glyph, tint, filled) },
    )
}

/**
 * Une icône qu'on touche.
 *
 * La cible fait [AuleTouch.minimum] quand le dessin en fait 24 : c'est la
 * marge invisible qui distingue un bouton atteignable d'un bouton qu'on
 * manque.
 */
@Composable
fun AuleIconButton(
    glyph: AuleGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AuleTheme.tokens.onSurface.color,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(AuleTouch.minimum)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            }
            .drawBehind { drawAuleGlyph(glyph, tint, filled) },
    )
}

private const val GRID = 24f
