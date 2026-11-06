package ru.meow.reminder

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.meow.reminder.ui.*
import ru.meow.reminder.widget.MeowWidgetProvider

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ACTION = "extra_action"
        const val ACTION_QUICK_ADD = "quick_add"
    }

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag("ru"))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private val model: MeowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val settings by model.settings.collectAsStateWithLifecycle()
            MeowTheme(settings) { Surface(color = MaterialTheme.colorScheme.background) { MeowNavigation(model) } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_QUICK_ADD) {
            model.quickAddRequested.value = true
        }
    }

    override fun onResume() {
        super.onResume()
        model.task {
            model.app.scheduler.reconcile()
            MeowWidgetProvider.updateAllWidgets(this@MainActivity)
        }
    }
}
