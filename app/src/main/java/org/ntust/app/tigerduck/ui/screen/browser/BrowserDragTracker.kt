package org.ntust.app.tigerduck.ui.screen.browser

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Which way the student's finger moves the page, for [BrowserChromeCollapse.dragged]. A touch
 * drags the page once it has gone [touchSlop] from where it started; from then on each move is
 * told, a turn back of any length included, so a fling is told the way the finger last went. With
 * more than one finger it follows their middle, and a finger landing or lifting moves nothing.
 */
internal class BrowserDragTracker(
    private val touchSlop: Float,
    private val dragged: (down: Boolean) -> Unit,
) {
    /** Where the touch started, for the slop. */
    private var start = 0f

    /** Where the touch was at its last move. */
    private var last = 0f

    /** Whether the touch has gone past the slop, and so drags the page. */
    private var dragging = false

    /** A first finger landed at [y]. */
    fun down(y: Float) {
        start = y
        last = y
        dragging = false
    }

    /** A finger landed or lifted; [y] is where the fingers on the page are now. */
    fun pointersChanged(y: Float) {
        last = y
        if (!dragging) start = y
    }

    /** The fingers moved to [y]. */
    fun moved(y: Float) {
        if (!dragging && abs(y - start) >= touchSlop) dragging = true
        // A finger going up moves the page down.
        if (dragging && y != last) dragged(y < last)
        last = y
    }

    /** The last finger lifted, or the touch was taken away. */
    fun up() {
        dragging = false
    }
}

/**
 * Where the touch is, for [BrowserDragTracker]: the fingers' middle, leaving out one lifting, on
 * screen, since the WebView itself moves while the bars resize.
 */
internal fun MotionEvent.focalScreenY(): Float {
    val lifting = if (actionMasked == MotionEvent.ACTION_POINTER_UP) actionIndex else -1
    var sum = 0f
    var count = 0
    for (i in 0 until pointerCount) {
        if (i == lifting) continue
        sum += getRawY(i)
        count++
    }
    return if (count == 0) rawY else sum / count
}
