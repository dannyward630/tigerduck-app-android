package org.ntust.app.tigerduck.data

import android.app.Application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Holiday choices whose PUT has not landed ([PendingHolidayOverrides]). A
 * failed upload used to be only logged: the choice stayed on this phone, and
 * the next full sync, which replaced the set with the server's, took it back.
 * As the iPhone's `holidayOverridesAwaitingUpload`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PendingHolidayOverridesTest {

    private lateinit var prefs: AppPreferences
    private lateinit var holidays: PendingHolidayOverrides

    private val sent = mutableListOf<Pair<Int, Boolean>>()
    private var answer: suspend (Int, Boolean) -> Unit = { _, _ -> }

    /** TigerSync on. */
    private var syncs = true
    private var signInGeneration = 1
    private var now = 1_000_000L

    /** The sign-in each PUT was pinned to. */
    private val signIns = mutableListOf<Int>()

    @Before
    fun setUp() {
        prefs = AppPreferences(RuntimeEnvironment.getApplication())
        holidays = PendingHolidayOverrides(
            prefs = prefs,
            sender = HolidayOverrideSender { id, notify, signIn ->
                signIns += signIn
                sent += id to notify
                answer(id, notify)
            },
            syncs = { syncs },
            signInGeneration = { signInGeneration },
            clock = { now },
        )
    }

    /** The user's tap, as `CalendarViewModel.setNotifyOnHoliday` makes it: the local set first. */
    private suspend fun tap(id: Int, notify: Boolean) {
        val current = prefs.holidayNotifyOverrides
        prefs.holidayNotifyOverrides = if (notify) current + id else current - id
        holidays.send(id)
    }

    @Test
    fun `a choice whose PUT fails is kept, and the next sync sends today's value and lets it go`() = runTest {
        answer = { _, _ -> throw IOException("offline") }
        tap(3, notify = true)
        assertEquals(setOf(3), holidays.waitingIds)

        // Changed back while it waited: the retry sends what the user wants now.
        prefs.holidayNotifyOverrides = emptySet()
        answer = { _, _ -> }
        holidays.retry()

        assertEquals(listOf(3 to true, 3 to false), sent)
        assertEquals(emptySet<Int>(), holidays.waitingIds)
    }

    @Test
    fun `a choice that lands is let go at once`() = runTest {
        tap(3, notify = true)

        assertEquals(listOf(3 to true), sent)
        assertEquals(listOf(1), signIns)
        assertEquals(emptySet<Int>(), holidays.waitingIds)
    }

    @Test
    fun `with TigerSync off a choice goes nowhere and is not kept`() = runTest {
        syncs = false
        tap(3, notify = true)
        holidays.retry()

        assertEquals(emptyList<Pair<Int, Boolean>>(), sent)
        assertEquals(emptySet<Int>(), holidays.waitingIds)
    }

    @Test
    fun `a choice changed while its PUT is under way stays waiting`() = runTest {
        val gate = CompletableDeferred<Unit>()
        answer = { _, _ -> gate.await() }
        val first = launch { tap(3, notify = true) }
        testScheduler.runCurrent()

        // Tapped again mid-request: the server is about to hold the older value.
        prefs.holidayNotifyOverrides = emptySet()
        gate.complete(Unit)
        first.join()

        assertEquals(setOf(3), holidays.waitingIds)
    }

    @Test
    fun `a PUT asked before a sign-out is neither sent nor settled as the next account`() = runTest {
        val gate = CompletableDeferred<Unit>()
        answer = { _, _ -> gate.await() }
        val first = launch { tap(3, notify = true) }
        testScheduler.runCurrent()
        val second = launch { tap(4, notify = true) }
        testScheduler.runCurrent()

        // Signed out and in as someone else while 3 was under way and 4 waited behind it.
        signInGeneration = 2
        prefs.holidayOverridesAwaitingUpload = setOf(4)
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals("4 never went up as the next account", listOf(3 to true), sent)
        assertEquals("nor was the next account's waiting set let go by 3's answer", setOf(4), holidays.waitingIds)
    }

    @Test
    fun `a sync snapshot keeps the choices still waiting as this phone has them`() = runTest {
        answer = { _, _ -> throw IOException("offline") }
        tap(3, notify = true)
        tap(5, notify = false)
        now += 1_000

        // The server knows 5 and 9 but not 3, and still has the 5 this phone took back.
        val merged = holidays.merged(snapshot = setOf(5, 9), fetchedAtMs = now, local = prefs.holidayNotifyOverrides)

        assertEquals(setOf(3, 9), merged)
    }

    @Test
    fun `a snapshot fetched before the last choice, or during a PUT, is not applied`() = runTest {
        tap(3, notify = true)
        assertNull("fetched before the tap", holidays.merged(emptySet(), fetchedAtMs = now - 1, local = setOf(3)))

        val gate = CompletableDeferred<Unit>()
        answer = { _, _ -> gate.await() }
        now += 1_000
        val tapping = launch { tap(4, notify = true) }
        testScheduler.runCurrent()
        now += 1_000
        assertNull("a PUT under way", holidays.merged(emptySet(), fetchedAtMs = now, local = setOf(3, 4)))

        gate.complete(Unit)
        tapping.join()
        now += 1_000
        assertEquals(setOf(3, 4), holidays.merged(setOf(3, 4), fetchedAtMs = now, local = setOf(3, 4)))
    }

    @Test
    fun `the sign-out forgets both sets`() {
        prefs.holidayNotifyOverrides = setOf(3)
        prefs.holidayOverridesAwaitingUpload = setOf(3)

        prefs.forgetHolidayOverrides()

        assertEquals(emptySet<Int>(), prefs.holidayNotifyOverrides)
        assertEquals(emptySet<Int>(), prefs.holidayOverridesAwaitingUpload)
    }
}
