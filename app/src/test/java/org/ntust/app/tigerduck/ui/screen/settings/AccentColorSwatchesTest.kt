package org.ntust.app.tigerduck.ui.screen.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack says for the accent colour swatches in Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccentColorSwatchesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var picked: Int? = null

    private val wallpaper: String get() = composeRule.activity.getString(R.string.color_name_wallpaper)

    private fun show(selectedHex: Int, isDark: Boolean = false) {
        composeRule.setContent {
            AccentColorSwatches(selectedHex = selectedHex, isDark = isDark, onSelect = { picked = it })
        }
    }

    // The default SDK, 33, shows the wallpaper's swatch first in the row; the
    // colours after it keep their names and their order.
    @Test
    fun `each swatch is named by its colour and the chosen one is selected`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
        composeRule.onNodeWithContentDescription("Purple").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Indigo").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithContentDescription(wallpaper).assertIsNotSelected()
    }

    @Test
    fun `choosing a swatch in dark mode stores its light colour`() {
        show(selectedHex = 0x007AFF, isDark = true)

        composeRule.onNodeWithContentDescription("Cyan").performScrollTo().performClick()

        assertEquals(0x5AC8FA, picked)
    }

    @Config(sdk = [30])
    @Test
    fun `wallpaper swatch is hidden on Android 11 and earlier`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription(wallpaper).assertDoesNotExist()
    }

    @Config(sdk = [31])
    @Test
    fun `wallpaper swatch is visible and selectable on Android 12 and later`() {
        show(selectedHex = AppPreferences.ACCENT_WALLPAPER)

        val wallpaperNode = composeRule.onNodeWithContentDescription(wallpaper)
        wallpaperNode.assertIsSelected()
        wallpaperNode.performClick()
        assertEquals(AppPreferences.ACCENT_WALLPAPER, picked)
    }
}
