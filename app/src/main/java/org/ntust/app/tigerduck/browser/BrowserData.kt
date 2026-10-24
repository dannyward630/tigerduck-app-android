package org.ntust.app.tigerduck.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Moves on whenever the browser's data is cleared, so a browser screen open
 * at that moment, which shows the departing student's pages, closes; and
 * says while a clearing runs, so a screen opened meanwhile waits for it.
 */
object BrowserSession {
    private val state = MutableStateFlow(0)
    val generation: StateFlow<Int> = state.asStateFlow()

    private val clearingState = MutableStateFlow(false)
    val clearing: StateFlow<Boolean> = clearingState.asStateFlow()

    private val pendingDeletions = MutableStateFlow(0)

    /**
     * False while a deletion the WebView has not confirmed is still under
     * way, however long after its clearing gave up waiting: the browser
     * does not open until it has.
     */
    val usable: Boolean get() = pendingDeletions.value == 0

    fun end() = state.update { it + 1 }

    internal fun setClearing(clearing: Boolean) {
        clearingState.value = clearing
    }

    internal fun deletionStarted() = pendingDeletions.update { it + 1 }

    internal fun deletionDone() = pendingDeletions.update { (it - 1).coerceAtLeast(0) }
}

/**
 * Starts [delete], which calls back once its deletion is done, and waits up
 * to [timeoutMs] for it. A deletion that has not reported by then keeps the
 * browser closed ([BrowserSession.usable]) until it does, however late; the
 * clearing goes on meanwhile. False when it timed out.
 */
suspend fun awaitDeletion(timeoutMs: Long, delete: (onDone: () -> Unit) -> Unit): Boolean {
    val done = CompletableDeferred<Unit>()
    BrowserSession.deletionStarted()
    done.invokeOnCompletion { BrowserSession.deletionDone() }
    delete { done.complete(Unit) }
    return withTimeoutOrNull(timeoutMs) { done.await() } != null
}

/** The WebView's own stores: cookies, web storage, saved HTTP auth and the HTTP cache. */
fun interface WebViewStorage {
    suspend fun clear()
}

/**
 * Clears everything the TigerDuck browser keeps about the student: the
 * WebView's stores, each site's zoom and desktop-site choice, and the list of
 * downloads (the files stay in the phone's Downloads). Run at every school
 * sign-in and sign-out, which a student switch and the demo account go
 * through, and by the full reset.
 */
class BrowserDataCleaner(
    private val storage: WebViewStorage,
    private val settings: BrowserSettings,
    private val downloads: BrowserDownloads,
) {
    /**
     * One clearing at a time. The session moves on as it starts, closing the
     * screens open, and again as it ends, closing one opened meanwhile, which
     * may have shown the departing student's pages before the cookies went.
     */
    suspend fun clear() = lock.withLock {
        BrowserSession.setClearing(true)
        try {
            BrowserSession.end()
            downloads.clear()
            settings.clearSites()
            runCatching { storage.clear() }.onFailure { Log.w(TAG, "browser storage not cleared: ${it.javaClass.simpleName}") }
        } finally {
            BrowserSession.end()
            BrowserSession.setClearing(false)
        }
    }

    private val lock = Mutex()

    private companion object {
        const val TAG = "BrowserData"
    }
}

/**
 * [WebViewStorage] for the app's WebViews, which share one profile: every
 * cookie (waited for, as the removal is asynchronous), web storage, saved
 * HTTP auth, and the disk cache, which a WebView clears for all of them. On
 * the main thread, as the WebView wants. The mail WebViews keep none of these.
 */
class AndroidWebViewStorage(private val context: Context) : WebViewStorage {
    override suspend fun clear(): Unit = withContext(Dispatchers.Main) {
        val cookies = CookieManager.getInstance()
        if (!awaitDeletion(CLEAR_TIMEOUT_MS) { done -> cookies.removeAllCookies { done() } }) {
            Log.w(TAG, "cookie removal did not report back yet")
        }
        cookies.flush()
        // Everything a site can keep, service workers and Cache Storage too, where the WebView
        // can; deleteAllData below leaves those. The browser opens only where it can.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            if (!awaitDeletion(CLEAR_TIMEOUT_MS) { done -> WebStorageCompat.deleteBrowsingData(WebStorage.getInstance()) { done() } }) {
                Log.w(TAG, "browsing data deletion did not report back yet")
            }
        } else {
            Log.w(TAG, "this WebView cannot delete service workers and Cache Storage")
        }
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        WebView(context.applicationContext).apply {
            clearCache(true)
            destroy()
        }
        // Photos taken for a page's upload field.
        withContext(Dispatchers.IO) { java.io.File(context.cacheDir, UPLOADS_DIR).deleteRecursively() }
        Unit
    }

    private companion object {
        const val TAG = "BrowserData"
        const val CLEAR_TIMEOUT_MS = 5_000L

        /** InAppBrowserActivity's, under the cache. */
        const val UPLOADS_DIR = "browser_uploads"
    }
}
