package org.ntust.app.tigerduck.wear.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.LocalTextStyle
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentColor
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentGradient
import org.ntust.app.tigerduck.wear.ui.theme.accentTitleBrush

/**
 * A screen's title in the phone's accent, its gradient included, as the
 * iPhone's watch app shows its titles in the app's accent color.
 */
@Composable
fun AccentHeader(text: String) {
    val brush = accentTitleBrush(LocalAccentColor.current, LocalAccentGradient.current)
    ListHeader {
        // BasicText, which paints with the style's brush: Material's Text would put its color over it.
        BasicText(text = text, style = LocalTextStyle.current.merge(TextStyle(brush = brush)))
    }
}
