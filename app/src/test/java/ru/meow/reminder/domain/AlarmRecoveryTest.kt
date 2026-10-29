package ru.meow.reminder.domain

import org.junit.Assert.*
import org.junit.Test

class AlarmRecoveryTest {
    @Test fun justDueKnownAlarmIsNotSwallowedByRepair() { assertTrue(AlarmRecovery.retainDueAttempt(true, 1000, 1500, true)) }
    @Test fun unarmedHistoricalDoseIsNotReplayed() { assertFalse(AlarmRecovery.retainDueAttempt(false, 1000, 1500, true)) }
    @Test fun oldAlarmIsNotReplayed() { assertFalse(AlarmRecovery.retainDueAttempt(true, 1000, 121001, true)) }
    @Test fun bootAndClockRestorationDoNotReplayPast() { assertFalse(AlarmRecovery.retainDueAttempt(true, 1000, 1500, false)) }
    @Test fun futureAttemptDoesNotUseRecoveryPath() { assertFalse(AlarmRecovery.retainDueAttempt(true, 2000, 1500, true)) }
}
