package ru.meow.reminder.domain

import ru.meow.reminder.data.MedicationSchedule
import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Wall-clock rules. A DST gap moves forward; an overlap uses the earlier offset exactly once. */
object ScheduleEngine {
    fun validate(s: MedicationSchedule) {
        require(s.frequency in setOf("ONCE", "DAILY", "WEEKLY", "MONTHLY")) { "Выберите периодичность" }
        require(s.interval in 1..999) { "Интервал должен быть от 1 до 999" }
        require(s.repeatMinutes in 1..1440 && s.extraNotifications in 1..10) { "Проверьте повторы уведомлений" }
        val start = LocalDate.parse(s.startDate)
        require(start.year in 1900..2200) { "Дата должна быть между 1900 и 2200 годами" }
        require(s.endDate == null || !LocalDate.parse(s.endDate).isBefore(start)) { "Окончание раньше начала" }
        require(s.maxOccurrences == null || s.maxOccurrences in 1..100000) { "Укажите от 1 до 100000 приёмов" }
        require(s.monthDay in 1..31)
        require(s.times.split(',').isNotEmpty())
        s.times.split(',').forEach { LocalTime.parse(it) }
        require(s.weekDays.split(',').all { it.toInt() in 1..7 })
    }

    fun localTimes(s: MedicationSchedule): Sequence<LocalDateTime> = sequence {
        validate(s)
        val start = LocalDate.parse(s.startDate)
        val end = s.endDate?.let(LocalDate::parse) ?: LocalDate.of(9999, 12, 31)
        val times = s.times.split(',').map(LocalTime::parse).distinct().sorted()
        val days = s.weekDays.split(',').map(String::toInt).toSet()
        val weekAnchor = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var date = start
        var count = 0
        while (!date.isAfter(end)) {
            val matches = when (s.frequency) {
                "ONCE" -> date == start
                "DAILY" -> ChronoUnit.DAYS.between(start, date) % s.interval == 0L
                "WEEKLY" -> date.dayOfWeek.value in days && ChronoUnit.WEEKS.between(weekAnchor, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) % s.interval == 0L
                "MONTHLY" -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)) % s.interval == 0L &&
                    date.dayOfMonth == (if (s.lastDay) date.lengthOfMonth() else if (s.skipShortMonth && s.monthDay > date.lengthOfMonth()) -1 else minOf(s.monthDay, date.lengthOfMonth()))
                else -> false
            }
            if (matches) for (time in times) {
                if (s.maxOccurrences != null && count >= s.maxOccurrences) return@sequence
                yield(date.atTime(time))
                count++
            }
            if (s.frequency == "ONCE") break
            date = date.plusDays(1)
        }
    }

    fun next(s: MedicationSchedule, after: Instant, zone: ZoneId, limit: Int = 10): List<ZonedDateTime> =
        localTimes(s).map { it.atZone(zone) }.filter { it.toInstant() > after }.take(limit).toList()

    fun between(s: MedicationSchedule, from: LocalDateTime, through: LocalDateTime): List<LocalDateTime> =
        localTimes(s).takeWhile { it <= through }.filter { it >= from }.toList()

    fun occurrenceId(scheduleId: String, local: LocalDateTime) = "$scheduleId@$local"
    fun canNotify(status: String, enabled: Boolean, cancelled: Boolean) = status == "PENDING" && enabled && !cancelled
    fun snoozeAt(now: Instant, minutes: Int): Instant {
        require(minutes in 1..1440)
        return now.plusSeconds(minutes * 60L)
    }
}
