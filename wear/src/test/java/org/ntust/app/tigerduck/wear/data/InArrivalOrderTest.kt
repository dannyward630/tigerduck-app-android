package org.ntust.app.tigerduck.wear.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

/**
 * [InArrivalOrder], which the Data Layer listener hands each callback's
 * payloads to: an older schedule must never be stored over a newer one.
 */
class InArrivalOrderTest {

    @Test
    fun `work runs in the order it came, even when the first takes longer`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val done = Collections.synchronizedList(mutableListOf<String>())
        val inOrder = InArrivalOrder(scope)

        inOrder.submit {
            delay(200)
            done += "older"
        }
        inOrder.submit { done += "newer" }

        withTimeout(5_000) { while (done.size < 2) delay(10) }
        assertEquals(listOf("older", "newer"), done.toList())
        scope.cancel()
    }
}
