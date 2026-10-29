@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*

@Composable
fun QuickReminderSheet(
    vm: MeowViewModel,
    initialMedicationId: String?,
    dismiss: () -> Unit,
    newMedication: () -> Unit,
    editMedication: (String) -> Unit
) {
    val medications by vm.medications.collectAsStateWithLifecycle()
    val saving by vm.quickSaving.collectAsStateWithLifecycle()
    val error by vm.quickError.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf(initialMedicationId) }
    var search by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var time by remember { mutableStateOf<String?>(null) }
    // Периодичность: DAILY (interval 1), DAILY_ALT (interval 2), WEEKLY, ONCE
    var periodChoice by remember { mutableStateOf("DAILY") }
    // Длительность: 3, 5, 7, 10, 14, 30, "FOREVER", "CUSTOM"
    var durationChoice by remember { mutableStateOf("FOREVER") }
    var customDays by remember { mutableStateOf("") }

    val selected = medications.find { it.id == selectedId && !it.deleted && !it.archived }
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.quickError.value = null }

    ModalBottomSheet(
        onDismissRequest = { if (!saving) dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Новое напоминание", style = MaterialTheme.typography.titleLarge)

            if (selected == null) {
                Text("Выберите препарат из сохранённых или создайте новый:", style = MaterialTheme.typography.bodyMedium)
                Field(search, "Поиск по названию", { search = it })
                val matches = medications.filter { !it.deleted && !it.archived && it.name.isNotBlank() && it.name.contains(search, true) }
                if (matches.isEmpty()) {
                    Text("Препараты не найдены. Можно создать новый.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { it.id }) { med ->
                        OutlinedCard(
                            onClick = { selectedId = med.id; vm.quickError.value = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("${med.icon}  ${med.name}", style = MaterialTheme.typography.titleMedium)
                                if (med.purpose.isNotBlank()) Text(med.purpose, style = MaterialTheme.typography.bodySmall)
                                if (med.dosage.isNotBlank()) Text("${med.dosage} ${med.unit}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = newMedication, modifier = Modifier.fillMaxWidth()) {
                    Text("Создать новый препарат")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${selected.icon}  ${selected.name}", style = MaterialTheme.typography.titleMedium)
                            if (selected.purpose.isNotBlank()) Text(selected.purpose, style = MaterialTheme.typography.bodySmall)
                            if (selected.dosage.isNotBlank()) Text("${selected.dosage} ${selected.unit}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { selectedId = null; vm.quickError.value = null }) {
                            Text("Сменить")
                        }
                    }
                }

                // 1. Время приёма
                Text("Время приёма", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = {
                            val initial = time?.let(LocalTime::parse) ?: LocalTime.now().plusMinutes(5)
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    time = LocalTime.of(hour, minute).format(timeFormat)
                                    vm.quickError.value = null
                                },
                                initial.hour,
                                initial.minute,
                                true
                            ).show()
                        }
                    ) {
                        Text(time?.let { "⏰  $it" } ?: "Выбрать время")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("09:00" to "Утро 09:00", "14:00" to "День 14:00", "19:00" to "Вечер 19:00", "22:00" to "Ночь 22:00").forEach { (preset, label) ->
                        FilterChip(
                            selected = time == preset,
                            onClick = { time = preset; vm.quickError.value = null },
                            label = { Text(label) }
                        )
                    }
                }

                // 2. Периодичность
                Text("Периодичность", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = periodChoice == "DAILY",
                        onClick = { periodChoice = "DAILY"; vm.quickError.value = null },
                        label = { Text("Каждый день") }
                    )
                    FilterChip(
                        selected = periodChoice == "DAILY_ALT",
                        onClick = { periodChoice = "DAILY_ALT"; vm.quickError.value = null },
                        label = { Text("Через день") }
                    )
                    FilterChip(
                        selected = periodChoice == "WEEKLY",
                        onClick = { periodChoice = "WEEKLY"; vm.quickError.value = null },
                        label = { Text("Раз в неделю") }
                    )
                    FilterChip(
                        selected = periodChoice == "ONCE",
                        onClick = { periodChoice = "ONCE"; vm.quickError.value = null },
                        label = { Text("Один раз") }
                    )
                }

                // 3. Сколько дней повторять (если не разово)
                if (periodChoice != "ONCE") {
                    Text("Сколько дней повторять", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3" to "3 дня", "5" to "5 дней", "7" to "7 дней", "10" to "10 дней", "14" to "14 дней", "30" to "30 дней", "FOREVER" to "Постоянно", "CUSTOM" to "Своё число").forEach { (key, label) ->
                            FilterChip(
                                selected = durationChoice == key,
                                onClick = { durationChoice = key; vm.quickError.value = null },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (durationChoice == "CUSTOM") {
                        OutlinedTextField(
                            value = customDays,
                            onValueChange = { customDays = it.filter { ch -> ch.isDigit() }.take(4); vm.quickError.value = null },
                            label = { Text("Количество дней курса") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                DateButton("Дата первого приёма", date) { date = it; vm.quickError.value = null }

                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    enabled = time != null && !saving && (periodChoice == "ONCE" || durationChoice != "CUSTOM" || (customDays.toIntOrNull() ?: 0) > 0),
                    onClick = {
                        val parsedTime = LocalTime.parse(time!!)
                        var targetDateTime = LocalDate.parse(date).atTime(parsedTime)
                        if (targetDateTime <= java.time.LocalDateTime.now() && date == java.time.LocalDate.now().toString()) {
                            if (periodChoice == "WEEKLY") targetDateTime = targetDateTime.plusWeeks(1)
                            else if (periodChoice != "ONCE") targetDateTime = targetDateTime.plusDays(1)
                        }
                        val freq = when (periodChoice) {
                            "ONCE" -> "ONCE"
                            "WEEKLY" -> "WEEKLY"
                            else -> "DAILY"
                        }
                        val interval = if (periodChoice == "DAILY_ALT") 2 else 1
                        val days: Int? = if (periodChoice == "ONCE") {
                            null
                        } else when (durationChoice) {
                            "FOREVER" -> null
                            "CUSTOM" -> customDays.toIntOrNull()?.coerceIn(1, 3650)
                            else -> durationChoice.toIntOrNull()
                        }
                        vm.addQuickReminder(
                            medicationId = selected.id,
                            local = targetDateTime,
                            frequency = freq,
                            interval = interval,
                            durationDays = days,
                            complete = dismiss
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save-quick-reminder")
                ) {
                    Text(if (saving) "Сохраняем…" else "Добавить напоминание")
                }
            }

            TextButton(enabled = !saving, onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}
