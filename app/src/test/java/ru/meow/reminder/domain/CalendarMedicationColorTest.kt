package ru.meow.reminder.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.meow.reminder.data.AppSettings
import ru.meow.reminder.data.appJson

class CalendarMedicationColorTest {
    @Test fun medicationColorsPersistThroughSettingsJsonRoundTrip() {
        val settings = AppSettings(medicationCalendarColors = mapOf("med-a" to 0xFF00838F.toInt()))
        val restored = appJson.decodeFromString<AppSettings>(appJson.encodeToString(settings))
        assertEquals(settings.medicationCalendarColors, restored.medicationCalendarColors)
    }

    @Test fun olderSettingsJsonDefaultsToNoCustomColors() {
        val restored = appJson.decodeFromString<AppSettings>("{}")
        assertEquals(emptyMap<String, Int>(), restored.medicationCalendarColors)
    }
}
