package org.ntust.app.tigerduck.wear.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs the work handed to it one piece at a time, in the order it was handed
 * over, in [scope]. The Data Layer's callbacks come in order, but a coroutine
 * launched for each can finish out of it: an older schedule's write would
 * then land after a newer one's and stay, with the accent or courses the
 * phone has since replaced.
 */
internal class InArrivalOrder(scope: CoroutineScope) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (work in queue) work() }
    }

    fun submit(work: suspend () -> Unit) {
        // Fails only once the scope is gone, when nothing is listening anyway.
        queue.trySend(work)
    }
}
