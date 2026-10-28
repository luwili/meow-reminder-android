package ru.meow.reminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity(tableName = "medications")
data class MedicationTemplate(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val purpose: String = "",
    val dosage: String = "",
    val unit: String = "",
    val note: String = "",
    val color: Int = 0,
    val icon: String = "💊",
    val enabled: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "schedules", indices = [androidx.room.Index("medicationId")])
data class MedicationSchedule(
    @PrimaryKey val id: String = newId(),
    val medicationId: String,
    val label: String = "Основное расписание",
    val enabled: Boolean = true,
    val frequency: String = "ONCE",
    val interval: Int = 1,
    val times: String = "09:00",
    val weekDays: String = "1,2,3,4,5,6,7",
    val monthDay: Int = 1,
    val lastDay: Boolean = false,
    val skipShortMonth: Boolean = false,
    val startDate: String = LocalDate.now().toString(),
    val endDate: String? = null,
    val maxOccurrences: Int? = null,
    val repeatNotification: Boolean = false,
    val repeatMinutes: Int = 10,
    val extraNotifications: Int = 3,
    val generatedThrough: String? = null,
)

@Serializable
@Entity(tableName = "occurrences", indices = [androidx.room.Index("scheduleId"), androidx.room.Index("plannedAt")])
data class DoseOccurrence(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val medicationId: String,
    val medicationName: String,
    val localDateTime: String,
    val plannedAt: Long,
    val status: String = "PENDING",
    val confirmedAt: Long? = null,
    val notified: Boolean = false,
    val nextAttemptAt: Long? = null,
    val nextAttemptKind: String? = null,
    val extraSent: Int = 0,
    val cancelled: Boolean = false,
)

@Serializable
@Entity(tableName = "intake_records", indices = [androidx.room.Index("occurrenceId")])
data class IntakeRecord(
    @PrimaryKey val id: String = newId(),
    val occurrenceId: String,
    val oldStatus: String,
    val status: String,
    val changedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "phrases")
data class CustomPhrase(@PrimaryKey val id: String = newId(), val text: String, val category: String = "Милые")

@Serializable
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = newId(),
    val title: String = "",
    val body: String = "",
    val color: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val checklist: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class AppSettings(
    val initialized: Boolean = false,
    val welcomed: Boolean = false,
    val theme: String = "system",
    val dynamicColor: Boolean = true,
    val accent: Int = 0,
    val animations: Boolean = true,
    val cat: Boolean = true,
    val phrases: Boolean = true,
    val categories: Set<String> = setOf("Милые", "Мотивирующие", "Уютные", "Кошачьи", "Доброе утро", "Доброй ночи"),
    val recentPhrases: List<String> = emptyList(),
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val privateNotifications: Boolean = true,
    val notificationPreview: Boolean = true,
    val repeatDefault: Boolean = false,
    val repeatMinutes: Int = 10,
)

@Serializable
data class Backup(
    val version: Int = 1,
    val medications: List<MedicationTemplate>,
    val schedules: List<MedicationSchedule>,
    val occurrences: List<DoseOccurrence>,
    val records: List<IntakeRecord>,
    val notes: List<Note>,
    val phrases: List<CustomPhrase>,
    val settings: AppSettings,
)
