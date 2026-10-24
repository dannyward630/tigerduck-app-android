package org.ntust.app.tigerduck.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/**
 * The scrolling content of a Material3 bottom sheet, kept inside the sheet. Put [modifier] before
 * the content's `verticalScroll(scroll)`.
 *
 * Material3's sheet takes whatever a downward drag or fling leaves over once the content reaches
 * its top: a hard fling from the bottom scrolled the content up in a moment and then pulled the
 * sheet down with the rest. Here a gesture that starts with the content scrolled away from its top
 * stops at the top, and only the next one, started there, moves the sheet.
 */
internal class SheetContentScroll(private val scroll: ScrollState) {

    /** Whether the gesture under way began with the content at its top. */
    private var startedAtTop = true

    private val connection = object : NestedScrollConnection {
        // The finger's drag only: a fling's own steps, left over at the top, stay unconsumed, so the
        // fling ends there and hands its velocity to onPostFling rather than running on unseen.
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (!startedAtTop && source == NestedScrollSource.UserInput && available.y > 0f) {
                Offset(0f, available.y)
            } else {
                Offset.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            if (!startedAtTop && available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
    }

    val modifier: Modifier = Modifier
        .pointerInput(this) {
            awaitEachGesture {
                // Seen on the way in, before the content scrolls, and left for the content to use.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                startedAtTop = !scroll.canScrollBackward
            }
        }
        .nestedScroll(connection)
}
