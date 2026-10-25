package org.ntust.app.tigerduck.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentColor
import org.ntust.app.tigerduck.wear.ui.theme.readableOnBlack

/**
 * A padding setting's bar, in the phone's accent: [valueDp] of [minDp]..[maxDp].
 *
 * [valueDp] is read inside the indicator's progress lambda. Wear's indicator
 * starts from the first value and follows the state it sees read there; a
 * number read once outside it left the bar on that first value, about half,
 * whatever the padding became.
 */
@Composable
internal fun PaddingBar(valueDp: () -> Int, minDp: Int, maxDp: Int, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { paddingFraction(valueDp(), minDp, maxDp) },
        modifier = modifier,
        colors = ProgressIndicatorDefaults.colors(indicatorColor = readableOnBlack(LocalAccentColor.current)),
    )
}

/** How far [valueDp] is through [minDp]..[maxDp], from 0 to 1. */
internal fun paddingFraction(valueDp: Int, minDp: Int, maxDp: Int): Float =
    if (maxDp <= minDp) 0f else ((valueDp - minDp).toFloat() / (maxDp - minDp)).coerceIn(0f, 1f)
