package org.ntust.app.tigerduck.browser

import org.ntust.app.tigerduck.network.moodle.MoodleWebservice

/** What the browser adds to pages it shows, before the pages' own scripts run. */
object BrowserPageScripts {
    /**
     * Lets the page pan. YUI's gesture events, which Moodle still loads (its file picker, its
     * dialogs), put `touch-action: none` inline on the element they listen on, the body among
     * them, wherever the browser has pointer events, and a page whose body says so cannot be
     * scrolled at all: Moodle's profile editor among them. A stylesheet rule marked important
     * outranks an inline style. Added to the document element, as the head may not be there yet.
     * Moodle's pages only ([TOUCH_PAN_ORIGINS]): elsewhere a page may hold its body still on purpose.
     */
    const val TOUCH_PAN = """(function() {
  var style = document.createElement('style');
  style.textContent = 'html, body { touch-action: auto !important; }';
  (document.head || document.documentElement).appendChild(style);
})();"""

    /** Where [TOUCH_PAN] goes, as WebViewCompat.addDocumentStartJavaScript's origin rules. */
    val TOUCH_PAN_ORIGINS: Set<String> = setOf(MoodleWebservice.SITE)

    /**
     * Hides Moodle's logout: every link to it, and every form that posts to it (its sign-in page's
     * "Log out" button). Moodle's session ends when TigerDuck signs out, which clears the browser;
     * a logout inside would only leave the student at a sign-in the next open may have no key for.
     * A stylesheet, so the user menu Moodle draws later is matched as well. The address itself is
     * refused besides ([BrowserRouting.isMoodleLogout]).
     */
    const val HIDE_LOGOUT = """(function() {
  var style = document.createElement('style');
  style.textContent = 'a[href*="logout.php"], form[action*="logout.php"] { display: none !important; }';
  (document.head || document.documentElement).appendChild(style);
})();"""

    /** Where [HIDE_LOGOUT] goes, as WebViewCompat.addDocumentStartJavaScript's origin rules. */
    val HIDE_LOGOUT_ORIGINS: Set<String> = setOf(MoodleWebservice.SITE)
}
