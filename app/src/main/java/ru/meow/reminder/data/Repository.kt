package ru.meow.reminder.data

import androidx.room.withTransaction
import ru.meow.reminder.domain.ScheduleEngine
import java.time.LocalDateTime

class Repository(val database: MeowDatabase, val settings: SettingsStore) {
    val dao = database.dao()
    suspend fun initialize() {
        if (settings.get().initialized) return
        database.withTransaction {
            // Fixed IDs make first-run seeding idempotent even after an interrupted launch.
            listOf("Ибупрофен" to "От головной боли", "Витамин D" to "", "Мой препарат" to "").forEachIndexed { i, (name, purpose) ->
                if (dao.allMedications().none { it.id == "example-$i" }) {
                    dao.medication(MedicationTemplate(id = "example-$i", name = name, purpose = purpose, color = i))
                }
            }
        }
        settings.update { it.copy(initialized = true) }
    }

    suspend fun saveSchedule(schedule: MedicationSchedule) {
        ScheduleEngine.validate(schedule)
        database.withTransaction {
            val old = dao.allSchedules().find { it.id == schedule.id }
            val sameRule = old != null && old.copy(label = schedule.label,
                repeatNotification = schedule.repeatNotification, repeatMinutes = schedule.repeatMinutes,
                extraNotifications = schedule.extraNotifications, generatedThrough = null) == schedule.copy(generatedThrough = null)
            if (sameRule) {
                dao.schedule(schedule.copy(generatedThrough = old!!.generatedThrough))
                if (!schedule.repeatNotification && old.repeatNotification) {
                    dao.allOccurrences().filter { it.scheduleId == schedule.id && it.status == "PENDING" && it.nextAttemptKind == "REPEAT" }
                        .forEach { dao.occurrence(it.copy(nextAttemptAt = null, nextAttemptKind = null)) }
                }
            } else {
                dao.cancelSchedule(schedule.id)
                dao.clearFuture(schedule.id, System.currentTimeMillis())
                dao.schedule(schedule.copy(generatedThrough = null))
            }
        }
    }

    suspend fun mark(id: String, status: String) {
        require(status in setOf("PENDING", "TAKEN", "SKIPPED"))
        database.withTransaction {
            val dose = dao.occurrence(id) ?: return@withTransaction
            if (dose.status == status) return@withTransaction
            dao.record(IntakeRecord(occurrenceId = id, oldStatus = dose.status, status = status))
            dao.occurrence(dose.copy(status = status, confirmedAt = if (status == "PENDING") null else System.currentTimeMillis(), nextAttemptAt = null, nextAttemptKind = null))
        }
    }

    suspend fun deleteOccurrence(id: String) {
        database.withTransaction {
            dao.deleteOccurrence(id)
            dao.deleteRecordsForOccurrence(id)
        }
    }

    suspend fun deleteSchedule(id: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val schedule = dao.allSchedules().find { it.id == id }
            dao.clearFuture(id, now)
            dao.cancelSchedule(id)
            dao.deleteSchedule(id)
            if (schedule != null) {
                val hasOther = dao.allSchedules().any { it.medicationId == schedule.medicationId && it.enabled && it.id != id }
                if (!hasOther) {
                    val med = dao.allMedications().find { it.id == schedule.medicationId }
                    if (med != null && med.enabled) dao.medication(med.copy(enabled = false))
                }
            }
        }
    }

    suspend fun addQuickReminder(
        medicationId: String,
        local: LocalDateTime,
        frequency: String,
        interval: Int = 1,
        durationDays: Int? = null
    ): MedicationSchedule {
        require(frequency in setOf("ONCE", "DAILY", "WEEKLY")) { "Выберите периодичность" }
        require(interval in 1..99) { "Интервал должен быть от 1 до 99" }
        require(durationDays == null || durationDays in 1..3650) { "Количество дней должно быть от 1 до 3650" }
        require(local.second == 0 && local.nano == 0) { "Выберите время с точностью до минуты" }
        require(local > LocalDateTime.now()) { "Выберите будущее время напоминания" }
        val prefs = settings.get()
        val startDate = local.toLocalDate()
        val endDate = if (frequency != "ONCE" && durationDays != null) startDate.plusDays(durationDays - 1L).toString() else null
        val label = when (frequency) {
            "ONCE" -> "Разовое напоминание"
            "WEEKLY" -> if (durationDays != null) "Раз в неделю, $durationDays дн." else "Раз в неделю"
            else -> if (interval == 2) {
                if (durationDays != null) "Через день, $durationDays дн." else "Через день"
            } else if (interval > 1) {
                if (durationDays != null) "Каждые $interval дн., $durationDays дн." else "Каждые $interval дн."
            } else {
                if (durationDays != null) "Каждый день, $durationDays дн." else "Каждый день"
            }
        }
        val schedule = MedicationSchedule(
            medicationId = medicationId,
            label = label,
            enabled = true,
            frequency = frequency,
            interval = interval,
            startDate = startDate.toString(),
            endDate = endDate,
            times = local.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
            repeatNotification = prefs.repeatDefault,
            repeatMinutes = prefs.repeatMinutes
        )
        ScheduleEngine.validate(schedule)
        database.withTransaction {
            val med = dao.allMedications().find { it.id == medicationId }
                ?: throw IllegalArgumentException("Препарат не найден")
            require(!med.deleted && !med.archived && med.name.isNotBlank()) { "Выберите препарат из действующей библиотеки" }
            val old = dao.allSchedules().filter { it.medicationId == medicationId }
            if (med.enabled) {
                require(old.none { it.enabled && ScheduleEngine.between(it, local, local).isNotEmpty() }) {
                    "На это время уже запланирован приём этого препарата"
                }
            } else {
                // Explicit quick-add starts only the new reminder, not every saved rule.
                old.filter { it.enabled }.forEach {
                    dao.cancelSchedule(it.id)
                    dao.clearFuture(it.id, System.currentTimeMillis())
                    dao.schedule(it.copy(enabled = false, generatedThrough = null))
                }
            }
            dao.schedule(schedule)
            dao.medication(med.copy(enabled = true))
        }
        return schedule
    }

    suspend fun duplicate(medication: MedicationTemplate) {
        database.withTransaction {
            val copy = medication.copy(id = newId(), name = medication.name + " — копия", enabled = false, archived = false, deleted = false, createdAt = System.currentTimeMillis())
            dao.medication(copy)
            dao.allSchedules().filter { it.medicationId == medication.id }.forEach {
                dao.schedule(it.copy(id = newId(), medicationId = copy.id, generatedThrough = null))
            }
        }
    }

    suspend fun backup(): String = database.withTransaction {
        appJson.encodeToString(Backup(medications = dao.allMedications(), schedules = dao.allSchedules(), occurrences = dao.allOccurrences(), records = dao.allRecords(), notes = dao.allNotes(), phrases = dao.allPhrases(), settings = settings.get().copy(recentPhrases = emptyList())))
    }

    suspend fun importBackup(text: String, replace: Boolean) {
        val backup = appJson.decodeFromString<Backup>(text)
        require(backup.version == 1) { "Неизвестная версия резервной копии" }
        require(backup.medications.map { it.id }.distinct().size == backup.medications.size) { "Повторяющиеся идентификаторы" }
        val meds = backup.medications.map { it.id }.toSet()
        require(backup.schedules.map { it.id }.distinct().size == backup.schedules.size) { "Повторяющиеся расписания" }
        require(backup.occurrences.map { it.id }.distinct().size == backup.occurrences.size) { "Повторяющиеся приёмы" }
        backup.schedules.forEach { ScheduleEngine.validate(it); require(it.medicationId in meds) { "Расписание без препарата" } }
        backup.occurrences.forEach { LocalDateTime.parse(it.localDateTime); require(it.medicationId in meds); require(it.status in setOf("PENDING", "TAKEN", "SKIPPED")) }
        require(backup.settings.theme in setOf("system", "light", "dark"))
        database.withTransaction {
            if (replace) clear()
            // Merge never overwrites a local entry with the same stable ID.
            val oldM = dao.allMedications().map { it.id }.toSet()
            val oldS = dao.allSchedules().map { it.id }.toSet()
            val oldN = dao.allNotes().map { it.id }.toSet()
            val oldP = dao.allPhrases().map { it.id }.toSet()
            val oldR = dao.allRecords().map { it.id }.toSet()
            backup.medications.filter { it.id !in oldM }.forEach { dao.medication(it) }
            backup.schedules.filter { it.id !in oldS }.forEach { dao.schedule(it.copy(generatedThrough = null)) }
            dao.insertOccurrences(backup.occurrences.map { it.copy(nextAttemptAt = null) })
            backup.records.filter { it.id !in oldR }.forEach { dao.record(it) }
            backup.notes.filter { it.id !in oldN }.forEach { dao.note(it) }
            backup.phrases.filter { it.id !in oldP }.forEach { dao.phrase(it) }
        }
        if (replace) settings.update { backup.settings.copy(initialized = true, welcomed = true) }
    }

    suspend fun clear() {
        dao.clearRecords(); dao.clearOccurrences(); dao.clearSchedules(); dao.clearMedications(); dao.clearNotes(); dao.clearPhrases()
    }
}
