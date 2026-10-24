package org.ntust.app.tigerduck.network.tls

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.ntust.app.tigerduck.BuildConfig
import org.ntust.app.tigerduck.shared.tls.PinDocument
import org.ntust.app.tigerduck.shared.tls.PinDocumentKeys
import org.ntust.app.tigerduck.shared.tls.PinDocumentResult
import org.ntust.app.tigerduck.shared.tls.PinDocumentVerifier
import org.ntust.app.tigerduck.shared.tls.PinMismatch
import org.ntust.app.tigerduck.shared.tls.PinMismatchResolver
import org.ntust.app.tigerduck.shared.tls.PinPolicy
import org.ntust.app.tigerduck.shared.tls.PinState
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's TLS pins (TLS pins spec, A4 to A6): the [state] every connection reads, the
 * TigerSync document it fetches and keeps, the student's acceptances, and what a mismatch leads
 * to. On a mismatch it refreshes the document, at most once per set per launch, past every
 * cache; a chain the new document allows is retried silently. Otherwise, for a chain anchored in
 * the document's `prompt_roots` (D18), it queues a prompt, which a foreground request waits for
 * and a background one doesn't (D5). Any other mismatch fails with no prompt, logged.
 */
@Singleton
class PinManager internal constructor(
    private val documents: PinDocumentStore,
    private val acceptances: PinAcceptanceStore,
    private val client: PinDocumentSource,
    private val verifier: PinDocumentVerifier,
    versionCode: Long,
    private val clock: () -> Long,
    private val isForeground: () -> Boolean,
    private val executor: Executor,
) : PinMismatchResolver {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.applicationContext,
        PinDocumentVerifier(PinDocumentKeys.keys),
    )

    private constructor(context: Context, verifier: PinDocumentVerifier) : this(
        documents = PinDocumentStore(File(context.noBackupFilesDir, "tls/pin-document.json"), verifier),
        acceptances = PinAcceptanceStore(
            context.getSharedPreferences(PinAcceptanceStore.FILE, Context.MODE_PRIVATE),
            BuildConfig.VERSION_CODE.toLong(),
        ),
        client = PinDocumentClient(),
        verifier = verifier,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        clock = System::currentTimeMillis,
        isForeground = { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
        executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "tls-pins").apply { isDaemon = true } },
    )

    /** The pins every connection reads. */
    val state = PinState(PinPolicy(document = documents.load(), acceptances = acceptances.all, appVersion = versionCode))

    val prompts = PinMismatchPrompts(acceptances, clock, ::accept)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** A new document or a new acceptance: what the watch holds is out of date (A8). */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private val _accepted = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** The set a Continue was just made for, so features can refresh as after a network error. */
    val accepted: SharedFlow<String> = _accepted.asSharedFlow()

    /** The document's envelope as received, for the watch, or null while none is held. */
    val envelope: ByteArray? get() = state.policy.document?.envelope

    /** The acceptances in force, for the watch. */
    val currentAcceptances get() = acceptances.all

    private val lastFetchAt = AtomicLong(0)
    private val mismatchRefreshes = ConcurrentHashMap<String, FutureTask<Boolean>>()

    /**
     * Fetches the document and takes it if it verifies and supersedes the one held (A5).
     * True when a new document came into force.
     */
    fun refresh(fresh: Boolean = false): Boolean {
        lastFetchAt.set(clock())
        val bytes = client.fetch(fresh) ?: return false
        val document = (verifier.verify(bytes) as? PinDocumentResult.Verified)?.document ?: return false
        synchronized(this) {
            val held = state.policy.document
            if (held != null && held.envelope.contentEquals(document.envelope)) return false
            if (!PinDocumentVerifier.supersedes(document, held)) return false
            runCatching { documents.save(document) }.onFailure { Log.w(TAG, "pin document not saved: ${it.javaClass.simpleName}") }
            state.update { it.with(document = document) }
        }
        _changes.tryEmit(Unit)
        return true
    }

    /** At app start and while in use, at most every 6 hours (A5), off the calling thread. */
    fun refreshIfDue() {
        val last = lastFetchAt.get()
        if (last != 0L && clock() - last in 0 until REFRESH_INTERVAL_MS) return
        executor.execute { refresh() }
    }

    /** What [org.ntust.app.tigerduck.shared.tls.PinnedTrustManager] reports, from the handshake's thread. */
    fun onMismatch(mismatch: PinMismatch) {
        executor.execute { settle(mismatch) }
    }

    override fun mayRetry(mismatch: PinMismatch): Boolean {
        if (settle(mismatch)) return true
        if (!isForeground()) return false
        return prompts.awaitAnswer(mismatch.setId, ANSWER_WAIT_MS) && allows(mismatch)
    }

    /**
     * Refreshes the document once per set per launch, then decides (A4): true when the pins now
     * allow the chain; otherwise a prompt is queued for a chain the student may be offered.
     */
    private fun settle(mismatch: PinMismatch): Boolean {
        val refresh = mismatchRefreshes.computeIfAbsent(mismatch.setId) { _ ->
            FutureTask { refresh(fresh = true) }.also(executor::execute)
        }
        runCatching { refresh.get(REFRESH_WAIT_MS, TimeUnit.MILLISECONDS) }
        if (allows(mismatch)) return true
        if (state.policy.mayPrompt(mismatch, Instant.ofEpochMilli(clock()))) {
            prompts.queue(mismatch)
        } else {
            Log.w(TAG, "pin mismatch with no prompt: set ${mismatch.setId}, host ${mismatch.host}")
        }
        return false
    }

    private fun allows(mismatch: PinMismatch) = state.policy.allows(mismatch, Instant.ofEpochMilli(clock()))

    /** Continue: the chain's leaf and intermediates are trusted for its set until the build changes. */
    private fun accept(mismatch: PinMismatch) {
        val all = acceptances.accept(mismatch.setId, mismatch.acceptableSpkis, clock())
        state.update { it.with(acceptances = all) }
        _changes.tryEmit(Unit)
        _accepted.tryEmit(mismatch.setId)
    }

    /** Debug builds only: [setId]'s pins become one no chain has, so the prompt can be seen. */
    fun simulateMismatch(setId: String?) {
        if (!BuildConfig.DEBUG) return
        state.update { it.with(simulatedMismatch = setId) }
    }

    /** The document in force, for tests and the developer page. */
    val document: PinDocument? get() = state.policy.document

    private companion object {
        const val TAG = "TlsPins"
        const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1000

        /** The fetch's own timeout is 10 s. */
        const val REFRESH_WAIT_MS = 12_000L

        /** How long a foreground request waits for the student's answer. */
        const val ANSWER_WAIT_MS = 120_000L
    }
}
