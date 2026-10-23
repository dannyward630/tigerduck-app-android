package org.ntust.app.tigerduck.ui.screen.mail

import android.app.Application
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The mail settings' row for the mail downloaded for search, and the question before deleting it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [Build.VERSION_CODES.TIRAMISU])
class DownloadedMailRowTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `deleting asks first, and only Delete in the question deletes`() {
        var deletes = 0
        composeRule.setContent { DownloadedMailRow(bytes = 5_000_000L, onDelete = { deletes++ }) }
        composeRule.onNodeWithText(text(R.string.school_mail_storage_title)).assertExists()

        composeRule.onNodeWithText(text(R.string.school_mail_delete)).performClick()
        composeRule.onNodeWithText(text(R.string.school_mail_storage_delete_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.school_mail_storage_delete_message)).assertExists()
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertEquals(0, deletes)
        composeRule.onNodeWithText(text(R.string.school_mail_storage_delete_title)).assertDoesNotExist()

        composeRule.onNodeWithText(text(R.string.school_mail_delete)).performClick()
        // The row's Delete and the question's: the question's is the last one drawn.
        val deleteButtons = composeRule.onAllNodesWithText(text(R.string.school_mail_delete))
        deleteButtons[1].performClick()
        assertEquals(1, deletes)
        composeRule.onNodeWithText(text(R.string.school_mail_storage_delete_title)).assertDoesNotExist()
    }

    @Test
    fun `nothing downloaded, or not measured yet, leaves nothing to delete`() {
        var bytes by mutableStateOf<Long?>(null)
        composeRule.setContent { DownloadedMailRow(bytes = bytes, onDelete = {}) }
        composeRule.onNodeWithText(text(R.string.school_mail_delete)).assertIsNotEnabled()
        bytes = 0L
        composeRule.onNodeWithText(text(R.string.school_mail_delete)).assertIsNotEnabled()
        bytes = 1L
        composeRule.onNodeWithText(text(R.string.school_mail_delete)).assertIsEnabled()
    }
}
