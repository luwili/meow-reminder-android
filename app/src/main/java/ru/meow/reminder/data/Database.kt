package ru.meow.reminder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeowDao {
    @Query("SELECT * FROM medications ORDER BY favorite DESC, name COLLATE NOCASE") fun medications(): Flow<List<MedicationTemplate>>
    @Query("SELECT * FROM schedules") fun schedules(): Flow<List<MedicationSchedule>>
    @Query("SELECT * FROM occurrences ORDER BY plannedAt DESC") fun occurrences(): Flow<List<DoseOccurrence>>
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC") fun notes(): Flow<List<Note>>
    @Query("SELECT * FROM phrases") fun phrases(): Flow<List<CustomPhrase>>
    @Query("SELECT * FROM medications") suspend fun allMedications(): List<MedicationTemplate>
    @Query("SELECT * FROM schedules") suspend fun allSchedules(): List<MedicationSchedule>
    @Query("SELECT * FROM occurrences") suspend fun allOccurrences(): List<DoseOccurrence>
    @Query("SELECT * FROM intake_records") suspend fun allRecords(): List<IntakeRecord>
    @Query("SELECT * FROM notes") suspend fun allNotes(): List<Note>
    @Query("SELECT * FROM phrases") suspend fun allPhrases(): List<CustomPhrase>
    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun occurrence(id: String): DoseOccurrence?
    @Upsert suspend fun medication(value: MedicationTemplate)
    @Upsert suspend fun schedule(value: MedicationSchedule)
    @Upsert suspend fun occurrence(value: DoseOccurrence)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrences(values: List<DoseOccurrence>)
    @Upsert suspend fun record(value: IntakeRecord)
    @Upsert suspend fun note(value: Note)
    @Upsert suspend fun phrase(value: CustomPhrase)
    @Query("DELETE FROM schedules WHERE id = :id") suspend fun deleteSchedule(id: String)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
    @Query("DELETE FROM phrases WHERE id = :id") suspend fun deletePhrase(id: String)
    @Query("DELETE FROM occurrences WHERE scheduleId = :id AND plannedAt > :now AND status = 'PENDING'") suspend fun clearFuture(id: String, now: Long)
    @Query("UPDATE occurrences SET cancelled = 1, nextAttemptAt = NULL, nextAttemptKind = NULL WHERE scheduleId = :id AND status = 'PENDING'") suspend fun cancelSchedule(id: String)
    @Query("DELETE FROM medications") suspend fun clearMedications()
    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("DELETE FROM occurrences") suspend fun clearOccurrences()
    @Query("DELETE FROM intake_records") suspend fun clearRecords()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM phrases") suspend fun clearPhrases()
}

@Database(entities = [MedicationTemplate::class, MedicationSchedule::class, DoseOccurrence::class, IntakeRecord::class, CustomPhrase::class, Note::class], version = 1, exportSchema = true)
abstract class MeowDatabase : RoomDatabase() { abstract fun dao(): MeowDao }
