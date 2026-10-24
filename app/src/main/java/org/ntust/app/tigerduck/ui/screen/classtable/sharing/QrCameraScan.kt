package org.ntust.app.tigerduck.ui.screen.classtable.sharing

/**
 * Lets a code through once while it stays in view, once the import took it. A camera reads a code
 * on every frame that shows it, and without this a code still in front of it would open its
 * confirm step again the moment it was cancelled. The iOS app's `RepeatedCodeFilter`.
 *
 * A code the import [refused] passes again at the next frame: refused while something was over
 * the scanner, as the Enter link dialog, a confirm step or a prompt, it would otherwise stay
 * held for as long as it stayed in view, and scan only once moved out of it. Not one the import
 * has [taken] on this page, though: refused while its own confirm step is open, as when it comes
 * back into view, it stays held, so cancelling the step does not reopen it.
 * Called from the camera's thread and the main thread.
 */
class RepeatedQrCodeFilter {
    private val lastSeen = mutableMapOf<String, Long>()
    private val takenCodes = mutableSetOf<String>()

    /** Whether [text], read at [nowMs], is new: unseen, or out of view for longer than [GAP_MS]. */
    @Synchronized
    fun isNew(text: String, nowMs: Long): Boolean {
        val seen = lastSeen[text]
        lastSeen.entries.removeAll { nowMs - it.value > GAP_MS }
        lastSeen[text] = nowMs
        return seen == null || nowMs - seen > GAP_MS
    }

    /** [text], passed on, was taken by the import. */
    @Synchronized
    fun taken(text: String) {
        takenCodes += text
    }

    /** [text], passed on, was not taken: unless it was before, it is new again at the next frame that shows it. */
    @Synchronized
    fun refused(text: String) {
        if (text !in takenCodes) lastSeen.remove(text)
    }

    companion object {
        /** Longer than the time between two frames that show the same code. */
        const val GAP_MS = 1_000L
    }
}

/**
 * Hands the codes the camera reads ([read], on its thread) to the import through [post], on the
 * main thread where [take] says whether it took one, and tells [filter] which it took and which
 * it refused.
 */
class QrCodeHandOff(
    private val filter: RepeatedQrCodeFilter,
    private val post: (Runnable) -> Unit,
    private val take: (String) -> Boolean,
) {
    fun read(text: String, nowMs: Long) {
        if (!filter.isNew(text, nowMs)) return
        post(Runnable { if (take(text)) filter.taken(text) else filter.refused(text) })
    }
}

/**
 * A camera bound for as long as the import page is there. The camera provider answers some time
 * after the page asks, and the page can have left by then: binding the camera at that point
 * would keep it running over the class table, and start it again on every return to the app.
 * Called on the main thread only.
 */
class CameraSession {
    private var disposed = false
    private var unbind: (() -> Unit)? = null

    /** Until the page leaves. */
    val isActive: Boolean get() = !disposed

    /** Binds with [bind], which returns how to let go, unless the page has left; says whether it bound. */
    fun start(bind: () -> (() -> Unit)): Boolean {
        if (disposed) return false
        unbind = bind()
        return true
    }

    /** The page left: lets go of what was bound, and binds nothing from now on. */
    fun dispose() {
        disposed = true
        unbind?.invoke()
        unbind = null
    }
}
