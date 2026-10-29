package ru.meow.reminder

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import ru.meow.reminder.data.AppSettings
import ru.meow.reminder.ui.*

class MascotTest {
    @get:Rule val compose = createComposeRule()
    private fun animationScale(value: String) {
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global animator_duration_scale $value")).use { it.readBytes() }
    }
    @Before fun enableSystemAnimationForPixelTest() { animationScale("1") }
    @After fun restoreTestAnimationSetting() { animationScale("0") }
    @Test fun pillRotatesWithoutTextOrCat() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MeowTheme(AppSettings()) { RotatingPill(Modifier.size(96.dp)) } }
        val pill = compose.onNodeWithContentDescription("Загрузка: таблетка")
        val before = pill.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(400)
        val after = pill.captureToImage().asAndroidBitmap()
        assertFalse(before.sameAs(after))
        compose.onNodeWithText("Мяу, пора!").assertDoesNotExist()
        compose.onNodeWithContentDescription("Довольный котик").assertDoesNotExist()
        compose.onNodeWithContentDescription("Милый котик с большими глазками").assertDoesNotExist()
    }
    @Test fun disabledAnimationLeavesPillStill() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MeowTheme(AppSettings(animations = false)) { RotatingPill(Modifier.size(96.dp), animate = false) } }
        val pill = compose.onNodeWithContentDescription("Загрузка: таблетка")
        val before = pill.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(400)
        assertTrue(before.sameAs(pill.captureToImage().asAndroidBitmap()))
    }
}
