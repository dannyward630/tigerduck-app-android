package org.ntust.app.tigerduck.network

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import java.util.concurrent.CopyOnWriteArrayList

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

    private val school = OkHttpClient.Builder().addInterceptor { chain -> answer(chain.request()) }.build()

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

    private fun TestScope.service(): MoodleTokenService {
        val latch = PersistedStoredPasswordLatch(latchPrefs, now = { 1_800_000_000_000L }) {
            val id = tokens.studentId
            val password = tokens.password
            if (id == null || password == null) null else id to password
        }
        return MoodleTokenService(
            clientFor = { school },
            tokenStore = tokens,
            passwordLatch = latch,
            scope = backgroundScope,
            now = { 1_800_000_000_000L },
            newCookieJar = { SignInCookieJar().also { jars += it } },
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

    private suspend fun expectFailure(type: Class<out Throwable>, block: suspend () -> Unit) {
        try {
            block()
            fail("expected ${type.simpleName}")
        } catch (e: Throwable) {
            if (!type.isInstance(e)) throw e
        }
    }

    // --- the fake school: SSO and Moodle, as OkHttp would see them after following redirects ---

    private fun answer(request: Request): Response {
        val url = request.url.toString()
        requests += url
        val jar = jars.last()
        val (landed, html, sets) = when {
            request.method == "POST" && request.url.host == SSO_HOST -> {
                posts += url
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
            url.startsWith("$MOODLE/auth/oidc/") -> Triple(SSO_LOGIN_URL, LOGIN_PAGE, emptyList())
            url == "$MOODLE/user/edit.php" -> {
                val sent = jar.loadForRequest(request.url).any { it.name == "MoodleSession" }
                if (sent && moodleHoldsSession) Triple(url, "<html><body>Edit profile</body></html>", emptyList())
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
