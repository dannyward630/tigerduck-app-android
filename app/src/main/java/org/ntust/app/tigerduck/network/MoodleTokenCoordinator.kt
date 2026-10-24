package org.ntust.app.tigerduck.network

import kotlinx.coroutines.CoroutineScope
import okhttp3.Cookie
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ntust.app.tigerduck.auth.PasswordPosting
import org.ntust.app.tigerduck.auth.StoredPasswordLatch
import org.ntust.app.tigerduck.auth.guardAutomaticHarvest

/**
 * When [MoodleTokenService] signs in to SSO for a token, and where it keeps it. Apart from the
 * service's SSO flow so a test can run it: [login] is that flow, a student ID and password in, a
 * wstoken out. It posts the password, and reads SSO's answer to it, through the
 * [PasswordPosting] it is handed.
 *
 * A renewal signs in with the stored password the user did not just type, so it runs under
 * [passwordLatch]: none at all while a refusal of that password waits, a check again right before
 * the password goes out under the lock every automatic post takes, and a refusal counted for the
 * whole app. The sign-in the user makes ([obtain]) posts what they typed.
 *
 * One sign-in at a time: the sign-in a user makes, a renewal and a sign-in to Moodle's website
 * ([website]) all take [mutex]. A renewal goes through [MoodleTokenRenewal], so it keeps its pause
 * and its wait after a refused password, and checks the stored token again once it holds the
 * lock: one that waited behind a sign-in takes the token that sign-in got rather than signing in
 * a second time. A sign-in to the website keeps the same rules, through its own
 * [MoodleSignInGate], under the same latch.
 */
internal class MoodleTokenCoordinator(
    private val tokens: Tokens,
    private val login: suspend (
        studentId: String,
        password: String,
        posting: PasswordPosting,
    ) -> String,
    scope: CoroutineScope,
    now: () -> Long,
    pauseMs: Long,
    private val passwordLatch: StoredPasswordLatch,
    /**
     * Signs in to Moodle's website, a student ID and password in, the session's cookies out,
     * posting the password through the [PasswordPosting] it is handed.
     */
    private val websiteLogin: suspend (studentId: String, password: String, posting: PasswordPosting) -> List<Cookie> =
        { _, _, _ -> throw MoodleWebserviceError.MalformedResponse("no website sign-in") },
) {
    /** Where the token and the account it is harvested with are stored. */
    interface Tokens {
        var token: String?
        val studentId: String?
        val password: String?

        /**
         * Stores [token] only while [studentId] is still the stored account, as one step with a
         * sign-out's clear, so a sign-out can never land between the check and the store.
         */
        fun storeIfSignedIn(studentId: String, token: String): Boolean
    }

    private val mutex = Mutex()

    private val renewal = MoodleTokenRenewal(
        storedToken = { tokens.token },
        credentials = {
            val id = tokens.studentId
            val password = tokens.password
            if (id == null || password == null) null else id to password
        },
        harvest = { rejected ->
            mutex.withLock {
                val stored = tokens.token
                if (!stored.isNullOrEmpty() && stored != rejected) stored else harvestUnlocked()
            }
        },
        now = now,
        scope = scope,
        pauseMs = pauseMs,
        refusal = LatchedRefusal(passwordLatch),
    )

    /** A token in place of [rejected]; see [MoodleTokenRenewal]. */
    suspend fun renew(rejected: String?): String = renewal.renew(rejected)

    private val websiteGate = MoodleSignInGate<List<Cookie>>(
        // A session is the browser's to keep: every caller here has none.
        kept = { null },
        credentials = {
            val id = tokens.studentId
            val password = tokens.password
            if (id == null || password == null) null else id to password
        },
        signIn = { mutex.withLock { websiteUnlocked() } },
        now = now,
        scope = scope,
        pauseMs = pauseMs,
        refusal = KeptRefusal(LatchedRefusal(passwordLatch)),
    )

    /**
     * A session on Moodle's website, signed in with the stored account: its cookies, for the
     * TigerDuck browser when Moodle has no autologin key for it. Posts the stored password, so it
     * keeps every rule a renewal keeps.
     */
    suspend fun website(): List<Cookie> = websiteGate.take(null)

    /**
     * A token with credentials the user just entered, which clears what held renewals back. It is
     * stored only while [studentId] is still the stored account, as one step with a sign-out's
     * clear: a sign-out during the sign-in leaves none of the departing student's behind.
     */
    suspend fun obtain(studentId: String, password: String): String = mutex.withLock {
        val normalized = studentId.trim().uppercase()
        login(normalized, password, PasswordPosting.Typed).also {
            renewal.reset()
            websiteGate.reset()
            if (!tokens.storeIfSignedIn(normalized, it)) throw MoodleWebserviceError.MissingStoredCredentials()
        }
    }

    private suspend fun websiteUnlocked(): List<Cookie> {
        val sid = tokens.studentId ?: throw MoodleWebserviceError.MissingStoredCredentials()
        val pwd = tokens.password ?: throw MoodleWebserviceError.MissingStoredCredentials()
        val cookies = passwordLatch.guardAutomaticHarvest(sid, pwd) { posting ->
            websiteLogin(sid.trim().uppercase(), pwd, posting)
        }
        // Signed out, or signed in as another student, while it ran: the session is the departing one's.
        if (tokens.studentId != sid) throw MoodleWebserviceError.MissingStoredCredentials()
        return cookies
    }

    private suspend fun harvestUnlocked(): String {
        val sid = tokens.studentId ?: throw MoodleWebserviceError.MissingStoredCredentials()
        val pwd = tokens.password ?: throw MoodleWebserviceError.MissingStoredCredentials()
        val token = passwordLatch.guardAutomaticHarvest(sid, pwd) { posting ->
            login(sid.trim().uppercase(), pwd, posting)
        }
        // Signed out while it ran: the token is the departing account's, and stored it would
        // answer for whoever signs in next.
        if (!tokens.storeIfSignedIn(sid, token)) throw MoodleWebserviceError.MissingStoredCredentials()
        return token
    }
}

/**
 * [StoredPasswordRefusal] as the app-wide [StoredPasswordLatch], a count, so no fingerprint of the
 * credentials is ever kept. Refused while a refusal's wait holds. [guardAutomaticHarvest] counts
 * each, with the school's message and only for the password still stored; a post the school
 * takes and a sign-in by hand end the count.
 */
internal class LatchedRefusal(private val latch: StoredPasswordLatch) : StoredPasswordRefusal {
    override fun isRefused(fingerprint: String): Boolean = latch.postMustWait()
    override fun markRefused(fingerprint: String) = Unit
    override fun clear() = latch.clear()
}

/**
 * [refusal], and besides it the credentials SSO refused here, kept until they change or a sign-in
 * by hand clears them. The app-wide count lets a first refusal be tried again after a short wait;
 * a sign-in to Moodle's website, which only spares the student a sign-in screen, never posts a
 * password SSO refused again.
 */
internal class KeptRefusal(private val refusal: StoredPasswordRefusal) : StoredPasswordRefusal {
    @Volatile private var refused: String? = null

    override fun isRefused(fingerprint: String): Boolean = refused == fingerprint || refusal.isRefused(fingerprint)

    override fun markRefused(fingerprint: String) {
        refused = fingerprint
        refusal.markRefused(fingerprint)
    }

    override fun clear() {
        refused = null
        refusal.clear()
    }
}
