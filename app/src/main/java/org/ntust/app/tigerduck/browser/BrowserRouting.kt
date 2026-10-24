package org.ntust.app.tigerduck.browser

/** What a page inside the TigerDuck browser asks to go to, and what the browser does. */
sealed interface NavigationDecision {
    /** Loads inside. */
    data object Load : NavigationDecision

    /** Off the allowlist: asks, then hands it to the phone's browser. */
    data class OpenOutside(val url: String) : NavigationDecision

    /** A `mailto:` link: the mail app's, as anywhere else in the app. */
    data class HandOff(val url: String) : NavigationDecision

    /** A scheme no page should hand anyone (`intent:`, `file:`, `javascript:`…): nothing happens. */
    data object Refuse : NavigationDecision
}

object BrowserRouting {
    /**
     * What the browser does with a page's main-frame navigation to [url]:
     * load it when the allowlist lets it in, ask before sending any other web
     * page outside, hand a mail link to the mail app, and refuse the rest.
     * `about:blank`, which pages open to write into, stays.
     */
    fun navigation(url: String, allowlist: BrowserAllowlist): NavigationDecision = when (scheme(url)) {
        "https", "http" -> if (allowlist.allows(url)) NavigationDecision.Load else NavigationDecision.OpenOutside(url)
        "about" -> if (url.equals("about:blank", ignoreCase = true)) NavigationDecision.Load else NavigationDecision.Refuse
        "mailto" -> NavigationDecision.HandOff(url)
        else -> NavigationDecision.Refuse
    }

    /**
     * Whether a request the WebView is about to send must not go: a main-frame
     * one the allowlist does not let in. A form's POST is never put to
     * `shouldOverrideUrlLoading`, so this is the check that stops it before
     * the form reaches another site.
     */
    fun blocksRequest(url: String, isForMainFrame: Boolean, allowlist: BrowserAllowlist): Boolean =
        isForMainFrame && navigation(url, allowlist) != NavigationDecision.Load

    private fun scheme(url: String): String? = url.substringBefore(':', "").takeIf { it.isNotEmpty() }?.lowercase()
}
