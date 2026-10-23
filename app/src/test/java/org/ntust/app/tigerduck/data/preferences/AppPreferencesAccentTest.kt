package org.ntust.app.tigerduck.data.preferences

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The accent picked in Settings, as its two preferences store it and read it back. */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class AppPreferencesAccentTest {

    private lateinit var context: Context
    private val raw: SharedPreferences
        get() = context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        raw.edit().clear().commit()
    }

    @Test
    fun `an install that never picked an accent reads Blue, without the wallpaper`() {
        val prefs = AppPreferences(context)
        assertEquals(0x007AFF, prefs.accentColorHex)
        assertFalse(prefs.useWallpaperColors)
    }

    @Test
    fun `a stored -1 reads as Blue, so no surface draws it as white`() {
        raw.edit().putInt("accentColorHex", -1).commit()
        assertEquals(0x007AFF, AppPreferences(context).accentColorHex)
    }

    @Test
    fun `the wallpaper is stored apart from the swatch, which an older build still reads`() {
        val prefs = AppPreferences(context)
        prefs.accentColorHex = 0xFF2D55
        prefs.useWallpaperColors = true

        val restarted = AppPreferences(context)
        assertTrue(restarted.useWallpaperColors)
        assertEquals(0xFF2D55, restarted.accentColorHex)
        // An older build reads only this key: it must still hold the swatch.
        assertEquals(0xFF2D55, raw.getInt("accentColorHex", 0))
    }

    @Test
    fun `a full reset turns the wallpaper off again`() {
        val prefs = AppPreferences(context)
        prefs.useWallpaperColors = true
        prefs.clearAllPrefs()
        assertFalse(prefs.useWallpaperColors)
        assertEquals(0x007AFF, prefs.accentColorHex)
    }
}
