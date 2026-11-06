package ru.meow.reminder.widget

import org.junit.Assert.*
import org.junit.Test
import ru.meow.reminder.data.DoseOccurrence
import ru.meow.reminder.data.MedicationTemplate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class WidgetLogicTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val nowMillis = System.currentTimeMillis()

    private fun sampleDose(
        id: String,
        medId: String,
        name: String,
        local: LocalDateTime,
        status: String = "PENDING"
    ): DoseOccurrence {
        val plannedAt = local.atZone(zone).toInstant().toEpochMilli()
        return DoseOccurrence(
            id = id,
            scheduleId = "sched-$medId",
            medicationId = medId,
            medicationName = name,
            localDateTime = local.toString(),
            plannedAt = plannedAt,
            status = status
        )
    }

    @Test
    fun overdueDoseTakesPriorityOverUpcoming() {
        val pastLocal = LocalDateTime.now(zone).minusHours(1)
        val futureLocal = LocalDateTime.now(zone).plusHours(2)

        val overdue = sampleDose("1", "m1", "Ибупрофен", pastLocal)
        val upcoming = sampleDose("2", "m2", "Витамин C", futureLocal)

        val doses = listOf(upcoming, overdue)

        val dayDoses = doses.filter {
            Instant.ofEpochMilli(it.plannedAt).atZone(zone).toLocalDate() == today &&
                (!it.cancelled || it.status != "PENDING")
        }.sortedBy { it.plannedAt }

        val todayPending = dayDoses.filter { !it.cancelled && it.status == "PENDING" }
        val todayOverdue = todayPending.filter { it.plannedAt <= nowMillis }.sortedBy { it.plannedAt }
        val todayUpcoming = todayPending.filter { it.plannedAt > nowMillis }.sortedBy { it.plannedAt }

        val heroDose: DoseOccurrence? = when {
            todayOverdue.isNotEmpty() -> todayOverdue.first()
            todayUpcoming.isNotEmpty() -> todayUpcoming.first()
            else -> null
        }

        assertNotNull(heroDose)
        assertEquals("1", heroDose?.id)
        assertEquals("Ибупрофен", heroDose?.medicationName)
    }

    @Test
    fun allTakenDosesCalculatesCorrectProgress() {
        val d1 = sampleDose("1", "m1", "Ибупрофен", today.atTime(9, 0), status = "TAKEN")
        val d2 = sampleDose("2", "m2", "Витамин D", today.atTime(14, 0), status = "TAKEN")

        val dayDoses = listOf(d1, d2)
        val todayPending = dayDoses.filter { !it.cancelled && it.status == "PENDING" }
        val totalToday = dayDoses.size
        val takenToday = dayDoses.count { it.status == "TAKEN" }

        assertTrue(todayPending.isEmpty())
        assertEquals(2, totalToday)
        assertEquals(2, takenToday)
    }

    @Test
    fun doseDescCombinesDosageAndPurpose() {
        val med = MedicationTemplate(
            id = "m1",
            name = "Аспирин",
            dosage = "100",
            unit = "мг",
            purpose = "Для сердца"
        )

        val dosagePart = if (med.dosage.isNotBlank()) "${med.dosage} ${med.unit}".trim() else ""
        val purposePart = med.purpose.takeIf { it.isNotBlank() } ?: ""
        val desc = listOf(dosagePart, purposePart).filter { it.isNotBlank() }.joinToString(" · ")

        assertEquals("100 мг · Для сердца", desc)
    }

    @Test
    fun russianDateFormattingWorksProperly() {
        val ruLocale = Locale.forLanguageTag("ru")
        val date = LocalDate.of(2026, 11, 6)
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", ruLocale)).replaceFirstChar { it.uppercase(ruLocale) }
        assertTrue(formatted.contains("ноября"))
    }
}
