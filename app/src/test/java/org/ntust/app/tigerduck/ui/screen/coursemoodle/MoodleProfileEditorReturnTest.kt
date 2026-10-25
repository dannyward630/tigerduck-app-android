package org.ntust.app.tigerduck.ui.screen.coursemoodle

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The member page after Moodle's profile editor: the members are fetched again on coming back
 * from it, so the edit shows, as on the iPhone (tigerduck-app#802, #806), and on no other return.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MoodleProfileEditorReturnTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The page's back stack entry, which the test pauses and resumes as the editor opens and closes. */
    private val page = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private fun leaveAndComeBack() {
        composeRule.runOnIdle {
            page.registry.currentState = Lifecycle.State.STARTED
            page.registry.currentState = Lifecycle.State.RESUMED
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the members are fetched again once on coming back from the editor, and on no other return`() {
        var editing by mutableStateOf(false)
        var fetches = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides page) {
                RefetchWhenBackFromEditor(editing, done = { editing = false }, refetch = { fetches++ })
            }
        }

        leaveAndComeBack()
        assertEquals("a return from anywhere else fetches nothing", 0, fetches)

        // The pencil opens the editor.
        composeRule.runOnIdle { editing = true }
        composeRule.waitForIdle()
        assertEquals("not before the editor closes", 0, fetches)

        leaveAndComeBack()
        assertEquals(1, fetches)
        assertFalse(editing)

        leaveAndComeBack()
        assertEquals("once per edit", 1, fetches)
    }
}
