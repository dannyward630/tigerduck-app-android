package org.ntust.app.tigerduck.ui.screen.browser

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Which way the student's finger moves the page, for [BrowserChromeCollapse.dragged]. A touch
 * drags the page once it has gone [touchSlop] from where it started; from then on each move is
 * told, a turn back of any length included. A lift then tells the way the page flings: the
 * release velocity's, from the last [HORIZON_MS] of the touch, when it is [minFlingVelocity] or
 * more, as the WebView's own fling, so a fling that ends in a short turn back is told the way it
 * goes. A finger held still for [STOPPED_MS] before lifting flings nothing. With more than one
 * finger it follows their middle, and a finger landing or lifting moves nothing.
 */
internal class BrowserDragTracker(
    private val touchSlop: Float,
    /** In px a second. */
    private val minFlingVelocity: Float,
    private val dragged: (down: Boolean) -> Unit,
) {
    /** Where the touch started, for the slop. */
    private var start = 0f

    /** Where the touch was at its last move. */
    private var last = 0f

    /** Whether the touch has gone past the slop, and so drags the page. */
    private var dragging = false

    /** The touch's recent moves, oldest first, as (ms, y). */
    private val samples = ArrayDeque<Pair<Long, Float>>()

    /** A first finger landed at [y], at [timeMs]. */
    fun down(y: Float, timeMs: Long) {
        start = y
        last = y
        dragging = false
        restart(y, timeMs)
    }

    /** A finger landed or lifted; [y] is where the fingers on the page are now, at [timeMs]. */
    fun pointersChanged(y: Float, timeMs: Long) {
        last = y
        if (!dragging) start = y
        // The middle jumped: the moves before it are no part of a fling.
        restart(y, timeMs)
    }

    /** The fingers moved to [y], at [timeMs]. */
    fun moved(y: Float, timeMs: Long) {
        if (!dragging && abs(y - start) >= touchSlop) dragging = true
        // A finger going up moves the page down.
        if (dragging && y != last) dragged(y < last)
        last = y
        record(y, timeMs)
    }

    /** The last finger lifted at [y], at [timeMs]. */
    fun up(y: Float, timeMs: Long) {
        record(y, timeMs)
        if (dragging) {
            val velocity = velocity()
            if (abs(velocity) >= minFlingVelocity) dragged(velocity < 0)
        }
        dragging = false
        samples.clear()
    }

    /** The touch was taken away: no fling. */
    fun cancel() {
        dragging = false
        samples.clear()
    }

    private fun restart(y: Float, timeMs: Long) {
        samples.clear()
        samples.addLast(timeMs to y)
    }

    private fun record(y: Float, timeMs: Long) {
        samples.addLast(timeMs to y)
        while (samples.first().first < timeMs - HORIZON_MS) samples.removeFirst()
    }

    /**
     * The release velocity in px a second, down the screen positive: the least-squares slope of
     * the moves since the finger last held still, within [HORIZON_MS].
     */
    private fun velocity(): Float {
        var from = samples.size - 1
        while (from > 0 && samples[from].first - samples[from - 1].first <= STOPPED_MS) from--
        val run = samples.subList(from, samples.size)
        if (run.size < 2) return 0f
        val meanT = run.sumOf { it.first.toDouble() } / run.size
        val meanY = run.sumOf { it.second.toDouble() } / run.size
        var covariance = 0.0
        var variance = 0.0
        for ((t, y) in run) {
            covariance += (t - meanT) * (y - meanY)
            variance += (t - meanT) * (t - meanT)
        }
        return if (variance == 0.0) 0f else (covariance / variance * 1_000).toFloat()
    }

    companion object {
        /** How far back the release velocity looks, in ms, as Android's VelocityTracker. */
        const val HORIZON_MS = 100L

        /** A gap between moves this long, in ms, is the finger held still, as Android's VelocityTracker. */
        const val STOPPED_MS = 40L
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
