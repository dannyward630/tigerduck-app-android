package org.ntust.app.tigerduck.wear

import android.app.Application
import android.content.Context
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
 * Covers [wearAccentHex], the accent the phone sends the watch: the swatch
 * last picked, as the watch has no wallpaper colors of its own to follow.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class WearAccentHexTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = AppPreferences(context)
    }

    @Test
    fun `with the wallpaper picked the watch is sent the swatch last picked`() {
        AccentSetting(prefs).apply {
            pick(0xFF2D55)
            pickWallpaper()
        }
        assertEquals("#FF2D55", wearAccentHex(prefs))
    }

    @Test
    fun `a stored -1 is sent as Blue, not white`() {
        context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit()
            .putInt("accentColorHex", -1).commit()
        assertEquals("#007AFF", wearAccentHex(prefs))
    }
}
