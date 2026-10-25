package org.ntust.app.tigerduck.data.storage

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.moodle.MoodleFileDownloads
import org.ntust.app.tigerduck.data.storage.DownloadedFiles.Kind
import org.ntust.app.tigerduck.mail.store.MailCache
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * Settings' "Downloaded files": what each kind of file the app downloaded takes, and deleting a
 * kind, or all of them, without touching anything else the app or the user keeps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadedFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val files get() = File(temp.root, "files")
    private val cache get() = File(temp.root, "cache")
    private val moodleRoot get() = File(files, "moodle_files")
    private val mailRoot get() = File(cache, "mail")
    private val imagesRoot get() = File(cache, DownloadedFiles.CLASS_TABLE_EXPORT_DIR)

    private val now = 1_800_000_000_000L

    /**
     * [bytes] at [file], it and its folders last written [ago] ms before [now]: an hour, unless a
     * test says otherwise.
     */
    private fun write(file: File, bytes: Int, ago: Long = 60 * 60 * 1000L): File {
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(bytes))
        var touched: File? = file
        while (touched != null && touched != temp.root) {
            touched.setLastModified(now - ago)
            touched = touched.parentFile
        }
        return file
    }

    private val mailCache by lazy { MailCache(mailRoot) }

    private fun downloaded(scope: kotlinx.coroutines.CoroutineScope) = DownloadedFiles(
        moodle = MoodleFileDownloads(root = { moodleRoot }, scope = scope, io = Dispatchers.Unconfined),
        mailAttachments = { mailCache.attachmentsDir },
        attachmentsInUse = mailCache::attachmentSlotsInUse,
        classTableImages = { imagesRoot },
        now = { now },
        io = Dispatchers.Unconfined,
    )

    /** One of each kind, and the files around them that are not downloads. */
    private fun fill() {
        write(File(moodleRoot, "a1b2/1234/m/slot/0/Week 1.pdf"), 1_000)
        write(File(moodleRoot, "a1b2/pictures/77-1"), 200)
        write(File(mailRoot, "attachments/0b5e/Syllabus.pdf"), 3_000)
        write(File(imagesRoot, "4f1c/class table.png"), 500)
    }

    // A send stages a reply's attachments here while it goes out.
    private val staged get() = File(mailRoot, "attachments/outgoing-9d2e/1-Report.pdf")
    private val cachedMail get() = File(mailRoot, "folders/inbox.json")
    private val browserDownload get() = File(temp.root, "Download/Handout.pdf")
    private val heldSend get() = File(temp.root, "no_backup/mail_held_sends/held.json")

    @Test
    fun `each kind is measured on its own`() = runTest {
        fill()

        val sizes = downloaded(backgroundScope).measure()

        assertEquals(mapOf(Kind.MOODLE_FILES to 1_200L, Kind.MAIL_ATTACHMENTS to 3_000L, Kind.CLASS_TABLE_IMAGES to 500L), sizes)
    }

    @Test
    fun `deleting one kind leaves the others`() = runTest {
        fill()
        val downloaded = downloaded(backgroundScope)

        downloaded.delete(Kind.MOODLE_FILES)

        assertEquals(mapOf(Kind.MOODLE_FILES to 0L, Kind.MAIL_ATTACHMENTS to 3_000L, Kind.CLASS_TABLE_IMAGES to 500L), downloaded.measure())
    }

    @Test
    fun `mail attachments go, the cached mail and a send's staged files stay`() = runTest {
        fill()
        write(staged, 4_000)
        write(cachedMail, 50)
        val downloaded = downloaded(backgroundScope)
        assertEquals(3_000L, downloaded.measure()[Kind.MAIL_ATTACHMENTS])

        downloaded.delete(Kind.MAIL_ATTACHMENTS)

        assertEquals(0L, downloaded.measure()[Kind.MAIL_ATTACHMENTS])
        assertTrue(staged.exists())
        assertTrue(cachedMail.exists())
    }

    @Test
    fun `deleting everything takes every kind and nothing else`() = runTest {
        fill()
        write(staged, 4_000)
        write(cachedMail, 50)
        write(browserDownload, 700)
        write(heldSend, 60)
        val downloaded = downloaded(backgroundScope)

        downloaded.deleteAll()

        assertEquals(Kind.entries.associateWith { 0L }, downloaded.measure())
        assertTrue(staged.exists())
        assertTrue(cachedMail.exists())
        assertTrue(browserDownload.exists())
        assertTrue(heldSend.exists())
    }

    @Test
    fun `an attachment opened in the last few minutes stays, and isn't counted yet`() = runTest {
        // Another app may still be reading it, or it may still be downloading.
        val opened = write(File(mailRoot, "attachments/77aa/Slides.pdf"), 2_000, ago = 60_000)
        write(File(mailRoot, "attachments/0b5e/Syllabus.pdf"), 3_000)
        val downloaded = downloaded(backgroundScope)
        assertEquals(3_000L, downloaded.measure()[Kind.MAIL_ATTACHMENTS])

        downloaded.delete(Kind.MAIL_ATTACHMENTS)

        assertTrue(opened.exists())
        assertFalse(File(mailRoot, "attachments/0b5e").exists())
    }

    @Test
    fun `an attachment still waiting to download stays, however long it waits`() = runTest {
        // Its file is open from the start, and the download may wait its turn for the mail session.
        val slot = mailCache.newAttachmentSlot()
        val waiting = write(File(slot, "Big.zip"), 10, ago = 60 * 60 * 1000L)
        val downloaded = downloaded(backgroundScope)

        downloaded.delete(Kind.MAIL_ATTACHMENTS)

        assertTrue(waiting.exists())
        mailCache.releaseAttachmentSlot(slot)
        downloaded.delete(Kind.MAIL_ATTACHMENTS)
        assertFalse(waiting.exists())
    }

    @Test
    fun `a class table image shared in the last few minutes stays`() = runTest {
        val shared = write(File(imagesRoot, "9e2b/class table.png"), 800, ago = 60_000)
        write(File(imagesRoot, "4f1c/class table.png"), 500)
        val downloaded = downloaded(backgroundScope)

        downloaded.delete(Kind.CLASS_TABLE_IMAGES)

        assertTrue(shared.exists())
        assertEquals(0L, downloaded.measure()[Kind.CLASS_TABLE_IMAGES])
    }

    @Test
    fun `a link in a folder goes, not what it points to`() = runTest {
        write(heldSend, 60)
        imagesRoot.mkdirs()
        val link = File(imagesRoot, "4f1c")
        Files.createSymbolicLink(link.toPath(), heldSend.parentFile!!.toPath())
        val downloaded = downloaded(backgroundScope)
        assertEquals(0L, downloaded.measure()[Kind.CLASS_TABLE_IMAGES])

        downloaded.delete(Kind.CLASS_TABLE_IMAGES)

        assertTrue(heldSend.exists())
    }

    @Test
    fun `nothing downloaded yet measures as nothing`() = runTest {
        assertEquals(Kind.entries.associateWith { 0L }, downloaded(backgroundScope).measure())
    }
}
