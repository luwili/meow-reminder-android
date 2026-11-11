package ru.meow.reminder

import android.content.ContentValues
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.meow.reminder.data.MIGRATION_1_2
import ru.meow.reminder.data.MeowDatabase

@RunWith(AndroidJUnit4::class)
class MigrationPreservationTest {
    @Test fun versionOneDataSurvivesUpgradeWithUnknownAmount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-preservation-test.db"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("ru.meow.reminder.data.MeowDatabase/1.json").bufferedReader().use { JSONObject(it.readText()) }
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        val original = context.openOrCreateDatabase(name, 0, null)
        try {
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                original.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) original.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                if (table in setOf("medications", "occurrences", "notes")) {
                    val values = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    for (j in 0 until fields.length()) {
                        val field = fields.getJSONObject(j)
                        val col = field.getString("columnName")
                        if (!field.optBoolean("notNull", false)) continue
                        when (field.getString("affinity")) {
                            "TEXT" -> values.put(col, when (col) {
                                "id" -> "legacy-$table"
                                "medicationId" -> "legacy-medications"
                                "medicationName", "name", "title" -> "Сохранённые данные"
                                "localDateTime" -> "2025-01-01T09:00"
                                "status" -> "TAKEN"
                                "body" -> "Старая заметка"
                                else -> ""
                            })
                            "INTEGER" -> values.put(col, 0)
                            "REAL" -> values.put(col, 0.0)
                        }
                    }
                    assertNotEquals(-1L, original.insertOrThrow(table, null, values))
                }
            }
            original.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            original.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES (42,'6697aed36e982c316edb15540d6b232b')")
            original.version = 1
        } finally { original.close() }
        val migrated = Room.databaseBuilder(context, MeowDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        try {
            val med = migrated.dao().allMedications().single()
            val occurrence = migrated.dao().allOccurrences().single()
            assertEquals("Сохранённые данные", med.name)
            assertFalse(med.isSyrup)
            assertEquals("TAKEN", occurrence.status)
            assertNull(occurrence.amount)
            assertNull(occurrence.amountUnit)
            assertEquals("Старая заметка", migrated.dao().allNotes().single().body)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
