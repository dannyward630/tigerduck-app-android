package org.ntust.app.tigerduck.browser

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Moves on whenever the browser's data is cleared, so a browser screen open
 * at that moment, which shows the departing student's pages, closes.
 */
object BrowserSession {
    private val state = MutableStateFlow(0)
    val generation: StateFlow<Int> = state.asStateFlow()

    fun end() = state.update { it + 1 }
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
    suspend fun clear() {
        BrowserSession.end()
        downloads.clear()
        settings.clearSites()
        runCatching { storage.clear() }.onFailure { Log.w(TAG, "browser storage not cleared: ${it.javaClass.simpleName}") }
    }

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
        val removed = withTimeoutOrNull(CLEAR_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont -> cookies.removeAllCookies { cont.resume(Unit) } }
        }
        if (removed == null) Log.w(TAG, "cookie removal did not report back")
        cookies.flush()
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
