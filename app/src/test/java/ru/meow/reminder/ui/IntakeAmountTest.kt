package ru.meow.reminder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntakeAmountTest {
    @Test fun decimalCommaAndDotAreAccepted() {
        assertEquals(0.5, parseIntakeAmount("0,5")!!, 0.0)
        assertEquals(2.5, parseIntakeAmount("2.5")!!, 0.0)
    }

    @Test fun rejectsMissingNonPositiveAndNonFiniteValues() {
        listOf("", "0", "-1", "NaN", "Infinity", "abc").forEach { assertNull(parseIntakeAmount(it)) }
    }

    @Test fun formatsWithoutUnnecessaryZeroes() {
        assertEquals("0,5", formatIntakeAmount(0.5))
        assertEquals("2", formatIntakeAmount(2.0))
    }
}
