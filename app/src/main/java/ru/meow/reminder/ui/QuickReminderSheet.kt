@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*

@Composable
fun QuickReminderSheet(vm: MeowViewModel, initialMedicationId: String?, dismiss: () -> Unit,
    newMedication: () -> Unit, editMedication: (String) -> Unit) {
    val medications by vm.medications.collectAsStateWithLifecycle()
    val saving by vm.quickSaving.collectAsStateWithLifecycle()
    val error by vm.quickError.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf(initialMedicationId) }
    var search by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDateTime.now().plusMinutes(5).toLocalDate().toString()) }
    var time by remember { mutableStateOf<String?>(null) }
    var frequency by remember { mutableStateOf("ONCE") }
    val selected = medications.find { it.id == selectedId && !it.deleted && !it.archived }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.quickError.value = null }
    ModalBottomSheet(onDismissRequest = { if (!saving) dismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Добавить напоминание", style = MaterialTheme.typography.titleLarge)
            if (selected == null) {
                Text("Выберите сохранённый препарат — повторно вводить описание не нужно.")
                Field(search, "Поиск по названию", { search = it })
                val matches = medications.filter { !it.deleted && !it.archived && it.name.isNotBlank() && it.name.contains(search, true) }
                if (matches.isEmpty()) Text("Препараты не найдены. Можно создать новый.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { it.id }) { med ->
                        OutlinedCard(onClick = { selectedId = med.id; vm.quickError.value = null }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${med.icon}  ${med.name}", style = MaterialTheme.typography.titleMedium)
                                if (med.purpose.isNotBlank()) Text(med.purpose, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = newMedication, modifier = Modifier.fillMaxWidth()) { Text("Создать новый препарат") }
            } else {
                Text("${selected.icon}  ${selected.name}", style = MaterialTheme.typography.titleMedium)
                if (selected.dosage.isNotBlank()) Text("${selected.dosage} ${selected.unit}", style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = !saving, onClick = { selectedId = null; vm.quickError.value = null }) { Text("Выбрать другой препарат") }
                DateButton("Дата начала", date) { date = it; vm.quickError.value = null }
                OutlinedButton(enabled = !saving, onClick = {
                    val initial = time?.let(LocalTime::parse) ?: LocalTime.now().plusMinutes(5)
                    TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute).format(timeFormat); vm.quickError.value = null },
                        initial.hour, initial.minute, true).show()
                }) { Text(time?.let { "Время: $it" } ?: "Выбрать время") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = frequency == "ONCE", onClick = { frequency = "ONCE" }, label = { Text("Один раз") })
                    FilterChip(selected = frequency == "DAILY", onClick = { frequency = "DAILY" }, label = { Text("Каждый день") })
                }
                if (frequency == "DAILY") Text("Ежедневно с выбранной даты, бессрочно. Конечный курс, недели и месяцы настраиваются в полном редакторе.", style = MaterialTheme.typography.bodySmall)
                if (!selected.enabled) Text("Включится только новое напоминание. Другие сохранённые расписания останутся выключенными.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(enabled = time != null && !saving, onClick = {
                    vm.addQuickReminder(selected.id, LocalDate.parse(date).atTime(LocalTime.parse(time!!)), frequency, dismiss)
                }, modifier = Modifier.fillMaxWidth().testTag("save-quick-reminder")) {
                    Text(if (saving) "Сохраняем…" else "Добавить напоминание")
                }
                TextButton(enabled = !saving, onClick = { editMedication(selected.id) }) { Text("Открыть препарат и полную настройку") }
            }
            TextButton(enabled = !saving, onClick = dismiss) { Text("Отмена") }
        }
    }
}
