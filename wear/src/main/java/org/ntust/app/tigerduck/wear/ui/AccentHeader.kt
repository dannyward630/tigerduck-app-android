package org.ntust.app.tigerduck.wear.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalTextConfiguration
import androidx.wear.compose.material3.LocalTextStyle
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentColor
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentGradient
import org.ntust.app.tigerduck.wear.ui.theme.accentTitleBrush
import org.ntust.app.tigerduck.wear.ui.theme.readableOnBlack

/**
 * A screen's title in the phone's accent, its gradient included, as the
 * iPhone's watch app shows its titles in the app's accent color, all along it
 * [made readable on black][readableOnBlack].
 */
@Composable
fun AccentHeader(text: String) {
    val accent = LocalAccentColor.current
    val gradient = LocalAccentGradient.current
    val brush = remember(accent, gradient) { accentTitleBrush(accent, gradient, ::readableOnBlack) }
    ListHeader {
        // BasicText, which paints with the style's brush: Material's Text would put its
        // color over it. So it takes the header's alignment, lines and overflow itself.
        val configuration = LocalTextConfiguration.current
        BasicText(
            text = text,
            style = LocalTextStyle.current.merge(
                TextStyle(brush = brush, textAlign = configuration.textAlign ?: TextAlign.Unspecified),
            ),
            maxLines = configuration.maxLines,
            overflow = configuration.overflow,
        )
    }
}
