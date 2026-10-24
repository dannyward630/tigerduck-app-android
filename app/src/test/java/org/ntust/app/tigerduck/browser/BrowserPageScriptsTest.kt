package org.ntust.app.tigerduck.browser

import org.junit.Assert.assertTrue
import org.junit.Test

/** The style the browser adds to every page so it can always be panned. */
class BrowserPageScriptsTest {

    @Test
    fun `every page gets a rule that lets the document and the body pan, over any inline style`() {
        val script = BrowserPageScripts.TOUCH_PAN

        assertTrue(script, script.contains("html, body { touch-action: auto !important; }"))
        assertTrue("adds it as a stylesheet", script.contains("createElement('style')"))
        assertTrue("before the page has a head", script.contains("document.documentElement"))
    }
}
