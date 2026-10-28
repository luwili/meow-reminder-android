package ru.meow.reminder

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

class ComposeSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm grant ru.meow.reminder android.permission.POST_NOTIFICATIONS").close()
        }
    }
    @Test fun nativeApplicationOpensAndHasNotesNavigation() {
        val title = "Проверка заметки ${System.currentTimeMillis()}"
        compose.waitUntil(30000) { compose.onAllNodesWithText("Начать заботиться о себе").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Сегодня").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Начать заботиться о себе").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Начать заботиться о себе").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Сегодня").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Заметки").onFirst().performClick()
        compose.onNodeWithText("Новая заметка").assertExists()
        compose.onNodeWithText("Новая заметка").performScrollTo().performClick()
        compose.onNodeWithText("Заголовок").performTextInput(title)
        compose.onNodeWithText("Ваш текст").performTextInput("Мур, всё сохранено")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText(title).assertExists()
    }
}
