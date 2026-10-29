package ru.meow.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.meow.reminder.data.*
import ru.meow.reminder.reminders.ReminderScheduler
import java.time.*

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private lateinit var db: MeowDatabase
    private lateinit var repo: Repository
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<MeowApp>()
        db = Room.inMemoryDatabaseBuilder(context, MeowDatabase::class.java).build()
        repo = Repository(db, context.repository.settings)
    }
    @After fun close() { db.close() }
    @Test fun confirmationKeepsAuditAndCancelsRetries() = runBlocking {
        val dose = DoseOccurrence("d", "s", "m", "Проверка", "2026-01-01T09:00", 100, nextAttemptAt = 999)
        repo.dao.occurrence(dose)
        repo.mark("d", "TAKEN")
        assertEquals("TAKEN", repo.dao.occurrence("d")!!.status)
        assertNull(repo.dao.occurrence("d")!!.nextAttemptAt)
        repo.mark("d", "SKIPPED")
        assertEquals(2, repo.dao.allRecords().size)
    }
    @Test fun schedulerIsIdempotentAndDisablePreservesHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MeowApp>()
        val med = MedicationTemplate(name = "Тест", enabled = true)
        repo.dao.medication(med)
        repo.saveSchedule(MedicationSchedule(id = "test-schedule", medicationId = med.id, frequency = "DAILY", times = "23:59"))
        val scheduler = ReminderScheduler(context, repo)
        try {
            scheduler.reconcile()
            val ids = repo.dao.allOccurrences().map { it.id }
            scheduler.reconcile()
            assertEquals(ids.toSet(), repo.dao.allOccurrences().map { it.id }.toSet())
            val dose = repo.dao.allOccurrences().first()
            repo.mark(dose.id, "TAKEN")
            repo.dao.medication(med.copy(enabled = false))
            scheduler.reconcile()
            assertEquals("TAKEN", repo.dao.occurrence(dose.id)!!.status)
            assertTrue(repo.dao.allOccurrences().none { !it.cancelled && it.status == "PENDING" })
        } finally { scheduler.cancelAll() }
    }
    @Test fun importMergeDoesNotDuplicateAndNotesKeepLongText() = runBlocking {
        val med = MedicationTemplate(name = "Проверка")
        repo.dao.medication(med)
        repo.dao.note(Note(title = "Длинная заметка", body = "мур ".repeat(20000)))
        val backup = repo.backup()
        repo.importBackup(backup, false)
        repo.importBackup(backup, false)
        assertEquals(1, repo.dao.allMedications().size)
        assertEquals(80000, repo.dao.allNotes().single().body.length)
    }
    @Test fun scheduleEditDoesNotEraseConfirmedHistory() = runBlocking {
        val med = MedicationTemplate(name = "Проверка")
        repo.dao.medication(med)
        val schedule = MedicationSchedule(medicationId = med.id)
        repo.saveSchedule(schedule)
        repo.dao.occurrence(DoseOccurrence("old", schedule.id, med.id, med.name, "2026-01-01T09:00", 100, status = "TAKEN"))
        repo.saveSchedule(schedule.copy(times = "18:30"))
        assertEquals("TAKEN", repo.dao.occurrence("old")!!.status)
        repo.dao.medication(med.copy(archived = true))
        assertNotNull(repo.dao.occurrence("old"))
    }
    @Test fun snoozeBelongsToDoseAndDoesNotMoveBaseSchedule() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MeowApp>()
        val med = MedicationTemplate(name = "Отсрочка", enabled = true)
        val schedule = MedicationSchedule(medicationId = med.id, frequency = "DAILY", times = "22:45")
        repo.dao.medication(med)
        repo.saveSchedule(schedule)
        val now = System.currentTimeMillis()
        val dose = DoseOccurrence("snoozed", schedule.id, med.id, med.name, LocalDateTime.now().toString(), now, notified = true)
        repo.dao.occurrence(dose)
        val scheduler = ReminderScheduler(context, repo)
        try {
            scheduler.snooze(dose.id, 15)
            val postponed = repo.dao.occurrence(dose.id)!!
            assertTrue(postponed.nextAttemptAt!! >= now + 15 * 60000)
            assertEquals("SNOOZE", postponed.nextAttemptKind)
            assertEquals("22:45", repo.dao.allSchedules().single().times)
            scheduler.mark(dose.id, "SKIPPED")
            assertNull(repo.dao.occurrence(dose.id)!!.nextAttemptAt)
        } finally { scheduler.cancelAll() }
    }
    @Test fun duplicateBroadcastDoesNotSendAnotherRepeat() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MeowApp>()
        val med = MedicationTemplate(name = "Повторы", enabled = true)
        val schedule = MedicationSchedule(medicationId = med.id, repeatNotification = true, extraNotifications = 2)
        repo.dao.medication(med)
        repo.saveSchedule(schedule)
        val now = System.currentTimeMillis()
        val dose = DoseOccurrence("repeat-dose", schedule.id, med.id, med.name, LocalDateTime.now().toString(), now)
        repo.dao.occurrence(dose)
        val scheduler = ReminderScheduler(context, repo)
        try {
            scheduler.fire(dose.id, now)
            val first = repo.dao.occurrence(dose.id)!!
            assertTrue(first.notified)
            assertNotNull(first.nextAttemptAt)
            scheduler.fire(dose.id, now)
            assertEquals(first, repo.dao.occurrence(dose.id))
            scheduler.mark(dose.id, "TAKEN")
            assertNull(repo.dao.occurrence(dose.id)!!.nextAttemptAt)
        } finally { scheduler.cancelAll() }
    }
    @Test fun repairDoesNotConsumeAnAlreadyArmedJustDueAlarm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MeowApp>()
        val local = LocalDateTime.now().minusSeconds(2).withNano(0)
        val med = MedicationTemplate(name = "Гонка восстановления", enabled = true)
        val schedule = MedicationSchedule(medicationId = med.id, startDate = local.toLocalDate().toString(), times = local.toLocalTime().toString())
        val dose = DoseOccurrence(ru.meow.reminder.domain.ScheduleEngine.occurrenceId(schedule.id, local),
            schedule.id, med.id, med.name, local.toString(), local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        repo.dao.medication(med)
        repo.saveSchedule(schedule)
        repo.dao.occurrence(dose)
        context.getSharedPreferences("system_alarms", android.content.Context.MODE_PRIVATE).edit()
            .putStringSet("ids", setOf(dose.id)).commit()
        val scheduler = ReminderScheduler(context, repo)
        try {
            scheduler.reconcile()
            assertFalse("Repair не должен поглощать уже взведённый будильник перед FIRE", repo.dao.occurrence(dose.id)!!.notified)
            scheduler.reconcile(recoverRecent = false) // boot/time-change: no historical replay
            assertTrue(repo.dao.occurrence(dose.id)!!.notified)
        } finally { scheduler.cancelAll() }
    }
    @Test fun quickReminderReusesMedicationAndDoesNotActivateSavedRules() = runBlocking {
        val med = MedicationTemplate(name = "Сохранённый препарат", purpose = "Личное назначение", dosage = "Пользовательская", note = "Не менять", enabled = false)
        repo.dao.medication(med)
        val old = MedicationSchedule(medicationId = med.id, label = "Сохранённый шаблон", frequency = "DAILY", times = "07:00")
        repo.saveSchedule(old)
        val local = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
        val created = repo.addQuickReminder(med.id, local, "ONCE")
        assertEquals(1, repo.dao.allMedications().size)
        assertEquals(med.copy(enabled = true), repo.dao.allMedications().single())
        assertTrue(created.enabled)
        val saved = repo.dao.allSchedules().first { it.id == old.id }
        assertFalse(saved.enabled)
        assertEquals(old.times, saved.times)
        assertEquals(old.label, saved.label)
    }
    @Test fun quickReminderLeavesAlreadyActiveSchedulesUnchanged() = runBlocking {
        val med = MedicationTemplate(name = "Активный препарат", enabled = true)
        repo.dao.medication(med)
        val local = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0)
        val old = MedicationSchedule(medicationId = med.id, startDate = local.toLocalDate().toString(), times = "08:00", frequency = "DAILY")
        repo.saveSchedule(old)
        repo.addQuickReminder(med.id, local, "ONCE")
        assertEquals(old, repo.dao.allSchedules().first { it.id == old.id })
    }
    @Test fun quickReminderRejectsAnExistingDoseAtTheSameTime() = runBlocking {
        val med = MedicationTemplate(name = "Без дублей", enabled = true)
        repo.dao.medication(med)
        val local = LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
        repo.addQuickReminder(med.id, local, "ONCE")
        try {
            repo.addQuickReminder(med.id, local, "ONCE")
            fail("Дубль должен быть отклонён")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, repo.dao.allSchedules().size)
    }
    @Test fun deleteOccurrenceRemovesOccurrenceAndAuditRecords() = runBlocking {
        val med = MedicationTemplate(name = "Для удаления", enabled = true)
        repo.dao.medication(med)
        val dose = DoseOccurrence("del-1", "sched-1", med.id, med.name, "2026-01-01T10:00", 100, status = "PENDING")
        repo.dao.occurrence(dose)
        repo.mark(dose.id, "TAKEN")
        assertTrue(repo.dao.allOccurrences().any { it.id == "del-1" })
        assertTrue(repo.dao.allRecords().any { it.occurrenceId == "del-1" })

        repo.deleteOccurrence("del-1")
        assertNull(repo.dao.occurrence("del-1"))
        assertFalse(repo.dao.allRecords().any { it.occurrenceId == "del-1" })
    }
    @Test fun deleteScheduleClearsFutureAndCancelsPending() = runBlocking {
        val med = MedicationTemplate(name = "Для отмены расписания", enabled = true)
        repo.dao.medication(med)
        val schedule = MedicationSchedule(id = "sched-to-delete", medicationId = med.id, frequency = "DAILY", times = "09:00")
        repo.saveSchedule(schedule)
        val now = System.currentTimeMillis()
        val pastDose = DoseOccurrence("past", schedule.id, med.id, med.name, "2026-01-01T09:00", now - 100000, status = "TAKEN")
        val futureDose = DoseOccurrence("future", schedule.id, med.id, med.name, "2026-01-02T09:00", now + 100000, status = "PENDING")
        repo.dao.occurrence(pastDose)
        repo.dao.occurrence(futureDose)

        repo.deleteSchedule(schedule.id)
        assertNull(repo.dao.allSchedules().find { it.id == schedule.id })
        // Future pending occurrence cleared
        assertNull(repo.dao.occurrence("future"))
        // Past taken occurrence kept in history
        assertNotNull(repo.dao.occurrence("past"))
    }
    @Test fun quickReminderWithIntervalAndDurationConfiguresScheduleCorrectly() = runBlocking {
        val med = MedicationTemplate(name = "Курс через день", enabled = true)
        repo.dao.medication(med)
        val local = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0)
        val schedule = repo.addQuickReminder(med.id, local, "DAILY", interval = 2, durationDays = 10)
        assertEquals(2, schedule.interval)
        assertEquals("DAILY", schedule.frequency)
        assertEquals(local.toLocalDate().toString(), schedule.startDate)
        assertEquals(local.toLocalDate().plusDays(9).toString(), schedule.endDate)
        assertTrue(schedule.label.contains("Через день"))
        assertTrue(schedule.label.contains("10 дн."))
    }
}
