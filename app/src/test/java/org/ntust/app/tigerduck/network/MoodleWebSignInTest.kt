package org.ntust.app.tigerduck.network

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.auth.PersistedStoredPasswordLatch
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [MoodleTokenService]'s sign-in to Moodle's website, for the TigerDuck browser when no autologin
 * key can be had, against hand-made SSO and Moodle pages. OkHttp's own cookie handling is played
 * here by the fake school, which keeps what it sets in the run's own jar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MoodleWebSignInTest {

    private lateinit var latchPrefs: SharedPreferences
    private val tokens = Tokens()
    private val posts = CopyOnWriteArrayList<String>()
    private val requests = CopyOnWriteArrayList<String>()

    /** The jars the service made, one per run. */
    private val jars = mutableListOf<SignInCookieJar>()

    /** What SSO answers the password with. */
    private var ssoAnswer: () -> Pair<String, String> = { BRIDGE_URL to BRIDGE_PAGE }

    /** Whether Moodle keeps the session it set. */
    private var moodleHoldsSession = true

    /** Runs as each SSO sign-in arrives, before its answer. */
    private var onPost: () -> Unit = {}

    /** Runs as Moodle's OIDC entry is asked for, before its answer. */
    private var onEntry: () -> Unit = {}

    /** Whether SSO holds the next password post until the call is cancelled, as a server that never answers. */
    private var ssoHangs = false

    private val school = OkHttpClient.Builder().addInterceptor { chain -> answer(chain) }.build()

    /** The time the latch and the pause read. */
    private var clock = 1_800_000_000_000L

    /** What Moodle's profile editor answers a signed-in request with. */
    private var editorPage = EDITOR_PAGE

    private class Tokens : MoodleTokenPairStore {
        override var token: String? = "made-up-wstoken"
        override var privateToken: String? = null
        override var studentId: String? = STUDENT
        override var password: String? = PASSWORD

        override fun storeIfSignedIn(studentId: String, token: String, privateToken: String?): Boolean {
            if (this.studentId != studentId) return false
            this.token = token
            this.privateToken = privateToken
            return true
        }
    }

    @Before
    fun setUp() {
        latchPrefs = RuntimeEnvironment.getApplication().getSharedPreferences("moodle-web-latch-test", Context.MODE_PRIVATE)
        latchPrefs.edit().clear().commit()
    }

    /** The latch the last service was made with, as a fresh process reads it from disk. */
    private lateinit var latch: PersistedStoredPasswordLatch

    private fun TestScope.service(websiteDeadlineMs: Long = 15_000): MoodleTokenService {
        latch = PersistedStoredPasswordLatch(latchPrefs, now = { clock }) {
            val id = tokens.studentId
            val password = tokens.password
            if (id == null || password == null) null else id to password
        }
        return MoodleTokenService(
            clientFor = { school },
            tokenStore = tokens,
            passwordLatch = latch,
            scope = backgroundScope,
            newCookieJar = { SignInCookieJar().also { jars += it } },
            websiteDeadlineMs = websiteDeadlineMs,
            now = { clock },
        )
    }

    @Test
    fun `it posts the stored password once and hands back Moodle's session, nothing of SSO's`() = runTest(UnconfinedTestDispatcher()) {
        val cookies = service().signInToWebsite()

        assertEquals(listOf("MoodleSession", "MOODLEID1_"), cookies.map { it.name })
        assertTrue(cookies.all { it.domain == MOODLE_HOST })
        assertEquals(listOf(SSO_POST_URL), posts)
        // Signed in, checked on a page only a signed-in student gets.
        assertEquals("$MOODLE/user/edit.php", requests.last())
    }

    @Test
    fun `each run keeps its cookies in a jar of its own`() = runTest(UnconfinedTestDispatcher()) {
        val service = service()
        service.signInToWebsite()
        service.signInToWebsite()

        assertEquals(2, jars.size)
        assertTrue(jars[0] !== jars[1])
    }

    @Test
    fun `a refused password is latched, and the next run posts nothing`() = runTest(UnconfinedTestDispatcher()) {
        ssoAnswer = { SSO_LOGIN_URL to LOGIN_PAGE_REFUSED }
        val service = service()

        expectFailure(MoodleWebserviceError.SsoLoginRejected::class.java) { service.signInToWebsite() }
        expectFailure(MoodleWebserviceError.SsoLoginRejected::class.java) { service.signInToWebsite() }

        assertEquals(1, posts.size)
    }

    @Test
    fun `a second factor after the password is a failure, never a session`() = runTest(UnconfinedTestDispatcher()) {
        ssoAnswer = { "https://ssoam2.ntust.edu.tw/account/mfa" to MFA_PAGE }

        expectFailure(MoodleWebserviceError.MalformedResponse::class.java) { service().signInToWebsite() }
    }

    @Test
    fun `a session Moodle does not hold is a failure`() = runTest(UnconfinedTestDispatcher()) {
        moodleHoldsSession = false

        expectFailure(MoodleWebserviceError.MalformedResponse::class.java) { service().signInToWebsite() }
    }

    @Test
    fun `a failure that is not the network's pauses the next run`() = runTest(UnconfinedTestDispatcher()) {
        moodleHoldsSession = false
        val service = service()
        expectFailure(MoodleWebserviceError.MalformedResponse::class.java) { service.signInToWebsite() }
        moodleHoldsSession = true

        expectFailure(MoodleWebserviceError.MalformedResponse::class.java) { service.signInToWebsite() }

        assertEquals("paused: the password went out once", 1, posts.size)
    }

    @Test
    fun `a sign-out during the run keeps nothing of it`() = runTest(UnconfinedTestDispatcher()) {
        onPost = { tokens.studentId = null }

        expectFailure(MoodleWebserviceError.MissingStoredCredentials::class.java) { service().signInToWebsite() }
    }

    @Test
    fun `with no account stored nothing is posted`() = runTest(UnconfinedTestDispatcher()) {
        tokens.studentId = null

        expectFailure(MoodleWebserviceError.MissingStoredCredentials::class.java) { service().signInToWebsite() }
        assertTrue(posts.isEmpty())
    }

    @Test
    fun `a refused password is not posted again for the website, whatever the app-wide wait allows`() = runTest(UnconfinedTestDispatcher()) {
        ssoAnswer = { SSO_LOGIN_URL to LOGIN_PAGE_REFUSED }
        val service = service()
        expectFailure(MoodleWebserviceError.SsoLoginRejected::class.java) { service.signInToWebsite() }

        // The app-wide count lets a first refusal be tried again after a short wait; the website does not,
        // in this process or the next.
        clock += 60 * 60_000L
        ssoAnswer = { BRIDGE_URL to BRIDGE_PAGE }
        expectFailure(MoodleWebserviceError.SsoLoginRejected::class.java) { service.signInToWebsite() }
        expectFailure(MoodleWebserviceError.SsoLoginRejected::class.java) { service().signInToWebsite() }
        assertEquals(1, posts.size)

        // A sign-in by hand, which clears the count, lets it post again.
        latch.clear()
        service().signInToWebsite()
        assertEquals(2, posts.size)
    }

    @Test
    fun `a page at the editor's address that is not the editor is a failure, session cookie or not`() = runTest(UnconfinedTestDispatcher()) {
        // A challenge the school's gateway serves in place of the page, say.
        editorPage = "<html><body><h1>Checking your browser</h1></body></html>"

        expectFailure(MoodleWebserviceError.MalformedResponse::class.java) { service().signInToWebsite() }
    }

    @Test
    fun `a refusal another sign-in counts while a run is on its way stops its post, however short the wait`() = runTest(UnconfinedTestDispatcher()) {
        val service = service()
        onEntry = {
            latch.markRejected(latch.generation, STUDENT, PASSWORD, "Wrong account or password")
            // Past the count's first short wait by the time the form is posted.
            clock += 60_000L
        }

        expectFailure(Exception::class.java) { service.signInToWebsite() }

        assertTrue(posts.isEmpty())
        assertEquals("nothing posted, nothing more counted", 1, latch.refusals.value)
    }

    @Test
    fun `a website sign-in SSO never answers lets go at its deadline, and a renewal after it goes on`() = runTest(UnconfinedTestDispatcher()) {
        // The deadline is on the clock; a short one keeps the test quick.
        val service = service(websiteDeadlineMs = 500)
        ssoHangs = true

        val website = async { runCatching { service.signInToWebsite() } }
        val failed = onTheClock { withTimeout(10_000) { website.await() } }
        assertTrue("the website sign-in failed at its deadline", failed.exceptionOrNull() is MoodleWebserviceError.TransientNetwork)

        // The renewal that waited behind it is not held past the deadline.
        ssoAnswer = { "$MOODLE/admin/tool/mobile/launch.php?confirmed=1" to tokenPage() }
        assertEquals("made-up-renewed-wstoken", onTheClock { withTimeout(10_000) { service.renewToken("made-up-wstoken") } })
    }

    /** [block] timed on the clock rather than the test's virtual time, as the run is. */
    private suspend fun <T> onTheClock(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1), block)

    private suspend fun expectFailure(type: Class<out Throwable>, block: suspend () -> Unit) {
        try {
            block()
            fail("expected ${type.simpleName}")
        } catch (e: Throwable) {
            if (!type.isInstance(e)) throw e
        }
    }

    // --- the fake school: SSO and Moodle, as OkHttp would see them after following redirects ---

    private fun answer(chain: okhttp3.Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        requests += url
        val jar = jars.last()
        val (landed, html, sets) = when {
            request.method == "POST" && request.url.host == SSO_HOST -> {
                posts += url
                if (ssoHangs) {
                    ssoHangs = false
                    // As OkHttp's own: a cancelled call gives up on its read.
                    while (!chain.call().isCanceled()) Thread.sleep(5)
                    throw java.io.IOException("Canceled")
                }
                onPost()
                val (at, page) = ssoAnswer()
                Triple(at, page, listOf("$SSO_COOKIE=sso-made-up; Path=/; Secure; HttpOnly"))
            }
            request.method == "POST" && url.startsWith("$MOODLE/auth/oidc/") ->
                Triple(
                    "$MOODLE/my/",
                    "<html><body>Dashboard</body></html>",
                    listOf("MoodleSession=fake-session-000; Path=/; Secure; HttpOnly", "MOODLEID1_=made-up; Path=/; Secure"),
                )
            // A token renewal's launch, which lands on the sign-in form as the website's entry does.
            url.startsWith("$MOODLE/admin/tool/mobile/launch.php") -> Triple(SSO_LOGIN_URL, LOGIN_PAGE, emptyList())
            url.startsWith("$MOODLE/auth/oidc/") -> {
                onEntry()
                Triple(SSO_LOGIN_URL, LOGIN_PAGE, emptyList())
            }
            url == "$MOODLE/user/edit.php" -> {
                val sent = jar.loadForRequest(request.url).any { it.name == "MoodleSession" }
                if (sent && moodleHoldsSession) Triple(url, editorPage, emptyList())
                else Triple(SSO_LOGIN_URL, LOGIN_PAGE, emptyList())
            }
            else -> error("unexpected $url")
        }
        val landedUrl = landed.toHttpUrl()
        jar.saveFromResponse(landedUrl, sets.mapNotNull { Cookie.parse(landedUrl, it) })
        return Response.Builder()
            .request(request.newBuilder().url(landedUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()
    }

    private companion object {
        const val STUDENT = "B00015999"
        const val PASSWORD = "made-up-password"
        const val MOODLE_HOST = "moodle2.ntust.edu.tw"
        const val MOODLE = "https://$MOODLE_HOST"
        const val SSO_HOST = "ssoam2.ntust.edu.tw"
        const val SSO_COOKIE = ".AspNetCore.Identity.Application"
        const val SSO_LOGIN_URL = "https://$SSO_HOST/account/login?ReturnUrl=%2Fconnect"
        const val SSO_POST_URL = "https://$SSO_HOST/account/login"
        const val BRIDGE_URL = "https://$SSO_HOST/connect/authorize/callback"

        val LOGIN_PAGE = """
            <html><body>
            <form action="/account/login" method="post">
              <input type="hidden" name="__RequestVerificationToken" value="made-up-antiforgery" />
              <input type="hidden" name="ClientId" value="moodle" />
              <input type="hidden" name="ReturnUrl" value="/connect" />
              <input type="hidden" name="Uri" value="" />
              <input type="text" name="Username" />
              <input type="password" name="Password" />
            </form>
            </body></html>
        """.trimIndent()

        val LOGIN_PAGE_REFUSED = LOGIN_PAGE.replace(
            "<form",
            """<div class="validation-summary-errors"><ul><li>Wrong account or password</li></ul></div><form""",
        )

        val BRIDGE_PAGE = """
            <html><body onload="document.forms[0].submit()">
            <form method="post" action="$MOODLE/auth/oidc/">
              <input type="hidden" name="code" value="made-up-code" />
              <input type="hidden" name="state" value="made-up-state" />
              <input type="hidden" name="iss" value="https://$SSO_HOST" />
            </form>
            </body></html>
        """.trimIndent()

        /** Moodle's profile editor as it opens signed in: its body, and its form with the session key. */
        val EDITOR_PAGE = """
            <html><body id="page-user-edit" class="format-site path-user loggedin">
            <form autocomplete="off" action="$MOODLE/user/edit.php" method="post" accept-charset="utf-8">
              <input name="sesskey" type="hidden" value="made-up-sesskey" />
            </form>
            </body></html>
        """.trimIndent()

        fun tokenPage(): String {
            val pair = "signature:::made-up-renewed-wstoken:::made-up-private"
            val payload = android.util.Base64.encodeToString(pair.toByteArray(), android.util.Base64.NO_WRAP)
            return """<html><body><a href="moodlemobile://token=$payload">Open</a></body></html>"""
        }

        val MFA_PAGE = """
            <html><body>
            <form action="/account/mfa" method="post">
              <input type="hidden" name="__RequestVerificationToken" value="made-up-antiforgery" />
              <input type="text" name="Code" />
            </form>
            </body></html>
        """.trimIndent()
    }
}
