package ru.meow.reminder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ru.meow.reminder.data.MedicationTemplate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalContext
import android.app.TimePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
    val medications by vm.medications.collectAsStateWithLifecycle()
    val saving by vm.manualSaving.collectAsStateWithLifecycle()
    val saveError by vm.manualError.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var colorDialogFor by remember { mutableStateOf<String?>(null) }
    var manualIntake by remember { mutableStateOf(false) }
    var selectedMedicationId by remember { mutableStateOf("") }
    var newMedication by remember { mutableStateOf(false) }
    var manualName by remember { mutableStateOf("") }
    var manualDosage by remember { mutableStateOf("") }
    var manualUnit by remember { mutableStateOf("") }
    var manualSyrup by remember { mutableStateOf(false) }
    var manualTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var manualAmount by remember { mutableStateOf("") }
    var manualError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val savedMedications = medications.filter { !it.deleted && !it.archived && it.name.isNotBlank() }
    val zone = remember { mutableStateOf(ZoneId.systemDefault()) }
    var today by remember { mutableStateOf(LocalDate.now(zone.value)) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var selectedDates by remember { mutableStateOf(setOf<LocalDate>()) }
    var deleting by remember { mutableStateOf(false) }
    var deletingDose by remember { mutableStateOf<DoseOccurrence?>(null) }
    var amountFor by remember { mutableStateOf<DoseOccurrence?>(null) }
    var amountText by remember { mutableStateOf("") }
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
    Page("Календарь", actions = {
        IconButton(onClick = {
            selectedMedicationId = savedMedications.firstOrNull()?.id.orEmpty()
            newMedication = false
            manualName = ""
            manualDosage = ""
            manualUnit = ""
            manualSyrup = false
            manualTime = LocalTime.now().withSecond(0).withNano(0)
            manualAmount = ""
            manualError = null
            vm.manualError.value = null
            manualIntake = true
        }) { Icon(Icons.Outlined.Add, "Добавить приём") }
    }) {
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
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.pointerInput(month) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (drag > 70f) month = month.minusMonths(1)
                        else if (drag < -70f) month = month.plusMonths(1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                )
            }
        ) {
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
                                val isSelected = if (selectedDates.isNotEmpty()) date in selectedDates else selected == date
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize().combinedClickable(onClick = { if (selectedDates.isNotEmpty()) selectedDates = if (date in selectedDates) selectedDates - date else selectedDates + date else selected = date }, onLongClick = { selectedDates = if (date in selectedDates) selectedDates - date else selectedDates + date; selected = date }).testTag("calendar-day-$date")) {
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(selected.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                selectedMedicationId = savedMedications.firstOrNull()?.id.orEmpty()
                newMedication = false; manualName = ""; manualDosage = ""; manualUnit = ""; manualSyrup = false
                manualTime = LocalTime.now().withSecond(0).withNano(0); manualAmount = ""; manualError = null; vm.manualError.value = null; manualIntake = true
            }) { Icon(Icons.Outlined.Add, "Добавить приём в выбранный день") }
        }
        if (selectedDoses.isEmpty()) Text("На этот день записей нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selectedDates.isEmpty()) TextButton(onClick = { selectedDates = setOf(selected) }) { Text("Выбрать несколько дней") }
        if (selectedDates.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Выбрано дней: ${selectedDates.size}", Modifier.weight(1f))
            TextButton(onClick = { selectedDates = emptySet() }) { Text("Отмена") }
            TextButton(onClick = { deleting = true }) { Text("Удалить записи") }
        }
        selectedDoses.forEach { dose ->
            val medication = medications.firstOrNull { it.id == dose.medicationId }
            CalendarDoseRow(dose, zone.value, now, medication?.dosage.orEmpty(), medication?.unit.orEmpty(), { amountFor = dose; amountText = dose.amount?.toString().orEmpty() }, { deletingDose = dose }) { id, status -> vm.mark(id, status) }
        }
        deletingDose?.let { dose ->
            AlertDialog(onDismissRequest = { deletingDose = null }, title = { Text("Удалить запись?") }, text = { Text("Приём «${dose.medicationName}» будет удалён, а его напоминание отменено. Это действие нельзя отменить.") }, confirmButton = { TextButton(onClick = { vm.deleteOccurrences(listOf(dose.id)); deletingDose = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deletingDose = null }) { Text("Отмена") } })
        }
        if (deleting) {
            val ids = byDate.filterKeys { it in selectedDates }.values.flatten().map { it.id }
            AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить записи?") }, text = { Text("Удаляются записи выбранных дней, включая запланированные приёмы со статусом «Ожидает». Другие дни не затрагиваются.") }, confirmButton = { TextButton(onClick = { vm.deleteOccurrences(ids); deleting = false; selectedDates = emptySet() }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } })
        }
        amountFor?.let { dose -> AlertDialog(onDismissRequest = { amountFor = null }, title = { Text("Изменить количество") }, text = { IntakeAmountField(amountText, { amountText = it }, dose.amountUnit == "мл" || (dose.amountUnit == null && medications.firstOrNull { it.id == dose.medicationId }?.isSyrup == true), required = true) }, confirmButton = { TextButton(enabled = parseIntakeAmount(amountText) != null, onClick = { parseIntakeAmount(amountText)?.let { vm.updateOccurrenceAmount(dose.id, it); amountFor = null } }) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = { amountFor = null }) { Text("Отмена") } }) }
        if (manualIntake) {
            val selectedMedication = savedMedications.firstOrNull { it.id == selectedMedicationId }
            val syrup = if (newMedication || savedMedications.isEmpty()) manualSyrup else selectedMedication?.isSyrup ?: false
            AlertDialog(
                onDismissRequest = { manualIntake = false },
                title = { Text("Добавить приём") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(selected.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))).replaceFirstChar { it.uppercase() })
                        if (savedMedications.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !newMedication, onClick = { newMedication = false }, label = { Text("Из сохранённых") })
                                FilterChip(selected = newMedication, onClick = { newMedication = true }, label = { Text("Новый препарат") })
                            }
                        }
                        if (!newMedication && savedMedications.isNotEmpty()) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { menu = true }) {
                                    Text(savedMedications.firstOrNull { it.id == selectedMedicationId }?.let { "${it.name} · ${it.dosage} ${it.unit}".trim() } ?: "Выбрать препарат")
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    savedMedications.forEach { med ->
                                        DropdownMenuItem(text = { Text("${med.name} · ${med.dosage} ${med.unit}".trim()) }, onClick = { selectedMedicationId = med.id; manualSyrup = med.isSyrup; manualUnit = med.unit; manualDosage = med.dosage; menu = false })
                                    }
                                }
                            }
                        }
                        if (newMedication || savedMedications.isEmpty()) {
                            OutlinedTextField(manualName, { manualName = it }, label = { Text("Название препарата *") }, singleLine = true)
                            OutlinedTextField(manualDosage, { manualDosage = it }, label = { Text("Дозировка") }, singleLine = true)
                            OutlinedTextField(manualUnit, { manualUnit = it }, label = { Text("Единица дозировки") }, singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Это сироп", modifier = Modifier.weight(1f))
                                Switch(manualSyrup, { manualSyrup = it })
                            }
                        }
                        val med = savedMedications.firstOrNull { it.id == selectedMedicationId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Время: ${manualTime.format(DateTimeFormatter.ofPattern("HH:mm"))}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { TimePickerDialog(context, { _, h, m -> manualTime = LocalTime.of(h, m) }, manualTime.hour, manualTime.minute, true).show() }) { Text("Изменить") }
                        }
                        IntakeAmountField(manualAmount, { manualAmount = it }, syrup, required = syrup)
                        if (syrup && manualAmount.isBlank()) Text("Укажите объём сиропа в миллилитрах", style = MaterialTheme.typography.bodySmall)
                        manualError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !saving && (parseIntakeAmount(manualAmount) != null || (!syrup && manualAmount.isBlank())), onClick = {
                        val useNew = newMedication || savedMedications.isEmpty()
                        val med = if (useNew) MedicationTemplate(name = manualName.trim(), dosage = manualDosage.trim(), unit = manualUnit.trim(), isSyrup = manualSyrup) else savedMedications.firstOrNull { it.id == selectedMedicationId }
                        val parsed = if (manualAmount.isBlank() && !syrup) 1.0 else parseIntakeAmount(manualAmount)
                        when {
                            med == null -> manualError = "Выберите препарат"
                            med.name.isBlank() -> manualError = "Укажите название препарата"
                            parsed == null -> manualError = "Укажите количество больше нуля"
                            LocalDateTime.of(selected, manualTime).isAfter(LocalDateTime.now()) -> manualError = "Нельзя отметить будущий приём как принятый"
                            else -> vm.addManualIntake(med, useNew, LocalDateTime.of(selected, manualTime), parsed) { manualIntake = false }
                        }
                    }) { Text(if (saving) "Сохраняем…" else "Сохранить") }
                },
                dismissButton = { TextButton(onClick = { manualIntake = false }) { Text("Отмена") } }
            )
        }
    }
}

@Composable
private fun CalendarDoseRow(dose: DoseOccurrence, zone: ZoneId, now: Long, dosage: String, unit: String, onAmount: () -> Unit, onDelete: () -> Unit, mark: (String, String) -> Unit) {
    val canMark = dose.plannedAt <= now
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Instant.ofEpochMilli(dose.plannedAt).atZone(zone).toLocalTime().toString().take(5), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(dose.medicationName, style = MaterialTheme.typography.titleMedium)
                if (dosage.isNotBlank()) Text("Дозировка: $dosage $unit".trim(), style = MaterialTheme.typography.bodySmall)
                if (dose.amount != null) {
                    val amountLabel = dose.amountUnit?.takeIf { it.isNotBlank() } ?: "шт."
                    Text("За приём: ${formatIntakeAmount(dose.amount)} $amountLabel", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onAmount) { Text("Изменить количество") }
                TextButton(onClick = onDelete) { Text("Удалить запись") }
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
