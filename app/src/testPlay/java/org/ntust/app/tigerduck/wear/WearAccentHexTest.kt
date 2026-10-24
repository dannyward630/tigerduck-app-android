package org.ntust.app.tigerduck.wear

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.ntust.app.tigerduck.shared.AccentGradientDirection
import org.ntust.app.tigerduck.ui.AccentSetting
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Covers [wearAccentHex] and [wearAccentGradient], the accent the phone sends
 * the watch: the swatch or gradient last picked, as the watch has no wallpaper
 * colors of its own to follow.
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

    // The watch draws on black: black picked as one's own would leave its
    // rings and guides invisible there.
    @Test
    fun `a dark color of the user's own is sent in a tone that stands out on the watch's black`() {
        AccentSetting(prefs).pick(0x000000)
        val sent = Color(android.graphics.Color.parseColor(wearAccentHex(prefs)))
        val contrast = (sent.luminance() + 0.05f) / (Color.Black.luminance() + 0.05f)
        assertTrue("$sent stands out at $contrast:1", contrast >= 3f)
    }

    @Test
    fun `a preset is sent as it is`() {
        AccentSetting(prefs).pick(0x5856D6)
        assertEquals("#5856D6", wearAccentHex(prefs))
    }

    @Test
    fun `a single color sends no gradient`() {
        AccentSetting(prefs).pick(0xFF2D55)
        assertNull(wearAccentGradient(prefs))
    }

    @Test
    fun `a ready-made gradient sends its start, its end as it is and its direction`() {
        AccentSetting(prefs).apply {
            pickGradient(0x007AFF, 0xAF52DE)
            pickDirection(AccentGradientDirection.LeftToRight)
        }
        assertEquals("#007AFF", wearAccentHex(prefs))
        assertEquals(WearAccentGradient("#AF52DE", 2), wearAccentGradient(prefs))
    }

    @Test
    fun `a gradient picked with no direction goes top left to bottom right`() {
        AccentSetting(prefs).pickGradient(0x5856D6, 0x5AC8FA)
        assertEquals(WearAccentGradient("#5AC8FA", 5), wearAccentGradient(prefs))
    }

    // As its start does: black as its end would vanish into the watch's black.
    @Test
    fun `a gradient's end of the user's own goes in a tone that stands out on the watch's black`() {
        AccentSetting(prefs).pickGradient(0x007AFF, 0x000000)
        val end = Color(android.graphics.Color.parseColor(wearAccentGradient(prefs)!!.endHex))
        val contrast = (end.luminance() + 0.05f) / (Color.Black.luminance() + 0.05f)
        assertTrue("$end stands out at $contrast:1", contrast >= 3f)
    }

    // An older build knows only accentColorHex: a color picked there leaves the
    // gradient behind, which the phone then no longer draws, nor the watch.
    @Test
    fun `a gradient an older build picked over sends none`() {
        AccentSetting(prefs).pickGradient(0x007AFF, 0xAF52DE)
        context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE).edit()
            .putInt("accentColorHex", 0xFF2D55).commit()
        assertEquals("#FF2D55", wearAccentHex(prefs))
        assertNull(wearAccentGradient(prefs))
    }

    // The watch hears of an accent pick through accentColorChanged. A gradient
    // from the color already picked, and a new direction, leave
    // accentColorHex as it was, and must reach the watch too.
    @Test
    fun `a gradient from the color already picked and a new direction each reach the watch`() = runTest {
        val setting = AccentSetting(prefs).apply { pick(0x007AFF) }
        var heard = 0
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) {
            prefs.accentColorChanged.collect { heard++ }
        }

        setting.pickGradient(0x007AFF, 0xAF52DE)
        assertTrue("a gradient from the color picked is heard", heard > 0)

        heard = 0
        setting.pickDirection(AccentGradientDirection.RightToLeft)
        assertTrue("a new direction is heard", heard > 0)

        heard = 0
        setting.pickGradient(0x007AFF, 0xAF52DE)
        assertEquals("the same gradient again changes nothing", 0, heard)

        listening.cancel()
    }

    @Test
    fun `with the wallpaper picked the watch is sent the gradient last picked`() {
        AccentSetting(prefs).apply {
            pickGradient(0xFF2D55, 0xFF9500)
            pickWallpaper()
        }
        assertEquals("#FF2D55", wearAccentHex(prefs))
        assertEquals(WearAccentGradient("#FF9500", 5), wearAccentGradient(prefs))
    }
}
