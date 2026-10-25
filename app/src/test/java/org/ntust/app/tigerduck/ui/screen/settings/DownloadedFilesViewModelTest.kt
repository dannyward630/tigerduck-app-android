package org.ntust.app.tigerduck.ui.screen.settings

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.moodle.MoodleFileDownloads
import org.ntust.app.tigerduck.data.storage.DownloadedFiles
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadedFilesViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Stands in for the app's own scope, which outlives every page. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        appScope.cancel()
        Dispatchers.resetMain()
    }

    private val file get() = File(temp.root, "files/moodle_files/a1b2/1234/m/slot/0/Week 1.pdf")

    private fun files(): DownloadedFiles {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(1_000))
        return DownloadedFiles(
            moodle = MoodleFileDownloads(root = { File(temp.root, "files/moodle_files") }, scope = appScope, io = Dispatchers.Unconfined),
            mailAttachments = { File(temp.root, "cache/mail/attachments") },
            attachmentsInUse = { emptySet() },
            classTableImages = { File(temp.root, "cache/class_table_export") },
            io = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `a confirmed delete still happens when the page is already gone`() {
        val viewModel = DownloadedFilesViewModel(files(), appScope)
        // Back pressed as the dialog closed: the page's own scope is gone.
        viewModel.viewModelScope.cancel()

        viewModel.deleteAll()

        assertFalse(file.exists())
    }

    @Test
    fun `the page open when a delete from an earlier one ends shows it`() {
        val files = files()
        val earlier = DownloadedFilesViewModel(files, appScope)
        val later = DownloadedFilesViewModel(files, appScope)
        later.measure()
        assertEquals(1_000L, later.sizes.value?.get(DownloadedFiles.Kind.MOODLE_FILES))

        earlier.deleteAll()

        assertEquals(0L, later.sizes.value?.get(DownloadedFiles.Kind.MOODLE_FILES))
    }
}
