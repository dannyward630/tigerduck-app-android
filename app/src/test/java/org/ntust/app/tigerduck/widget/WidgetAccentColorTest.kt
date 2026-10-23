package org.ntust.app.tigerduck.widget

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.ntust.app.tigerduck.ui.AccentSetting
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers [resolveWidgetAccentColor]: the widgets draw the fixed accent, the
 * swatch last picked, also while the app follows the wallpaper.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class WidgetAccentColorTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = AppPreferences(context)
    }

    @Config(sdk = [31])
    @Test
    fun `with the wallpaper picked the widgets keep the swatch last picked`() {
        AccentSetting(prefs).apply {
            pick(0xFF2D55)
            pickWallpaper()
        }

        assertEquals(Color(0xFFFF2D55), resolveWidgetAccentColor(prefs, isDark = false))
        assertEquals(Color(0xFFFF375F), resolveWidgetAccentColor(prefs, isDark = true))
    }

    @Test
    fun `a stored -1 draws Blue, not white`() {
        context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit()
            .putInt("accentColorHex", -1).commit()

        assertEquals(Color(0xFF007AFF), resolveWidgetAccentColor(prefs, isDark = false))
        assertEquals(Color(0xFF0A84FF), resolveWidgetAccentColor(prefs, isDark = true))
    }
}
