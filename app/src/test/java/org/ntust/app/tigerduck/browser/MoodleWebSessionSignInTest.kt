package org.ntust.app.tigerduck.browser

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the browser keeps of the app's own sign-in to Moodle's website, and when it keeps nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleWebSessionSignInTest {

    private val written = mutableListOf<Pair<String, String>>()
    private var flushes = 0
    private var runs = 0
    private var generation = 7
    private var usable = true
    private var demo = false

    private val moodle = "https://moodle2.ntust.edu.tw/".toHttpUrl()
    private val sso = "https://ssoam2.ntust.edu.tw/".toHttpUrl()

    private fun cookie(url: okhttp3.HttpUrl, header: String) = Cookie.parse(url, header)!!

    private val session = listOf(
        cookie(moodle, "MoodleSession=fake-session-000; Path=/; Secure; HttpOnly"),
        cookie(moodle, "MOODLEID1_=made-up; Path=/; Secure"),
    )

    private fun signIn(
        timeoutMs: Long = 15_000,
        run: suspend () -> List<Cookie> = { runs++; session },
    ) = MoodleWebSessionSignIn(
        run = run,
        isDemo = { demo },
        usable = { usable },
        generation = { generation },
        writeCookie = { url, value -> written += url to value },
        flush = { flushes++ },
        timeoutMs = timeoutMs,
    )

    @Test
    fun `Moodle's session goes into the browser, for Moodle alone, and is flushed`() = runTest {
        assertTrue(signIn().signIn())

        assertEquals(listOf("https://moodle2.ntust.edu.tw", "https://moodle2.ntust.edu.tw"), written.map { it.first })
        assertTrue(written[0].second.startsWith("MoodleSession=fake-session-000;"))
        assertTrue("secure", written[0].second.contains("secure"))
        assertTrue("httponly", written[0].second.contains("httponly"))
        assertTrue(written[1].second.startsWith("MOODLEID1_=made-up;"))
        assertEquals(1, flushes)
    }

    @Test
    fun `nothing of SSO's, nor any other cookie of Moodle's, goes into the browser`() = runTest {
        val handed = session + listOf(
            cookie(sso, ".AspNetCore.Identity.Application=sso-made-up; Path=/; Secure; HttpOnly"),
            cookie(moodle, "SomethingElse=x; Path=/"),
        )

        assertTrue(signIn { handed }.signIn())

        assertEquals(listOf("MoodleSession", "MOODLEID1_"), written.map { it.second.substringBefore('=') })
    }

    @Test
    fun `a run that hands back no Moodle session leaves the browser as it was`() = runTest {
        assertFalse(signIn { listOf(session[1]) }.signIn())

        assertTrue(written.isEmpty())
    }

    @Test
    fun `a sign-out or account switch during the run keeps nothing of it`() = runTest {
        assertFalse(signIn { generation++; session }.signIn())

        assertTrue(written.isEmpty())
        assertEquals(0, flushes)
    }

    @Test
    fun `a clearing owed during the run keeps nothing of it`() = runTest {
        assertFalse(signIn { usable = false; session }.signIn())

        assertTrue(written.isEmpty())
    }

    @Test
    fun `while a clearing is owed, and for the demo account, it never runs`() = runTest {
        usable = false
        assertFalse(signIn().signIn())
        usable = true
        demo = true
        assertFalse(signIn().signIn())

        assertEquals(0, runs)
        assertTrue(written.isEmpty())
    }

    @Test
    fun `a run that fails, or takes too long, leaves the browser as it was`() = runTest {
        assertFalse(signIn { error("SSO is down") }.signIn())
        assertFalse(signIn(timeoutMs = 15_000) { delay(20_000); session }.signIn())

        assertTrue(written.isEmpty())
    }
}
