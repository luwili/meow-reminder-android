package ru.meow.reminder.domain

import org.junit.Assert.*
import org.junit.Test
import ru.meow.reminder.data.*
import java.time.*

class ScheduleEngineTest {
    private val utc = ZoneId.of("UTC")
    private fun rule(vararg ignored: String) = MedicationSchedule(id = "rule", medicationId = "med", startDate = "2026-01-01")
    private fun dates(s: MedicationSchedule, n: Int = 5) = ScheduleEngine.localTimes(s).take(n).map { it.toString() }.toList()

    @Test fun oneTimeHasExactlyOneOccurrence() { assertEquals(listOf("2026-01-01T09:00"), dates(rule())) }
    @Test fun oneTimeSeveralTimesAreSortedAndUnique() { assertEquals(listOf("2026-01-01T08:00", "2026-01-01T20:00"), dates(rule().copy(times = "20:00,08:00,08:00"))) }
    @Test fun dailyEveryTwoDays() { assertEquals(listOf("2026-01-01T09:00", "2026-01-03T09:00", "2026-01-05T09:00"), dates(rule().copy(frequency = "DAILY", interval = 2), 3)) }
    @Test fun selectedWeekdays() { assertEquals(listOf("2026-01-02T09:00", "2026-01-05T09:00", "2026-01-09T09:00"), dates(rule().copy(frequency = "WEEKLY", weekDays = "1,5"), 3)) }
    @Test fun everyOtherWeekAnchorsToMonday() { assertEquals(listOf("2026-01-01T09:00", "2026-01-15T09:00", "2026-01-29T09:00"), dates(rule().copy(frequency = "WEEKLY", weekDays = "4", interval = 2), 3)) }
    @Test fun monthlyThirtyFirstClampsToFebruary() { assertEquals(listOf("2026-01-31T09:00", "2026-02-28T09:00", "2026-03-31T09:00"), dates(rule().copy(frequency = "MONTHLY", monthDay = 31), 3)) }
    @Test fun monthlyCanSkipShortMonth() { assertEquals(listOf("2026-01-31T09:00", "2026-03-31T09:00", "2026-05-31T09:00"), dates(rule().copy(frequency = "MONTHLY", monthDay = 31, skipShortMonth = true), 3)) }
    @Test fun lastDayHandlesLeapYear() { assertEquals(listOf("2028-01-31T09:00", "2028-02-29T09:00", "2028-03-31T09:00"), dates(rule().copy(startDate = "2028-01-01", frequency = "MONTHLY", lastDay = true), 3)) }
    @Test fun february29Once() { assertEquals(listOf("2028-02-29T09:00"), dates(rule().copy(startDate = "2028-02-29"))) }
    @Test fun everyTwelveMonthsFebruary29() { assertEquals(listOf("2028-02-29T09:00", "2029-02-28T09:00", "2030-02-28T09:00"), dates(rule().copy(startDate = "2028-02-29", frequency = "MONTHLY", interval = 12, monthDay = 29), 3)) }
    @Test fun intervalMonths() { assertEquals(listOf("2026-01-01T09:00", "2026-04-01T09:00", "2026-07-01T09:00"), dates(rule().copy(frequency = "MONTHLY", interval = 3), 3)) }
    @Test fun indefiniteProducesMoreThanPreview() { assertEquals(1000, ScheduleEngine.localTimes(rule().copy(frequency = "DAILY")).take(1000).count()) }
    @Test fun finiteCountCountsTimesNotDays() { assertEquals(listOf("2026-01-01T08:00", "2026-01-01T20:00", "2026-01-02T08:00"), dates(rule().copy(frequency = "DAILY", times = "08:00,20:00", maxOccurrences = 3), 10)) }
    @Test fun endDateIsInclusive() { assertEquals(4, dates(rule().copy(frequency = "DAILY", times = "08:00,20:00", endDate = "2026-01-02"), 10).size) }
    @Test fun completedCourseHasNoNext() { assertTrue(ScheduleEngine.next(rule().copy(frequency = "DAILY", maxOccurrences = 2), Instant.parse("2026-02-01T00:00:00Z"), utc).isEmpty()) }
    @Test fun nextDoesNotRestartFiniteCount() { assertEquals("2026-01-03T09:00Z[UTC]", ScheduleEngine.next(rule().copy(frequency = "DAILY", maxOccurrences = 3), Instant.parse("2026-01-02T10:00:00Z"), utc).single().toString()) }
    @Test fun midnightCrossing() { assertEquals(listOf("2026-01-01T23:59", "2026-01-02T00:01", "2026-01-02T23:59"), ScheduleEngine.between(rule().copy(frequency = "DAILY", times = "00:01,23:59"), LocalDateTime.parse("2026-01-01T23:00"), LocalDateTime.parse("2026-01-03T00:00")).map { it.toString() }) }
    @Test fun springGapMovesForward() { val next = ScheduleEngine.next(rule().copy(startDate = "2026-03-29", times = "02:30"), Instant.parse("2026-03-28T00:00:00Z"), ZoneId.of("Europe/Berlin")).single(); assertEquals(3, next.hour); assertEquals(30, next.minute) }
    @Test fun autumnOverlapOnlyOnce() { val next = ScheduleEngine.next(rule().copy(startDate = "2026-10-25", times = "02:30"), Instant.parse("2026-10-24T00:00:00Z"), ZoneId.of("Europe/Berlin")); assertEquals(1, next.size); assertEquals(ZoneOffset.ofHours(2), next.single().offset) }
    @Test fun changingZoneKeepsWallClock() { val s = rule(); val after = Instant.parse("2025-12-31T00:00:00Z"); val a = ScheduleEngine.next(s, after, utc).single(); val b = ScheduleEngine.next(s, after, ZoneId.of("Asia/Tokyo")).single(); assertEquals(a.toLocalTime(), b.toLocalTime()); assertNotEquals(a.toInstant(), b.toInstant()) }
    @Test fun snoozeCrossesMidnightWithoutChangingRule() { assertEquals(Instant.parse("2026-01-02T00:05:00Z"), ScheduleEngine.snoozeAt(Instant.parse("2026-01-01T23:55:00Z"), 10)) }
    @Test fun confirmationStopsNotification() { assertFalse(ScheduleEngine.canNotify("TAKEN", true, false)); assertFalse(ScheduleEngine.canNotify("SKIPPED", true, false)) }
    @Test fun disabledAndCancelledNeverNotify() { assertFalse(ScheduleEngine.canNotify("PENDING", false, false)); assertFalse(ScheduleEngine.canNotify("PENDING", true, true)) }
    @Test fun deterministicIdsPreventDuplicates() { val s = rule().copy(frequency = "DAILY"); val run1 = ScheduleEngine.localTimes(s).take(10).map { ScheduleEngine.occurrenceId(s.id, it) }.toList(); val run2 = ScheduleEngine.localTimes(s).take(10).map { ScheduleEngine.occurrenceId(s.id, it) }.toList(); assertEquals(10, (run1 + run2).toSet().size) }
    @Test fun simultaneousDifferentSchedulesHaveDifferentIds() { val local = LocalDateTime.parse("2026-01-01T09:00"); assertNotEquals(ScheduleEngine.occurrenceId("a", local), ScheduleEngine.occurrenceId("b", local)) }
    @Test(expected = IllegalArgumentException::class) fun invalidCountRejected() { ScheduleEngine.validate(rule().copy(maxOccurrences = 0)) }
    @Test(expected = IllegalArgumentException::class) fun invalidEndRejected() { ScheduleEngine.validate(rule().copy(endDate = "2025-12-01")) }
    @Test fun originalPhrasesHaveAtLeast100UniqueTexts() { assertTrue(PhraseLibrary.builtIn.map { it.text }.toSet().size >= 100) }
    @Test fun jsonRoundTripPreservesAllFields() { val s = rule().copy(interval = 7, monthDay = 31, repeatNotification = true, maxOccurrences = 20); assertEquals(s, appJson.decodeFromString<MedicationSchedule>(appJson.encodeToString(s))) }
}
