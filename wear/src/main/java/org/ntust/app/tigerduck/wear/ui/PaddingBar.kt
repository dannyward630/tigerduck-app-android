package org.ntust.app.tigerduck.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorColors
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import org.ntust.app.tigerduck.wear.ui.theme.LocalAccentColor
import org.ntust.app.tigerduck.wear.ui.theme.readableOnBlack

/**
 * Wear's linear progress indicator at [value], following it as it changes.
 *
 * Wear's indicator starts from the first value and then follows only the state
 * it sees read inside its progress lambda. A plain number handed to it as
 * `{ value }` kept it on the first one: the screen padding bar sat near half
 * whatever the padding became, and Now & Next's bar stood still through a class.
 */
@Composable
internal fun FollowingProgressIndicator(
    value: Float,
    modifier: Modifier = Modifier,
    colors: ProgressIndicatorColors = ProgressIndicatorDefaults.colors(),
) {
    val latest by rememberUpdatedState(value)
    LinearProgressIndicator(progress = { latest }, modifier = modifier, colors = colors)
}

/** A padding setting's bar, in the phone's accent: [valueDp] of [minDp]..[maxDp]. */
@Composable
internal fun PaddingBar(valueDp: Int, minDp: Int, maxDp: Int, modifier: Modifier = Modifier) {
    FollowingProgressIndicator(
        value = paddingFraction(valueDp, minDp, maxDp),
        modifier = modifier,
        colors = ProgressIndicatorDefaults.colors(indicatorColor = readableOnBlack(LocalAccentColor.current)),
    )
}

/** How far [valueDp] is through [minDp]..[maxDp], from 0 to 1. */
internal fun paddingFraction(valueDp: Int, minDp: Int, maxDp: Int): Float =
    if (maxDp <= minDp) 0f else ((valueDp - minDp).toFloat() / (maxDp - minDp)).coerceIn(0f, 1f)
