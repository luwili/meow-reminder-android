@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.*
import ru.meow.reminder.domain.ScheduleEngine
import java.time.*

@Composable
fun ScheduleEditor(vm: MeowViewModel, id: String, back: () -> Unit) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val medications by vm.medications.collectAsStateWithLifecycle()
    val original = schedules.find { it.id == id }
    if (original == null) { Page("Расписание", back) { Text("Загрузка…") }; return }
    val med = medications.find { it.id == original.medicationId }
    var rule by remember(id) { mutableStateOf(original) }
    var deleting by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    fun change(value: MedicationSchedule) {
        rule = value
        validation = runCatching { ScheduleEngine.validate(value) }.exceptionOrNull()?.message
        if (validation == null) vm.save(value)
    }
    val preview = remember(rule) { runCatching { ScheduleEngine.next(rule, Instant.now(), ZoneId.systemDefault(), 10) }.getOrDefault(emptyList()) }
    Page("Расписание", back) {
        if (med != null) {
            Text("${med.icon}  ${med.name}", style = MaterialTheme.typography.titleLarge)
        }
        Text(if (validation == null) "Сохраняется автоматически" else validation!!, color = if (validation == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Field(rule.label, "Название шаблона расписания", { change(rule.copy(label = it)) })
        Toggle("Расписание включено", rule.enabled, "Можно приостановить отдельно от других расписаний") { change(rule.copy(enabled = it)) }
        DateButton("Начало", rule.startDate) { change(rule.copy(startDate = it)) }
        Section("Время приёма") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                listOf("09:00" to "Утро 09:00", "14:00" to "День 14:00", "19:00" to "Вечер 19:00", "22:00" to "Ночь 22:00").forEach { (preset, label) ->
                    val isSelected = preset in rule.times.split(',')
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val currentTimes = rule.times.split(',').filter { it.isNotBlank() }
                            val newTimes = if (isSelected) {
                                if (currentTimes.size > 1) currentTimes - preset else currentTimes
                            } else {
                                (currentTimes + preset).distinct().sorted()
                            }
                            change(rule.copy(times = newTimes.joinToString(",")))
                        },
                        label = { Text(label) }
                    )
                }
            }
            rule.times.split(',').forEachIndexed { index, time ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeButton(time) { new -> change(rule.copy(times = rule.times.split(',').toMutableList().apply { set(index, new) }.distinct().sorted().joinToString(","))) }
                    if (rule.times.split(',').size > 1) IconButton(onClick = { change(rule.copy(times = rule.times.split(',').toMutableList().apply { removeAt(index) }.joinToString(","))) }) { Icon(Icons.Outlined.Close, "Удалить время $time") }
                }
            }
            var adding by remember { mutableStateOf(false) }
            if (adding) TimeButton("12:00") { time -> change(rule.copy(times = (rule.times.split(',') + time).distinct().sorted().joinToString(","))); adding = false }
            OutlinedButton(onClick = { adding = true }) { Text("Добавить время") }
            Text("24-часовой формат. Одинаковое время не дублируется.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Периодичность") {
            Toggle("Повторять расписание", rule.frequency != "ONCE") { change(rule.copy(frequency = if (it) "DAILY" else "ONCE", maxOccurrences = null, endDate = null)) }
            if (rule.frequency != "ONCE") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DAILY" to "Дни", "WEEKLY" to "Недели", "MONTHLY" to "Месяцы").forEach { (key, label) ->
                        FilterChip(selected = rule.frequency == key, onClick = { change(rule.copy(frequency = key)) }, label = { Text(label) })
                    }
                }
                NumberField(rule.interval, "Каждые N ${when (rule.frequency) { "WEEKLY" -> "недель"; "MONTHLY" -> "месяцев"; else -> "дней" }}", 1..999) { change(rule.copy(interval = it)) }
                if (rule.frequency == "WEEKLY") FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { index, label ->
                        val days = rule.weekDays.split(',').map(String::toInt).toSet()
                        FilterChip(selected = index + 1 in days, onClick = {
                            val updated = if (index + 1 in days) days - (index + 1) else days + (index + 1)
                            if (updated.isNotEmpty()) change(rule.copy(weekDays = updated.sorted().joinToString(",")))
                        }, label = { Text(label) })
                    }
                }
                if (rule.frequency == "MONTHLY") {
                    Toggle("Последний день месяца", rule.lastDay) { change(rule.copy(lastDay = it)) }
                    if (!rule.lastDay) {
                        NumberField(rule.monthDay, "Число месяца", 1..31) { change(rule.copy(monthDay = it)) }
                        Toggle("Пропускать короткий месяц", rule.skipShortMonth, "Иначе используется последний день месяца, если выбранного числа нет") { change(rule.copy(skipShortMonth = it)) }
                    }
                }
                Text("Продолжительность", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = rule.endDate == null && rule.maxOccurrences == null, onClick = { change(rule.copy(endDate = null, maxOccurrences = null)) }, label = { Text("Бессрочно") })
                    FilterChip(selected = rule.maxOccurrences != null, onClick = { change(rule.copy(maxOccurrences = 1, endDate = null)) }, label = { Text("Число приёмов") })
                    FilterChip(selected = rule.endDate != null, onClick = { change(rule.copy(endDate = rule.startDate, maxOccurrences = null)) }, label = { Text("До даты") })
                }
                rule.maxOccurrences?.let { count ->
                    NumberField(count, "Всего запланированных приёмов", 1..100000) { change(rule.copy(maxOccurrences = it)) }
                    Text("Считается каждое выбранное время, а не число дней. Отметки и отсрочки не меняют этот счётчик.", style = MaterialTheme.typography.bodySmall)
                    val last = remember(rule) { runCatching { ScheduleEngine.localTimes(rule).lastOrNull() }.getOrNull() }
                    last?.let { Text("Последний приём: ${it.format(dateTimeFormat)}") }
                }
                rule.endDate?.let { date -> DateButton("Включительно до", date) { change(rule.copy(endDate = it)) } }
            } else Text("В выбранную дату сработает каждое указанное время один раз.")
        }
        Section("Повторы уведомления") {
            Toggle("Повторять, пока не подтверждено", rule.repeatNotification, "Это дополнительные уведомления об одном приёме, не новое расписание") { change(rule.copy(repeatNotification = it)) }
            if (rule.repeatNotification) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 30, 60).forEach { minutes -> FilterChip(selected = rule.repeatMinutes == minutes, onClick = { change(rule.copy(repeatMinutes = minutes)) }, label = { Text("$minutes мин") }) } }
                NumberField(rule.repeatMinutes, "Свой интервал, минут", 1..1440) { change(rule.copy(repeatMinutes = it)) }
                NumberField(rule.extraNotifications, "Дополнительных уведомлений", 1..10) { change(rule.copy(extraNotifications = it)) }
                Text("После «Принято» или «Пропустить» повторы этого приёма прекращаются.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Section("Следующие приёмы") {
            if (preview.isEmpty()) Text("Будущих приёмов нет. Проверьте дату начала и ограничения курса.")
            preview.forEach { Text(it.format(dateTimeFormat)) }
            if (!rule.enabled) Text("Предпросмотр правила: само расписание сейчас выключено.")
            Text("Время местное. При переводе часов отсутствующее время сдвигается вперёд, повторяющееся срабатывает один раз.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { deleting = true }) { Text("Удалить расписание", color = MaterialTheme.colorScheme.error) }
    }
    if (deleting) Confirm("Удалить расписание?", "Будущие напоминания отменятся. История сохранится.", { deleting = false }) { vm.deleteSchedule(id); back() }
}

@Composable
fun NumberField(value: Int, label: String, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(value = text, onValueChange = {
        text = it.filter(Char::isDigit)
        text.toIntOrNull()?.takeIf { n -> n in range }?.let(onChange)
    }, label = { Text(label) }, singleLine = true, isError = text.toIntOrNull() !in range,
        supportingText = { Text("От ${range.first} до ${range.last}") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}
