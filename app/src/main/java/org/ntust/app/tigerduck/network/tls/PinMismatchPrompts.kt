package org.ntust.app.tigerduck.network.tls

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The question a pin mismatch asks: whether to continue to a school site whose certificate a
 * system authority vouches for but the app's pins don't name. [PinnedTrustManager][org.ntust.app.tigerduck.shared.tls.PinnedTrustManager]
 * reports each mismatch from whichever thread made the call, and the app's root shows
 * [pending]. One domain is asked at a time; a domain the student said no to isn't asked again
 * until the app starts again, however many calls fail meanwhile.
 */
@Singleton
class PinMismatchPrompts @Inject constructor(private val store: PinOverrideStore) {

    data class Prompt(val domain: String, val host: String)

    private val _pending = MutableStateFlow<Prompt?>(null)

    /** The domain being asked about, or null. */
    val pending: StateFlow<Prompt?> = _pending.asStateFlow()

    private val declined = ConcurrentHashMap.newKeySet<String>()

    private val _accepted = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Each domain the student continued to, as they choose it. */
    val accepted: SharedFlow<String> = _accepted.asSharedFlow()

    /** A call to [host], under the pinned [domain], failed only its pins. */
    fun report(domain: String, host: String) {
        if (store.isOverridden(domain) || domain in declined) return
        _pending.compareAndSet(null, Prompt(domain, host))
    }

    /** No: the domain stays blocked, and isn't asked about again until the next launch. */
    fun decline() {
        val prompt = _pending.value ?: return
        declined += prompt.domain
        _pending.compareAndSet(prompt, null)
    }

    /** Continue: the domain's pins aren't checked until the app is updated. */
    fun accept() {
        val prompt = _pending.value ?: return
        store.accept(prompt.domain)
        _pending.compareAndSet(prompt, null)
        _accepted.tryEmit(prompt.domain)
    }
}
