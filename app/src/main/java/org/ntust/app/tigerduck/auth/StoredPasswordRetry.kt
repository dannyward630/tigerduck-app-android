package org.ntust.app.tigerduck.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ntust.app.tigerduck.shared.StoredPasswordSchedule
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Tries the stored password again once a refusal's wait is over ([StoredPasswordLatch.retryAt]),
 * so the quick retries go while the app runs and a relaunch takes the wait up where it stood
 * (tigerduck-app/tigerduck-app#768). Each try is [retry], [AuthService.retryStoredPassword] in
 * the app. A refusal it gets is counted and starts the next wait; a try that stops short of the
 * school's answer, lost to the network say, goes again a step further down the schedule. A post
 * the school takes, a sign-in by hand and a sign-out end the count, and with it the wait.
 */
@Singleton
class StoredPasswordRetry internal constructor(
    private val latch: StoredPasswordLatch,
    private val now: () -> Long,
    /** One try; true once it ran to the end. */
    private val retry: suspend () -> Boolean,
) {
    @Inject
    constructor(latch: StoredPasswordLatch, authService: AuthService) :
        this(latch, System::currentTimeMillis, authService::retryStoredPassword)

    /** Keeps the tries going in [scope] while it runs. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        retryWhenDue(latch.changes, { latch.refusals.value }, latch::retryAt, now, retry)
    }
}

/**
 * The tries behind [StoredPasswordRetry] and the library's: on each of [changes], which move with
 * every refusal counted and every end of the count, waits until [retryAt] and runs [retry] once.
 * A try that stops short of an answer goes again a step further down the schedule than the
 * [refusals] counted. A wait is taken up again from [retryAt] itself, so one a relaunch finds run
 * out goes at once.
 */
internal suspend fun retryWhenDue(
    changes: Flow<*>,
    refusals: () -> Int,
    retryAt: () -> Long?,
    now: () -> Long,
    /** One try; true once it got an answer, or there is nothing left to try. */
    retry: suspend () -> Boolean,
) {
    changes.collectLatest {
        if (refusals() == 0) return@collectLatest
        var misses = 0
        var wait = (retryAt() ?: return@collectLatest) - now()
        while (true) {
            delay(wait.coerceAtLeast(0))
            val at = retryAt() ?: return@collectLatest
            if (at > now()) {
                wait = at - now()
                continue
            }
            // Not cut short when a refusal it gets moves the count, which starts the next wait.
            if (withContext(NonCancellable) { retry() }) return@collectLatest
            misses++
            wait = StoredPasswordSchedule.delayMs(refusals() + misses)
        }
    }
}

/**
 * One try of the stored password, down the chain a typed sign-in runs: [signInToSchool], which
 * posts the password to the school, warm session or not, then [renewMoodleToken] where none is
 * stored, then [signInToBackend], told whether this try got the Moodle token. Signs nothing out.
 * It stops at the first step that fails, a refusal the latch counts included, and says whether it
 * ran to the end.
 */
internal suspend fun retryStoredPasswordChain(
    signInToSchool: suspend () -> Boolean,
    moodleTokenMissing: () -> Boolean,
    renewMoodleToken: suspend () -> Unit,
    signInToBackend: suspend (renewedToken: Boolean) -> Boolean,
): Boolean {
    if (!signInToSchool()) return false
    val renewed = moodleTokenMissing()
    if (renewed) {
        try {
            renewMoodleToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
    }
    return signInToBackend(renewed)
}
