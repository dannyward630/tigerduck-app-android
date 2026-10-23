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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What TalkBack says for the accent color swatches in Settings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccentColorSwatchesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var picked: Int? = null
    private var wallpaperPicks = 0

    private val wallpaper: String get() = composeRule.activity.getString(R.string.color_name_wallpaper)

    private fun show(selectedHex: Int, followsWallpaper: Boolean = false, isDark: Boolean = false) {
        composeRule.setContent {
            AccentColorSwatches(
                selectedHex = selectedHex,
                followsWallpaper = followsWallpaper,
                isDark = isDark,
                onSelect = { picked = it },
                onSelectWallpaper = { wallpaperPicks++ },
            )
        }
    }

    // The default SDK, 33, shows the wallpaper's swatch first in the row; the
    // colors after it keep their names and their order.
    @Test
    fun `each swatch is named by its color and the chosen one is selected`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
        composeRule.onNodeWithContentDescription("Purple").assertIsNotSelected()
        composeRule.onNodeWithContentDescription("Indigo").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithContentDescription(wallpaper).assertIsNotSelected()
    }

    @Test
    fun `choosing a swatch in dark mode stores its light color`() {
        show(selectedHex = 0x007AFF, isDark = true)

        composeRule.onNodeWithContentDescription("Cyan").performScrollTo().performClick()

        assertEquals(0x5AC8FA, picked)
    }

    @Config(sdk = [30])
    @Test
    fun `wallpaper swatch is hidden on Android 11 and earlier`() {
        show(selectedHex = 0x007AFF)

        composeRule.onNodeWithContentDescription(wallpaper).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Blue").assertIsSelected()
    }

    @Config(sdk = [31])
    @Test
    fun `while the wallpaper is picked its swatch is the one selected, not the swatch it keeps`() {
        show(selectedHex = 0x007AFF, followsWallpaper = true)

        composeRule.onNodeWithContentDescription(wallpaper).assertIsSelected()
        composeRule.onNodeWithContentDescription("Blue").assertIsNotSelected()
    }

    @Config(sdk = [31])
    @Test
    fun `choosing the wallpaper leaves the swatch's color alone`() {
        show(selectedHex = 0xFF2D55)

        composeRule.onNodeWithContentDescription(wallpaper).performClick()

        assertEquals(1, wallpaperPicks)
        assertNull(picked)
    }
}
