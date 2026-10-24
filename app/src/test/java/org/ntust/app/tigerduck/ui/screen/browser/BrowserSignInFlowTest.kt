package org.ntust.app.tigerduck.ui.screen.browser

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ntust.app.tigerduck.browser.BrowserSignIn
import org.ntust.app.tigerduck.browser.BrowserSignIns
import org.ntust.app.tigerduck.browser.MoodleBrowserSignIn

/** The sign-in screen: when it signs in by itself, and which page a sign-in goes on to. */
@OptIn(ExperimentalCoroutinesApi::class)
class BrowserSignInFlowTest {

    private val state = BrowserPageState()
    private lateinit var loads: BrowserLoads
    private val loaded = mutableListOf<String>()
    private var stops = 0

    /** A site whose links can be had every [intervalMs], from [firstDueAtMs] on; [works] says whether one comes. */
    private inner class Site(
        var firstDueAtMs: Long = 0,
        val intervalMs: Long = 360_000,
        var works: Boolean = true,
        /** How long a link takes to come. */
        val takesMs: Long = 0,
    ) : BrowserSignIn {
        lateinit var clock: () -> Long
        val asked = mutableListOf<String>()
        private var dueAt = firstDueAtMs

        override val siteName = "Moodle"
        override fun handles(url: String) = url.startsWith("https://moodle2.ntust.edu.tw/")
        override fun hasSession(cookieHeader: String?) = false
        override fun isSignInPage(url: String) = url.startsWith(SIGN_IN_PAGE)
        override fun isReturnPage(url: String) = MoodleBrowserSignIn.isMoodleReturnPage(url)
        override fun isSignInLink(url: String) = url.startsWith(SIGNED_IN)
        override suspend fun signedInUrl(target: String): String? {
            asked += target
            delay(takesMs)
            if (msUntilRetry() > 0 || !works) return null
            dueAt = clock() + intervalMs
            return "$SIGNED_IN$target"
        }
        override fun msUntilRetry() = (dueAt - clock()).coerceAtLeast(0)
        override fun webSignInUrl(target: String) = WEB_SIGN_IN
    }

    private fun TestScope.flow(site: Site): BrowserSignInFlow {
        site.clock = { testScheduler.currentTime }
        val signIns = BrowserSignIns(listOf(site))
        loads = BrowserLoads(state, signIns)
        return BrowserSignInFlow(state, loads, signIns, backgroundScope, load = { url ->
            // As the activity's load: the browser asks the page to go.
            loads.requested(url)
            loaded += url
        }) { stops++ }
    }

    @Test
    fun `the site's sign-in page with a link due signs in at once, back to the page it came in the way of`() = runTest {
        val site = Site()
        val flow = flow(site)
        loads.requested(PAGE)

        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        assertEquals(1, stops)
        assertEquals(listOf("$SIGNED_IN$PAGE"), loaded)
        assertNull(state.signIn)
    }

    @Test
    fun `with no link due, the screen counts down and signs in once one is`() = runTest {
        val site = Site(firstDueAtMs = 3_000)
        val flow = flow(site)
        loads.requested(PAGE)

        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        assertEquals(emptyList<String>(), loaded)
        assertTrue(state.signIn!!.msLeft > 0)

        advanceTimeBy(3_600)
        runCurrent()

        assertEquals(listOf("$SIGNED_IN$PAGE"), loaded)
    }

    @Test
    fun `a sign-in that fails leaves the screen up and does not keep trying by itself`() = runTest {
        // A link due that never comes: an account with no Moodle private token, say.
        val site = Site(works = false)
        val flow = flow(site)
        loads.requested(PAGE)

        flow.signInPageReached(site, SIGN_IN_PAGE)
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf(PAGE), site.asked)
        assertEquals(true, state.signIn?.failed)
        assertEquals(emptyList<String>(), loaded)
    }

    @Test
    fun `a site that bounces its link back to its sign-in page counts down rather than loop`() = runTest {
        val site = Site()
        val flow = flow(site)
        flow.signIn(site, PAGE)
        runCurrent()

        // Moodle did not take the link, which the WebView may report as a page start first.
        loads.started(loaded.last())
        loads.finished(loaded.last())
        flow.signInPageReached(site, SIGN_IN_PAGE)
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf(PAGE), site.asked)
        assertEquals(PAGE, state.signIn?.target)
        assertTrue(state.signIn!!.msLeft > 0)
    }

    @Test
    fun `the sign-in page reported twice while a link is on its way asks for one link`() = runTest {
        val site = Site(takesMs = 1_000)
        val flow = flow(site)
        loads.requested(PAGE)

        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()
        // The same navigation, through the page's start as well.
        flow.signInPageReached(site, SIGN_IN_PAGE)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(listOf(PAGE), site.asked)
        assertEquals(listOf("$SIGNED_IN$PAGE"), loaded)
    }

    @Test
    fun `once signed in, a later sign-in goes on to the page it came in the way of, not the first`() = runTest {
        val site = Site(firstDueAtMs = 0, intervalMs = 1_000)
        val flow = flow(site)
        flow.signIn(site, PAGE)
        runCurrent()
        loads.started(PAGE)
        loads.finished(PAGE)

        // Later the student opens another page, and Moodle has signed the browser out meanwhile.
        advanceTimeBy(5_000)
        loads.requested(OTHER_PAGE)
        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        assertEquals(listOf(PAGE, OTHER_PAGE), site.asked)
        assertEquals("$SIGNED_IN$OTHER_PAGE", loaded.last())
    }

    @Test
    fun `the web sign-in opens the page first, so Moodle keeps it, then goes on from Moodle's sign-in page`() = runTest {
        // No link to be had: the student signs in on the web page.
        val site = Site(works = false)
        val flow = flow(site)
        flow.signIn(site, PAGE)
        runCurrent()

        flow.signInOnWeb()
        runCurrent()
        assertEquals(listOf(PAGE), loaded)
        assertNull(state.signIn)

        // Moodle, signed out, keeps the page and sends the browser to its sign-in page.
        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        assertEquals(listOf(PAGE, WEB_SIGN_IN), loaded)
        assertNull(state.signIn)
        assertEquals("no link asked for on the way", listOf(PAGE), site.asked)

        // The web sign-in's own entry does not end the sign-in.
        loads.started(WEB_SIGN_IN)
        assertEquals(PAGE, loads.signInTarget)
    }

    @Test
    fun `a web sign-in whose page opens as it is ends there`() = runTest {
        val site = Site(works = false)
        val flow = flow(site)
        flow.signIn(site, PAGE)
        runCurrent()
        flow.signInOnWeb()
        runCurrent()
        // Signed in after all.
        loads.started(PAGE)
        loads.finished(PAGE)

        // Signed out again later: the screen as ever, not the web sign-in.
        site.works = true
        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        assertEquals("$SIGNED_IN$PAGE", loaded.last())
    }

    @Test
    fun `leaving the sign-in while its link is on its way loads nothing and forgets it`() = runTest {
        val site = Site(takesMs = 1_000)
        val flow = flow(site)
        loads.requested(PAGE)
        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        // Back, to the page before.
        flow.leave()
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(emptyList<String>(), loaded)
        assertNull(state.signIn)
        assertNull(loads.signInTarget)
    }

    @Test
    fun `leaving the countdown stops it`() = runTest {
        val site = Site(firstDueAtMs = 3_000)
        val flow = flow(site)
        loads.requested(PAGE)
        flow.signInPageReached(site, SIGN_IN_PAGE)
        runCurrent()

        flow.leave()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(emptyList<String>(), site.asked)
        assertNull(state.signIn)
    }

    @Test
    fun `cancelling stops the countdown, also one a try that failed at once started`() = runTest(UnconfinedTestDispatcher()) {
        // As the activity's Main.immediate: a launch runs at once, up to its first wait.
        val site = Site(firstDueAtMs = 3_000)
        val flow = flow(site)

        flow.signIn(site, PAGE)
        flow.cancel()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf(PAGE), site.asked)
    }

    private companion object {
        const val PAGE = "https://moodle2.ntust.edu.tw/user/edit.php"
        const val OTHER_PAGE = "https://moodle2.ntust.edu.tw/course/view.php?id=2"
        const val SIGN_IN_PAGE = "https://moodle2.ntust.edu.tw/login/index.php"
        const val SIGNED_IN = "https://moodle2.ntust.edu.tw/admin/tool/mobile/autologin.php?key=k&urltogo="
        const val WEB_SIGN_IN = "https://moodle2.ntust.edu.tw/auth/oidc/?source=loginpage"
    }
}
