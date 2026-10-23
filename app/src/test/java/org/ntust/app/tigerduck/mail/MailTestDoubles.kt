package org.ntust.app.tigerduck.mail

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.ntust.app.tigerduck.mail.imap.SpecialFolder
import org.ntust.app.tigerduck.mail.model.MailAddress
import org.ntust.app.tigerduck.mail.model.MailBody
import org.ntust.app.tigerduck.mail.model.MailFlags
import org.ntust.app.tigerduck.mail.model.MailSummary
import org.ntust.app.tigerduck.mail.notify.MailNotifier
import org.ntust.app.tigerduck.mail.store.MailCredentialStore
import org.ntust.app.tigerduck.mail.sync.MailBackgroundScheduler
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Stands in for the app-wide `@ApplicationScope`: the only thing [MailAccount]
 * runs there is sign-out's cache wipe, so a plain supervised scope is enough.
 * A test that asserts on that work waits for it (it lands on [Dispatchers.IO]).
 */
fun testApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Stops what a test left running on a [testApplicationScope] and waits for it
 * to end. Sign-out's cache wipe would otherwise outlive the test and race
 * TemporaryFolder deleting the same folder, and what it throws then fails a
 * later runTest, in any class, as UncaughtExceptionsBeforeTest. Call it from
 * an @After, which runs before rules such as TemporaryFolder clean up. Gives
 * up after ten seconds, failing that test, rather than hang the suite on work
 * that never ends.
 */
fun CoroutineScope.cancelAndJoinBlocking() {
    // Read here: inside runBlocking, coroutineContext is runBlocking's own.
    val job = coroutineContext.job
    runBlocking { withTimeout(10_000) { job.cancelAndJoin() } }
}

class InMemoryMailDevServerStore(
    override var settings: MailDevServerSettings = MailDevServerSettings.OFF,
) : MailDevServerStore

/** A [MailSite] with no developer override stored: the school server, as everything but the override screen sees it. */
fun schoolMailSite(): MailSite = MailSite(InMemoryMailDevServerStore())

class InMemoryCredentialStore : MailCredentialStore {
    /** The thread of every read of the student ID: the real store decrypts it, so never on the main thread. */
    val idReaders = ConcurrentLinkedQueue<Thread>()

    override var mailStudentId: String? = null
        get() {
            idReaders += Thread.currentThread()
            return field
        }
    override var mailPassword: String? = null

    /** Fires inside [clearMailCredentials], before anything is removed: lets a test act mid-sign-out. */
    var beforeClear: (() -> Unit)? = null
    override fun clearMailCredentials() {
        beforeClear?.invoke()
        mailStudentId = null
        mailPassword = null
    }
}

class RecordingScheduler : MailBackgroundScheduler {
    var scheduled = 0
    var cancelled = 0

    /** Every schedule and cancel, in order, from whatever thread. */
    val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Fires inside [cancel], before it takes: a test acts while a cancel is under way. */
    @Volatile var onCancel: (() -> Unit)? = null

    override fun schedule() {
        scheduled++
        events += "schedule"
    }

    override fun cancel() {
        onCancel?.invoke()
        cancelled++
        events += "cancel"
    }
}

class RecordingNotifier : MailNotifier {
    val posted = mutableListOf<List<MailSummary>>()
    var authFailures = 0
    val cancelledUids = mutableListOf<Long>()
    var cancelledAll = 0

    /** Fires inside [postNewMail] -- lets a test act at the exact point the check notifies. */
    var onPostNewMail: (() -> Unit)? = null
    override fun postNewMail(folder: String, messages: List<MailSummary>) {
        posted += messages
        onPostNewMail?.invoke()
    }
    override fun postAuthFailure() { authFailures++ }
    var authFailuresCancelled = 0
    override fun cancelAuthFailure() { authFailuresCancelled++ }
    override fun cancelMessage(uid: Long) { cancelledUids += uid }
    val cancelledPosted = mutableListOf<List<MailSummary>>()
    override fun cancelPosted(messages: List<MailSummary>) { cancelledPosted += messages }
    /** Fires inside [cancelAll] -- lets a test wait for sign-out's own cleanup, which runs on its own. */
    var onCancelAll: (() -> Unit)? = null
    override fun cancelAll() {
        cancelledAll++
        onCancelAll?.invoke()
    }
}

class FakeDemoGate(override var appDemoActive: Boolean = false) : MailDemoGate {
    val box = DemoMailbox(
        studentId = "B10000099", password = "demo", displayName = "示範同學",
        messages = listOf(
            DemoMail(
                MailSummary(1001, MailAddress("教務處", "office@mail.ntust.edu.tw"), emptyList(), emptyList(), emptyList(),
                    "期中考時間公告", null, null, MailFlags.NONE, 100, false, "<d1@x>", null, null),
                MailBody("<p>期中考</p>", "期中考", emptyList(), emptyMap()),
            ),
            DemoMail(
                MailSummary(1002, MailAddress("示範同學", "b10000099@mail.ntust.edu.tw"), emptyList(),
                    listOf(MailAddress("系辦公室", "office2@mail.ntust.edu.tw")), emptyList(),
                    "詢問事項", null, null, MailFlags.NONE.copy(seen = true, draft = true), 50, false, "<d2@x>", null, null),
                MailBody(null, "詢問內容", emptyList(), emptyMap()),
                folder = SpecialFolder.DRAFTS,
            ),
            DemoMail(
                MailSummary(1003, MailAddress("示範同學", "b10000099@mail.ntust.edu.tw"), emptyList(),
                    listOf(MailAddress("教務處", "office@mail.ntust.edu.tw")), emptyList(),
                    "Re: 期中考時間公告", null, null, MailFlags.NONE.copy(seen = true), 50, false, "<d3@x>", null, null),
                MailBody(null, "收到，謝謝", emptyList(), emptyMap()),
                folder = SpecialFolder.SENT,
            ),
        ),
    )
    override fun matches(studentId: String, password: String) = box.matches(studentId, password)
    override fun mailbox() = box
}

/**
 * Signs [studentId] in again on [account], on threads of its own, from inside the account's mark
 * of a refusal: as [state]'s authFailed is about to turn true. The mark goes on once the sign-in
 * has got as far as it can -- signed in, or held at the account's lock, which a mark made in one
 * step with a sign-in's move of the generation holds. [server] is the one it signs in to.
 */
class SignInDuringRefusal(
    account: MailAccount,
    state: InMemoryMailStateStore,
    server: FakeMailServer,
    studentId: String,
    password: String,
) {
    @Volatile private var signIn: Thread? = null

    init {
        val signInThread = AtomicReference<Thread?>(null)
        state.onAuthFailedSet = { failed ->
            if (failed) {
                state.onAuthFailedSet = null
                server.onListFolders = { signInThread.set(Thread.currentThread()) }
                val done = CountDownLatch(1)
                signIn = thread {
                    try {
                        runBlocking { account.signIn(studentId, password) }
                    } finally {
                        done.countDown()
                    }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                while (done.count > 0 && signInThread.get()?.state != Thread.State.BLOCKED) {
                    check(System.nanoTime() < deadline) { "the sign-in got nowhere" }
                    Thread.onSpinWait()
                }
            }
        }
    }

    /** Waits for the sign-in to end, and fails if the refusal never came to be marked. */
    fun join() {
        val started = checkNotNull(signIn) { "no refusal was marked" }
        started.join(5_000)
        check(!started.isAlive) { "the sign-in never ended" }
    }
}

