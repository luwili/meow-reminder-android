package ru.meow.reminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import ru.meow.reminder.data.*

@Composable
fun MeowNavigation(vm: MeowViewModel) {
    val nav = rememberNavController()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(true) }
    var quickAdd by remember { mutableStateOf(false) }
    var quickMedicationId by remember { mutableStateOf<String?>(null) }
    var quickDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var editingScheduleId by remember { mutableStateOf<String?>(null) }
    val requestedQuickAdd by vm.quickAddRequested.collectAsStateWithLifecycle()
    LaunchedEffect(requestedQuickAdd) {
        if (requestedQuickAdd) {
            quickMedicationId = null
            quickAdd = true
            vm.quickAddRequested.value = false
        }
    }
    LaunchedEffect(Unit) { delay(1100); loading = false }
    LaunchedEffect(error) { error?.let { snack.showSnackbar(it); vm.error.value = null } }
    if (loading || !settings.initialized) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RotatingPill(Modifier.size(96.dp), animate = settings.animations)
        }
        return
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!settings.welcomed) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Cat(Modifier.size(140.dp), playing = true, animate = settings.animations)
            Text("Забота без суеты", style = MaterialTheme.typography.headlineLarge)
            Text("Препараты, напоминания и заметки — только на вашем устройстве. Без регистрации и рекламы.", modifier = Modifier.padding(vertical = 20.dp))
            Text("Режим приёма и дозировку указываете вы, согласно назначению или инструкции.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = {
                vm.setting { it.copy(welcomed = true) }
                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Начать заботиться о себе") }
        }
        return
    }
    val entries = listOf("today" to "Сегодня", "calendar" to "Календарь", "library" to "Препараты", "history" to "История", "settings" to "Настройки")
    val icons = listOf(Icons.Outlined.Today, Icons.Outlined.CalendarMonth, Icons.Outlined.Medication, Icons.Outlined.History, Icons.Outlined.Settings)
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        if (entries.any { it.first == route }) NavigationBar {
            entries.forEachIndexed { index, (path, title) ->
                NavigationBarItem(selected = route == path, onClick = {
                    nav.navigate(path) { popUpTo("today") { saveState = true }; launchSingleTop = true; restoreState = true }
                }, icon = { Icon(icons[index], title) }, label = { Text(title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) })
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = nav,
                startDestination = "today",
                enterTransition = { if (settings.animations) slideInHorizontally(initialOffsetX = { it / 8 }, animationSpec = tween(220)) + fadeIn(tween(220)) else EnterTransition.None },
                exitTransition = { if (settings.animations) fadeOut(tween(120)) else ExitTransition.None },
                popEnterTransition = { if (settings.animations) slideInHorizontally(initialOffsetX = { -it / 8 }, animationSpec = tween(220)) + fadeIn(tween(220)) else EnterTransition.None },
                popExitTransition = { if (settings.animations) fadeOut(tween(120)) else ExitTransition.None }
            ) {
                composable("today") {
                    TodayScreen(
                        vm = vm,
                        add = { quickMedicationId = null; quickAdd = true },
                        settings = { nav.navigate("settings") },
                        editSchedule = { scheduleId -> editingScheduleId = scheduleId }
                    )
                }
                composable("calendar") { CalendarScreen(vm) }
                composable("library") { LibraryScreen(vm, edit = { nav.navigate("med/$it") }) }
                composable("med/{id}") { e -> MedicationEditor(vm, e.arguments!!.getString("id")!!, back = { nav.popBackStack() }) }
                composable("schedule/{id}") { e -> ScheduleEditor(vm, e.arguments!!.getString("id")!!, back = { nav.popBackStack() }) }
                composable("history") { HistoryScreen(vm) }
                composable("settings") { SettingsScreen(vm) }
            }
        }
    }
    if (quickAdd) QuickReminderSheet(vm, quickMedicationId, dismiss = { quickAdd = false }, initialDate = quickDate, newMedication = {
        quickAdd = false
        val med = MedicationTemplate()
        vm.save(med)
        nav.navigate("med/${med.id}")
    }, editMedication = { quickAdd = false; nav.navigate("med/$it") })
    editingScheduleId?.let { scheduleId ->
        EditScheduleSheet(
            vm = vm,
            scheduleId = scheduleId,
            dismiss = { editingScheduleId = null },
            openAdvancedEditor = { id ->
                editingScheduleId = null
                nav.navigate("schedule/$id")
            }
        )
    }
}
