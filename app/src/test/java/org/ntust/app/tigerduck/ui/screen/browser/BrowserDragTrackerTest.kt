package org.ntust.app.tigerduck.ui.screen.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way the finger moves the page, as the browser's bars read it; y grows down the screen. */
class BrowserDragTrackerTest {

    /** What the tracker told: true for the page dragged down, toward its end. */
    private val told = mutableListOf<Boolean>()
    private val drags = BrowserDragTracker(touchSlop = 8f, minFlingVelocity = 50f) { down -> told += down }

    /** The time of the touch's last event, in ms; each call below comes a frame (16 ms) later by default. */
    private var clock = 0L

    private fun down(y: Float) = drags.down(y, clock)
    private fun pointersChanged(y: Float, after: Long = 16) = drags.pointersChanged(y, (clock + after).also { clock = it })
    private fun moved(y: Float, after: Long = 16) = drags.moved(y, (clock + after).also { clock = it })
    private fun up(y: Float, after: Long = 16) = drags.up(y, (clock + after).also { clock = it })

    @Test
    fun `a finger going up past the slop drags the page down`() {
        down(1_000f)
        moved(995f)
        assertEquals(emptyList<Boolean>(), told)
        moved(990f)
        assertEquals(listOf(true), told)
    }

    @Test
    fun `a tap tells nothing`() {
        down(1_000f)
        moved(1_003f)
        up(1_003f)
        assertEquals(emptyList<Boolean>(), told)
    }

    @Test
    fun `once the page is dragging, a turn back shorter than the slop is told`() {
        down(1_000f)
        moved(990f)
        moved(986f)
        // Back down the screen by 6 px, under the slop: the page now goes up.
        moved(992f)
        assertEquals(false, told.last())
    }

    @Test
    fun `a fling is told the way it goes, though the finger turned back at the very end`() {
        down(1_000f)
        for (y in listOf(968f, 936f, 904f, 872f, 840f, 846f)) moved(y)
        assertEquals("the last move went up the page", false, told.last())
        up(846f, after = 8)
        // Released at about 1,500 px/s up the screen: the page flings down.
        assertEquals(true, told.last())
    }

    @Test
    fun `a fling up the page is told up, though the finger turned back at the very end`() {
        down(1_000f)
        for (y in listOf(1_032f, 1_064f, 1_096f, 1_128f, 1_160f, 1_154f)) moved(y)
        up(1_154f, after = 8)
        assertEquals(false, told.last())
    }

    @Test
    fun `a slow lift tells nothing more`() {
        down(1_000f)
        moved(991f, after = 200)
        moved(990f, after = 200)
        up(990f, after = 100)
        assertEquals(listOf(true, true), told)
    }

    @Test
    fun `a lift after the finger held still tells nothing more`() {
        down(1_000f)
        moved(968f)
        moved(936f)
        told.clear()
        up(936f, after = 60)
        assertEquals(emptyList<Boolean>(), told)
    }

    @Test
    fun `a touch taken away tells no fling`() {
        down(1_000f)
        moved(968f)
        moved(936f)
        told.clear()
        drags.cancel()
        assertEquals(emptyList<Boolean>(), told)
    }

    @Test
    fun `the fingers' moves are told with a second finger down and after it lifts`() {
        down(1_000f)
        moved(980f)
        // A second finger lands at 800: the pair's middle is at 890.
        pointersChanged(890f)
        moved(910f)
        assertEquals(false, told.last())
        // It lifts, leaving the first at 920.
        pointersChanged(920f)
        told.clear()
        moved(900f)
        assertEquals(listOf(true), told)
    }

    @Test
    fun `a finger landing or lifting is no move, nor part of a fling`() {
        down(1_000f)
        moved(980f)
        told.clear()
        // The middle jumps up the screen as a second finger lands far above.
        pointersChanged(600f)
        moved(600f)
        pointersChanged(980f)
        up(980f)
        assertEquals(emptyList<Boolean>(), told)
    }
}
