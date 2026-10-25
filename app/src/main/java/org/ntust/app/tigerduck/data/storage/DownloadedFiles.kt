package org.ntust.app.tigerduck.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ntust.app.tigerduck.data.moodle.MoodleFileDownloads
import org.ntust.app.tigerduck.mail.store.MailCache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files the app downloaded and keeps where only it can see them, for Settings' "Downloaded
 * files": what each [Kind] takes, and deleting a kind or all of them. Nothing else the app keeps
 * goes with them: not the cached mail or its search index, not a mail waiting to send or the
 * files a send is staging. The TigerDuck browser saves its downloads to the phone's Downloads
 * folder, where they are the user's, so they are not counted here. As iOS's Downloaded files.
 */
@Singleton
class DownloadedFiles internal constructor(
    private val moodle: MoodleFileDownloads,
    private val mail: MailCache,
    private val classTableImages: () -> File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    @Inject
    constructor(moodle: MoodleFileDownloads, mail: MailCache, @ApplicationContext context: Context) :
        this(moodle, mail, { File(context.cacheDir, CLASS_TABLE_EXPORT_DIR) })

    enum class Kind {
        /** Moodle's materials and attachments, and members' pictures ([MoodleFileDownloads]). */
        MOODLE_FILES,

        /** Attachments opened from School Mail. */
        MAIL_ATTACHMENTS,

        /** Class table images made for sharing, which the next export sweeps after a day. */
        CLASS_TABLE_IMAGES,
    }

    /** The bytes each kind takes. Real IO: off the main thread. */
    suspend fun measure(): Map<Kind, Long> {
        val moodleBytes = moodle.measure()
        return withContext(io) {
            mapOf(
                Kind.MOODLE_FILES to moodleBytes,
                Kind.MAIL_ATTACHMENTS to mail.attachmentsBytes(),
                Kind.CLASS_TABLE_IMAGES to bytes(classTableImages()),
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
            Kind.MAIL_ATTACHMENTS -> withContext(io) { mail.clearAttachments() }
            Kind.CLASS_TABLE_IMAGES -> withContext(io) { classTableImages().deleteRecursively() }
        }
    }

    suspend fun deleteAll() {
        Kind.entries.forEach { delete(it) }
    }

    private fun bytes(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        /** Under cacheDir, where the class table export writes its images. */
        const val CLASS_TABLE_EXPORT_DIR = "class_table_export"
    }
}
