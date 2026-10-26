package org.ntust.app.tigerduck.ui.screen.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way the finger moves the page, as the browser's bars read it; y grows down the screen. */
class BrowserDragTrackerTest {

    /** What the tracker told: true for the page dragged down, toward its end. */
    private val told = mutableListOf<Boolean>()
    private val drags = BrowserDragTracker(touchSlop = 8f) { down -> told += down }

    @Test
    fun `a finger going up past the slop drags the page down`() {
        drags.down(1_000f)
        drags.moved(995f)
        assertEquals(emptyList<Boolean>(), told)
        drags.moved(990f)
        assertEquals(listOf(true), told)
    }

    @Test
    fun `a tap tells nothing`() {
        drags.down(1_000f)
        drags.moved(1_003f)
        drags.up()
        assertEquals(emptyList<Boolean>(), told)
    }

    @Test
    fun `once the page is dragging, a turn back shorter than the slop is told`() {
        drags.down(1_000f)
        drags.moved(990f)
        drags.moved(986f)
        // Back down the screen by 6 px, under the slop: the page now goes up, and so does a fling from here.
        drags.moved(992f)
        assertEquals(false, told.last())
    }

    @Test
    fun `the fingers' moves are told with a second finger down and after it lifts`() {
        drags.down(1_000f)
        drags.moved(980f)
        // A second finger lands at 800: the pair's middle is at 890.
        drags.pointersChanged(890f)
        drags.moved(910f)
        assertEquals(false, told.last())
        // It lifts, leaving the first at 920.
        drags.pointersChanged(920f)
        told.clear()
        drags.moved(900f)
        assertEquals(listOf(true), told)
    }

    @Test
    fun `a finger landing or lifting is no move`() {
        drags.down(1_000f)
        drags.moved(980f)
        told.clear()
        // The middle jumps up the screen as a second finger lands far above.
        drags.pointersChanged(600f)
        drags.moved(600f)
        drags.pointersChanged(980f)
        assertEquals(emptyList<Boolean>(), told)
    }
}
