package ru.meow.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(vm: MeowViewModel, add: () -> Unit, settings: () -> Unit) {
    val doses by vm.occurrences.collectAsStateWithLifecycle()
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val phrase by vm.homePhrase.collectAsStateWithLifecycle()
    val refreshing by vm.refreshingToday.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        vm.refreshPhrase()
        while (true) { kotlinx.coroutines.delay(30000); today = LocalDate.now() }
    }
    val day = doses.filter { Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() == today && (!it.cancelled || it.status != "PENDING") }.sortedBy { it.plannedAt }
    val next = doses.filter { !it.cancelled && it.status == "PENDING" && it.plannedAt > System.currentTimeMillis() }.minByOrNull { it.plannedAt }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Тихой ночи" }
    Page("Мяу, пора!", actions = { IconButton(onClick = add) { Icon(Icons.Outlined.Add, "Добавить напоминание") } },
        refreshing = refreshing, onRefresh = { today = LocalDate.now(); vm.refreshToday() }) {
        Text(greeting, style = MaterialTheme.typography.headlineLarge)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prefs.cat) Cat(Modifier.size(78.dp), sleepy = LocalTime.now().hour < 6)
                Column(Modifier.weight(1f).padding(start = if (prefs.cat) 12.dp else 0.dp)) {
                    Text(if (prefs.phrases && phrase.isNotBlank()) phrase else "В вашем ритме. С заботой о себе.", style = MaterialTheme.typography.titleMedium)
                    Text("Маленькие шаги, тёплые привычки", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        PermissionCard(vm, settings)
        if (next != null) Section("Ближайший приём") {
            Text(next.medicationName, style = MaterialTheme.typography.titleMedium)
            Text(Instant.ofEpochMilli(next.plannedAt).atZone(zone).format(dateTimeFormat))
        }
        Text(today.format(dayFormat).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        if (day.isEmpty()) EmptyState("На сегодня всё спокойно", "Добавьте препарат или включите сохранённое расписание.", prefs.cat)
        day.forEach { dose -> DoseCard(dose, vm, false) }
        FilledTonalButton(onClick = add, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить напоминание") }
    }
}

@Composable
fun DoseCard(dose: DoseOccurrence, vm: MeowViewModel, history: Boolean) {
    var editing by remember { mutableStateOf(false) }
    var snooze by remember { mutableStateOf(false) }
    val pendingPast = dose.status == "PENDING" && dose.plannedAt < System.currentTimeMillis()
    val status = when (dose.status) { "TAKEN" -> "Принято"; "SKIPPED" -> "Пропущено"; else -> if (pendingPast) "Не подтверждено" else "Ожидается" }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Instant.ofEpochMilli(dose.plannedAt).atZone(ZoneId.systemDefault()).format(timeFormat), style = MaterialTheme.typography.titleLarge)
                Text(dose.medicationName, modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                if (dose.status == "TAKEN") Icon(Icons.Outlined.CheckCircle, "Принято", tint = MaterialTheme.colorScheme.primary)
            }
            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            dose.confirmedAt?.let { Text("Отметка: ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(dateTimeFormat)}", style = MaterialTheme.typography.bodySmall) }
            if (dose.status == "PENDING" && !dose.cancelled && !history) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = { vm.mark(dose.id, "TAKEN") }) { Text("Принято") }
                    IconButton(onClick = { snooze = true }) { Icon(Icons.Outlined.Snooze, "Отложить") }
                    TextButton(onClick = { vm.mark(dose.id, "SKIPPED") }) { Text("Пропустить") }
                }
            } else TextButton(onClick = { editing = true }) { Text("Изменить отметку") }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Статус приёма") }, text = {
        Column { listOf("TAKEN" to "Принято", "SKIPPED" to "Пропущено", "PENDING" to "Не подтверждено").forEach { (status, name) ->
            TextButton(onClick = { vm.mark(dose.id, status); editing = false }) { Text(name) }
        } }
    }, confirmButton = { TextButton(onClick = { editing = false }) { Text("Закрыть") } })
    if (snooze) SnoozeDialog(dismiss = { snooze = false }, select = { minutes -> vm.task { vm.app.scheduler.snooze(dose.id, minutes) } })
}

@Composable
fun SnoozeDialog(dismiss: () -> Unit, select: (Int) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Отложить приём") }, text = {
        Column { listOf(5, 10, 15, 30).forEach { minutes -> TextButton(onClick = { select(minutes); dismiss() }) { Text("На $minutes минут") } }
            Field(custom, "Другое время, минут", { custom = it.filter(Char::isDigit) })
            Text("От 1 до 1440 минут. Основное расписание не изменится.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = custom.toIntOrNull() in 1..1440, onClick = { select(custom.toInt()); dismiss() }) { Text("Отложить") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Отмена") } })
}

@Composable
fun HistoryScreen(vm: MeowViewModel) {
    val doses by vm.occurrences.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var filter by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val day = doses.filter { Instant.ofEpochMilli(it.plannedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() == date && (filter == null || it.medicationId == filter) && it.plannedAt <= System.currentTimeMillis() }
    Page("История приёма") {
        Text("Без оценок, только ваши отметки", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = LocalDate.parse(date).minusDays(1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "Предыдущий день") }
            DateButton("Дата", date) { date = it }
            IconButton(onClick = { date = LocalDate.parse(date).plusDays(1).toString() }) { Icon(Icons.Outlined.ChevronRight, "Следующий день") }
        }
        Box {
            OutlinedButton(onClick = { menu = true }) { Text(meds.find { it.id == filter }?.name ?: "Все препараты") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Все препараты") }, onClick = { filter = null; menu = false })
                meds.forEach { med -> DropdownMenuItem(text = { Text(med.name) }, onClick = { filter = med.id; menu = false }) }
            }
        }
        Text("Отсутствие подтверждения не означает, что препарат не был принят.", style = MaterialTheme.typography.bodySmall)
        if (day.isEmpty()) EmptyState("Пока нет записей", "Выберите другой день или препарат.", settings.cat)
        day.sortedBy { it.plannedAt }.forEach { DoseCard(it, vm, true) }
    }
}
