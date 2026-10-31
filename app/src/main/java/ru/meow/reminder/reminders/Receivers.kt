package ru.meow.reminder.reminders

import android.content.*
import android.os.PowerManager
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.launch
import ru.meow.reminder.MeowApp

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as MeowApp
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meow:reminder_fire")?.apply {
            setReferenceCounted(false)
            acquire(30000)
        }
        app.scope.launch {
            try {
                val id = intent.getStringExtra("id") ?: return@launch
                when (intent.action) {
                    "FIRE" -> app.scheduler.fire(id, intent.getLongExtra("at", 0))
                    "TAKEN", "SKIPPED", "SNOOZE_10" -> app.scheduler.handleNotificationAction(id, intent.action!!)
                }
            } catch (e: Exception) {
                Log.e("MeowReminder", "Не удалось обработать напоминание", e)
                WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<RepairWorker>().build())
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                pending.finish()
            }
        }
    }
}

class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
            )) return
        val pending = goAsync()
        val app = context.applicationContext as MeowApp
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "meow:restore_receiver")?.apply {
            setReferenceCounted(false)
            acquire(30000)
        }
        app.scope.launch {
            try {
                app.scheduler.reconcile(recoverRecent = true)
            } catch (e: Exception) {
                Log.e("MeowReminder", "Не удалось восстановить расписание", e)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "restore-alarms",
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<RepairWorker>().setInputData(workDataOf("discardPast" to false)).build()
                )
            } finally {
                wakeLock?.let { if (it.isHeld) it.release() }
                pending.finish()
            }
        }
    }
}

class RepairWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as MeowApp).scheduler.reconcile(recoverRecent = !inputData.getBoolean("discardPast", false))
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
