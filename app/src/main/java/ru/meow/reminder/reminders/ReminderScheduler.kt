package ru.meow.reminder.reminders

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.meow.reminder.MainActivity
import ru.meow.reminder.R
import ru.meow.reminder.data.*
import ru.meow.reminder.domain.ScheduleEngine
import ru.meow.reminder.domain.AlarmRecovery
import java.time.*
import java.time.format.DateTimeFormatter

class ReminderScheduler(private val context: Context, private val repo: Repository) {
    private val mutex = Mutex()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val alarmPrefs = context.getSharedPreferences("system_alarms", Context.MODE_PRIVATE)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    private fun pending(id: String, at: Long = 0): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("FIRE").setData(Uri.parse("meow://alarm/${Uri.encode(id)}"))
            .putExtra("id", id).putExtra("at", at), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun cancelAlarms() {
        alarmPrefs.getStringSet("ids", emptySet()).orEmpty().forEach { id -> alarms.cancel(pending(id)) }
        alarmPrefs.edit().remove("ids").commit()
    }

    suspend fun reconcile(recoverRecent: Boolean = true) = mutex.withLock { reconcileLocked(recoverRecent) }

    private suspend fun reconcileLocked(recoverRecent: Boolean = true) {
        val previouslyArmed = alarmPrefs.getStringSet("ids", emptySet()).orEmpty().toSet()
        cancelAlarms()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val medications = repo.dao.allMedications().associateBy { it.id }
        val schedules = repo.dao.allSchedules()
        val active = schedules.filter { s -> s.enabled && medications[s.medicationId]?.let { it.enabled && it.name.isNotBlank() && !it.archived && !it.deleted } == true }
        val activeIds = active.map { it.id }.toSet()
        schedules.filter { it.id !in activeIds }.forEach {
            repo.dao.cancelSchedule(it.id)
            repo.dao.clearFuture(it.id, now)
            if (it.generatedThrough != null) repo.dao.schedule(it.copy(generatedThrough = null))
        }
        for (s in active) {
            val start = s.generatedThrough?.let(LocalDate::parse)?.plusDays(1)?.atStartOfDay() ?: LocalDate.parse(s.startDate).atStartOfDay()
            val horizon = today.plusDays(35)
            val generated = if (start.toLocalDate() <= horizon) ScheduleEngine.between(s, start, horizon.atTime(LocalTime.MAX)) else emptyList()
            // Sparse (e.g. annual) rules always keep their next alarm, outside the materialization window too.
            val next = ScheduleEngine.next(s, Instant.ofEpochMilli(now), zone, 1)
                .map { it.toLocalDateTime() }
                .filter { it.toLocalDate() > horizon }
            val med = medications.getValue(s.medicationId)
            repo.dao.insertOccurrences((generated + next).distinct().map { local ->
                val at = local.atZone(zone).toInstant().toEpochMilli()
                DoseOccurrence(ScheduleEngine.occurrenceId(s.id, local), s.id, s.medicationId, med.name, local.toString(), at, notified = at < now)
            })
            repo.dao.schedule(s.copy(generatedThrough = horizon.toString()))
        }
        val occurrences = repo.dao.allOccurrences()
        val toSchedule = mutableListOf<Pair<String, Long>>()
        for (dose in occurrences) {
            if (!ScheduleEngine.canNotify(dose.status, dose.scheduleId in activeIds, dose.cancelled)) {
                notifications.cancel(dose.id, 1)
                continue
            }
            var updated = dose
            // Reinterpret only future, untouched doses in the new local zone. History keeps its instant.
            if (!dose.notified) {
                val at = LocalDateTime.parse(dose.localDateTime).atZone(zone).toInstant().toEpochMilli()
                updated = dose.copy(plannedAt = at)
            }
            if (!updated.notified && updated.plannedAt <= now) {
                if (AlarmRecovery.retainDueAttempt(updated.id in previouslyArmed, updated.plannedAt, now, recoverRecent)) {
                    toSchedule.add(updated.id to updated.plannedAt)
                } else updated = updated.copy(notified = true)
            }
            if (updated.nextAttemptAt != null && updated.nextAttemptAt!! <= now &&
                !AlarmRecovery.retainDueAttempt(updated.id in previouslyArmed, updated.nextAttemptAt!!, now, recoverRecent)) {
                updated = updated.copy(nextAttemptAt = null, nextAttemptKind = null)
            }
            if (updated != dose) repo.dao.occurrence(updated)
            updated.nextAttemptAt?.let { toSchedule.add(updated.id to it) }
        }
        val fresh = repo.dao.allOccurrences()
        active.forEach { s ->
            fresh.filter { it.scheduleId == s.id && !it.notified && !it.cancelled && it.status == "PENDING" && it.plannedAt > now }
                .minByOrNull { it.plannedAt }?.let { toSchedule.add(it.id to it.plannedAt) }
        }
        // Persist before interacting with AlarmManager so a later repair can cancel partial work.
        alarmPrefs.edit().putStringSet("ids", toSchedule.map { it.first }.toSet()).commit()
        toSchedule.forEach { (id, at) ->
            val pi = pending(id, at)
            try {
                if (exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    suspend fun fire(id: String, expectedAt: Long) = mutex.withLock {
        val dose = repo.dao.occurrence(id) ?: return@withLock
        val schedule = repo.dao.allSchedules().find { it.id == dose.scheduleId } ?: return@withLock
        val med = repo.dao.allMedications().find { it.id == dose.medicationId } ?: return@withLock
        val expected = dose.nextAttemptAt ?: if (!dose.notified) dose.plannedAt else null
        if (expected != expectedAt || !ScheduleEngine.canNotify(dose.status, schedule.enabled && med.enabled && !med.archived && !med.deleted, dose.cancelled)) return@withLock
        val now = System.currentTimeMillis()
        val isExtra = dose.notified
        val count = dose.extraSent + if (isExtra) 1 else 0
        val next = if (schedule.repeatNotification && count < schedule.extraNotifications) now + schedule.repeatMinutes * 60000L else null
        repo.dao.occurrence(dose.copy(notified = true, extraSent = count, nextAttemptAt = next, nextAttemptKind = if (next != null) "REPEAT" else null))
        if (now - expectedAt < 60 * 60000L) show(dose, med)
        reconcileLocked()
    }

    suspend fun mark(id: String, status: String) = mutex.withLock {
        markLocked(id, status)
    }

    private suspend fun markLocked(id: String, status: String) {
        repo.mark(id, status)
        notifications.cancel(id, 1)
        reconcileLocked()
    }

    suspend fun snooze(id: String, minutes: Int) = mutex.withLock {
        snoozeLocked(id, minutes)
    }

    private suspend fun snoozeLocked(id: String, minutes: Int) {
        val dose = repo.dao.occurrence(id) ?: return
        if (dose.status != "PENDING" || dose.cancelled) return
        repo.dao.occurrence(dose.copy(notified = true, nextAttemptAt = ScheduleEngine.snoozeAt(Instant.now(), minutes).toEpochMilli(), nextAttemptKind = "SNOOZE"))
        notifications.cancel(id, 1)
        reconcileLocked()
    }

    /** A bridged watch action can arrive after its notification was cancelled on the phone. */
    suspend fun handleNotificationAction(id: String, action: String) = mutex.withLock {
        val dose = repo.dao.occurrence(id)
        if (dose == null || dose.status != "PENDING" || dose.cancelled) {
            notifications.cancel(id, 1)
            return@withLock
        }
        when (action) {
            "TAKEN", "SKIPPED" -> markLocked(id, action)
            "SNOOZE_10" -> snoozeLocked(id, 10)
        }
    }

    private fun action(id: String, action: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(action).setData(Uri.parse("meow://$action/${Uri.encode(id)}")).putExtra("id", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal suspend fun buildNotification(dose: DoseOccurrence, med: MedicationTemplate): Notification {
        val settings = repo.settings.get()
        val channelId = "doses-${settings.sound}-${settings.vibration}"
        val channel = NotificationChannel(channelId, "Напоминания · ${if (settings.sound) "со звуком" else "без звука"} · ${if (settings.vibration) "вибрация" else "без вибрации"}", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Напоминания о запланированных приёмах"
            enableVibration(settings.vibration)
            if (!settings.sound) setSound(null, null)
            lockscreenVisibility = if (settings.privateNotifications) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC
        }
        notifications.createNotificationChannel(channel)
        val phrase = PhraseLibrary.pick(repo.settings, repo.dao.allPhrases())
        val time = LocalDateTime.parse(dose.localDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))
        val text = if (settings.notificationPreview) "${med.name} — $time" + (if (med.dosage.isNotBlank()) " · ${med.dosage} ${med.unit}" else "") else "Откройте приложение, чтобы посмотреть приём"
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getActivity(context, 0, Intent(context, SnoozeActivity::class.java)
            .setData(Uri.parse("meow://snooze/${Uri.encode(dose.id)}")).putExtra("id", dose.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val publicVersion = NotificationCompat.Builder(context, channelId).setSmallIcon(R.drawable.ic_notification).setContentTitle("Мяу, пора!").setContentText("У вас запланирован приём").build()
        val taken = NotificationCompat.Action.Builder(0, "Принято", action(dose.id, "TAKEN")).setAllowGeneratedReplies(false).build()
        val skipped = NotificationCompat.Action.Builder(0, "Пропустить", action(dose.id, "SKIPPED")).setAllowGeneratedReplies(false).build()
        val watchSnooze = NotificationCompat.Action.Builder(0, "На 10 минут", action(dose.id, "SNOOZE_10")).setAllowGeneratedReplies(false).build()
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Мяу! Время позаботиться о себе 🐾")
            .setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(listOf(text, phrase).filter { it.isNotEmpty() }.joinToString("\n")))
            .setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setLocalOnly(false)
            .setVisibility(if (settings.privateNotifications) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicVersion)
            .addAction(taken)
            .addAction(0, "Отложить", snooze)
            .addAction(skipped)
            .extend(NotificationCompat.WearableExtender()
                .setDismissalId(dose.id)
                .addAction(taken).addAction(watchSnooze).addAction(skipped))
            .build()
    }

    private suspend fun show(dose: DoseOccurrence, med: MedicationTemplate) {
        val notification = buildNotification(dose, med)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            try { notifications.notify(dose.id, 1, notification) } catch (_: SecurityException) { /* Permission may change between check and post. */ }
        }
    }

    suspend fun testNotification() {
        val med = MedicationTemplate(name = "Тестовое напоминание")
        show(DoseOccurrence("test", "test", med.id, med.name, LocalDateTime.now().toString(), System.currentTimeMillis()), med)
    }

    suspend fun cancelAll() = mutex.withLock { cancelAlarms(); notifications.cancelAll() }
}
