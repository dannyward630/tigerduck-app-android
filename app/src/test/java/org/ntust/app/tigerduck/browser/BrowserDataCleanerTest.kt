package org.ntust.app.tigerduck.browser

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator

/** Clearing everything the browser keeps about the student, and the certificate's fingerprint. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BrowserDataCleanerTest {

    @Test
    fun `clearing ends the session, the downloads list, each site's choices and the WebView's stores`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val settings = BrowserSettings(context).apply {
            appearance = BrowserAppearance.DARK
            setZoom("moodle2.ntust.edu.tw", 150)
        }
        val never = CompletableDeferred<android.net.Uri>()
        val downloads = BrowserDownloads(this) { _, _ -> never.await() }
        downloads.start(DownloadPlan.Save("https://moodle2.ntust.edu.tw/a.pdf", "a.pdf", "application/pdf"))
        var storageCleared = 0
        val before = BrowserSession.generation.value

        BrowserDataCleaner(storage = { storageCleared++ }, settings = settings, downloads = downloads).clear()
        advanceUntilIdle()

        assertEquals(before + 1, BrowserSession.generation.value)
        assertTrue(downloads.items.value.isEmpty())
        assertEquals(BrowserZoom.DEFAULT, settings.zoom("moodle2.ntust.edu.tw"))
        assertEquals(BrowserAppearance.DARK, settings.appearance)
        assertEquals(1, storageCleared)
    }

    @Test
    fun `a WebView store that fails to clear does not stop the rest`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val settings = BrowserSettings(context).apply { setDesktopSite("moodle2.ntust.edu.tw", true) }
        val downloads = BrowserDownloads(this) { _, _ -> error("unused") }

        BrowserDataCleaner(storage = { throw IllegalStateException("no WebView") }, settings = settings, downloads = downloads).clear()

        assertEquals(false, settings.desktopSite("moodle2.ntust.edu.tw"))
    }

    @Test
    fun `the fingerprint is SHA-256 over the certificate as colon-separated hex`() {
        // SHA-256 of the three bytes "abc".
        assertEquals(
            "BA:78:16:BF:8F:01:CF:EA:41:41:40:DE:5D:AE:22:23:B0:03:61:A3:96:17:7A:9C:B4:10:FF:61:F2:00:15:AD",
            CertificateInfo.fingerprint("abc".toByteArray()),
        )
        // Unrelated input, unrelated output: a stand-in for a certificate's encoding.
        val encoded = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public.encoded
        assertEquals(32 * 3 - 1, CertificateInfo.fingerprint(encoded).length)
    }
}
