package org.ntust.app.tigerduck.ui.screen.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.print.PrintManager
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewFeature
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.browser.BrowserAllowlistStore
import org.ntust.app.tigerduck.browser.BrowserAppearance
import org.ntust.app.tigerduck.browser.BrowserColors
import org.ntust.app.tigerduck.browser.BrowserDownload
import org.ntust.app.tigerduck.browser.BrowserDownloadPlans
import org.ntust.app.tigerduck.browser.BrowserDownloads
import org.ntust.app.tigerduck.browser.BrowserRouting
import org.ntust.app.tigerduck.browser.BrowserSession
import org.ntust.app.tigerduck.browser.BrowserSettings
import org.ntust.app.tigerduck.browser.BrowserSignIn
import org.ntust.app.tigerduck.browser.BrowserSignIns
import org.ntust.app.tigerduck.browser.BrowserUpload
import org.ntust.app.tigerduck.browser.BrowserUserAgent
import org.ntust.app.tigerduck.browser.BrowserZoom
import org.ntust.app.tigerduck.browser.CertificateInfo
import org.ntust.app.tigerduck.browser.DownloadPlan
import org.ntust.app.tigerduck.browser.NavigationDecision
import org.ntust.app.tigerduck.browser.PageSchemeFollow
import org.ntust.app.tigerduck.browser.UploadSource
import org.ntust.app.tigerduck.browser.watchSignInRetry
import org.ntust.app.tigerduck.ui.AppScale
import org.ntust.app.tigerduck.ui.AppState
import org.ntust.app.tigerduck.ui.BrowserPreference
import org.ntust.app.tigerduck.ui.openLink
import org.ntust.app.tigerduck.ui.theme.TigerDuckAppTheme
import org.ntust.app.tigerduck.util.fileProviderAuthority
import java.io.ByteArrayInputStream
import java.io.File
import javax.inject.Inject

/**
 * Opens pages in the TigerDuck browser, the app's own browser built on a
 * WebView, whatever "Open links with" says. Only the Moodle profile pencil
 * opens it so far; another feature opens a page in it with one call to
 * [open]. Sign-in is per site, through [BrowserSignIns].
 */
object InAppBrowser {
    const val EXTRA_URL = "org.ntust.app.tigerduck.browser.URL"

    /**
     * Whether this phone's WebView can delete everything a site keeps,
     * service workers and Cache Storage included. The browser opens only
     * where it can: on an older WebView one student's pages could outlive a
     * sign-out. Set by tests.
     */
    @Volatile
    internal var canClearEverything: () -> Boolean = {
        runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA) }.getOrDefault(false)
    }

    /**
     * Opens [url], an https page, in the TigerDuck browser; false when it is
     * not one, when this phone's WebView cannot be cleared ([canClearEverything]),
     * or nothing could open, for the caller to open it another way.
     */
    fun open(context: Context, url: String): Boolean {
        if (url.toHttpUrlOrNull()?.isHttps != true) return false
        if (!canClearEverything()) return false
        val intent = Intent(context, InAppBrowserActivity::class.java).putExtra(EXTRA_URL, url)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

/**
 * The TigerDuck browser. It owns the WebView, so a recomposition never
 * remakes it, and keeps it across a rotation; the screen
 * ([InAppBrowserScreen]) draws around it.
 *
 * The page loads only what the allowlist lets in, checked at every
 * main-frame navigation and redirect, and again as a page starts (a form's
 * POST asks no one). A site's own sign-in page is replaced by the browser's
 * sign-in screen ([BrowserSignIn]). Everything the WebView keeps is the
 * browser's to clear at sign-out ([org.ntust.app.tigerduck.browser.BrowserDataCleaner]), which closes
 * this screen. Screenshots are allowed: there is no telling which field
 * holds a password.
 */
@AndroidEntryPoint
class InAppBrowserActivity : AppCompatActivity() {

    @Inject lateinit var appState: AppState
    @Inject lateinit var allowlistStore: BrowserAllowlistStore
    @Inject lateinit var settings: BrowserSettings
    @Inject lateinit var downloads: BrowserDownloads
    @Inject lateinit var signIns: BrowserSignIns

    private val state = BrowserPageState()
    private var appearance by mutableStateOf(BrowserAppearance.AUTO)

    private var appScale = AppScale.DEFAULT
    private var attachedConfiguration: Configuration? = null

    private lateinit var pageTheme: ContextThemeWrapper
    private lateinit var webView: WebView
    private lateinit var mobileUserAgent: String

    /** The scheme the page was last told; see [tellPage]. */
    private var toldDark: Boolean? = null
    private var schemePokePending = false

    /** The page the student was going to when a site sent the browser to its sign-in. */
    private var signInTarget: String? = null
    private var signInJob: Job? = null

    /** The main-frame URL being loaded, for an error that names it. */
    private var loadingUrl: String? = null
    private var appliedHost: String? = null

    private var pendingUpload: UploadRequest? = null
    private var cameraFile: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliverPicks(listOfNotNull(uri))
    }
    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        deliverPicks(uris)
    }
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = cameraFile
        cameraFile = null
        deliverPicks(if (taken && uri != null) listOf(uri) else emptyList(), cameraShot = uri)
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else {
            Toast.makeText(this, R.string.browser_upload_camera_denied, Toast.LENGTH_SHORT).show()
            deliverUpload(null)
        }
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        deliverPicks(listOfNotNull(uri))
    }
    private val openDocuments = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        deliverPicks(uris)
    }

    override fun attachBaseContext(newBase: Context) {
        appScale = AppScale.stored(newBase)
        attachedConfiguration = Configuration(newBase.resources.configuration)
        super.attachBaseContext(appScale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(InAppBrowser.EXTRA_URL)?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
        if (target == null) {
            finish()
            return
        }
        enableEdgeToEdge()

        appearance = settings.appearance
        pageTheme = ContextThemeWrapper(this, R.style.Theme_TigerDuck)
        tellPage(BrowserColors.pageToldDark(appearance, appIsDark(systemDark = isNight(resources.configuration))))
        webView = createWebView()

        val restored = savedInstanceState?.getBundle(KEY_WEB_STATE)?.let { webView.restoreState(it) != null } == true
        signInTarget = savedInstanceState?.getString(KEY_SIGN_IN_TARGET)
        lifecycleScope.launch {
            // Not while a sign-out clears the browser: the departing student's session is still there.
            BrowserSession.clearing.first { !it }
            // A sign-out from here on clears the browser; the pages shown are the departing student's.
            val session = BrowserSession.generation.value
            launch { BrowserSession.generation.collect { if (it != session) finish() } }
            if (!restored) open(target)
        }

        onBackPressedDispatcher.addCallback(this) { handleBack() }

        setContent {
            val appDark = appIsDark(systemDark = isSystemInDarkTheme())
            LaunchedEffect(appDark, appearance) { tellPage(BrowserColors.pageToldDark(appearance, appDark)) }
            val barsDark = BrowserColors.barsDark(appearance, state.pageDark, appDark)
            LaunchedEffect(barsDark) {
                val style = if (barsDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            TigerDuckAppTheme(
                darkTheme = barsDark,
                accentColor = appState.accentColor(barsDark),
                onAccentColor = appState.accentOnColor(barsDark),
                accentGradient = appState.accentGradient(barsDark),
                useDynamicColor = appState.usesWallpaperColors,
                visualPreset = appState.visualPreset,
            ) {
                val items by downloads.items.collectAsState()
                InAppBrowserScreen(
                    state = state,
                    appearance = appearance,
                    downloads = items,
                    actions = actions,
                    schemeFollow = SCHEME_FOLLOW,
                ) { modifier ->
                    AndroidView(
                        factory = { webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // The screen's size in dp laid over this one is the old screen's; the page comes back from its saved state.
        if (attachedConfiguration?.let { appScale.isStale(it, newConfig) } == true) recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::webView.isInitialized) return
        outState.putBundle(KEY_WEB_STATE, Bundle().also { webView.saveState(it) })
        signInTarget?.let { outState.putString(KEY_SIGN_IN_TARGET, it) }
    }

    override fun onDestroy() {
        signInJob?.cancel()
        pendingUpload?.deliver?.invoke(null)
        pendingUpload = null
        state.dialog?.answer?.invoke(false, null)
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    // ── The page ──────────────────────────────────────────────────────────

    /** The app's resolved theme: its Theme setting, or the phone's when that follows the system. */
    private fun appIsDark(systemDark: Boolean): Boolean = when (appState.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    private fun isNight(configuration: Configuration) =
        configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Tells the page [dark] through `prefers-color-scheme`: the WebView reads
     * it from its theme's isLightTheme, laid over here, when its settings
     * change, so a nudge to one passes it to the loaded page at once (spike
     * on API 37: the page's `matchMedia` flips and fires its change event,
     * no reload).
     */
    private fun tellPage(dark: Boolean) {
        if (toldDark == dark) return
        toldDark = dark
        pageTheme.theme.applyStyle(
            if (dark) R.style.ThemeOverlay_TigerDuck_BrowserPage_Dark else R.style.ThemeOverlay_TigerDuck_BrowserPage_Light,
            true,
        )
        if (!::webView.isInitialized) return
        when (SCHEME_FOLLOW) {
            PageSchemeFollow.LIVE -> pokeSettings()
            PageSchemeFollow.NEXT_LOAD -> schemePokePending = true
        }
        // The page's own colors may follow; read them again once it has.
        webView.postDelayed({ probeTone() }, 300)
    }

    private fun pokeSettings() {
        val zoom = webView.settings.textZoom
        webView.settings.textZoom = zoom + 1
        webView.settings.textZoom = zoom
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(pageTheme).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        mobileUserAgent = settings.userAgentString
        val web = this
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, false)
        }
        webViewClient = Client()
        webChromeClient = Chrome()
        setDownloadListener { url, _, contentDisposition, mimeType, _ -> onDownload(url, contentDisposition, mimeType) }
        setFindListener { ordinal, count, done ->
            state.find = state.find?.copy(ordinal = if (count == 0) 0 else ordinal + 1, count = count, done = done)
        }
        setOnLongClickListener { onLongPress() }
    }

    /** Opens [target]: signed in first where its site signs the browser in and no session is kept. */
    private fun open(target: String) {
        val provider = signIns.forUrl(target)
        if (provider == null || provider.hasSession(CookieManager.getInstance().getCookie(target))) {
            load(target)
            return
        }
        signInTarget = target
        // No page yet: the bars name the one on its way.
        if (state.url == null) state.url = target
        state.signIn = SignInState(provider.siteName, target, msLeft = 0, working = true, hasWebSignIn = provider.webSignInUrl(target) != null)
        signInJob?.cancel()
        signInJob = lifecycleScope.launch {
            val signedIn = provider.signedInUrl(target)
            if (signedIn != null) {
                state.signIn = null
                load(signedIn)
            } else {
                // TODO(#483): with no key due (one went to another open in the last 6 minutes) and no
                //  session kept, this screen is all there is; see
                //  https://github.com/tigerduck-app/tigerduck-app-android/issues/483 for the options.
                showSignIn(provider, target, failed = provider.msUntilRetry() == 0L)
            }
        }
    }

    /** Loads [url], one the app chose, on the same terms as a page's own navigation. */
    private fun load(url: String) {
        when (val decision = BrowserRouting.navigation(url, allowlistStore.allowlist.value)) {
            NavigationDecision.Load -> {
                state.error = null
                loadingUrl = url
                webView.loadUrl(url)
            }
            is NavigationDecision.OpenOutside -> state.outsideUrl = decision.url
            is NavigationDecision.HandOff -> openLink(this, decision.url, BrowserPreference.SYSTEM)
            NavigationDecision.Refuse -> Unit
        }
    }

    /** The browser's sign-in screen for [provider], counting down to the next try and trying when due. */
    private fun showSignIn(provider: BrowserSignIn, target: String, failed: Boolean = false) {
        signInTarget = target
        webView.stopLoading()
        state.signIn = SignInState(
            siteName = provider.siteName,
            target = target,
            msLeft = provider.msUntilRetry(),
            failed = failed,
            hasWebSignIn = provider.webSignInUrl(target) != null,
        )
        signInJob?.cancel()
        signInJob = lifecycleScope.launch {
            watchSignInRetry(
                msUntilRetry = provider::msUntilRetry,
                show = { left -> state.signIn?.let { state.signIn = it.copy(msLeft = left) } },
                retry = { trySignIn(provider, target) },
                stillShowing = { state.signIn?.target == target && state.signIn?.working == false },
            )
        }
    }

    private suspend fun trySignIn(provider: BrowserSignIn, target: String) {
        val current = state.signIn ?: return
        state.signIn = current.copy(working = true, failed = false, msLeft = 0)
        val signedIn = provider.signedInUrl(target)
        if (signedIn != null) {
            state.signIn = null
            load(signedIn)
        } else {
            showSignIn(provider, target, failed = provider.msUntilRetry() == 0L)
        }
    }

    /** What a main-frame navigation to [url] does; true when the browser takes it over and the page does not go. */
    private fun handleNavigation(url: String): Boolean {
        val provider = signIns.forUrl(url)
        if (provider != null && provider.isSignInPage(url)) {
            showSignIn(provider, signInTarget ?: state.url ?: url)
            return true
        }
        return when (val decision = BrowserRouting.navigation(url, allowlistStore.allowlist.value)) {
            NavigationDecision.Load -> {
                loadingUrl = url
                false
            }
            is NavigationDecision.OpenOutside -> {
                state.outsideUrl = decision.url
                true
            }
            is NavigationDecision.HandOff -> {
                openLink(this, decision.url, BrowserPreference.SYSTEM)
                true
            }
            NavigationDecision.Refuse -> true
        }
    }

    private fun onPageStart(url: String) {
        // A navigation shouldOverrideUrlLoading was not asked about (a form's POST): checked here, and stopped.
        val provider = signIns.forUrl(url)
        if (provider != null && provider.isSignInPage(url)) {
            showSignIn(provider, signInTarget ?: state.url ?: url)
            return
        }
        val decision = BrowserRouting.navigation(url, allowlistStore.allowlist.value)
        if (decision != NavigationDecision.Load) {
            webView.stopLoading()
            if (decision is NavigationDecision.OpenOutside) state.outsideUrl = decision.url
            if (webView.canGoBack()) webView.goBack()
            return
        }
        if (schemePokePending) {
            schemePokePending = false
            pokeSettings()
        }
        state.url = url
        state.loading = true
        state.certificate = null
        state.signIn = null
        applySiteSettings(url)
        updateHistory()
    }

    private fun onPageDone(url: String) {
        state.loading = false
        state.url = url
        state.title = webView.title
        state.certificate = webView.certificate?.let(CertificateInfo::of)
        state.signedIn = signIns.forUrl(url)?.hasSession(CookieManager.getInstance().getCookie(url)) == true
        updateHistory()
        probeTone()
    }

    private fun updateHistory() {
        state.canGoBack = webView.canGoBack()
        state.canGoForward = webView.canGoForward()
    }

    /** Reads how dark the page is, for the bars in Auto. */
    private fun probeTone() {
        val told = toldDark ?: false
        webView.evaluateJavascript(BrowserColors.TONE_PROBE) { result ->
            runCatching { BrowserColors.pageTone(result, told) }.getOrNull()?.let { state.pageDark = it }
        }
    }

    /** The zoom and desktop-site choice kept for [url]'s host, applied as the browser arrives there. */
    private fun applySiteSettings(url: String) {
        val host = url.toHttpUrlOrNull()?.host ?: return
        if (host == appliedHost) return
        appliedHost = host
        val zoom = settings.zoom(host)
        val desktop = settings.desktopSite(host)
        state.zoom = zoom
        webView.settings.textZoom = zoom
        if (desktop != state.desktopSite) {
            state.desktopSite = desktop
            applyUserAgent(desktop)
        }
    }

    private fun applyUserAgent(desktop: Boolean) {
        webView.settings.apply {
            userAgentString = if (desktop) BrowserUserAgent.desktop(mobileUserAgent) else mobileUserAgent
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
        }
    }

    private fun onLongPress(): Boolean {
        val hit = webView.hitTestResult
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> hit.extra?.let { state.linkMenuUrl = it }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // The hit names the image; the link around it comes back in a message.
                val handler = Handler(Looper.getMainLooper()) { message: Message ->
                    message.data.getString("url")?.takeIf { it.isNotBlank() }?.let { state.linkMenuUrl = it }
                    true
                }
                webView.requestFocusNodeHref(handler.obtainMessage())
            }
            else -> return false
        }
        return true
    }

    private fun onDownload(url: String, contentDisposition: String?, mimeType: String?) {
        val plan = BrowserDownloadPlans.plan(
            url = url,
            contentDisposition = contentDisposition,
            mimeType = mimeType,
            allowlist = allowlistStore.allowlist.value,
            guessFileName = URLUtil::guessFileName,
            mimeOfExtension = { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) },
        )
        when (plan) {
            is DownloadPlan.Save -> downloads.start(plan)
            DownloadPlan.Refused -> Toast.makeText(this, R.string.browser_download_refused, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Uploads ───────────────────────────────────────────────────────────

    private fun showUpload(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        pendingUpload?.deliver?.invoke(null)
        val accept = params.acceptTypes?.toList().orEmpty()
        var answered = false
        val request = UploadRequest(
            host = state.host,
            sources = BrowserUpload.sources(accept),
            imagesOnly = BrowserUpload.wantsImagesOnly(accept),
            acceptTypes = BrowserUpload.acceptedTypes(accept),
            multiple = params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE,
            deliver = { uris ->
                // The page waits on exactly one answer.
                if (!answered) {
                    answered = true
                    callback.onReceiveValue(uris)
                }
            },
        )
        pendingUpload = request
        state.upload = request
    }

    /** What a picker gave back, as far as the page may have it; see [BrowserUpload.safePicks]. */
    private fun deliverPicks(picks: List<Uri>, cameraShot: Uri? = null) =
        deliverUpload(BrowserUpload.safePicks(picks, cameraShot, ::isForeignProvider))

    /** Whether another app serves [authority]: not one of this app's, by name or by who serves it, and not nothing. */
    private fun isForeignProvider(authority: String): Boolean {
        if (authority == packageName || authority.startsWith("$packageName.")) return false
        val owner = runCatching { packageManager.resolveContentProvider(authority, 0)?.packageName }.getOrNull()
        return owner != null && owner != packageName
    }

    private fun deliverUpload(uris: Array<Uri>?) {
        pendingUpload?.deliver?.invoke(uris)
        pendingUpload = null
        state.upload = null
    }

    private fun pickUpload(source: UploadSource) {
        val request = pendingUpload ?: return
        state.upload = null
        when (source) {
            UploadSource.PHOTOS -> {
                val media = if (request.imagesOnly) ActivityResultContracts.PickVisualMedia.ImageOnly else ActivityResultContracts.PickVisualMedia.ImageAndVideo
                if (request.multiple) pickImages.launch(PickVisualMediaRequest(media)) else pickImage.launch(PickVisualMediaRequest(media))
            }
            UploadSource.CAMERA ->
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            UploadSource.FILES -> {
                val types = BrowserUpload.documentTypes(request.acceptTypes) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
                if (request.multiple) openDocuments.launch(types) else openDocument.launch(types)
            }
        }
    }

    private fun launchCamera() {
        val file = runCatching {
            File(cacheDir, UPLOADS_DIR).apply { mkdirs() }.let { File.createTempFile("photo_", ".jpg", it) }
        }.getOrNull()
        val uri = file?.let { runCatching { FileProvider.getUriForFile(this, fileProviderAuthority(this), it) }.getOrNull() }
        if (uri == null) {
            deliverUpload(null)
            return
        }
        cameraFile = uri
        runCatching { takePicture.launch(uri) }.onFailure { deliverUpload(null) }
    }

    // ── Back ──────────────────────────────────────────────────────────────

    private fun handleBack() {
        when {
            state.find != null -> actions.endFind()
            webView.canGoBack() -> {
                state.signIn = null
                state.error = null
                webView.goBack()
            }
            else -> finish()
        }
    }

    private fun toastBelowAndroid13(text: Int) {
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun copyText(label: String, text: String, toast: Int) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        toastBelowAndroid13(toast)
    }

    /** The phone's own browser, as the app's "Open links with: System" opens a link. */
    private fun openOutside(url: String) {
        openLink(this, url, BrowserPreference.SYSTEM)
    }

    private val actions = object : BrowserActions() {
        override fun close() = finish()
        override fun back() = handleBack()
        override fun forward() { if (webView.canGoForward()) webView.goForward() }
        override fun reload() {
            if (state.error != null) retry() else webView.reload()
        }
        override fun stop() {
            webView.stopLoading()
            state.loading = false
        }
        override fun startFind() { state.find = FindState() }
        override fun find(query: String) {
            state.find = (state.find ?: FindState()).copy(query = query, ordinal = 0, count = 0, done = query.isEmpty())
            if (query.isEmpty()) webView.clearMatches() else webView.findAllAsync(query)
        }
        override fun findNext(forward: Boolean) {
            if ((state.find?.count ?: 0) > 0) webView.findNext(forward)
        }
        override fun endFind() {
            webView.clearMatches()
            state.find = null
        }
        override fun share(url: String) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            state.title?.let { send.putExtra(Intent.EXTRA_SUBJECT, it) }
            runCatching { startActivity(Intent.createChooser(send, null)) }
        }
        override fun copy(url: String) = copyText(url, url, R.string.browser_copied)
        override fun openExternal(url: String) = openOutside(url)
        override fun openCurrentExternal() {
            (loadingUrl ?: state.url)?.let(::openOutside)
        }
        override fun open(url: String) {
            if (!handleNavigation(url)) load(url)
        }
        override fun zoomIn() = setZoom(BrowserZoom.zoomIn(state.zoom))
        override fun zoomOut() = setZoom(BrowserZoom.zoomOut(state.zoom))
        override fun zoomReset() = setZoom(BrowserZoom.DEFAULT)
        private fun setZoom(percent: Int) {
            state.zoom = percent
            webView.settings.textZoom = percent
            state.host?.let { settings.setZoom(it, percent) }
        }
        override fun setDesktopSite(desktop: Boolean) {
            state.desktopSite = desktop
            state.host?.let { settings.setDesktopSite(it, desktop) }
            applyUserAgent(desktop)
            webView.reload()
        }
        override fun setAppearance(appearance: BrowserAppearance) {
            this@InAppBrowserActivity.appearance = appearance
            settings.appearance = appearance
        }
        override fun print() {
            val name = state.title?.takeIf { it.isNotBlank() } ?: state.host ?: getString(R.string.app_name)
            val printer = getSystemService(PrintManager::class.java) ?: return
            runCatching { printer.print(name, webView.createPrintDocumentAdapter(name), null) }
        }
        override fun copyCertificate(text: String) = copyText(getString(R.string.browser_certificate_title), text, R.string.browser_copied)
        override fun cancelDownload(id: Long) = downloads.cancel(id)
        override fun openDownload(download: BrowserDownload) {
            val uri = download.uri ?: return
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, download.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (runCatching { startActivity(view) }.isFailure) {
                Toast.makeText(this@InAppBrowserActivity, R.string.browser_download_no_app, Toast.LENGTH_SHORT).show()
            }
        }
        override fun retry() {
            val url = loadingUrl ?: state.url ?: return
            state.error = null
            load(url)
        }
        override fun leaveFor(url: String) {
            state.outsideUrl = null
            openLink(this@InAppBrowserActivity, url, BrowserPreference.SYSTEM)
        }
        override fun stayHere() { state.outsideUrl = null }
        override fun closeLinkMenu() { state.linkMenuUrl = null }
        override fun answerDialog(confirmed: Boolean, value: String?) {
            val dialog = state.dialog ?: return
            state.dialog = null
            dialog.answer(confirmed, value)
        }
        override fun pickUpload(source: UploadSource) = this@InAppBrowserActivity.pickUpload(source)
        override fun cancelUpload() = deliverUpload(null)
        override fun signInAgain() {
            val target = state.signIn?.target ?: return
            val provider = signIns.forUrl(target) ?: return
            signInJob?.cancel()
            signInJob = lifecycleScope.launch { trySignIn(provider, target) }
        }
        override fun signInOnWeb() {
            val target = state.signIn?.target ?: return
            val web = signIns.forUrl(target)?.webSignInUrl(target) ?: return
            signInJob?.cancel()
            state.signIn = null
            load(web)
        }
    }

    // ── WebView callbacks ─────────────────────────────────────────────────

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && handleNavigation(request.url.toString())

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Off the main thread. A form's POST off the allowlist is stopped here, before it is
            // sent: shouldOverrideUrlLoading is never asked about one. What no WebView callback
            // sees is a redirect (307, 308) an allowlisted site answers a POST with: the site's own
            // choice, as any script on it can send anywhere too. The allowlist decides which pages
            // this browser shows, and onPageStart stops such a page as it starts.
            val url = request.url.toString()
            if (!BrowserRouting.blocksRequest(url, request.isForMainFrame, allowlistStore.allowlist.value)) return null
            view.post {
                when (val decision = BrowserRouting.navigation(url, allowlistStore.allowlist.value)) {
                    is NavigationDecision.OpenOutside -> state.outsideUrl = decision.url
                    else -> Unit
                }
            }
            // 204: the page stays as it was.
            return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            url?.let(::onPageStart)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            url?.let(::onPageDone)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            // A page that changes its address itself (history.pushState) lands here only.
            if (url != null && BrowserRouting.navigation(url, allowlistStore.allowlist.value) == NavigationDecision.Load) state.url = url
            updateHistory()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            state.loading = false
            state.error = BrowserError.Unreachable(request.url.host)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never past a bad certificate.
            handler.cancel()
            val url = error.url
            if (url == loadingUrl || url == state.url || state.loading) {
                state.loading = false
                state.error = BrowserError.BadCertificate(url?.toHttpUrlOrNull()?.host ?: state.host)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            // The WebView is dead with its renderer; this screen goes with it rather than take the app down.
            finish()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            state.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            state.title = title
        }

        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            showUpload(callback, params)
            return true
        }

        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            pageDialog(PageDialog.Kind.ALERT, url, message, null) { confirmed, _ -> if (confirmed) result.confirm() else result.cancel() }

        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean =
            pageDialog(PageDialog.Kind.CONFIRM, url, message, null) { confirmed, _ -> if (confirmed) result.confirm() else result.cancel() }

        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean =
            pageDialog(PageDialog.Kind.PROMPT, url, message, defaultValue) { confirmed, value -> if (confirmed) result.confirm(value.orEmpty()) else result.cancel() }

        private fun pageDialog(kind: PageDialog.Kind, url: String?, message: String?, defaultValue: String?, answer: (Boolean, String?) -> Unit): Boolean {
            // One at a time: one already up is answered as cancelled.
            state.dialog?.answer?.invoke(false, null)
            state.dialog = PageDialog(kind, url?.toHttpUrlOrNull()?.host, message.orEmpty(), defaultValue, answer)
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // No page gets the camera, microphone or anything else through the browser.
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }
    }

    companion object {
        /** How a page follows a new color scheme; spike 1 found the loaded page switches live. */
        val SCHEME_FOLLOW = PageSchemeFollow.LIVE

        /** Camera shots for uploads, under the cache; cleared with the browser's data. */
        const val UPLOADS_DIR = "browser_uploads"

        private const val KEY_WEB_STATE = "web_state"
        private const val KEY_SIGN_IN_TARGET = "sign_in_target"
    }
}
