package ru.meow.reminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.DoseOccurrence
import ru.meow.reminder.data.MedicationSchedule
import ru.meow.reminder.domain.ScheduleEngine
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen(vm: MeowViewModel, editSchedule: (String) -> Unit, add: (LocalDate) -> Unit) {
    val palette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
    fun medicationColor(id: String) = palette[(id.hashCode() and Int.MAX_VALUE) % palette.size]
    val meds by vm.medications.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val doses by vm.occurrences.collectAsStateWithLifecycle()
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
    val dosesByDate = remember(doses, zone.value) {
        doses.asSequence().filter { !it.cancelled || it.status != "PENDING" }
            .groupBy { Instant.ofEpochMilli(it.plannedAt).atZone(zone.value).toLocalDate() }
    }
    val selectedExisting = dosesByDate[selected].orEmpty()
    val active = remember(schedules, meds) {
        val eligibleMedicationIds = meds.asSequence().filter { it.enabled && !it.archived && !it.deleted }
            .map { it.id }.toHashSet()
        schedules.filter { it.enabled && it.medicationId in eligibleMedicationIds }
    }
    val existing = dosesByDate.mapValues { (_, dayDoses) -> dayDoses.map { it.medicationId }.toMutableSet() }.toMutableMap()
    val first = month.atDay(1).minusDays(1)
    val last = month.atEndOfMonth().plusDays(1)
    active.forEach { schedule ->
        runCatching { ScheduleEngine.between(schedule, first.atStartOfDay(), last.atTime(23, 59, 59)) }.getOrDefault(emptyList()).forEach { local ->
            existing.getOrPut(local.toLocalDate()) { mutableSetOf() }.let { it.add(schedule.medicationId) }
        }
    }
    val byDate = existing
    val monthStart = month.atDay(1)
    val offset = monthStart.dayOfWeek.value - 1
    val cells = (0 until offset).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val existingScheduleTimes = remember(doses) { doses.mapTo(HashSet()) { it.scheduleId to it.plannedAt } }
    val medicationsById = remember(meds) { meds.associateBy { it.id } }
    val selectedDoses = (selectedExisting + active.flatMap { schedule ->
        val medication = medicationsById.getValue(schedule.medicationId)
        ScheduleEngine.occurrencesOn(schedule, selected).mapNotNull { local ->
            val plannedAt = local.atZone(zone.value).toInstant().toEpochMilli()
            if ((schedule.id to plannedAt) in existingScheduleTimes) null
            else DoseOccurrence(id = "preview-${schedule.id}-$local", scheduleId = schedule.id, medicationId = medication.id,
                medicationName = medication.name, localDateTime = local.toString(), plannedAt = plannedAt)
        }
    }).sortedBy { it.plannedAt }
    Page("Календарь") {
        Text("Расписание приёма на устройстве", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Предыдущий месяц") }
            Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.forLanguageTag("ru")).replaceFirstChar { it.uppercase() } + " ${month.year}", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "Следующий месяц") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(0.82f), contentAlignment = Alignment.Center) {
                            if (date != null) {
                                val dayMedIds = byDate[date].orEmpty()
                                val isSelected = selected == date
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().clickable { selected = date }.testTag("calendar-day-${date}")) {
                                    Surface(shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) {
                                        Text(date.dayOfMonth.toString(), Modifier.size(36.dp).wrapContentHeight(Alignment.CenterVertically), textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.bodyMedium, color = if (date == today && !isSelected) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    }
                                    if (dayMedIds.isNotEmpty()) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayMedIds.take(4).forEach { id ->
                                            val tint = medicationColor(id)
                                            Surface(Modifier.size(5.dp), shape = CircleShape, color = tint) {}
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(selected.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = { add(selected) }) { Icon(Icons.Outlined.Add, null); Text("Добавить") }
        }
        if (active.isNotEmpty()) {
            Text("Активные препараты", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                active.mapNotNull { schedule -> meds.find { it.id == schedule.medicationId } }.distinctBy { it.id }.forEach { med ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(8.dp), shape = CircleShape, color = medicationColor(med.id)) {}
                        Text("${med.icon} ${med.name}", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (selectedDoses.isEmpty()) Text("На этот день приёмов нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        selectedDoses.forEach { dose -> CalendarDoseRow(dose, editSchedule) }
    }
}

@Composable
private fun CalendarDoseRow(dose: DoseOccurrence, editSchedule: (String) -> Unit) {
    Card(onClick = { if (dose.scheduleId.isNotBlank()) editSchedule(dose.scheduleId) }, enabled = dose.scheduleId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Instant.ofEpochMilli(dose.plannedAt).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(dose.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(when (dose.status) { "TAKEN" -> "Принято"; "SKIPPED" -> "Пропущено"; else -> "Запланировано" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (dose.scheduleId.isNotBlank()) IconButton(onClick = { editSchedule(dose.scheduleId) }) { Icon(Icons.Outlined.Edit, "Изменить расписание") }
        }
    }
}
