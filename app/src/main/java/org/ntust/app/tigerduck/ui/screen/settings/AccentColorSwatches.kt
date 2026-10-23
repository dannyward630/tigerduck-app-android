package org.ntust.app.tigerduck.ui.screen.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.data.preferences.AppPreferences

/**
 * The name TalkBack reads for each accent swatch, in [AppPreferences.themeColors]
 * order. The same keys as iOS `AppState.themeColors`, whose seventh colour it
 * calls cyan.
 */
private val accentColorNames = listOf(
    R.string.color_name_blue,
    R.string.color_name_purple,
    R.string.color_name_pink,
    R.string.color_name_red,
    R.string.color_name_orange,
    R.string.color_name_green,
    R.string.color_name_cyan,
    R.string.color_name_indigo,
)

/**
 * The accent color swatches in Settings. Each is a selectable item named by
 * its color, so TalkBack says which color it is and which one is chosen,
 * not just "double tap to activate".
 *
 * [selectedHex] and [onSelect] carry the canonical (light) hex, so the pair
 * swaps when the color theme toggles; [isDark] picks the display color.
 *
 * From Android 12 the row opens with the wallpaper's colors, which
 * [onSelectWallpaper] picks. While [followsWallpaper] that swatch is the one
 * selected, and none of the others is: [selectedHex] still holds the swatch
 * last picked, for the surfaces that cannot follow the wallpaper.
 */
@Composable
internal fun AccentColorSwatches(
    selectedHex: Int,
    followsWallpaper: Boolean,
    isDark: Boolean,
    onSelect: (Int) -> Unit,
    onSelectWallpaper: () -> Unit,
) {
    val display = if (isDark) AppPreferences.themeColorsDark else AppPreferences.themeColors
    val context = LocalContext.current
    // As the root theme draws them, in this mode.
    val dynamicColors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    // Nine 32dp swatches need 368dp, more than a narrow phone -- or a
    // larger display size -- leaves inside this card, and a plain Row
    // squeezes whatever does not fit out of the last one. Scrolling
    // keeps every swatch round.
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (dynamicColors != null) {
            val wallpaperName = stringResource(R.string.color_name_wallpaper)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                dynamicColors.primary,
                                dynamicColors.tertiary,
                            )
                        )
                    )
                    .selectable(
                        selected = followsWallpaper,
                        role = Role.RadioButton,
                        onClick = onSelectWallpaper,
                    )
                    .semantics { contentDescription = wallpaperName },
                contentAlignment = Alignment.Center,
            ) {
                if (followsWallpaper) {
                    // Not white, which vanishes on the light primary of the
                    // wallpaper's dark colors.
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = dynamicColors.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        display.forEachIndexed { idx, (_, displayHex) ->
            val canonicalHex = AppPreferences.themeColors[idx].second
            val selected = !followsWallpaper && selectedHex == canonicalHex
            val name = accentColorNames.getOrNull(idx)?.let { stringResource(it) }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000 or displayHex.toLong()))
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(canonicalHex) },
                    )
                    .semantics { if (name != null) contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    // No semantics of its own: the selected state says it.
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
