package org.ntust.app.tigerduck.ui.screen.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the browser's bars hide and come back, as iOS's `BrowserChromeCollapseTests`, and as Android's WebView scrolls. */
class BrowserChromeCollapseTest {

    private var clock = 0L
    private val chrome = BrowserChromeCollapse(jitterPx = 4, topZonePx = 40, now = { clock })

    /** Where [scrolled] last dragged the page to. */
    private var dragged = 0

    /** The student drags a long page, far from its end, 1080 px wide as laid out, to [offset]. */
    private fun scrolled(offset: Int, contentWidth: Int = 1_080) {
        if (offset != dragged) chrome.dragged(down = offset > dragged)
        dragged = offset
        chrome.scrolled(offset, leftBelow = 10_000, contentWidth = contentWidth)
    }

    /** The page moves to [offset] by itself, as the WebView tells it, with no finger on it. */
    private fun movedItself(offset: Int) = chrome.scrolled(offset, leftBelow = 10_000, contentWidth = 1_080)

    @Test
    fun `scrolling down hides the bars and up brings them back`() {
        scrolled(50)
        scrolled(100)
        assertTrue(chrome.collapsed)
        scrolled(300)
        assertTrue(chrome.collapsed)
        scrolled(290)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `jitter and the top of the page change nothing`() {
        scrolled(0)
        scrolled(30)
        assertFalse(chrome.collapsed)
        scrolled(200)
        scrolled(202)
        assertTrue(chrome.collapsed)
        scrolled(199)
        assertTrue(chrome.collapsed)
    }

    @Test
    fun `small steps add up, down and up`() {
        scrolled(100)
        for (offset in 101..103) scrolled(offset)
        assertFalse("three pixels down, within the jitter", chrome.collapsed)
        for (offset in 104..160) scrolled(offset)
        assertTrue(chrome.collapsed)
        for (offset in 159 downTo 150) scrolled(offset)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `a turn starts the count again`() {
        scrolled(100)
        scrolled(103)
        scrolled(101)
        scrolled(104)
        assertFalse("three down, two up, three down: never past the jitter one way", chrome.collapsed)
    }

    @Test
    fun `the page moving up by itself while the student drags it down is no scroll`() {
        scrolled(100)
        scrolled(200)
        assertTrue(chrome.collapsed)
        // Laid out again as the bars go away, the page is moved up by the WebView.
        movedItself(182)
        assertTrue(chrome.collapsed)
        scrolled(260)
        assertTrue(chrome.collapsed)
    }

    @Test
    fun `the page moving down by itself while the student drags it up is no scroll`() {
        scrolled(100)
        scrolled(200)
        scrolled(150)
        assertFalse(chrome.collapsed)
        movedItself(284)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `before the student drags the page its own moves change nothing`() {
        movedItself(100)
        movedItself(300)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `back at the top of the page the bars show`() {
        scrolled(300)
        scrolled(400)
        assertTrue(chrome.collapsed)
        // A jump to the top, as a scroll the page does itself.
        scrolled(0)
        assertFalse(chrome.collapsed)
    }

    /**
     * A page [heightPx] tall, 1080 px wide, in a view 1000 px tall with the bars shown and 81 px
     * taller with them put away, scrolled as the WebView scrolls it: when the view grows at the
     * page's end, the WebView pulls the page up so it still fills the view, and tells that as a
     * scroll of its own.
     */
    private inner class Page(private val heightPx: Int) {
        var offset = 0
            private set

        /** How many times the bars have moved. */
        var flips = 0
            private set

        private val end get() = maxOf(0, heightPx - 1_000 - if (chrome.collapsed) 81 else 0)

        /** The student drags the page [dy] down, or up if negative; it stops at either end. */
        fun drag(dy: Int) {
            chrome.dragged(down = dy > 0)
            val to = (offset + dy).coerceIn(0, end)
            if (to == offset) return
            offset = to
            tell()
        }

        private fun tell() {
            val was = chrome.collapsed
            chrome.scrolled(offset, leftBelow = end - offset, contentWidth = 1_080)
            if (chrome.collapsed == was) return
            flips++
            chrome.resized()
            if (offset > end) {
                offset = end
                tell()
            }
        }
    }

    @Test
    fun `at the end of the page the bars stay away till a drag up`() {
        val page = Page(heightPx = 3_000)
        repeat(1_000) { page.drag(3) }
        assertTrue(chrome.collapsed)
        assertEquals("put away once, and not brought back by the end", 1, page.flips)
        page.drag(-10)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `bars put away at the end stay away, though putting them away pulls the page up`() {
        val page = Page(heightPx = 3_000)
        repeat(1_000) { page.drag(3) }
        // A tap on the collapsed bar brings them back at the end; the view shrinks, and the page
        // can scroll down again by the bars' room.
        chrome.expand()
        chrome.resized()
        val flips = page.flips
        repeat(100) { page.drag(3) }
        assertTrue(chrome.collapsed)
        assertEquals("put away once more, and left away", flips + 1, page.flips)
    }

    @Test
    fun `a page the bars' room leaves too short to scroll keeps them away till a drag up`() {
        // 100 px to scroll with the bars shown, 19 with them away.
        val page = Page(heightPx = 1_100)
        repeat(100) { page.drag(3) }
        assertTrue(chrome.collapsed)
        assertEquals(1, page.flips)
        page.drag(-10)
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `a page that comes back to its old place is no scroll`() {
        scrolled(100)
        scrolled(200)
        chrome.reset()
        assertFalse(chrome.collapsed)
        // The WebView puts a page met before back where it was.
        scrolled(500)
        assertFalse(chrome.collapsed)
        scrolled(510)
        assertTrue(chrome.collapsed)
    }

    @Test
    fun `an offset a resize moved is no scroll`() {
        clock = 10_000
        scrolled(300)
        chrome.resized()
        scrolled(900)
        assertFalse(chrome.collapsed)
        scrolled(910)
        assertTrue(chrome.collapsed)
        clock += 10_000
        chrome.resized()
        scrolled(400)
        assertTrue("a resize keeps the bars as they are", chrome.collapsed)
    }

    @Test
    fun `a pinch's offset is no scroll, though the WebView tells it before the zoom`() {
        scrolled(300)
        // Zoomed in: the page is twice as wide, and its offset doubles with it.
        scrolled(600, contentWidth = 2_160)
        assertFalse(chrome.collapsed)
        scrolled(610, contentWidth = 2_160)
        assertTrue(chrome.collapsed)
    }

    @Test
    fun `the bars' own resize leaves a scroll made meanwhile to count`() {
        clock = 10_000
        scrolled(100)
        scrolled(200)
        assertTrue(chrome.collapsed)
        // The bars shrink over some frames, the page growing each frame, while the student scrolls back up.
        for (offset in listOf(190, 180, 170)) {
            clock += 16
            chrome.resized()
            scrolled(offset)
        }
        assertFalse(chrome.collapsed)
    }

    @Test
    fun `the keyboard coming up just after the bars came back is no scroll, its page's move neither`() {
        clock = 10_000
        scrolled(100)
        scrolled(200)
        chrome.expand()
        // A field tapped at once: the keyboard comes, the page shrinks, and the WebView scrolls the field into view.
        clock += 100
        chrome.keyboardMoved()
        clock += 16
        chrome.resized()
        scrolled(500)
        clock += 300
        scrolled(560)
        assertFalse(chrome.collapsed)
        // Once it has settled, a scroll is a scroll again.
        clock += BrowserChromeCollapse.SETTLE_MS
        scrolled(570)
        scrolled(580)
        assertTrue(chrome.collapsed)
    }

    @Test
    fun `tapping the collapsed bar brings the bars back, till the page scrolls down again`() {
        scrolled(100)
        scrolled(200)
        chrome.expand()
        assertFalse(chrome.collapsed)
        scrolled(260)
        assertTrue(chrome.collapsed)
    }
}
