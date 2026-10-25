package org.ntust.app.tigerduck.ui.screen.settings

import android.app.Application
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.data.moodle.MoodleFileDownloads
import org.ntust.app.tigerduck.data.storage.DownloadedFiles
import org.ntust.app.tigerduck.mail.store.MailCache
import org.ntust.app.tigerduck.util.replaceIosArg
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class DownloadedFilesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private val moodleFile get() = File(temp.root, "files/moodle_files/a1b2/1234/m/slot/0/Week 1.pdf")
    private val attachment get() = File(temp.root, "cache/mail/attachments/0b5e/Syllabus.pdf")
    private val image get() = File(temp.root, "cache/class_table_export/4f1c/class table.png")

    private fun write(file: File, bytes: Int) {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun size(bytes: Long) = Formatter.formatFileSize(composeRule.activity, bytes)

    private fun openPage() {
        write(moodleFile, 1_000_000)
        write(attachment, 300_000)
        write(image, 50_000)
        val files = DownloadedFiles(
            moodle = MoodleFileDownloads(root = { File(temp.root, "files/moodle_files") }, scope = scope, io = Dispatchers.Unconfined),
            mailAttachments = { MailCache(File(temp.root, "cache/mail")).attachmentsDir },
            attachmentsInUse = { emptySet() },
            classTableImages = { File(temp.root, "cache/class_table_export") },
            now = { System.currentTimeMillis() + 60 * 60 * 1000L },
            io = Dispatchers.Unconfined,
        )
        composeRule.setContent {
            MaterialTheme { DownloadedFilesScreen(onBack = {}, viewModel = DownloadedFilesViewModel(files, scope)) }
        }
        composeRule.waitUntil { composeRule.onAllNodesWithText(size(1_350_000)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the page shows the total and each kind`() {
        openPage()

        composeRule.onNodeWithText(size(1_350_000)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_moodle)).assertExists()
        composeRule.onNodeWithText(size(1_000_000)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_mail)).assertExists()
        composeRule.onNodeWithText(size(300_000)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_images)).assertExists()
        composeRule.onNodeWithText(size(50_000)).assertExists()
    }

    @Test
    fun `a row's delete takes only its kind, at once`() {
        openPage()

        // The rows go Moodle files, mail attachments, class table images.
        composeRule.onAllNodesWithText(string(R.string.action_delete)).onFirst().performClick()
        composeRule.waitUntil { !moodleFile.exists() }

        assertTrue(attachment.exists())
        assertTrue(image.exists())
        composeRule.waitUntil { composeRule.onAllNodesWithText(size(350_000)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `deleting all asks first, then takes every kind`() {
        openPage()

        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_delete_all)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_delete_all_title)).assertExists()
        assertTrue(moodleFile.exists())

        composeRule.onNode(hasText(string(R.string.action_delete)) and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitUntil { !moodleFile.exists() && !attachment.exists() && !image.exists() }

        assertFalse(image.exists())
    }

    @Test
    fun `talkback hears which kind each delete deletes`() {
        openPage()

        for (kind in listOf(R.string.settings_downloaded_files_moodle, R.string.settings_downloaded_files_mail, R.string.settings_downloaded_files_images)) {
            composeRule.onNodeWithContentDescription(string(R.string.settings_downloaded_files_delete_kind).replaceIosArg(string(kind))).assertExists()
        }
    }

    @Test
    fun `cancel leaves the files`() {
        openPage()

        composeRule.onNodeWithText(string(R.string.settings_downloaded_files_delete_all)).performClick()
        composeRule.onNodeWithText(string(R.string.action_cancel)).performClick()
        composeRule.waitForIdle()

        assertTrue(moodleFile.exists() && attachment.exists() && image.exists())
    }
}
