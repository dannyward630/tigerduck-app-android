package org.ntust.app.tigerduck.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `Moodle's logout links and forms are hidden, those drawn later too`() {
        val script = BrowserPageScripts.HIDE_LOGOUT

        assertTrue(script, script.contains("a[href^=\"https://moodle2.ntust.edu.tw/login/logout.php\"]"))
        assertTrue(script, script.contains("a[href^=\"https://moodle2.ntust.edu.tw/auth/oidc/logout.php\"]"))
        assertTrue(script, script.contains("form[action^=\"https://moodle2.ntust.edu.tw/login/logout.php\"]"))
        assertTrue(script, script.contains("a[href^=\"/login/logout.php\"]"))
        assertTrue(script, script.contains("display: none !important"))
        assertTrue("a stylesheet, which also matches the menus Moodle draws later", script.contains("createElement('style')"))
        assertTrue("before the page has a head", script.contains("document.documentElement"))
    }

    @Test
    fun `only Moodle's pages lose their logout`() {
        assertEquals(setOf("https://moodle2.ntust.edu.tw"), BrowserPageScripts.HIDE_LOGOUT_ORIGINS)
    }

    @Test
    fun `a link that only names the logout in its query stays`() {
        // Matched by where a link starts, not by anything inside it.
        assertFalse(BrowserPageScripts.HIDE_LOGOUT, BrowserPageScripts.HIDE_LOGOUT.contains("*=\"logout.php\""))
    }
}
