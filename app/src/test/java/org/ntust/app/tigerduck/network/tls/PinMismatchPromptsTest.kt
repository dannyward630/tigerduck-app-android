package org.ntust.app.tigerduck.network.tls

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.shared.tls.PinDomain
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config

/** The prompt queue (TLS pins spec, A6 and D5). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PinMismatchPromptsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = PinAcceptanceStore(context.getSharedPreferences(PinAcceptanceStore.FILE, Context.MODE_PRIVATE), 30)
    private var now = 1_000_000L
    private val continued = mutableListOf<String>()
    private val prompts = PinMismatchPrompts(store, { now }) { continued += it.setId }

    private val school = PinTestChain().mismatch()
    private val library = PinTestChain(host = "api.lib.ntust.edu.tw").mismatch("library", listOf(PinDomain("api.lib.ntust.edu.tw", true)))

    @Test
    fun `one prompt at a time, the next after it is answered`() {
        prompts.queue(school)
        prompts.queue(library)

        assertEquals("ntust", prompts.pending.value?.setId)
        prompts.cancel()
        assertEquals("library", prompts.pending.value?.setId)
        prompts.continueToSite()
        assertNull(prompts.pending.value)
        assertEquals(listOf("library"), continued)
    }

    @Test
    fun `a set is asked about once per launch`() {
        prompts.queue(school)
        prompts.continueToSite()

        prompts.queue(school)

        assertNull(prompts.pending.value)
    }

    @Test
    fun `a set queued twice is asked about once`() {
        prompts.queue(school)
        prompts.queue(school)
        prompts.cancel()

        assertNull(prompts.pending.value)
    }

    @Test
    fun `after a Cancel the set stays quiet for an hour, past the next launch`() {
        prompts.queue(school)
        prompts.cancel()

        val nextLaunch = PinMismatchPrompts(store, { now }) {}
        now += PinAcceptanceStore.QUIET_MS - 1
        nextLaunch.queue(school)
        assertNull(nextLaunch.pending.value)

        now += 1
        nextLaunch.queue(school)
        assertEquals("ntust", nextLaunch.pending.value?.setId)
    }

    @Test
    fun `a waiting request hears the answer`() {
        prompts.queue(school)
        val waiting = CompletableFuture.supplyAsync { prompts.awaitAnswer("ntust", 5_000) }
        Thread.sleep(50)

        prompts.continueToSite()

        assertTrue(waiting.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a Cancel, a timeout or no prompt answers no`() {
        assertFalse("nothing queued", prompts.awaitAnswer("ntust", 10))
        prompts.queue(school)
        assertFalse("timed out", prompts.awaitAnswer("ntust", 10))
        val waiting = CompletableFuture.supplyAsync { prompts.awaitAnswer("ntust", 5_000) }
        Thread.sleep(50)

        prompts.cancel()

        assertFalse(waiting.get(5, TimeUnit.SECONDS))
        assertTrue(continued.isEmpty())
    }

    @Test
    fun `a prompt queued in the background waits for the foreground`() {
        // Nothing shows it until the app's root composes; it stays pending meanwhile.
        prompts.queue(school)
        assertEquals(school, prompts.pending.value)
    }
}
