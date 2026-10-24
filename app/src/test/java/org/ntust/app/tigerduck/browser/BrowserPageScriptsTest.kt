package org.ntust.app.tigerduck.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The style the browser adds to Moodle's pages so they can always be panned. */
class BrowserPageScriptsTest {

    @Test
    fun `the rule lets the document and the body pan, over any inline style`() {
        val script = BrowserPageScripts.TOUCH_PAN

        assertTrue(script, script.contains("html, body { touch-action: auto !important; }"))
        assertTrue("adds it as a stylesheet", script.contains("createElement('style')"))
        assertTrue("before the page has a head", script.contains("document.documentElement"))
    }

    @Test
    fun `only Moodle's pages get it, where YUI turns touch off`() {
        assertEquals(setOf("https://moodle2.ntust.edu.tw"), BrowserPageScripts.TOUCH_PAN_ORIGINS)
    }
}
