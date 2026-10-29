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

/** Original minimalist mascot, restored at the user's request. */
@Composable
fun Cat(modifier: Modifier = Modifier, playing: Boolean = false, animate: Boolean = true, sleepy: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "Котик играет")
    val motion by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Лапка")
    val offset = if (playing && animate) motion else 0f
    val ink = MaterialTheme.colorScheme.onSurface
    val eyes = MaterialTheme.colorScheme.surface
    val pill = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = if (playing) "Котик играет с таблеткой" else "Довольный котик" }) {
        val unit = size.minDimension / 120f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        val head = Path().apply {
            moveTo(22 * unit, 72 * unit); quadraticTo(16 * unit, 48 * unit, 23 * unit, 24 * unit)
            lineTo(42 * unit, 39 * unit); quadraticTo(56 * unit, 34 * unit, 71 * unit, 39 * unit)
            lineTo(88 * unit, 24 * unit); quadraticTo(98 * unit, 65 * unit, 87 * unit, 81 * unit)
            cubicTo(75 * unit, 101 * unit, 34 * unit, 99 * unit, 22 * unit, 72 * unit); close()
        }
        drawPath(head, ink)
        if (sleepy) {
            drawLine(eyes, p(35f, 60f), p(45f, 60f), 3 * unit, StrokeCap.Round)
            drawLine(eyes, p(65f, 60f), p(75f, 60f), 3 * unit, StrokeCap.Round)
        } else {
            drawOval(eyes, p(36f, 54f), Size(7 * unit, 11 * unit))
            drawOval(eyes, p(65f, 54f), Size(7 * unit, 11 * unit))
        }
        drawLine(Color(0xFFF4CBD9), p(52f, 71f), p(58f, 71f), 4 * unit, StrokeCap.Round)
        drawArc(eyes, 10f, 160f, false, p(48f, 73f), Size(15 * unit, 9 * unit), style = Stroke(1.5f * unit))
        if (playing) {
            drawLine(ink, p(72f, 86f), p(94f + offset * 4, 91f), 12 * unit, StrokeCap.Round)
            drawLine(pill, p(94f + offset * 8, 100f), p(107f + offset * 8, 88f), 13 * unit, StrokeCap.Round)
            drawLine(eyes, p(96f + offset * 8, 89f), p(105f + offset * 8, 99f), 2 * unit)
        }
    }
}
