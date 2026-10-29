@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.*
import ru.meow.reminder.domain.ScheduleEngine
import java.time.Instant
import java.time.ZoneId

@Composable
fun LibraryScreen(vm: MeowViewModel, edit: (String) -> Unit, remind: (String) -> Unit) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    val filtered = meds.filter { !it.deleted && it.name.contains(search, true) && when (tab) { 0 -> it.enabled && !it.archived; 1 -> !it.enabled && !it.archived; else -> it.archived } }
    val sorted = when (sort) { 1 -> filtered.sortedByDescending { it.createdAt }; 2 -> filtered.sortedByDescending { it.enabled }; else -> filtered.sortedWith(compareByDescending<MedicationTemplate> { it.favorite }.thenBy { it.name.lowercase() }) }
    Page("Мои препараты", actions = { IconButton(onClick = {
        val med = MedicationTemplate()
        vm.save(med); edit(med.id)
    }) { Icon(Icons.Outlined.Add, "Создать препарат") } }) {
        Field(search, "Найти препарат", { search = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Активные", "Сохранённые", "Архив").forEachIndexed { index, title -> FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) }) }
        }
        var sortMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { sortMenu = true }) { Icon(Icons.Outlined.Sort, null); Text(listOf("По имени", "По дате добавления", "По активности")[sort]) }
            DropdownMenu(sortMenu, { sortMenu = false }) { listOf("По имени", "По дате добавления", "По активности").forEachIndexed { i, label -> DropdownMenuItem(text = { Text(label) }, onClick = { sort = i; sortMenu = false }) } }
        }
        if (sorted.isEmpty()) EmptyState("Здесь будут ваши препараты", "Сохраните описание один раз, включайте напоминания по необходимости.", settings.cat)
        sorted.forEach { med ->
            Card(onClick = { edit(med.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(med.icon, modifier = Modifier.background(pastels[med.color.coerceIn(0, 4)], CircleShape).padding(12.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(med.name.ifBlank { "Новый препарат" }, style = MaterialTheme.typography.titleMedium)
                        if (med.purpose.isNotBlank()) Text(med.purpose, style = MaterialTheme.typography.bodySmall)
                        Text(if (med.archived) "В архиве" else if (med.enabled) "Напоминания включены" else "Сохранён без напоминаний", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(enabled = !med.archived && med.name.isNotBlank(), onClick = { remind(med.id) }) {
                        Icon(Icons.Outlined.AddAlarm, "Напомнить: ${med.name}")
                    }
                    Switch(checked = med.enabled && !med.archived, onCheckedChange = {
                        if (it && med.name.isBlank()) vm.error.value = "Сначала укажите название препарата"
                        else vm.save(med.copy(enabled = it, archived = false))
                    })
                }
            }
        }
        Button(onClick = { val med = MedicationTemplate(); vm.save(med); edit(med.id) }, modifier = Modifier.fillMaxWidth()) { Text("Создать препарат") }
    }
}

@Composable
fun MedicationEditor(vm: MeowViewModel, id: String, back: () -> Unit, schedule: (String) -> Unit) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val original = meds.find { it.id == id }
    if (original == null) { Page("Препарат", back) { Text("Загрузка…") }; return }
    var med by remember(id) { mutableStateOf(original) }
    var deleting by remember { mutableStateOf(false) }
    var blankName by remember { mutableStateOf(false) }
    fun change(value: MedicationTemplate) { med = value; vm.save(value) }
    val leave: () -> Unit = { if (med.name.isBlank()) blankName = true else back() }
    BackHandler(onBack = leave)
    Page("Ваш препарат", leave, actions = {
        IconButton(onClick = { change(med.copy(favorite = !med.favorite)) }) { Icon(if (med.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Избранное") }
    }) {
        Text("Сохраняется автоматически", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Field(med.name, "Название *", { change(med.copy(name = it)) }, supporting = if (med.name.isBlank()) "Укажите название, чтобы включить напоминания" else null)
        Field(med.purpose, "Для чего принимается", { change(med.copy(purpose = it)) })
        Field(med.dosage, "Дозировка — по вашему назначению", { change(med.copy(dosage = it)) })
        Field(med.unit, "Единица измерения", { change(med.copy(unit = it)) })
        Field(med.note, "Дополнительная заметка", { change(med.copy(note = it)) }, singleLine = false)
        Section("Оформление") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pastels.forEachIndexed { index, _ ->
                FilterChip(selected = med.color == index, onClick = { change(med.copy(color = index)) }, label = { Text(listOf("Лаванда", "Роза", "Небо", "Персик", "Шалфей")[index]) })
            } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("💊", "🌿", "💧", "☀️", "♡").forEach { icon ->
                FilterChip(selected = med.icon == icon, onClick = { change(med.copy(icon = icon)) }, label = { Text(icon) })
            } }
        }
        Section("Напоминания") {
            Toggle("Включить напоминания", med.enabled, "Описание и история сохраняются при выключении") {
                if (it && med.name.isBlank()) vm.error.value = "Сначала укажите название препарата"
                else change(med.copy(enabled = it, archived = false))
            }
            val rules = schedules.filter { it.medicationId == id }
            if (rules.isEmpty()) Text("Добавьте расписание. Препарат можно хранить и без него.")
            rules.forEach { rule ->
                val completed = remember(rule) { runCatching { ScheduleEngine.next(rule, Instant.now(), ZoneId.systemDefault(), 1).isEmpty() }.getOrDefault(false) }
                OutlinedCard(onClick = { schedule(rule.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(rule.label, style = MaterialTheme.typography.titleMedium)
                        Text("${frequencyName(rule.frequency)} · ${rule.times.replace(",", ", ")}")
                        Text(if (!rule.enabled) "Приостановлено" else if (completed) "Расписание завершено" else "Включено", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = {
                val prefs = vm.settings.value
                val rule = MedicationSchedule(medicationId = id, enabled = false, repeatNotification = prefs.repeatDefault, repeatMinutes = prefs.repeatMinutes)
                vm.save(rule); schedule(rule.id)
            }) { Icon(Icons.Outlined.Add, null); Text("Добавить расписание / шаблон") }
        }
        Text("Приложение не назначает лечение. Дозировки и режим приёма указываете вы.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = med.name.isNotBlank(), onClick = { vm.task { vm.repo.duplicate(med) }; back() }) { Text("Дублировать препарат") }
        OutlinedButton(enabled = med.name.isNotBlank(), onClick = { change(med.copy(archived = !med.archived, enabled = false)); back() }) { Text(if (med.archived) "Вернуть из архива" else "В архив") }
        TextButton(onClick = { deleting = true }) { Text("Удалить препарат", color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) Confirm("Удалить препарат?", "Напоминания отключатся. История останется, препарат исчезнет из библиотеки.", { deleting = false }) {
        vm.save(med.copy(deleted = true, enabled = false)); back()
    }
    if (blankName) AlertDialog(onDismissRequest = { blankName = false }, title = { Text("Укажите название") },
        text = { Text("Название препарата обязательно. Продолжите редактирование или удалите этот черновик.") },
        confirmButton = { TextButton(onClick = { blankName = false }) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = { vm.save(med.copy(deleted = true, enabled = false)); blankName = false; back() }) { Text("Удалить черновик") } })
}

fun frequencyName(value: String) = when (value) { "DAILY" -> "Каждый день"; "WEEKLY" -> "По дням недели"; "MONTHLY" -> "Каждый месяц"; else -> "Один раз" }
