@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.meow.reminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val ru: Locale = Locale.forLanguageTag("ru")
val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, EEEE", ru)
val dateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)
val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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

@Composable
fun Page(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
            if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") }
        }, actions = actions)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Toggle(title: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun Field(value: String, label: String, onChange: (String) -> Unit, singleLine: Boolean = true, supporting: String? = null) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        supportingText = if (supporting != null) { { Text(supporting) } } else null)
}

@Composable
fun DateButton(label: String, value: String, change: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
        DatePickerDialog(context, { _, year, month, day -> change(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }) { Text("$label: ${runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault(value)}") }
}

@Composable
fun TimeButton(value: String, change: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val time = LocalTime.parse(value)
        TimePickerDialog(context, { _, h, m -> change(LocalTime.of(h, m).format(timeFormat)) }, time.hour, time.minute, true).show()
    }) { Text(value) }
}

@Composable
fun Confirm(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { confirm(); dismiss() }) { Text("Подтвердить") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
}

@Composable
fun EmptyState(title: String, subtitle: String, showCat: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showCat) Cat(Modifier.size(100.dp), sleepy = true)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}
