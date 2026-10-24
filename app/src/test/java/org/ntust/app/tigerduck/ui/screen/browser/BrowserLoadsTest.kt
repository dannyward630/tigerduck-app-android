package org.ntust.app.tigerduck.ui.screen.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The page Retry and "Open in browser" name, the error over the page and the page a sign-in goes
 * on to, as the WebView reports a page's loads. A failed load's error comes before its page starts
 * on some WebViews and after it on others.
 */
class BrowserLoadsTest {

    private val state = BrowserPageState()
    private val loads = BrowserLoads(state)

    private val unreachable = BrowserError.Unreachable("moodle2.ntust.edu.tw")

    @Test
    fun `an error that comes before its page starts stays up as that page starts`() {
        loads.failed(B, unreachable)
        loads.started(B, ofSigningInSite = true)
        loads.finished(B)

        assertEquals(unreachable, state.error)
        assertEquals(B, loads.loadingUrl)
    }

    @Test
    fun `an error that comes after its page started stays up`() {
        loads.started(B, ofSigningInSite = true)
        loads.failed(B, unreachable)
        loads.finished(B)

        assertEquals(unreachable, state.error)
    }

    @Test
    fun `another page starting takes the error down, whichever side of its start the error came`() {
        loads.failed(B, unreachable)
        loads.started(B, ofSigningInSite = true)
        loads.finished(B)
        // Forward, say: no navigation the browser was asked about.
        loads.started(C, ofSigningInSite = true)

        assertNull(state.error)
        assertEquals(C, loads.loadingUrl)

        loads.finished(C)
        loads.started(B, ofSigningInSite = true)
        loads.failed(B, unreachable)
        loads.finished(B)
        loads.started(C, ofSigningInSite = true)

        assertNull(state.error)
    }

    @Test
    fun `the failed page starting again, once its error page has, takes the error down`() {
        loads.failed(B, unreachable)
        loads.started(B, ofSigningInSite = true)
        loads.finished(B)

        loads.started(B, ofSigningInSite = true)

        assertNull(state.error)

        // The error after the start, this time.
        loads.failed(B, unreachable)
        loads.finished(B)
        loads.started(B, ofSigningInSite = true)

        assertNull(state.error)
    }

    @Test
    fun `a failed load the browser was not asked about is what Retry names`() {
        loads.requested(A)
        loads.started(A, ofSigningInSite = true)
        loads.finished(A)

        // A form's POST: no shouldOverrideUrlLoading, and the error before the page starts.
        loads.failed(B, unreachable)

        assertEquals(B, loads.loadingUrl)
    }

    @Test
    fun `a page that starts without the browser asked about it is what Retry names`() {
        loads.requested(A)
        loads.started(A, ofSigningInSite = true)
        loads.finished(A)

        loads.started(B, ofSigningInSite = true)

        assertEquals(B, loads.loadingUrl)
    }

    @Test
    fun `a sign-in goes on to its page until a page of the site shows`() {
        loads.signInTarget = A
        // The signed-in link, which Moodle bounces back to its sign-in page.
        loads.requested(SIGNED_IN)

        assertEquals(A, loads.signInReturn(SIGN_IN_PAGE))
        // The school's SSO, which is not the site's.
        loads.started(SSO, ofSigningInSite = false)
        assertEquals(A, loads.signInReturn(SIGN_IN_PAGE))
    }

    @Test
    fun `once a page of the site shows, a later sign-in goes on to the page it came in the way of`() {
        loads.signInTarget = A
        loads.requested(SIGNED_IN)
        loads.started(A, ofSigningInSite = true)
        loads.finished(A)

        // The student opens another page, and Moodle has signed the browser out meanwhile.
        loads.requested(C)

        assertNull(loads.signInTarget)
        assertEquals(C, loads.signInReturn(SIGN_IN_PAGE))
    }

    private companion object {
        const val A = "https://moodle2.ntust.edu.tw/user/edit.php"
        const val B = "https://moodle2.ntust.edu.tw/course/view.php?id=1"
        const val C = "https://moodle2.ntust.edu.tw/course/view.php?id=2"
        const val SIGN_IN_PAGE = "https://moodle2.ntust.edu.tw/login/index.php"
        const val SIGNED_IN = "https://moodle2.ntust.edu.tw/admin/tool/mobile/autologin.php?userid=1&key=k&urltogo=x"
        const val SSO = "https://ssoam2.ntust.edu.tw/nidp/app/login"
    }
}
