package org.ntust.app.tigerduck.data.preferences

import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * Which accent each surface draws, from the two preferences behind the accent
 * row in Settings: [AppPreferences.accentColorHex], the swatch last picked,
 * and [AppPreferences.useWallpaperColors], whether the wallpaper's colors are
 * picked in its place.
 *
 * Only the app's own screens follow the wallpaper, and only from Android 12,
 * the first with wallpaper colors to give. Everything else draws the fixed
 * accent: the widgets, the watch, the Live Update, and the app's screens on
 * Android 11 and earlier. Picking the wallpaper leaves the fixed accent as it
 * was, so those keep the color they had, and so does an older build, which
 * reads only `accentColorHex`.
 */
object AccentPolicy {

    /** The accent of a fresh install: Blue. */
    const val DEFAULT_LIGHT_HEX = 0x007AFF

    /**
     * The fixed accent a stored `accentColorHex` stands for, as its light
     * palette hex. A value that is no RGB hex reads as [DEFAULT_LIGHT_HEX]:
     * above all -1, which a build of the unshipped first take on the
     * wallpaper accent stored there, and which would draw as white.
     */
    fun fixedLightHex(stored: Int): Int =
        if (stored in 0..0xFFFFFF) stored else DEFAULT_LIGHT_HEX

    /** The fixed accent [lightHex] as [isDark]'s theme draws it: its dark pair in dark mode. */
    fun fixedColor(lightHex: Int, isDark: Boolean): Color {
        val hex = if (isDark) AppPreferences.accentDarkVariant(lightHex) else lightHex
        return Color(0xFF000000L or (hex.toLong() and 0xFFFFFFL))
    }

    /**
     * Whether the app's own screens draw the wallpaper's colors on Android
     * [sdkInt]: where the user picked them and the system has them.
     */
    fun appFollowsWallpaper(useWallpaperColors: Boolean, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        useWallpaperColors && sdkInt >= Build.VERSION_CODES.S
}
