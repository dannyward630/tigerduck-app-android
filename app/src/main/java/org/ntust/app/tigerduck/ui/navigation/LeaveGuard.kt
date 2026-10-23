package org.ntust.app.tigerduck.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Asks the page on screen before the app moves away from it by anything but its own back: a
 * bottom-bar tap or a link from a reminder, a widget or the Live Update, either of which would pop
 * it. The mail being written asks whether to save its draft; with no page asking, the move is made
 * at once.
 */
@Stable
class LeaveGuard {
    private var asker: ((proceed: () -> Unit) -> Unit)? = null

    /** Makes [move] once the page on screen allows it, which may be never: Keep editing. */
    fun leave(move: () -> Unit) {
        val ask = asker
        if (ask == null) move() else ask(move)
    }

    internal fun register(ask: (proceed: () -> Unit) -> Unit) {
        asker = ask
    }

    internal fun unregister(ask: (proceed: () -> Unit) -> Unit) {
        if (asker === ask) asker = null
    }
}

/**
 * While composed, a move away from this page that [guard] is asked about goes through [onLeave],
 * which runs the move it is handed once the page has let it go, or drops it.
 */
@Composable
fun AskBeforeLeaving(guard: LeaveGuard, onLeave: (proceed: () -> Unit) -> Unit) {
    val latest by rememberUpdatedState(onLeave)
    DisposableEffect(guard) {
        val ask: (() -> Unit) -> Unit = { latest(it) }
        guard.register(ask)
        onDispose { guard.unregister(ask) }
    }
}
