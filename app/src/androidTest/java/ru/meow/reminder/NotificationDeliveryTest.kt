package ru.meow.reminder

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.meow.reminder.data.*
import ru.meow.reminder.domain.ScheduleEngine
import java.time.*

/** Tests Android's actual alarm/receiver/notification path, not a Samsung firmware simulation. */
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    private lateinit var app: MeowApp
    private lateinit var med: MedicationTemplate
    private lateinit var rule: MedicationSchedule
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    @Before fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) shell("appops set ${app.packageName} SCHEDULE_EXACT_ALARM allow")
        med = MedicationTemplate(name = "Проверка системного уведомления", enabled = true)
        app.repository.dao.medication(med)
    }

    private suspend fun fixture(seconds: Long): DoseOccurrence {
        val local = LocalDateTime.now().plusSeconds(seconds).withNano(0)
        rule = MedicationSchedule(medicationId = med.id, startDate = local.toLocalDate().toString(),
            times = local.toLocalTime().toString(), repeatNotification = true, repeatMinutes = 5)
        app.repository.saveSchedule(rule)
        app.scheduler.reconcile()
        return app.repository.dao.occurrence(ScheduleEngine.occurrenceId(rule.id, local))!!
    }

    @After fun cleanup() = runBlocking {
        if (::rule.isInitialized) {
            app.repository.dao.cancelSchedule(rule.id)
            app.repository.dao.deleteSchedule(rule.id)
        }
        if (::med.isInitialized) app.repository.dao.medication(med.copy(enabled = false, deleted = true))
        app.scheduler.reconcile()
    }

    @Test fun actualAlarmPostsNotificationAndConfirmCancelsRetries() = runBlocking {
        assertTrue("Тесту нужен разрешённый точный будильник", app.scheduler.exactAllowed())
        val dose = fixture(8)
        assertFalse(dose.notified)
        // No direct fire() call: the OS must deliver PendingIntent to ReminderReceiver.
        val posted = withTimeout(30000) {
            while (true) {
                val notification = manager.activeNotifications.find { it.tag == dose.id }
                if (notification != null) return@withTimeout notification
                delay(100)
            }
            error("Недостижимо")
        }
        assertTrue(System.currentTimeMillis() >= dose.plannedAt)
        assertTrue(app.repository.dao.occurrence(dose.id)!!.notified)
        assertNotNull(app.repository.dao.occurrence(dose.id)!!.nextAttemptAt)
        posted.notification.actions.first().actionIntent.send()
        withTimeout(5000) { app.repository.dao.occurrences().first { list -> list.any { it.id == dose.id && it.status == "TAKEN" } } }
        withTimeout(5000) { while (manager.activeNotifications.any { it.tag == dose.id }) delay(50) }
        assertNull(app.repository.dao.occurrence(dose.id)!!.nextAttemptAt)
    }

    @Test fun watchContractHasDirectSnoozeAndIgnoresStaleSkip() = runBlocking {
        val dose = fixture(120)
        val notification = app.scheduler.buildNotification(dose, med)
        assertEquals(0, notification.flags and Notification.FLAG_LOCAL_ONLY)
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(Notification.CATEGORY_REMINDER, notification.category)
        val wearable = NotificationCompat.WearableExtender(notification)
        assertEquals(dose.id, wearable.dismissalId)
        assertEquals(listOf("Принято", "На 10 минут", "Пропустить"), wearable.actions.map { it.title.toString() })
        val actions = wearable.actions.map { requireNotNull(it.actionIntent) }
        assertFalse("Отсрочка с часов не должна открывать Activity на телефоне", actions[1].isActivity)
        val before = System.currentTimeMillis()
        actions[1].send()
        val snoozed = withTimeout(5000) {
            app.repository.dao.occurrences().first { list -> list.any { it.id == dose.id && it.nextAttemptKind == "SNOOZE" } }.first { it.id == dose.id }
        }
        assertTrue(snoozed.nextAttemptAt!! >= before + 600000)
        assertEquals(dose.plannedAt, snoozed.plannedAt)
        actions[0].send()
        withTimeout(5000) { app.repository.dao.occurrences().first { list -> list.any { it.id == dose.id && it.status == "TAKEN" } } }
        actions[2].send() // delayed action from an already-dismissed watch card
        delay(500)
        assertEquals("TAKEN", app.repository.dao.occurrence(dose.id)!!.status)
        assertNull(app.repository.dao.occurrence(dose.id)!!.nextAttemptAt)
        assertEquals(1, app.repository.dao.allRecords().count { it.occurrenceId == dose.id })
    }
}
