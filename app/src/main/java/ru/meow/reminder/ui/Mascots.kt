package ru.meow.reminder.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** One quiet capsule, no text or mascot on the loading screen. */
@Composable
fun RotatingPill(modifier: Modifier = Modifier, animate: Boolean = true) {
    val angle = if (animate) {
        val transition = rememberInfiniteTransition(label = "Загрузка")
        val rotation by transition.animateFloat(-35f, 325f,
            infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "Вращение таблетки")
        rotation
    } else -35f
    val accent = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.semantics { contentDescription = "Загрузка: таблетка" }) {
        val u = size.minDimension / 96f
        val bounds = Rect(center.x - 27 * u, center.y - 12 * u, center.x + 27 * u, center.y + 12 * u)
        rotate(angle) {
            val outline = Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(12 * u))) }
            drawPath(outline, fill)
            clipPath(outline) { drawRect(accent.copy(alpha = .75f), bounds.topLeft, Size(27 * u, 24 * u)) }
            drawPath(outline, accent, style = Stroke(1.8f * u))
            drawLine(accent, Offset(center.x, bounds.top), Offset(center.x, bounds.bottom), 1.4f * u)
        }
    }
}

/** Rounded cheeks, small ears and glossy eyes; the charcoal kitten stays dark in both themes. */
@Composable
fun Cat(modifier: Modifier = Modifier, playing: Boolean = false, sleepy: Boolean = false) {
    val ink = Color(0xFF35313F)
    val cream = Color(0xFFFFFBF7)
    val pink = Color(0xFFEBAFC4)
    val halo = MaterialTheme.colorScheme.primaryContainer
    val pill = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = when {
        playing -> "Милый котик обнимает таблетку"
        sleepy -> "Сонный котик с розовыми щёчками"
        else -> "Милый котик с большими глазками"
    } }) {
        val u = size.minDimension / 120f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        drawOval(halo.copy(alpha = .65f), p(8f, 14f), Size(104 * u, 100 * u))
        val head = Path().apply {
            moveTo(21 * u, 58 * u)
            quadraticTo(19 * u, 39 * u, 26 * u, 24 * u)
            quadraticTo(28 * u, 20 * u, 32 * u, 25 * u)
            lineTo(45 * u, 38 * u)
            quadraticTo(60 * u, 33 * u, 75 * u, 38 * u)
            lineTo(88 * u, 25 * u)
            quadraticTo(92 * u, 20 * u, 94 * u, 24 * u)
            quadraticTo(101 * u, 39 * u, 99 * u, 58 * u)
            cubicTo(111 * u, 92 * u, 91 * u, 105 * u, 60 * u, 105 * u)
            cubicTo(29 * u, 105 * u, 9 * u, 92 * u, 21 * u, 58 * u)
            close()
        }
        drawPath(head, ink)
        val leftEar = Path().apply { moveTo(27 * u, 42 * u); lineTo(29 * u, 30 * u); lineTo(39 * u, 42 * u); close() }
        val rightEar = Path().apply { moveTo(81 * u, 42 * u); lineTo(91 * u, 30 * u); lineTo(93 * u, 42 * u); close() }
        drawPath(leftEar, pink.copy(alpha = .8f)); drawPath(rightEar, pink.copy(alpha = .8f))
        if (sleepy) {
            drawArc(cream, 195f, 150f, false, p(33f, 61f), Size(16 * u, 12 * u), style = Stroke(2.8f * u, cap = StrokeCap.Round))
            drawArc(cream, 195f, 150f, false, p(71f, 61f), Size(16 * u, 12 * u), style = Stroke(2.8f * u, cap = StrokeCap.Round))
        } else {
            for (x in listOf(35f, 72f)) {
                drawOval(cream, p(x, 57f), Size(14 * u, 19 * u))
                drawOval(ink, p(x + 3, 61f), Size(8 * u, 12 * u))
                drawCircle(cream, 2.7f * u, p(x + 4.5f, 62f))
                drawCircle(cream, 1.2f * u, p(x + 9f, 68f))
            }
        }
        drawOval(pink.copy(alpha = .7f), p(26f, 77f), Size(16 * u, 7 * u))
        drawOval(pink.copy(alpha = .7f), p(78f, 77f), Size(16 * u, 7 * u))
        val nose = Path().apply { moveTo(56 * u, 77 * u); quadraticTo(60 * u, 75 * u, 64 * u, 77 * u); lineTo(60 * u, 81 * u); close() }
        drawPath(nose, pink)
        val smile = Path().apply {
            moveTo(51 * u, 84 * u); quadraticTo(56 * u, 89 * u, 60 * u, 82 * u)
            quadraticTo(64 * u, 89 * u, 69 * u, 84 * u)
        }
        drawPath(smile, cream, style = Stroke(1.8f * u, cap = StrokeCap.Round))
        for (y in listOf(72f, 78f)) {
            drawLine(cream.copy(alpha = .45f), p(17f, y - 1), p(28f, y + 1), 1.2f * u, StrokeCap.Round)
            drawLine(cream.copy(alpha = .45f), p(92f, y + 1), p(103f, y - 1), 1.2f * u, StrokeCap.Round)
        }
        if (playing) {
            rotate(-35f, p(84f, 100f)) {
                drawRoundRect(pill, p(70f, 93f), Size(29 * u, 13 * u), CornerRadius(6.5f * u))
                drawLine(cream, p(84f, 94f), p(84f, 105f), 1.4f * u)
            }
            drawOval(ink, p(65f, 92f), Size(16 * u, 13 * u))
        }
    }
}
