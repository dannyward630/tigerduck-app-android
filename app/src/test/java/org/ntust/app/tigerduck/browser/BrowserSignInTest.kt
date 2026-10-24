package org.ntust.app.tigerduck.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moodle's sign-in provider: its site, its session, its sign-in page. */
class BrowserSignInTest {

    @Test
    fun `Moodle's provider covers https pages on the Moodle site only`() {
        assertTrue(MoodleBrowserSignIn.isMoodle("https://moodle2.ntust.edu.tw/user/edit.php"))
        assertFalse(MoodleBrowserSignIn.isMoodle("http://moodle2.ntust.edu.tw/user/edit.php"))
        assertFalse(MoodleBrowserSignIn.isMoodle("https://ssoam2.ntust.edu.tw/account/login"))
    }

    @Test
    fun `a Moodle session cookie with a value is a session`() {
        assertTrue(MoodleBrowserSignIn.hasMoodleSession("MoodleSession=abc123"))
        assertTrue(MoodleBrowserSignIn.hasMoodleSession("x=1; MoodleSessionmoodle2=abc; y=2"))
        assertFalse(MoodleBrowserSignIn.hasMoodleSession(null))
        assertFalse(MoodleBrowserSignIn.hasMoodleSession(""))
        assertFalse(MoodleBrowserSignIn.hasMoodleSession("MoodleSession= "))
        assertFalse(MoodleBrowserSignIn.hasMoodleSession("NotMoodleSession=abc"))
    }

    @Test
    fun `Moodle's login pages are its sign-in, its logout and other pages are not`() {
        assertTrue(MoodleBrowserSignIn.isMoodleSignInPage("https://moodle2.ntust.edu.tw/login/index.php"))
        assertTrue(MoodleBrowserSignIn.isMoodleSignInPage("https://Moodle2.ntust.edu.tw/login/index.php?loginredirect=1"))
        assertFalse(MoodleBrowserSignIn.isMoodleSignInPage("https://moodle2.ntust.edu.tw/login/logout.php?sesskey=x"))
        assertFalse(MoodleBrowserSignIn.isMoodleSignInPage("https://moodle2.ntust.edu.tw/user/edit.php"))
        assertFalse(MoodleBrowserSignIn.isMoodleSignInPage("https://moodle2.ntust.edu.tw/auth/oidc/?source=loginpage"))
        assertFalse(MoodleBrowserSignIn.isMoodleSignInPage("https://example.com/login/index.php"))
    }

    @Test
    fun `the web sign-in goes through Moodle's OIDC entry, never its password form`() {
        assertEquals("https://moodle2.ntust.edu.tw/auth/oidc/?source=loginpage", MoodleBrowserSignIn.WEB_SIGN_IN_URL)
        assertFalse(MoodleBrowserSignIn.isMoodleSignInPage(MoodleBrowserSignIn.WEB_SIGN_IN_URL))
    }

    @Test
    fun `the providers are asked in order and the first that covers a page answers`() {
        val moodle = object : BrowserSignIn by stub("Moodle", "moodle2") {}
        val school = object : BrowserSignIn by stub("School", "ntust") {}
        val signIns = BrowserSignIns(listOf(moodle, school))

        assertSame(moodle, signIns.forUrl("https://moodle2.ntust.edu.tw/"))
        assertSame(school, signIns.forUrl("https://stuinfosys.ntust.edu.tw/"))
        assertNull(signIns.forUrl("https://example.com/"))
    }

    private fun stub(name: String, hostPart: String) = object : BrowserSignIn {
        override val siteName = name
        override fun handles(url: String) = url.contains(hostPart)
        override fun hasSession(cookieHeader: String?) = false
        override fun isSignInPage(url: String) = false
        override suspend fun signedInUrl(target: String): String? = null
        override fun msUntilRetry() = 0L
        override fun webSignInUrl(target: String): String? = null
    }
}
