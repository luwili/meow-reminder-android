package ru.meow.reminder

import android.app.Application
import androidx.room.Room
import androidx.work.*
import kotlinx.coroutines.*
import ru.meow.reminder.data.*
import ru.meow.reminder.reminders.*
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
            scheduler.reconcile()
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("reminder-repair", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RepairWorker>(6, TimeUnit.HOURS).build())
    }
}
