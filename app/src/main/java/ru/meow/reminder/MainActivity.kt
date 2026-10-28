package ru.meow.reminder

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.ui.*

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag("ru"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
    private val model: MeowViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            MeowTheme(settings) { Surface(color = MaterialTheme.colorScheme.background) { MeowNavigation(model) } }
        }
    }
    override fun onResume() {
        super.onResume()
        model.task { model.app.scheduler.reconcile() }
    }
}
