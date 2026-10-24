package org.ntust.app.tigerduck.browser

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.ntust.app.tigerduck.network.moodle.MoodleAutologin
import org.ntust.app.tigerduck.network.moodle.MoodleWebservice

/**
 * How the TigerDuck browser signs in to one site, so the browser screen knows
 * nothing of any site itself. Moodle's ([MoodleBrowserSignIn]) is the only one
 * now; the school's SSO sites would add theirs to [BrowserSignIns].
 *
 * A provider never copies the app's own cookies into the browser and never
 * fills a password into a page: it either hands the browser a link the site
 * signs it in with, or leaves the student to sign in on the page themselves.
 */
interface BrowserSignIn {
    /** The site's name as the sign-in screen shows it. */
    val siteName: String

    /** Whether [url] is on this provider's site. */
    fun handles(url: String): Boolean

    /** Whether the browser's [cookieHeader] for the site (`CookieManager.getCookie`) holds a session. */
    fun hasSession(cookieHeader: String?): Boolean

    /**
     * Whether a main-frame navigation to [url] means the browser is signed
     * out: the site's own sign-in page, which the browser replaces with its
     * sign-in screen rather than show.
     */
    fun isSignInPage(url: String): Boolean

    /**
     * Whether a sign-in can go on to [url]: a page of the site, not one of the pages it signs in
     * through (its sign-in page, the links [signedInUrl] gives).
     */
    fun isReturnPage(url: String): Boolean

    /**
     * Whether [url] is one of the links [signedInUrl] gives, which sign in once and only from this
     * phone: never shown, handed on or loaded again.
     */
    fun isSignInLink(url: String): Boolean

    /** A link that signs the browser in and goes on to [target], or null when none can be had now. */
    suspend fun signedInUrl(target: String): String?

    /** How long until [signedInUrl] can give a link again, 0 when it may now. */
    fun msUntilRetry(): Long

    /** Where "Sign in on the web page" goes for [target], where the student signs in themselves; null for none. */
    fun webSignInUrl(target: String): String?
}

/**
 * Moodle's sign-in: Moodle's own one-time autologin link ([MoodleAutologin]),
 * which signs the browser in under Moodle's session cookie; the browser keeps
 * that cookie, so later opens need no new key.
 *
 * Moodle's `/login/` page is never shown: it offers the local password form,
 * which counts each failure and locks the account after about ten (see
 * `MoodleTokenService`). The sign-in screen goes to the school's SSO instead,
 * through Moodle's OIDC entry, which comes back to the page Moodle kept.
 */
class MoodleBrowserSignIn(private val autologin: MoodleAutologin) : BrowserSignIn {
    override val siteName: String = "Moodle"

    override fun handles(url: String): Boolean = isMoodle(url)

    override fun hasSession(cookieHeader: String?): Boolean = hasMoodleSession(cookieHeader)

    override fun isSignInPage(url: String): Boolean = isMoodleSignInPage(url)

    override fun isReturnPage(url: String): Boolean = isMoodleReturnPage(url)

    override fun isSignInLink(url: String): Boolean = isMoodleSignInLink(url)

    override suspend fun signedInUrl(target: String): String? = autologin.signedIn(target)

    override fun msUntilRetry(): Long = autologin.msUntilKeyDue()

    override fun webSignInUrl(target: String): String = WEB_SIGN_IN_URL

    companion object {
        /** Moodle's OIDC entry, the school SSO's sign-in for Moodle. */
        const val WEB_SIGN_IN_URL = "${MoodleWebservice.SITE}/auth/oidc/?source=loginpage"

        // Moodle names its session cookie MoodleSession, with the site's suffix if it sets one.
        private val SESSION_COOKIE = Regex("""(?:^|;\s*)MoodleSession\w*=([^;]+)""")

        /** The Moodle pages a sign-in can go on to: not its sign-in entries under `/auth/`, the web sign-in's among them. */
        fun isMoodleReturnPage(url: String): Boolean =
            MoodleAutologin.isSignInTarget(url) && url.toHttpUrlOrNull()?.encodedPath?.lowercase()?.startsWith("/auth/") != true

        /** Moodle's one-time sign-in links: its app pages, autologin.php among them. */
        fun isMoodleSignInLink(url: String): Boolean =
            isMoodle(url) && url.toHttpUrlOrNull()?.encodedPath?.lowercase()?.startsWith("/admin/tool/mobile/") == true

        fun isMoodle(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return parsed.scheme == "https" && parsed.host.equals(MoodleWebservice.SITE_HOST, ignoreCase = true)
        }

        fun hasMoodleSession(cookieHeader: String?): Boolean =
            cookieHeader != null && SESSION_COOKIE.findAll(cookieHeader).any { it.groupValues[1].isNotBlank() }

        /** Moodle's `/login/` pages: the sign-in form and what it posts to. Its logout is under `/login/` too. */
        fun isMoodleSignInPage(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            if (!parsed.host.equals(MoodleWebservice.SITE_HOST, ignoreCase = true)) return false
            val path = parsed.encodedPath.lowercase()
            return path.startsWith("/login/") && !path.startsWith("/login/logout.php")
        }
    }
}

/**
 * The sign-in screen's wait: shows [msUntilRetry] every half second while
 * the screen is up ([stillShowing]), and tries again ([retry]) once a wait
 * that was counting reaches nothing. A key that comes back after the screen
 * gave up on it starts Moodle's wait late; watching on catches that and
 * counts it down too, rather than leave the screen with nothing to wait for.
 */
suspend fun watchSignInRetry(
    msUntilRetry: () -> Long,
    show: (Long) -> Unit,
    retry: suspend () -> Unit,
    stillShowing: () -> Boolean,
    tickMs: Long = 500,
) {
    var wasWaiting = msUntilRetry() > 0
    while (stillShowing()) {
        val left = msUntilRetry()
        show(left)
        if (wasWaiting && left == 0L) {
            retry()
            return
        }
        wasWaiting = left > 0
        kotlinx.coroutines.delay(tickMs)
    }
}

/** The providers the browser asks, first match wins. */
class BrowserSignIns(private val providers: List<BrowserSignIn>) {
    fun forUrl(url: String): BrowserSignIn? = providers.firstOrNull { it.handles(url) }
}
