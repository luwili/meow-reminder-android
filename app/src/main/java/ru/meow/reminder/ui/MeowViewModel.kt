package ru.meow.reminder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import ru.meow.reminder.MeowApp
import ru.meow.reminder.data.*

class MeowViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MeowApp
    val repo = app.repository
    val medications = repo.dao.medications().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val schedules = repo.dao.schedules().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val occurrences = repo.dao.occurrences().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes = repo.dao.notes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val phrases = repo.dao.phrases().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = repo.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val error = MutableStateFlow<String?>(null)
    val homePhrase = MutableStateFlow("")
    val refreshingToday = MutableStateFlow(false)
    val quickSaving = MutableStateFlow(false)
    val quickError = MutableStateFlow<String?>(null)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var repair: Job? = null
    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (operation in operations) {
                try { operation() }
                catch (e: Exception) {
                    if (e is CancellationException) throw e
                    error.value = when (e) {
                        is kotlinx.serialization.SerializationException -> "Не удалось прочитать резервную копию. Выберите JSON, экспортированный из этого приложения."
                        is java.time.format.DateTimeParseException -> "Не удалось прочитать дату или время. Проверьте расписание."
                        is SecurityException -> "Нет доступа к файлу или системному разрешению. Проверьте настройки Android."
                        is java.io.IOException -> "Не удалось прочитать или записать файл. Проверьте доступ и свободное место."
                        is IllegalArgumentException -> e.message ?: "Проверьте введённые значения"
                        else -> "Не удалось выполнить действие. Проверьте свободное место и повторите попытку."
                    }
                }
            }
        }
    }
    // Preserve edit order, including fast typing and immediately leaving the editor.
    fun task(block: suspend () -> Unit) { operations.trySend(block) }
    private fun scheduleRepair() {
        repair?.cancel()
        repair = viewModelScope.launch { delay(400); task { app.scheduler.reconcile() } }
    }
    fun save(med: MedicationTemplate) = task {
        val old = repo.dao.allMedications().find { it.id == med.id }
        repo.dao.medication(med)
        if (old?.enabled != med.enabled || old?.archived != med.archived || old?.deleted != med.deleted || old?.name?.isNotBlank() != med.name.isNotBlank()) scheduleRepair()
    }
    fun save(schedule: MedicationSchedule) = task { repo.saveSchedule(schedule); scheduleRepair() }
    fun save(note: Note) = task { repo.dao.note(note.copy(updatedAt = System.currentTimeMillis())) }
    fun mark(id: String, status: String) = task { app.scheduler.mark(id, status) }
    fun setting(change: (AppSettings) -> AppSettings) = task { repo.settings.update(change) }
    fun refreshPhrase() = task { homePhrase.value = PhraseLibrary.pick(repo.settings, repo.dao.allPhrases()) }
    fun refreshToday() {
        if (!refreshingToday.compareAndSet(false, true)) return
        task {
            try {
                app.scheduler.reconcile()
                homePhrase.value = PhraseLibrary.pick(repo.settings, repo.dao.allPhrases())
            } finally { refreshingToday.value = false }
        }
    }
    fun addQuickReminder(
        medicationId: String,
        local: java.time.LocalDateTime,
        frequency: String,
        interval: Int = 1,
        durationDays: Int? = null,
        complete: () -> Unit
    ) {
        if (!quickSaving.compareAndSet(false, true)) return
        quickError.value = null
        task {
            try {
                repo.addQuickReminder(medicationId, local, frequency, interval, durationDays)
                app.scheduler.reconcile()
                withContext(Dispatchers.Main) { complete() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                quickError.value = if (e is IllegalArgumentException) e.message else "Не удалось добавить напоминание. Проверьте данные и повторите попытку."
            } finally { quickSaving.value = false }
        }
    }

    fun addQuickReminder(medicationId: String, local: java.time.LocalDateTime, frequency: String, complete: () -> Unit) {
        addQuickReminder(medicationId, local, frequency, 1, null, complete)
    }

    fun deleteOccurrence(id: String) = task {
        repo.deleteOccurrence(id)
        app.scheduler.reconcile()
    }

    fun deleteSchedule(id: String) = task {
        repo.deleteSchedule(id)
        app.scheduler.reconcile()
    }
}
