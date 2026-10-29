package ru.meow.reminder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import ru.meow.reminder.ui.MeowViewModel
import ru.meow.reminder.data.MedicationTemplate
import kotlinx.coroutines.runBlocking
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

class ComposeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant ru.meow.reminder android.permission.POST_NOTIFICATIONS").close()
        }
    }
    private fun enterHome() {
        compose.waitUntil(30000) { compose.onAllNodesWithText("Начать заботиться о себе").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Сегодня").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Начать заботиться о себе").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Начать заботиться о себе").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Сегодня").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun nativeApplicationOpensAndHasNotesNavigation() {
        val title = "Проверка заметки ${System.currentTimeMillis()}"
        enterHome()
        compose.onAllNodesWithText("Заметки").onFirst().performClick()
        compose.onNodeWithText("Новая заметка").assertExists()
        compose.onNodeWithText("Новая заметка").performScrollTo().performClick()
        compose.onNodeWithText("Заголовок").performTextInput(title)
        compose.onNodeWithText("Ваш текст").performTextInput("Мур, всё сохранено")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText(title).assertExists()
    }
    @Test fun swipingDownOnTodayRefreshesLocalDataAndPhrase() {
        enterHome()
        val vm = ViewModelProvider(compose.activity)[MeowViewModel::class.java]
        compose.waitUntil(10000) { vm.homePhrase.value.isNotBlank() }
        val before = vm.homePhrase.value
        compose.onNodeWithTag("pull-to-refresh").performTouchInput {
            swipeDown(startY = height * .1f, endY = height * .85f, durationMillis = 650)
        }
        compose.waitUntil(10000) { vm.homePhrase.value != before && !vm.refreshingToday.value }
        compose.onNodeWithText(vm.homePhrase.value).assertExists()
    }
    @Test fun existingMedicationCanBeSelectedAndScheduledWithoutDuplication() {
        enterHome()
        val app = compose.activity.application as MeowApp
        val med = MedicationTemplate(name = "Быстрый выбор ${System.currentTimeMillis()}", note = "Сохранить описание")
        runBlocking { app.repository.dao.medication(med) }
        val vm = ViewModelProvider(compose.activity)[MeowViewModel::class.java]
        compose.waitUntil(10000) { vm.medications.value.any { it.id == med.id } }
        compose.onNodeWithContentDescription("Добавить напоминание").performClick()
        compose.onNodeWithText("Поиск по названию").performTextInput(med.name)
        compose.onNodeWithText("${med.icon}  ${med.name}").performClick()
        compose.onNodeWithText("Выбрать время").performScrollTo().performClick()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        requireNotNull(device.wait(Until.findObject(By.res("android", "button1")), 10000)).click()
        compose.onNodeWithTag("save-quick-reminder").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(10000) { vm.schedules.value.any { it.medicationId == med.id && it.enabled } }
        val stored = runBlocking { app.repository.dao.allMedications().filter { it.id == med.id } }
        org.junit.Assert.assertEquals(listOf(med.copy(enabled = true)), stored)
        org.junit.Assert.assertEquals(1, runBlocking { app.repository.dao.allSchedules().count { it.medicationId == med.id } })
        runBlocking { app.repository.dao.medication(med.copy(deleted = true)); app.scheduler.reconcile() }
    }
}
