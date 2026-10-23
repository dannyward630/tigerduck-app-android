package org.ntust.app.tigerduck.ui.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LeaveGuardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val guard = LeaveGuard()
    private var moves = 0
    private val move = { moves++; Unit }

    @Test
    fun `with no page asking, a tab or a link moves at once`() {
        guard.leave(move)

        assertEquals(1, moves)
    }

    @Test
    fun `a page asking holds the move until it lets it go`() {
        var held: (() -> Unit)? = null
        composeRule.setContent { AskBeforeLeaving(guard) { held = it } }
        composeRule.waitForIdle()

        composeRule.runOnIdle { guard.leave(move) }
        assertEquals("waits on Save draft or Discard", 0, moves)

        assertNotNull(held)
        held!!()
        assertEquals(1, moves)
    }

    @Test
    fun `a page that keeps its user, as Keep editing does, drops the move`() {
        composeRule.setContent { AskBeforeLeaving(guard) { } }
        composeRule.waitForIdle()

        composeRule.runOnIdle { guard.leave(move) }

        assertEquals(0, moves)
    }

    @Test
    fun `a page gone from the screen asks nothing more`() {
        val shown = mutableStateOf(true)
        composeRule.setContent { if (shown.value) AskBeforeLeaving(guard) { } }
        composeRule.waitForIdle()

        shown.value = false
        composeRule.waitForIdle()
        composeRule.runOnIdle { guard.leave(move) }

        assertEquals(1, moves)
    }
}
