package ru.meow.reminder

import android.app.Application
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import ru.meow.reminder.data.*
import ru.meow.reminder.reminders.*
import ru.meow.reminder.widget.MeowWidgetProvider
import java.util.concurrent.TimeUnit

class MeowApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: Repository
    lateinit var scheduler: ReminderScheduler
    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, MeowDatabase::class.java, "meow.db").build()
        repository = Repository(db, SettingsStore(this))
        scheduler = ReminderScheduler(this, repository)
        scope.launch {
            repository.initialize()
            MeowWidgetProvider.updateAllWidgets(this@MeowApp)
            repository.dao.occurrences().collectLatest {
                MeowWidgetProvider.updateAllWidgets(this@MeowApp)
            }
            // AlarmManager can create this process to deliver FIRE. Do not run repair here:
            // it could mark the just-due occurrence notified before ReminderReceiver reads it.
            // MainActivity.onResume and RepairWorker explicitly restore alarms instead.
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("reminder-repair", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RepairWorker>(6, TimeUnit.HOURS).build())
    }
}
