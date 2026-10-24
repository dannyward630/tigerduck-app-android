package org.ntust.app.tigerduck.ui.screen.browser

import org.ntust.app.tigerduck.browser.BrowserSignIns

/**
 * What the browser keeps about its main-frame loads: the page Retry and "Open in browser" name
 * ([loadingUrl]), the error shown over the page, and the page a sign-in goes on to
 * ([signInTarget]). Apart from the activity so a test can run it.
 *
 * The WebView reports a page's loads whether or not the browser was asked about them: a form's
 * POST and a move through the history only start. A failed load's error comes before the start of
 * its error page on some WebViews and after it on others, so an error stays up through the start
 * of the page it is for, and any other start takes it down.
 */
internal class BrowserLoads(private val state: BrowserPageState, private val signIns: BrowserSignIns) {
    /** The page loaded, or loading, last. */
    var loadingUrl: String? = null
        private set

    /** The page a sign-in under way goes on to; null once a page of its site shows. */
    var signInTarget: String? = null

    private var startedUrl: String? = null

    /** A page whose error came before the page started. */
    private var failedBeforeStart: String? = null

    /** A load the app chose, or a page's navigation the browser let through. */
    fun requested(url: String) {
        loadingUrl = url
        failedBeforeStart = null
    }

    /** [url]'s page started. */
    fun started(url: String) {
        loadingUrl = url
        if (failedBeforeStart != url) state.error = null
        failedBeforeStart = null
        startedUrl = url
        // A page of the site, past its sign-in links: whatever needs a sign-in next asks from the page it is on.
        if (signIns.forUrl(url)?.isReturnPage(url) == true) signInTarget = null
    }

    /** [url]'s page finished, failed or not. */
    fun finished(url: String) {
        if (url == startedUrl) startedUrl = null
    }

    /**
     * The WebView refused [url]'s certificate; true when that was the page's own, which then shows
     * the certificate error. The WebView asks about an image or a script on the page too: refused
     * all the same, it leaves the page as it is.
     */
    fun certificateFailed(url: String?, host: String?): Boolean {
        if (url == null || (url != loadingUrl && url != state.url)) return false
        failed(url, BrowserError.BadCertificate(host))
        return true
    }

    /** The main-frame load of [url] failed with [error]. */
    fun failed(url: String?, error: BrowserError) {
        state.error = error
        if (url == null) return
        loadingUrl = url
        failedBeforeStart = if (url == startedUrl) null else url
    }

    /**
     * The page the site's sign-in page at [url] came in the way of, for the sign-in to go on to:
     * the one a sign-in under way is for, or else the page being loaded or shown, where the site
     * can go back to it. Never one of the site's sign-in links, which work once.
     */
    fun signInReturn(url: String): String {
        val provider = signIns.forUrl(url)
        val returnable = { page: String? -> page?.takeIf { provider?.isReturnPage(it) == true } }
        return signInTarget ?: returnable(loadingUrl) ?: returnable(state.url) ?: url
    }
}
