@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.*
import java.time.temporal.ChronoUnit
import ru.meow.reminder.data.MedicationSchedule

@Composable
fun EditScheduleSheet(
    vm: MeowViewModel,
    scheduleId: String,
    dismiss: () -> Unit,
    openAdvancedEditor: (String) -> Unit
) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val medications by vm.medications.collectAsStateWithLifecycle()
    val schedule = schedules.find { it.id == scheduleId }
    if (schedule == null) {
        LaunchedEffect(scheduleId) { dismiss() }
        return
    }
    val med = medications.find { it.id == schedule.medicationId }
    val context = LocalContext.current

    val initialTime = remember(schedule.id) { schedule.times.split(',').firstOrNull() ?: "09:00" }
    var time by remember(schedule.id) { mutableStateOf(initialTime) }

    val initialPeriod = remember(schedule.id) {
        when {
            schedule.frequency == "ONCE" -> "ONCE"
            schedule.frequency == "WEEKLY" -> "WEEKLY"
            schedule.frequency == "DAILY" && schedule.interval == 2 -> "DAILY_ALT"
            else -> "DAILY"
        }
    }
    var periodChoice by remember(schedule.id) { mutableStateOf(initialPeriod) }

    val initialDurationAndCustom = remember(schedule.id) {
        if (schedule.endDate == null || schedule.frequency == "ONCE") {
            "FOREVER" to ""
        } else {
            val start = runCatching { LocalDate.parse(schedule.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(schedule.endDate) }.getOrNull()
            if (start != null && end != null && !end.isBefore(start)) {
                val d = ChronoUnit.DAYS.between(start, end) + 1
                if (d.toString() in listOf("3", "5", "7", "10", "14", "30")) {
                    d.toString() to ""
                } else {
                    "CUSTOM" to d.toString()
                }
            } else {
                "FOREVER" to ""
            }
        }
    }
    var durationChoice by remember(schedule.id) { mutableStateOf(initialDurationAndCustom.first) }
    var customDays by remember(schedule.id) { mutableStateOf(initialDurationAndCustom.second) }

    val initialStartDate = remember(schedule.id) { schedule.startDate }
    var startDate by remember(schedule.id) { mutableStateOf(initialStartDate) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }

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
            Text("Редактировать расписание", style = MaterialTheme.typography.titleLarge)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${med?.icon ?: "💊"}  ${med?.name ?: schedule.label}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!med?.purpose.isNullOrBlank()) {
                            Text(med!!.purpose, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!med?.dosage.isNullOrBlank()) {
                            Text(
                                "${med!!.dosage} ${med.unit}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. Время приёма
            Text("Время приёма", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).testTag("edit-time-button"),
                    enabled = !saving,
                    onClick = {
                        val initial = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now().plusMinutes(5))
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                time = LocalTime.of(hour, minute).format(timeFormat)
                                error = null
                            },
                            initial.hour,
                            initial.minute,
                            true
                        ).show()
                    }
                ) {
                    Text("⏰  $time")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("09:00" to "Утро 09:00", "14:00" to "День 14:00", "19:00" to "Вечер 19:00", "22:00" to "Ночь 22:00").forEach { (preset, label) ->
                    FilterChip(
                        selected = time == preset,
                        onClick = { time = preset; error = null },
                        label = { Text(label) }
                    )
                }
            }

            // 2. Периодичность
            Text("Периодичность", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = periodChoice == "DAILY",
                    onClick = { periodChoice = "DAILY"; error = null },
                    label = { Text("Каждый день") }
                )
                FilterChip(
                    selected = periodChoice == "DAILY_ALT",
                    onClick = { periodChoice = "DAILY_ALT"; error = null },
                    label = { Text("Через день") }
                )
                FilterChip(
                    selected = periodChoice == "WEEKLY",
                    onClick = { periodChoice = "WEEKLY"; error = null },
                    label = { Text("Раз в неделю") }
                )
                FilterChip(
                    selected = periodChoice == "ONCE",
                    onClick = { periodChoice = "ONCE"; error = null },
                    label = { Text("Один раз") }
                )
            }

            // 3. Сколько дней повторять
            if (periodChoice != "ONCE") {
                Text("Сколько дней повторять", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3" to "3 дня", "5" to "5 дней", "7" to "7 дней", "10" to "10 дней", "14" to "14 дней", "30" to "30 дней", "FOREVER" to "Постоянно", "CUSTOM" to "Своё число").forEach { (key, label) ->
                        FilterChip(
                            selected = durationChoice == key,
                            onClick = { durationChoice = key; error = null },
                            label = { Text(label) }
                        )
                    }
                }
                if (durationChoice == "CUSTOM") {
                    OutlinedTextField(
                        value = customDays,
                        onValueChange = { customDays = it.filter { ch -> ch.isDigit() }.take(4); error = null },
                        label = { Text("Количество дней курса") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            DateButton("Дата применения / начала", startDate) { startDate = it; error = null }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                enabled = !saving && (periodChoice == "ONCE" || durationChoice != "CUSTOM" || (customDays.toIntOrNull() ?: 0) > 0),
                onClick = {
                    saving = true
                    error = null
                    try {
                        val parsedTime = LocalTime.parse(time)
                        val parsedStartDate = LocalDate.parse(startDate)
                        val freq = when (periodChoice) {
                            "ONCE" -> "ONCE"
                            "WEEKLY" -> "WEEKLY"
                            else -> "DAILY"
                        }
                        val interval = if (periodChoice == "DAILY_ALT") 2 else 1
                        val days: Int? = if (periodChoice == "ONCE") null
                        else when (durationChoice) {
                            "FOREVER" -> null
                            "CUSTOM" -> customDays.toIntOrNull()?.coerceIn(1, 3650)
                            else -> durationChoice.toIntOrNull()
                        }
                        val endDate = if (freq != "ONCE" && days != null) {
                            parsedStartDate.plusDays(days - 1L).toString()
                        } else null

                        val label = when (freq) {
                            "ONCE" -> "Разовое напоминание"
                            "WEEKLY" -> if (days != null) "Раз в неделю, $days дн." else "Раз в неделю"
                            else -> if (interval == 2) {
                                if (days != null) "Через день, $days дн." else "Через день"
                            } else if (interval > 1) {
                                if (days != null) "Каждые $interval дн., $days дн." else "Каждые $interval дн."
                            } else {
                                if (days != null) "Каждый день, $days дн." else "Каждый день"
                            }
                        }

                        val updated = schedule.copy(
                            times = parsedTime.format(timeFormat),
                            frequency = freq,
                            interval = interval,
                            startDate = parsedStartDate.toString(),
                            endDate = endDate,
                            label = label
                        )
                        vm.save(updated)
                        dismiss()
                    } catch (e: Exception) {
                        error = e.message ?: "Ошибка сохранения расписания"
                        saving = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save-edit-schedule")
            ) {
                Text(if (saving) "Сохраняем…" else "Сохранить изменения")
            }

            OutlinedButton(
                onClick = {
                    dismiss()
                    openAdvancedEditor(schedule.id)
                },
                modifier = Modifier.fillMaxWidth().testTag("open-advanced-schedule-editor")
            ) {
                Text("Расширенные настройки расписания")
            }

            TextButton(
                onClick = { deleteConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Удалить расписание", color = MaterialTheme.colorScheme.error)
            }

            TextButton(enabled = !saving, onClick = dismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Удалить расписание?") },
            text = { Text("Все будущие напоминания для «${med?.name ?: schedule.label}» будут отменены. Прошедшая история сохранится.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSchedule(schedule.id)
                    deleteConfirm = false
                    dismiss()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
