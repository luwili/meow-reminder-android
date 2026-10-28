package ru.meow.reminder.reminders

import android.app.*
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.EditText
import android.text.InputType
import androidx.activity.ComponentActivity
import kotlinx.coroutines.launch
import ru.meow.reminder.MeowApp

class SnoozeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(java.util.Locale.forLanguageTag("ru"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("id") ?: run { finish(); return }
        AlertDialog.Builder(this).setTitle("Отложить этот приём")
            .setItems(arrayOf("5 минут", "10 минут", "15 минут", "30 минут", "Другое время")) { _, index ->
                if (index < 4) snooze(id, listOf(5, 10, 15, 30)[index])
                else {
                    val input = EditText(this).apply { hint = "От 1 до 1440 минут"; inputType = InputType.TYPE_CLASS_NUMBER }
                    val dialog = AlertDialog.Builder(this).setTitle("Через сколько минут?").setView(input)
                        .setPositiveButton("Отложить", null).setNegativeButton("Отмена") { _, _ -> finish() }.create()
                    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val minutes = input.text.toString().toIntOrNull()
                        if (minutes == null || minutes !in 1..1440) input.error = "Укажите от 1 до 1440 минут"
                        else { snooze(id, minutes); dialog.dismiss() }
                    } }
                    dialog.setOnCancelListener { finish() }
                    dialog.show()
                }
            }.setNegativeButton("Отмена") { _, _ -> finish() }.setOnCancelListener { finish() }.show()
    }
    private fun snooze(id: String, minutes: Int) {
        val app = application as MeowApp
        app.scope.launch { app.scheduler.snooze(id, minutes) }
        finish()
    }
}
