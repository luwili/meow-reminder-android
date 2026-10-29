package ru.meow.reminder.domain

object AlarmRecovery {
    private const val RECENT_WINDOW_MILLIS = 2 * 60 * 1000L

    /** Keep an already-armed, just-due alarm alive while its broadcast starts the process. */
    fun retainDueAttempt(wasArmed: Boolean, at: Long, now: Long, recoverRecent: Boolean): Boolean =
        recoverRecent && wasArmed && at <= now && now - at <= RECENT_WINDOW_MILLIS
}
