package ru.meow.reminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.DoseOccurrence
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(vm: MeowViewModel) {
    val doses by vm.occurrences.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    val zone = remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone.value)) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        zone.value = ZoneId.systemDefault()
        today = LocalDate.now(zone.value)
        month = YearMonth.from(today)
        selected = today
    }
    val byDate = remember(doses, zone.value) {
        doses.filter { !it.cancelled || it.status != "PENDING" }.groupBy {
            Instant.ofEpochMilli(it.plannedAt).atZone(zone.value).toLocalDate()
        }
    }
    val monthStart = month.atDay(1)
    val offset = monthStart.dayOfWeek.value - 1
    val cells = (0 until offset).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val selectedDoses = byDate[selected].orEmpty().sortedBy { it.plannedAt }
    val now = System.currentTimeMillis()
    val medicationColors = remember(doses, settings.medicationCalendarColors) {
        doses.map { it.medicationId to it.medicationName }.distinctBy { it.first }
            .sortedBy { it.first }.mapIndexed { index, (id, name) ->
                val color = settings.medicationCalendarColors[id]?.let(::Color) ?: calendarMedicationPalette[index % calendarMedicationPalette.size]
                id to (name to color)
            }.toMap()
    }
    colorDialogFor?.let { id ->
        val name = medicationColors[id]?.first ?: id
        AlertDialog(
            onDismissRequest = { colorDialogFor = null },
            title = { Text("Цвет препарата") },
            text = {
                Column {
                    Text(name)
                    calendarMedicationPalette.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { color ->
                                Surface(
                                    modifier = Modifier.padding(4.dp).size(40.dp).clickable {
                                        vm.setting { it.copy(medicationCalendarColors = it.medicationCalendarColors + (id to color.toArgb())) }
                                        colorDialogFor = null
                                    }.semantics { contentDescription = "Выбрать цвет" },
                                    shape = CircleShape, color = color
                                ) {}
                            }
                        }
                    }
                    TextButton(onClick = {
                        vm.setting { it.copy(medicationCalendarColors = it.medicationCalendarColors - id) }
                        colorDialogFor = null
                    }) { Text("Цвет по умолчанию") }
                }
            },
            confirmButton = { TextButton(onClick = { colorDialogFor = null }) { Text("Закрыть") } }
        )
    }
    Page("Календарь") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Предыдущий месяц") }
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() } + " ${month.year}", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Следующий месяц") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                val maxDots = week.filterNotNull().maxOfOrNull { date ->
                    byDate[date].orEmpty().filter { it.status == "TAKEN" }.map { it.medicationId }.distinct().size
                } ?: 0
                val dayHeight = maxOf(60, 40 + ((maxDots + 2) / 3) * 9).dp
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).height(dayHeight), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val dayDoses = byDate[date].orEmpty().filter { it.status == "TAKEN" }.distinctBy { it.medicationId }
                                val isSelected = selected == date
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().clickable { selected = date }.testTag("calendar-day-$date")) {
                                    Surface(shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                                        Text(date.dayOfMonth.toString(), Modifier.size(36.dp).wrapContentHeight(Alignment.CenterVertically), textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium, color = if (date == today && !isSelected) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (dayDoses.isNotEmpty()) Column(Modifier.padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayDoses.chunked(3).forEach { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                row.forEach { dose ->
                                                    val (name, color) = medicationColors[dose.medicationId] ?: (dose.medicationName to MaterialTheme.colorScheme.outline)
                                                    Surface(Modifier.size(7.dp).semantics { contentDescription = "$name: принято" }, shape = CircleShape, color = color) {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val monthMedications = remember(byDate, month) {
            byDate.filterKeys { YearMonth.from(it) == month }.values.flatten()
                .filter { it.status == "TAKEN" }.map { it.medicationId }.distinct().sorted()
        }
        if (monthMedications.isNotEmpty()) {
            Text("Принятые препараты · нажмите, чтобы изменить цвет", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            monthMedications.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { id ->
                        val (name, color) = medicationColors[id] ?: (id to MaterialTheme.colorScheme.outline)
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable { colorDialogFor = id }.semantics { contentDescription = "Изменить цвет: $name" }, verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(10.dp), shape = CircleShape, color = color) {}
                            Text(name, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(selected.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
        if (selectedDoses.isEmpty()) Text("На этот день записей нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        selectedDoses.forEach { dose -> CalendarDoseRow(dose, zone.value, now) { id, status -> vm.mark(id, status) } }
    }
}

@Composable
private fun CalendarDoseRow(dose: DoseOccurrence, zone: ZoneId, now: Long, mark: (String, String) -> Unit) {
    val canMark = dose.plannedAt <= now
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Instant.ofEpochMilli(dose.plannedAt).atZone(zone).toLocalTime().toString().take(5), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(dose.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(when (dose.status) { "TAKEN" -> "Принято"; "SKIPPED" -> "Пропущено"; else -> "Ожидает" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (canMark) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { mark(dose.id, "TAKEN") }, enabled = dose.status != "TAKEN") { Text("Принято") }
                    TextButton(onClick = { mark(dose.id, "SKIPPED") }, enabled = dose.status != "SKIPPED") { Text("Пропущено") }
                    if (dose.status != "PENDING") TextButton(onClick = { mark(dose.id, "PENDING") }) { Text("Сбросить") }
                }
            }
        }
    }
}


private val calendarMedicationPalette = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF4E342E),
    Color(0xFF4527A0), Color(0xFF558B2F), Color(0xFF0277BD), Color(0xFF9E3D00)
)
