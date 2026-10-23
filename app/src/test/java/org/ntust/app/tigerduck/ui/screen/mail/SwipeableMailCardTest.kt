package org.ntust.app.tigerduck.ui.screen.mail

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.mail.mailSummary
import org.ntust.app.tigerduck.mail.model.MailRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What a mail's card on the list does when it is swiped, pressed and held, or acted on by TalkBack. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SwipeableMailCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(@StringRes id: Int) = RuntimeEnvironment.getApplication().getString(id)

    /** Every callback the card made, in order. */
    private val calls = mutableListOf<String>()

    private fun show(writing: Boolean = false, direction: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                SwipeableMailCard(
                    row = MailRow("INBOX", mailSummary(1)),
                    mailDomain = "mail.ntust.edu.tw",
                    writing = writing,
                    onClick = { calls += "open" },
                    onToggleRead = { calls += "read" },
                    onMove = { calls += "move" },
                    onDelete = { calls += "delete" },
                )
            }
        }
    }

    private fun card(): SemanticsNodeInteraction = composeRule.onNodeWithText("subject 1")

    private fun customActions(): List<String> =
        card().fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }.map { it.label }

    @Test
    fun `a card whose delete or move is still out offers nothing more`() {
        show(writing = true)

        card().assertIsNotEnabled()
        assertTrue("TalkBack can still act on it", customActions().isEmpty())
        card().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        card().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        card().performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.school_mail_move_to)).assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
    }
}
