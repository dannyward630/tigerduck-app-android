package org.ntust.app.tigerduck.network.tls

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ntust.app.tigerduck.shared.tls.PinMismatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The pin-mismatch prompts waiting for the student (TLS pins spec, A6): one at a time, at most
 * once per set per launch, and none for a set within the hour after its Cancel (D5). A mismatch
 * from a background request only queues here; the app's root shows [pending] when it is next in
 * the foreground. A foreground request may wait for the answer through [awaitAnswer].
 */
class PinMismatchPrompts(
    private val store: PinAcceptanceStore,
    private val clock: () -> Long,
    private val onContinue: (PinMismatch) -> Unit,
) {
    private val _pending = MutableStateFlow<PinMismatch?>(null)

    /** The prompt to show, or null. */
    val pending: StateFlow<PinMismatch?> = _pending.asStateFlow()

    private val backlog = LinkedHashMap<String, PinMismatch>()
    private val answered = mutableSetOf<String>()
    private val answers = ConcurrentHashMap<String, CompletableFuture<Boolean>>()

    /** Queues [mismatch]'s prompt, unless its set was asked about this launch or is quiet. */
    @Synchronized
    fun queue(mismatch: PinMismatch) {
        val set = mismatch.setId
        if (set in answered || store.isQuiet(set, clock())) return
        if (_pending.value?.setId == set || set in backlog) return
        answers.putIfAbsent(set, CompletableFuture())
        if (_pending.value == null) _pending.value = mismatch else backlog[set] = mismatch
    }

    /**
     * The student's answer for [setId]'s queued prompt, waiting up to [timeoutMs]: true for
     * Continue; false for Cancel, a timeout, or no prompt queued.
     */
    fun awaitAnswer(setId: String, timeoutMs: Long): Boolean {
        val answer = answers[setId] ?: return false
        return runCatching { answer.get(timeoutMs, TimeUnit.MILLISECONDS) }.getOrDefault(false)
    }

    /** Continue on the prompt shown. */
    @Synchronized
    fun continueToSite() {
        val mismatch = _pending.value ?: return
        answered += mismatch.setId
        onContinue(mismatch)
        answers.remove(mismatch.setId)?.complete(true)
        showNext()
    }

    /** Cancel on the prompt shown: nothing is stored, and the set is quiet for an hour. */
    @Synchronized
    fun cancel() {
        val mismatch = _pending.value ?: return
        answered += mismatch.setId
        store.cancelled(mismatch.setId, clock())
        answers.remove(mismatch.setId)?.complete(false)
        showNext()
    }

    private fun showNext() {
        val next = backlog.entries.firstOrNull()
        if (next == null) {
            _pending.value = null
        } else {
            backlog.remove(next.key)
            _pending.value = next.value
        }
    }
}
