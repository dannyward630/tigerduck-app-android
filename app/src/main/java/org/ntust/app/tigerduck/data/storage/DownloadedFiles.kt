package org.ntust.app.tigerduck.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ntust.app.tigerduck.data.moodle.MoodleFileDownloads
import org.ntust.app.tigerduck.mail.store.MailCache
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files the app downloaded and keeps where only it can see them, for Settings' "Downloaded
 * files": what each [Kind] takes, and deleting a kind or all of them. Nothing else the app keeps
 * goes with them: not the cached mail or its search index, not a mail waiting to send or the
 * files a send is staging. The TigerDuck browser saves its downloads to the phone's Downloads
 * folder, where they are the user's, so they are not counted here. As iOS's Downloaded files.
 *
 * A mail attachment or class table image written in the last [IN_USE_MILLIS] stays, and is not
 * counted: one may still be downloading, and another app may still be reading one it was handed
 * (a draft it was shared into, a viewer that opens it again). Nothing here follows a link out of
 * the folders it measures and deletes.
 */
@Singleton
class DownloadedFiles internal constructor(
    private val moodle: MoodleFileDownloads,
    private val mailAttachments: () -> File,
    /** The attachment folders a download is still writing into, by name ([MailCache.newAttachmentSlot]). */
    private val attachmentsInUse: () -> Set<String>,
    private val classTableImages: () -> File,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(moodle: MoodleFileDownloads, mail: MailCache, @ApplicationContext context: Context) :
        this(moodle, { mail.attachmentsDir }, mail::attachmentSlotsInUse, { File(context.cacheDir, CLASS_TABLE_EXPORT_DIR) })

    enum class Kind {
        /** Moodle's materials and attachments, and members' pictures ([MoodleFileDownloads]). */
        MOODLE_FILES,

        /** Attachments opened from School Mail, each in a folder of its own. */
        MAIL_ATTACHMENTS,

        /** Class table images made for sharing, each in a folder of its own. */
        CLASS_TABLE_IMAGES,
    }

    /** The bytes each kind takes, as far as deleting it would free them. Real IO: off the main thread. */
    suspend fun measure(): Map<Kind, Long> {
        val moodleBytes = moodle.measure()
        return withContext(io) {
            mapOf(
                Kind.MOODLE_FILES to moodleBytes,
                Kind.MAIL_ATTACHMENTS to settledAttachments().sumOf(::bytes),
                Kind.CLASS_TABLE_IMAGES to settled(classTableImages()).sumOf(::bytes),
            )
        }
    }

    /**
     * Deletes [kind]'s files. Moodle's downloads on their way stop first, as a sign-out stops
     * them; each file downloads again when it is next opened.
     */
    suspend fun delete(kind: Kind) {
        when (kind) {
            Kind.MOODLE_FILES -> moodle.clear()
            Kind.MAIL_ATTACHMENTS -> withContext(io) { settledAttachments().forEach(::delete) }
            Kind.CLASS_TABLE_IMAGES -> withContext(io) { settled(classTableImages()).forEach(::delete) }
        }
    }

    suspend fun deleteAll() {
        Kind.entries.forEach { delete(it) }
    }

    /**
     * The attachments' folders, but not one a download is still writing into, however long it has
     * waited, nor one a send is staging, which it still has to read.
     */
    private fun settledAttachments(): List<File> {
        val inUse = attachmentsInUse()
        return settled(mailAttachments()).filterNot { it.name in inUse || it.name.startsWith(MailCache.OUTGOING_PREFIX) }
    }

    /** The entries of [dir] nothing was written to in the last [IN_USE_MILLIS]. */
    private fun settled(dir: File): List<File> {
        val cutoff = now() - IN_USE_MILLIS
        return dir.listFiles().orEmpty().filter { lastWritten(it) < cutoff }
    }

    companion object {
        /** Under cacheDir, where the class table export writes its images. */
        const val CLASS_TABLE_EXPORT_DIR = "class_table_export"

        /** How long a file just written or handed to another app is left alone. As iOS's export sweep. */
        const val IN_USE_MILLIS = 10 * 60 * 1000L

        /** Walks [entry] without following links: a link is an entry of its own, not what it points to. */
        private fun walk(entry: File, visit: (Path, BasicFileAttributes) -> Unit, leave: (Path) -> Unit = {}) {
            if (!Files.exists(entry.toPath(), LinkOption.NOFOLLOW_LINKS)) return
            Files.walkFileTree(entry.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    visit(file, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    visit(dir, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    leave(dir)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            })
        }

        /** When anything in [entry] was last written, a folder's own entries included. */
        private fun lastWritten(entry: File): Long {
            var latest = 0L
            walk(entry, { _, attrs -> latest = maxOf(latest, attrs.lastModifiedTime().toMillis()) })
            return latest
        }

        private fun bytes(entry: File): Long {
            var total = 0L
            walk(entry, { _, attrs -> if (attrs.isRegularFile) total += attrs.size() })
            return total
        }

        /** Deletes [entry], files and links alike, and each folder once it is empty. */
        private fun delete(entry: File) {
            walk(
                entry,
                visit = { path, attrs -> if (!attrs.isDirectory) runCatching { Files.delete(path) } },
                leave = { dir -> runCatching { Files.delete(dir) } },
            )
        }
    }
}
