package org.ntust.app.tigerduck.ui.screen.browser

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.ntust.app.tigerduck.browser.BrowserSignIn
import org.ntust.app.tigerduck.browser.BrowserSignIns
import org.ntust.app.tigerduck.browser.watchSignInRetry
import org.ntust.app.tigerduck.ui.AppProcessing

/**
 * The browser's sign-in screen and the sign-ins it runs, apart from the activity so a test can
 * run it. [load] loads a page the app chose, on the page's own terms; [stopLoading] stops the
 * one under way.
 *
 * A link the site signs the browser in with comes first. Where none can be had, the app signs in
 * itself ([BrowserSignIn.signInWithoutLink]) behind [processing]'s spinner, at most once every
 * [APP_SIGN_IN_INTERVAL_MS] by itself, so a site that will not keep the session shows the screen
 * rather than loop; "Sign in again" by hand may try it at once.
 */
internal class BrowserSignInFlow(
    private val state: BrowserPageState,
    private val loads: BrowserLoads,
    private val signIns: BrowserSignIns,
    private val scope: CoroutineScope,
    private val load: (String) -> Unit,
    private val stopLoading: () -> Unit,
    private val processing: AppProcessing = AppProcessing(),
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var job: Job? = null

    /** When the app last signed the browser in itself, by [now]; null for never. */
    private var appSignInAt: Long? = null

    /** Signs the browser in to [target]'s site through [provider] and loads [target]; the screen when it cannot now. */
    fun signIn(provider: BrowserSignIn, target: String) {
        loads.signInTarget = target
        // No page yet: the bars name the one on its way.
        if (state.url == null) state.url = target
        state.signIn = working(provider, target)
        restart { attempt(provider, target) }
    }

    /**
     * A site sent the browser to its own sign-in page, [url]: signed out there. Signs in at once
     * when a link is due, and otherwise shows the screen counting down to one. Moodle's next
     * link is 6 minutes after the last, so a link it bounces back here does not loop.
     */
    fun signInPageReached(provider: BrowserSignIn, url: String) {
        stopLoading()
        // A sign-in on its way loads nothing till it is done: this is the same navigation again.
        if (state.signIn?.working == true) return
        val target = loads.signInReturn(url)
        if (loads.signingInOnWeb) {
            loads.signingInOnWeb = false
            // The site has kept the page by now: the web sign-in comes back to it.
            provider.webSignInUrl(target)?.let {
                load(it)
                return
            }
        }
        if (provider.msUntilRetry() == 0L || mayTryApp(provider)) signIn(provider, target) else show(provider, target)
    }

    /** "Sign in again" on the screen. */
    fun signInAgain() {
        val target = state.signIn?.target ?: return
        val provider = signIns.forUrl(target) ?: return
        // Asked for by hand: the app may sign in itself again at once.
        appSignInAt = null
        signIn(provider, target)
    }

    /**
     * "Sign in on the web page": the site's own sign-in, where the student signs in themselves. The
     * page goes first: signed out, the site keeps it to come back to after the sign-in and sends
     * the browser to its sign-in page, which then goes on to the web sign-in.
     */
    fun signInOnWeb() {
        val target = state.signIn?.target ?: return
        if (signIns.forUrl(target)?.webSignInUrl(target) == null) return
        job?.cancel()
        state.signIn = null
        loads.signInTarget = target
        loads.signingInOnWeb = true
        load(target)
    }

    /** The student left the sign-in, by Back or Forward: its try or countdown stops, and it is forgotten. */
    fun leave() {
        job?.cancel()
        state.signIn = null
        loads.signInTarget = null
        loads.signingInOnWeb = false
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun attempt(provider: BrowserSignIn, target: String) {
        val signedIn = provider.signedInUrl(target)
        if (signedIn != null) {
            state.signIn = null
            load(signedIn)
        } else if (mayTryApp(provider) && signInByApp(provider)) {
            state.signIn = null
            load(target)
        } else {
            show(provider, target, failed = provider.msUntilRetry() == 0L)
        }
    }

    /** Whether the app may sign the browser in itself now: once every [APP_SIGN_IN_INTERVAL_MS]. */
    private fun mayTryApp(provider: BrowserSignIn): Boolean =
        provider.signsInWithoutLink && appSignInAt.let { it == null || now() - it >= APP_SIGN_IN_INTERVAL_MS }

    /** The app's own sign-in, behind the spinner, which lets go however it ends. */
    private suspend fun signInByApp(provider: BrowserSignIn): Boolean {
        appSignInAt = now()
        val hold = processing.newHold()
        hold.start()
        return try {
            provider.signInWithoutLink()
        } finally {
            hold.stop()
        }
    }

    /** The screen for [provider], counting down to the next try and trying when it is due. */
    private fun show(provider: BrowserSignIn, target: String, failed: Boolean = false) {
        loads.signInTarget = target
        state.signIn = SignInState(
            siteName = provider.siteName,
            target = target,
            msLeft = provider.msUntilRetry(),
            failed = failed,
            hasWebSignIn = provider.webSignInUrl(target) != null,
        )
        restart {
            watchSignInRetry(
                msUntilRetry = provider::msUntilRetry,
                show = { left -> state.signIn?.let { state.signIn = it.copy(msLeft = left) } },
                retry = {
                    state.signIn = working(provider, target)
                    attempt(provider, target)
                },
                stillShowing = { state.signIn?.target == target && state.signIn?.working == false },
            )
        }
    }

    private fun working(provider: BrowserSignIn, target: String) =
        SignInState(provider.siteName, target, msLeft = 0, working = true, hasWebSignIn = provider.webSignInUrl(target) != null)

    // A try that fails shows the screen from within its own job, which this cancels as it returns.
    // The job is kept before it starts: under Main.immediate a try that fails at once has shown the
    // screen, and started its countdown, before launch would return.
    private fun restart(block: suspend () -> Unit) {
        job?.cancel()
        val next = scope.launch(start = CoroutineStart.LAZY) { block() }
        job = next
        next.start()
    }
}

/**
 * How often the app signs the browser in by itself, as Moodle gives its keys: a site that will not
 * keep the session it signs in with is not asked again and again.
 */
internal const val APP_SIGN_IN_INTERVAL_MS = 360_000L
