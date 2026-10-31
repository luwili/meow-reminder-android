package ru.meow.reminder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(vm: MeowViewModel, add: () -> Unit, settings: () -> Unit, editSchedule: (String) -> Unit = {}) {
    val doses by vm.occurrences.collectAsStateWithLifecycle()
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    val meds by vm.medications.collectAsStateWithLifecycle()
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val phrase by vm.homePhrase.collectAsStateWithLifecycle()
    val refreshing by vm.refreshingToday.collectAsStateWithLifecycle()
    var zone by remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var nowTime by remember { mutableStateOf(LocalTime.now(zone)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val currentZone = ZoneId.systemDefault()
        zone = currentZone
        today = LocalDate.now(currentZone)
        nowMillis = System.currentTimeMillis()
        nowTime = LocalTime.now(currentZone)
        vm.refreshPhrase()
    }

    LaunchedEffect(Unit) {
        vm.refreshPhrase()
        while (true) {
            kotlinx.coroutines.delay(5000)
            val currentZone = ZoneId.systemDefault()
            zone = currentZone
            today = LocalDate.now(currentZone)
            nowMillis = System.currentTimeMillis()
            nowTime = LocalTime.now(currentZone)
        }
    }

    val day = doses.filter {
        Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() == today &&
            (!it.cancelled || it.status != "PENDING")
    }.sortedBy { it.plannedAt }

    val todayPending = day.filter { !it.cancelled && it.status == "PENDING" }
    val todayOverdue = todayPending.filter { it.plannedAt <= nowMillis }.sortedBy { it.plannedAt }
    val todayUpcoming = todayPending.filter { it.plannedAt > nowMillis }.sortedBy { it.plannedAt }

    val upcomingAfterToday = doses.filter {
        !it.cancelled && it.status == "PENDING" &&
            Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() > today
    }.sortedBy { it.plannedAt }.take(5)

    val heroDose: DoseOccurrence?
    val heroSectionTitle: String
    val isHeroFuture: Boolean
    if (todayOverdue.isNotEmpty()) {
        heroDose = todayOverdue.first()
        heroSectionTitle = "Пора принять"
        isHeroFuture = false
    } else if (todayUpcoming.isNotEmpty()) {
        heroDose = todayUpcoming.first()
        heroSectionTitle = "Ближайший приём"
        isHeroFuture = false
    } else if (upcomingAfterToday.isNotEmpty()) {
        heroDose = upcomingAfterToday.first()
        heroSectionTitle = "Следующий приём"
        isHeroFuture = true
    } else {
        heroDose = null
        heroSectionTitle = "Ближайший приём"
        isHeroFuture = false
    }

    val activeSchedules = schedules.filter { it.enabled }
    val greeting = when (nowTime.hour) { in 5..11 -> "Доброе утро"; in 12..17 -> "Добрый день"; in 18..22 -> "Добрый вечер"; else -> "Тихой ночи" }

    Page("Мяу, пора!", actions = { IconButton(onClick = add) { Icon(Icons.Outlined.Add, "Добавить напоминание") } },
        refreshing = refreshing, onRefresh = {
            val currentZone = ZoneId.systemDefault()
            zone = currentZone
            today = LocalDate.now(currentZone)
            nowMillis = System.currentTimeMillis()
            nowTime = LocalTime.now(currentZone)
            vm.refreshToday()
        }) {
        Text(greeting, style = MaterialTheme.typography.headlineLarge)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prefs.cat) Cat(Modifier.size(78.dp), sleepy = nowTime.hour < 6)
                Column(Modifier.weight(1f).padding(start = if (prefs.cat) 12.dp else 0.dp)) {
                    Text(if (prefs.phrases && phrase.isNotBlank()) phrase else "В вашем ритме. С заботой о себе.", style = MaterialTheme.typography.titleMedium)
                    Text("Маленькие шаги, тёплые привычки", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        PermissionCard(vm, settings)

        if (heroDose != null) {
            var deleteNextDialog by remember { mutableStateOf(false) }
            var snoozeNext by remember { mutableStateOf(false) }
            var confirmFutureTakeDialog by remember { mutableStateOf(false) }
            val heroDoseTimeText = Instant.ofEpochMilli(heroDose.plannedAt).atZone(zone).format(dateTimeFormat)

            Section(heroSectionTitle) {
                Card(
                    onClick = { if (heroDose.scheduleId.isNotBlank()) editSchedule(heroDose.scheduleId) },
                    modifier = Modifier.fillMaxWidth().testTag("next-dose-card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(heroDose.medicationName, style = MaterialTheme.typography.titleMedium)
                                Text(heroDoseTimeText, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (heroDose.scheduleId.isNotBlank()) {
                                IconButton(onClick = { editSchedule(heroDose.scheduleId) }, modifier = Modifier.testTag("edit-next-schedule")) {
                                    Icon(Icons.Outlined.Edit, "Редактировать расписание и время", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            IconButton(onClick = { deleteNextDialog = true }) {
                                Icon(Icons.Outlined.Delete, "Удалить напоминание", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = {
                                if (isHeroFuture) confirmFutureTakeDialog = true
                                else vm.mark(heroDose.id, "TAKEN")
                            }) { Text("Принято") }
                            IconButton(onClick = { snoozeNext = true }) { Icon(Icons.Outlined.Snooze, "Отложить") }
                            TextButton(onClick = { vm.mark(heroDose.id, "SKIPPED") }) { Text("Пропустить") }
                        }
                    }
                }
            }
            if (confirmFutureTakeDialog) {
                AlertDialog(
                    onDismissRequest = { confirmFutureTakeDialog = false },
                    title = { Text("Принять раньше времени?") },
                    text = { Text("Этот приём «${heroDose.medicationName}» запланирован на $heroDoseTimeText. Вы уверены, что хотите отметить его принятым сейчас?") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.mark(heroDose.id, "TAKEN")
                            confirmFutureTakeDialog = false
                        }) {
                            Text("Принято")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmFutureTakeDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
            if (snoozeNext) SnoozeDialog(dismiss = { snoozeNext = false }, select = { minutes -> vm.task { vm.app.scheduler.snooze(heroDose.id, minutes) } })
            if (deleteNextDialog) {
                AlertDialog(
                    onDismissRequest = { deleteNextDialog = false },
                    title = { Text("Удалить напоминание?") },
                    text = { Text("Удалить напоминание о приёме «${heroDose.medicationName}»?") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.deleteOccurrence(heroDose.id)
                            deleteNextDialog = false
                        }) {
                            Text("Удалить этот приём", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        Row {
                            if (heroDose.scheduleId.isNotBlank()) {
                                TextButton(onClick = {
                                    vm.deleteSchedule(heroDose.scheduleId)
                                    deleteNextDialog = false
                                }) {
                                    Text("Отменить всё расписание")
                                }
                            }
                            TextButton(onClick = { deleteNextDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    }
                )
            }
        }

        Text(today.format(dayFormat).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        if (day.isEmpty()) EmptyState("На сегодня всё спокойно", "Добавьте напоминание или выберите препарат.", prefs.cat)
        day.forEach { dose -> DoseCard(dose, vm, false, onEditSchedule = editSchedule) }

        if (upcomingAfterToday.isNotEmpty()) {
            Section("Скоро предстоит") {
                upcomingAfterToday.forEach { dose -> DoseCard(dose, vm, false, onEditSchedule = editSchedule) }
            }
        }

        if (activeSchedules.isNotEmpty()) {
            Section("Активные расписания") {
                activeSchedules.forEach { sched ->
                    var deleteScheduleDialog by remember { mutableStateOf(false) }
                    val med = meds.find { it.id == sched.medicationId }
                    Card(
                        onClick = { editSchedule(sched.id) },
                        modifier = Modifier.fillMaxWidth().testTag("schedule-card-${sched.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(med?.name ?: sched.label, style = MaterialTheme.typography.titleMedium)
                                Text("${sched.times.replace(",", ", ")} · ${sched.label}", style = MaterialTheme.typography.bodyMedium)
                                sched.endDate?.let {
                                    Text("Курс до $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { editSchedule(sched.id) }, modifier = Modifier.testTag("edit-schedule-${sched.id}")) {
                                Icon(Icons.Outlined.Edit, "Редактировать расписание и время", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { deleteScheduleDialog = true }) {
                                Icon(Icons.Outlined.Delete, "Удалить расписание", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (deleteScheduleDialog) {
                        AlertDialog(
                            onDismissRequest = { deleteScheduleDialog = false },
                            title = { Text("Удалить расписание?") },
                            text = { Text("Все будущие напоминания для «${med?.name ?: sched.label}» будут отменены. Прошедшая история приёмов сохранится.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    vm.deleteSchedule(sched.id)
                                    deleteScheduleDialog = false
                                }) {
                                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deleteScheduleDialog = false }) {
                                    Text("Отмена")
                                }
                            }
                        )
                    }
                }
            }
        }

        FilledTonalButton(onClick = add, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить напоминание")
        }
    }
}

@Composable
fun DoseCard(dose: DoseOccurrence, vm: MeowViewModel, history: Boolean, onEditSchedule: ((String) -> Unit)? = null) {
    var editing by remember { mutableStateOf(false) }
    var snooze by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val pendingPast = dose.status == "PENDING" && dose.plannedAt < System.currentTimeMillis()
    val status = when (dose.status) { "TAKEN" -> "Принято"; "SKIPPED" -> "Пропущено"; else -> if (pendingPast) "Ожидает приёма" else "Ожидается" }
    val canEditSchedule = !history && dose.scheduleId.isNotBlank() && onEditSchedule != null
    Card(
        onClick = { if (canEditSchedule) onEditSchedule!!(dose.scheduleId) },
        enabled = canEditSchedule,
        modifier = Modifier.fillMaxWidth().testTag("dose-card-${dose.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Instant.ofEpochMilli(dose.plannedAt).atZone(ZoneId.systemDefault()).format(timeFormat), style = MaterialTheme.typography.titleLarge)
                Text(dose.medicationName, modifier = Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
                if (dose.status == "TAKEN") {
                    Icon(Icons.Outlined.CheckCircle, "Принято", tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                }
                if (canEditSchedule) {
                    IconButton(onClick = { onEditSchedule!!(dose.scheduleId) }, modifier = Modifier.testTag("edit-dose-${dose.id}")) {
                        Icon(Icons.Outlined.Edit, "Редактировать расписание и время", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { deleting = true }) {
                    Icon(Icons.Outlined.Delete, "Удалить напоминание", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
        Column { listOf("TAKEN" to "Принято", "SKIPPED" to "Пропущено", "PENDING" to "Ожидает приёма").forEach { (status, name) ->
            TextButton(onClick = { vm.mark(dose.id, status); editing = false }) { Text(name) }
        } }
    }, confirmButton = { TextButton(onClick = { editing = false }) { Text("Закрыть") } })
    if (snooze) SnoozeDialog(dismiss = { snooze = false }, select = { minutes -> vm.task { vm.app.scheduler.snooze(dose.id, minutes) } })
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Удалить напоминание?") },
            text = { Text("Удалить напоминание о приёме «${dose.medicationName}»?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteOccurrence(dose.id)
                    deleting = false
                }) {
                    Text("Удалить этот приём", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    if (dose.scheduleId.isNotBlank()) {
                        TextButton(onClick = {
                            vm.deleteSchedule(dose.scheduleId)
                            deleting = false
                        }) {
                            Text("Отменить всё расписание")
                        }
                    }
                    TextButton(onClick = { deleting = false }) {
                        Text("Отмена")
                    }
                }
            }
        )
    }
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