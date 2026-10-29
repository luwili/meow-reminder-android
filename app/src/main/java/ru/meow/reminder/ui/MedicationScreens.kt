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

@Composable
fun LibraryScreen(vm: MeowViewModel, edit: (String) -> Unit) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    val filtered = meds.filter { !it.deleted && it.name.contains(search, true) && when (tab) { 0 -> !it.archived; else -> it.archived } }
    val sorted = when (sort) {
        1 -> filtered.sortedByDescending { it.createdAt }
        else -> filtered.sortedWith(compareByDescending<MedicationTemplate> { it.favorite }.thenBy { it.name.lowercase() })
    }
    Page("Мои препараты", actions = {
        IconButton(onClick = {
            val med = MedicationTemplate()
            vm.save(med); edit(med.id)
        }) { Icon(Icons.Outlined.Add, "Создать препарат") }
    }) {
        Field(search, "Найти препарат", { search = it })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "В архиве").forEachIndexed { index, title ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) })
            }
        }
        var sortMenu by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { sortMenu = true }) {
                Icon(Icons.Outlined.Sort, null)
                Text(listOf("По имени", "По дате добавления")[sort.coerceIn(0, 1)])
            }
            DropdownMenu(sortMenu, { sortMenu = false }) {
                listOf("По имени", "По дате добавления").forEachIndexed { i, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { sort = i; sortMenu = false })
                }
            }
        }
        if (sorted.isEmpty()) EmptyState("Здесь будут ваши препараты", "Сохраняйте препараты и настраивайте напоминания на главном экране.", settings.cat)
        sorted.forEach { med ->
            Card(onClick = { edit(med.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(med.icon, modifier = Modifier.background(pastels[med.color.coerceIn(0, 4)], CircleShape).padding(12.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(med.name.ifBlank { "Новый препарат" }, style = MaterialTheme.typography.titleMedium)
                            if (med.favorite) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Outlined.Star, "Избранное", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (med.purpose.isNotBlank()) Text(med.purpose, style = MaterialTheme.typography.bodySmall)
                        val dosageStr = listOf(med.dosage, med.unit).filter { it.isNotBlank() }.joinToString(" ")
                        if (dosageStr.isNotBlank()) Text(dosageStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (med.archived) Text("В архиве", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.Outlined.ChevronRight, "Открыть", tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        Button(onClick = { val med = MedicationTemplate(); vm.save(med); edit(med.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Создать препарат")
        }
    }
}

@Composable
fun MedicationEditor(vm: MeowViewModel, id: String, back: () -> Unit) {
    val meds by vm.medications.collectAsStateWithLifecycle()
    val original = meds.find { it.id == id }
    if (original == null) { Page("Препарат", back) { Text("Загрузка…") }; return }
    var med by remember(id) { mutableStateOf(original) }
    var deleting by remember { mutableStateOf(false) }
    var blankName by remember { mutableStateOf(false) }
    fun change(value: MedicationTemplate) { med = value; vm.save(value) }
    val leave: () -> Unit = { if (med.name.isBlank()) blankName = true else back() }
    BackHandler(onBack = leave)
    Page("Ваш препарат", leave, actions = {
        IconButton(onClick = { change(med.copy(favorite = !med.favorite)) }) {
            Icon(if (med.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Избранное")
        }
    }) {
        Text("Сохраняется автоматически", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Field(med.name, "Название *", { change(med.copy(name = it)) }, supporting = if (med.name.isBlank()) "Укажите название препарата" else null)
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
