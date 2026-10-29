@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package ru.meow.reminder.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import ru.meow.reminder.data.*
import ru.meow.reminder.BuildConfig
import java.time.LocalDate

@Composable
fun PermissionCard(vm: MeowViewModel, openSettings: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var exact by remember { mutableStateOf(vm.app.scheduler.exactAllowed()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exact = vm.app.scheduler.exactAllowed()
    }
    if (!allowed || !exact) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (!allowed) "Уведомления запрещены системой" else "Точное время пока не гарантируется", style = MaterialTheme.typography.titleMedium)
            Text(if (!allowed) "Разрешите уведомления в настройках, чтобы получать напоминания." else "Используется безопасный неточный режим: Android может задерживать уведомления.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = openSettings) { Text("Проверить разрешения") }
        }
    }
}

@Composable
fun SettingsScreen(vm: MeowViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val phrases by vm.phrases.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Милые") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.task {
            val backup = vm.repo.backup()
            checkNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(backup) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { message = "Резервная копия сохранена" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.task {
            val text = checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() }
            appJson.decodeFromString<Backup>(text)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { importText = text }
        }
    }
    fun systemNotifications() {
        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    Page("Настройки") {
        Section("Оформление") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "Как в системе", "light" to "Светлая", "dark" to "Тёмная").forEach { (key, label) -> FilterChip(selected = settings.theme == key, onClick = { vm.setting { it.copy(theme = key) } }, label = { Text(label) }) }
            }
            Toggle("Системные цвета Material You", settings.dynamicColor, "На Android 12 и новее — палитра ваших обоев") { value -> vm.setting { it.copy(dynamicColor = value) } }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < 31) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Лаванда", "Роза", "Небо", "Персик").forEachIndexed { i, label -> FilterChip(selected = settings.accent == i, onClick = { vm.setting { it.copy(accent = i) } }, label = { Text(label) }) }
            }
            Toggle("Анимации", settings.animations) { value -> vm.setting { it.copy(animations = value) } }
        }
        Section("Котик и фразы") {
            Toggle("Показывать котика", settings.cat) { value -> vm.setting { it.copy(cat = value) } }
            Toggle("Случайные добрые фразы", settings.phrases) { value -> vm.setting { it.copy(phrases = value) } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PhraseLibrary.categories.forEach { cat -> FilterChip(selected = cat in settings.categories, onClick = {
                vm.setting { it.copy(categories = if (cat in it.categories) it.categories - cat else it.categories + cat) }
            }, label = { Text(cat) }) } }
            Field(custom, "Своя фраза", { custom = it }, singleLine = false)
            Text("Категория новой фразы", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PhraseLibrary.categories.forEach { cat -> FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat) }) } }
            OutlinedButton(enabled = custom.isNotBlank(), onClick = { val text = custom.trim(); vm.task { vm.repo.dao.phrase(CustomPhrase(text = text, category = category)) }; custom = "" }) { Text("Добавить фразу") }
            phrases.forEach { phrase ->
                var deleting by remember(phrase.id) { mutableStateOf(false) }
                Text(phrase.text)
                TextButton(onClick = { deleting = true }) { Text("Удалить фразу") }
                if (deleting) Confirm("Удалить фразу?", phrase.text, { deleting = false }) { vm.task { vm.repo.dao.deletePhrase(phrase.id) } }
            }
        }
        Section("Уведомления") {
            Toggle("Звук", settings.sound, "Системные настройки канала имеют приоритет") { value -> vm.setting { it.copy(sound = value) } }
            Toggle("Вибрация", settings.vibration) { value -> vm.setting { it.copy(vibration = value) } }
            Toggle("Предпросмотр содержимого", settings.notificationPreview) { value -> vm.setting { it.copy(notificationPreview = value) } }
            Toggle("Скрывать препарат на экране блокировки", settings.privateNotifications) { value -> vm.setting { it.copy(privateNotifications = value) } }
            Toggle("Повторы для новых расписаний", settings.repeatDefault, "Для существующих меняются в редакторе расписания") { value -> vm.setting { it.copy(repeatDefault = value) } }
            NumberField(settings.repeatMinutes, "Интервал по умолчанию, минут", 1..1440) { value -> vm.setting { it.copy(repeatMinutes = value) } }
            PermissionCard(vm) { systemNotifications() }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else systemNotifications()
            }) { Text("Разрешения и каналы уведомлений") }
            if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }) { Text("Разрешить точные будильники") }
            OutlinedButton(onClick = { vm.task { vm.app.scheduler.testNotification() } }) { Text("Тестовое уведомление") }
            Text("Doze и прошивки производителей могут задерживать уведомления. После принудительной остановки откройте приложение снова. При необходимости проверьте ограничения батареи в системных настройках.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Samsung Galaxy и Galaxy Watch") {
            Text("Для Galaxy: разрешите уведомления и точные будильники. В настройках телефона проверьте «Батарея → Ограничения фонового использования»: приложение не должно находиться в списке глубокого сна. При необходимости добавьте его в список никогда не спящих приложений.")
            Text("Galaxy Wearable → Настройки часов → Уведомления → Уведомления приложений → Мяу, пора! Для проверки включите показ уведомлений на часах даже при использовании телефона. Названия пунктов зависят от модели и версии One UI.")
            Text("Часы должны быть подключены к телефону; проверьте «Не беспокоить», режим сна, ношение часов и громкость. Приложение на часах отдельно не устанавливается: используется пересылка уведомлений телефона. Для старых часов на Tizen набор действий может отличаться.", style = MaterialTheme.typography.bodySmall)
            Text("Скрытие подробностей на часах настраивается в Galaxy Wearable отдельно от экрана блокировки телефона.", style = MaterialTheme.typography.bodySmall)
            Text("На совместимых часах передаются действия «Принято», «На 10 минут», «Пропустить». Выбор произвольной отсрочки остаётся на телефоне. Фактический показ кнопок и доставка зависят от Galaxy Wearable и прошивки.", style = MaterialTheme.typography.bodySmall)
            var batteryExempt by remember { mutableStateOf(false) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                batteryExempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
            }
            Text(if (batteryExempt) "Исключение из стандартной оптимизации Android: включено. Списки сна Samsung нужно проверить отдельно."
                else "Стандартная оптимизация батареи Android активна. Приложение не может само проверить списки глубокого сна Samsung.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("Открыть параметры приложения") }
            Text("Для проверки создайте одноразовый приём через 2–3 минуты, закройте окно и заблокируйте телефон. Проверьте уведомление на телефоне и часах, затем каждое действие. Тестовое уведомление выше проверяет отображение, а не автономную работу часов.", style = MaterialTheme.typography.bodySmall)
        }
        Section("Данные") {
            Text("JSON содержит медицинские записи и заметки в открытом виде. Сохраняйте его в надёжном месте.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { export.launch("meow-backup-${LocalDate.now()}.json") }) { Text("Экспорт в JSON") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Импорт из JSON") }
            TextButton(onClick = { reset = true }) { Text("Сбросить все данные", color = MaterialTheme.colorScheme.error) }
        }
        Section("Конфиденциальность") {
            Text("Все записи хранятся локально. Нет рекламы, аналитики, регистрации и передачи медицинских данных. Приложение не запрашивает доступ к интернету.")
            Text("Не является медицинской рекомендацией или системой экстренного оповещения.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Мяу, пора! · ${BuildConfig.VERSION_NAME}\nРазработчик L", modifier = Modifier.padding(vertical = 24.dp), style = MaterialTheme.typography.bodySmall)
    }
    if (reset) Confirm("Полностью сбросить приложение?", "Препараты, история, заметки и свои фразы будут удалены. Сначала экспортируйте данные, если они нужны.", { reset = false }) {
        vm.task {
            vm.app.scheduler.cancelAll()
            vm.repo.database.withTransaction { vm.repo.clear() }
            vm.repo.settings.update { AppSettings(initialized = true, welcomed = true) }
            vm.app.scheduler.reconcile()
        }
    }
    if (importText != null) AlertDialog(onDismissRequest = { importText = null }, title = { Text("Импорт резервной копии") }, text = { Text("Объединить: новые записи добавятся, совпадающие идентификаторы останутся без изменений. Полная замена удалит текущие данные.") },
        confirmButton = { TextButton(onClick = { val text = importText!!; importText = null; vm.task { vm.repo.importBackup(text, false); vm.app.scheduler.reconcile() } }) { Text("Объединить") } },
        dismissButton = {
            var replace by remember { mutableStateOf(false) }
            TextButton(onClick = { replace = true }) { Text("Полностью заменить") }
            if (replace) Confirm("Заменить все данные?", "Текущие данные будут удалены. Действие нельзя отменить.", { replace = false }) {
                val text = importText!!; importText = null
                vm.task { vm.app.scheduler.cancelAll(); vm.repo.importBackup(text, true); vm.app.scheduler.reconcile() }
            }
        })
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Хорошо") } }) }
}
