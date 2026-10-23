package org.ntust.app.tigerduck.data.preferences

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [AccentPolicy], the rule for which accent each surface draws: the
 * wallpaper's colors on the app's own screens from Android 12, and the fixed
 * accent, the swatch last picked, everywhere else.
 */
class AccentPolicyTest {

    private val pink = 0xFF2D55

    @Test
    fun `a stored swatch reads as itself`() {
        assertEquals(pink, AccentPolicy.fixedLightHex(pink))
        assertEquals(0x007AFF, AccentPolicy.fixedLightHex(0x007AFF))
    }

    @Test
    fun `a stored -1, where a build of the wallpaper accent kept it, reads as Blue and not white`() {
        assertEquals(0x007AFF, AccentPolicy.fixedLightHex(-1))
        assertEquals(Color(0xFF007AFF), AccentPolicy.fixedColor(AccentPolicy.fixedLightHex(-1), isDark = false))
    }

    @Test
    fun `the fixed accent is drawn as its dark pair in dark mode`() {
        assertEquals(Color(0xFFFF2D55), AccentPolicy.fixedColor(pink, isDark = false))
        assertEquals(Color(0xFFFF375F), AccentPolicy.fixedColor(pink, isDark = true))
    }

    @Test
    fun `the app follows the wallpaper only where it is picked, from Android 12`() {
        assertTrue(AccentPolicy.appFollowsWallpaper(useWallpaperColors = true, sdkInt = 31))
        assertTrue(AccentPolicy.appFollowsWallpaper(useWallpaperColors = true, sdkInt = 36))
        assertFalse(AccentPolicy.appFollowsWallpaper(useWallpaperColors = true, sdkInt = 30))
        assertFalse(AccentPolicy.appFollowsWallpaper(useWallpaperColors = false, sdkInt = 31))
    }
}
