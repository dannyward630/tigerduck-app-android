package org.ntust.app.tigerduck.di

import android.content.Context
import android.webkit.WebSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ntust.app.tigerduck.browser.AndroidWebViewStorage
import org.ntust.app.tigerduck.browser.BrowserAllowlistStore
import org.ntust.app.tigerduck.browser.BrowserCleanup
import org.ntust.app.tigerduck.browser.BrowserDataCleaner
import org.ntust.app.tigerduck.browser.BrowserDownloads
import org.ntust.app.tigerduck.browser.BrowserSettings
import org.ntust.app.tigerduck.browser.BrowserSignIns
import org.ntust.app.tigerduck.browser.MediaStoreDownloadTransfer
import org.ntust.app.tigerduck.browser.MoodleBrowserSignIn
import org.ntust.app.tigerduck.browser.webViewCookies
import org.ntust.app.tigerduck.network.moodle.MoodleAutologin
import javax.inject.Singleton

/** The TigerDuck browser's stores and its sign-in providers. */
@Module
@InstallIn(SingletonComponent::class)
object BrowserModule {

    @Provides
    @Singleton
    fun provideAllowlistStore(@ApplicationContext context: Context): BrowserAllowlistStore = BrowserAllowlistStore.of(context)

    @Provides
    @Singleton
    fun provideBrowserSettings(@ApplicationContext context: Context): BrowserSettings = BrowserSettings(context)

    @Provides
    @Singleton
    fun provideBrowserDownloads(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        allowlist: BrowserAllowlistStore,
    ): BrowserDownloads = BrowserDownloads(
        scope = scope,
        transfer = MediaStoreDownloadTransfer(
            context = context,
            allowlist = { allowlist.allowlist.value },
            browserCookies = ::webViewCookies,
            userAgent = { runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull() },
        ),
    )

    @Provides
    @Singleton
    fun provideBrowserCleanup(@ApplicationContext context: Context): BrowserCleanup = BrowserCleanup.of(context)

    @Provides
    @Singleton
    fun provideBrowserDataCleaner(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        settings: BrowserSettings,
        downloads: BrowserDownloads,
        cleanup: BrowserCleanup,
    ): BrowserDataCleaner = BrowserDataCleaner(AndroidWebViewStorage(context), settings, downloads, cleanup, scope)

    /**
     * Who signs the browser in where: Moodle, through its autologin. A school
     * SSO provider would join this list; none is written yet.
     */
    @Provides
    @Singleton
    fun provideBrowserSignIns(autologin: MoodleAutologin): BrowserSignIns =
        BrowserSignIns(listOf(MoodleBrowserSignIn(autologin)))
}
