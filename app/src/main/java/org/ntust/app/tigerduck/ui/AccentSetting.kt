package org.ntust.app.tigerduck.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.ntust.app.tigerduck.data.preferences.AccentPolicy
import org.ntust.app.tigerduck.data.preferences.AppPreferences

/**
 * AppState's copy of the accent picked in Settings, held as Compose state over
 * its two preferences: the swatch last picked, and whether the wallpaper's
 * colors are picked in its place. The root theme reads it, so a pick recolors
 * every screen at once.
 *
 * A swatch's pick stores its color and turns the wallpaper off; the
 * wallpaper's pick leaves the swatch as it is, for the surfaces
 * [AccentPolicy] keeps on it. [onFixedChange] hears each pick that changes the
 * swatch: the widgets draw it, and redraw only when asked. Its own class
 * because [AppState] needs a full Hilt graph, and this needs only the store.
 */
internal class AccentSetting(
    private val prefs: AppPreferences,
    private val onFixedChange: () -> Unit = {},
) {
    private var lightHexState by mutableIntStateOf(prefs.accentColorHex)
    private var followsWallpaperState by mutableStateOf(prefs.useWallpaperColors)

    /** The swatch last picked, as its light palette hex, kept while the wallpaper is picked. */
    val lightHex: Int get() = lightHexState

    /** Whether the wallpaper's colors are the pick, whether or not this device has them. */
    val followsWallpaper: Boolean get() = followsWallpaperState

    /** Whether the app's screens draw the wallpaper's colors on this device. */
    val appFollowsWallpaper: Boolean
        get() = AccentPolicy.appFollowsWallpaper(followsWallpaperState)

    /**
     * Picks the swatch of light palette hex [lightHex], in place of the
     * wallpaper too. Stored even when the copy already holds it: a stored
     * value that is no color reads as Blue, and picking Blue then writes Blue
     * over it, which an older build reads as it is.
     */
    fun pick(lightHex: Int) {
        if (followsWallpaperState) {
            followsWallpaperState = false
            prefs.useWallpaperColors = false
        }
        prefs.accentColorHex = lightHex
        if (lightHexState == lightHex) return
        lightHexState = lightHex
        onFixedChange()
    }

    /** Picks the wallpaper's colors, keeping the swatch for what cannot follow them. */
    fun pickWallpaper() {
        if (followsWallpaperState) return
        followsWallpaperState = true
        prefs.useWallpaperColors = true
    }

    /** Reads the store again, after a full reset wiped it. */
    fun reload() {
        lightHexState = prefs.accentColorHex
        followsWallpaperState = prefs.useWallpaperColors
    }
}
