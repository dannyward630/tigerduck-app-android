package org.ntust.app.tigerduck.ui.screen.files

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.DateUtils
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.files.ArchiveItem
import org.ntust.app.tigerduck.files.ArchiveLimits
import org.ntust.app.tigerduck.files.ArchiveListing
import org.ntust.app.tigerduck.files.ArchiveNames
import org.ntust.app.tigerduck.files.ArchiveNode
import org.ntust.app.tigerduck.files.ArchiveProblem
import org.ntust.app.tigerduck.files.ArchiveProblemException
import org.ntust.app.tigerduck.files.ArchiveSource
import org.ntust.app.tigerduck.files.ArchiveTree
import org.ntust.app.tigerduck.files.Archives
import org.ntust.app.tigerduck.files.FileOpening
import org.ntust.app.tigerduck.files.FileViewer
import org.ntust.app.tigerduck.files.FileViewing
import org.ntust.app.tigerduck.util.fileProviderAuthority
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.util.UUID
import kotlin.concurrent.thread

/**
 * An archive ([Archives]): what it holds, a folder at a time, each file with its size and date.
 * Tapping a file takes out only it, into this viewer's own folder in the cache, and opens it as a
 * downloaded file opens: in the viewer, an archive inside too, or in another app. One that can't
 * be taken out says why, offering the archive to another app. The folder goes when the viewer
 * closes; [onFailed] gets the reason an archive can't be listed, null when there is none to give.
 */
@Composable
internal fun ArchiveViewer(file: ViewedFile, onFailed: (reason: Int?) -> Unit, onOpenElsewhere: () -> Unit) {
    val context = LocalContext.current
    // Kept through a recreation, so the files already taken out are found again.
    val session = rememberSaveable(file.uri) { UUID.randomUUID().toString() }
    val folder = remember(session) { ArchiveCache.folder(context, session) }
    DisposableEffect(folder) {
        onDispose {
            if (context.findActivity()?.isFinishing != false) thread(name = "archive-cache") { folder.deleteRecursively() }
        }
    }

    var listing by remember(file.uri) { mutableStateOf<ArchiveListing?>(null) }
    val source = remember(file.uri) { lazy { context.archiveSource(file.uri, file.name, folder) } }
    LaunchedEffect(file.uri) {
        try {
            listing = reading {
                ArchiveCache.sweep(context, keep = folder)
                Archives.list(source.value)
            }
        } catch (e: ArchiveProblemException) {
            onFailed(e.problem.reason())
        } catch (_: IOException) {
            onFailed(null)
        }
    }
    val shown = listing ?: return Reading()

    var path by rememberSaveable(file.uri) { mutableStateOf("") }
    val nodes = remember(shown, path) { ArchiveTree.children(shown.items, path) }
    val scope = rememberCoroutineScope()
    var taking by remember { mutableStateOf<Job?>(null) }
    var problem by remember { mutableStateOf<ArchiveProblem?>(null) }
    val taken = remember(folder) { mutableMapOf<Int, File>() }

    // Back stops a file being taken out, then goes up a folder, before it leaves.
    BackHandler(enabled = taking != null || path.isNotEmpty()) {
        taking?.let { it.cancel(); taking = null } ?: run { path = ArchiveTree.parent(path) }
    }

    fun open(item: ArchiveItem) {
        if (taking != null) return
        item.problem?.let { problem = it; return }
        taking = scope.launch {
            try {
                val out = taken[item.index]?.takeIf { it.exists() }
                    ?: reading { Archives.extract(source.value, item, folder) }.also { taken[item.index] = it }
                openTaken(context, out, item)
            } catch (e: ArchiveProblemException) {
                problem = e.problem
            } catch (_: IOException) {
                problem = ArchiveProblem.BROKEN
            } finally {
                taking = null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (taking != null) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))
        if (path.isNotEmpty()) FolderBar(path) { path = ArchiveTree.parent(path) }
        if (nodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.file_viewer_archive_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(nodes, key = { node -> if (node is ArchiveNode.File) "f${node.item.index}" else "d${(node as ArchiveNode.Folder).path}" }) { node ->
                    when (node) {
                        is ArchiveNode.Folder -> FolderRow(node) { path = node.path }
                        is ArchiveNode.File -> FileRow(node, enabled = taking == null) { open(node.item) }
                    }
                }
                if (shown.truncated) {
                    item {
                        Text(
                            stringResource(R.string.file_viewer_archive_truncated, NumberFormat.getIntegerInstance().format(shown.items.size)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }

    problem?.let { shownProblem ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(stringResource(R.string.file_viewer_cannot_show)) },
            text = shownProblem.reason()?.let { reason -> { Text(stringResource(reason)) } },
            confirmButton = {
                TextButton(onClick = { problem = null; onOpenElsewhere() }) { Text(stringResource(R.string.file_viewer_open_elsewhere)) }
            },
            dismissButton = { TextButton(onClick = { problem = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}

/** The folder being shown, under the bar, with the way up. */
@Composable
private fun FolderBar(path: String, onUp: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.file_viewer_archive_up))
        }
        Text(
            path.replace("/", " › "),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.StartEllipsis,
        )
    }
    HorizontalDivider()
}

@Composable
private fun FolderRow(folder: ArchiveNode.Folder, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = stringResource(R.string.file_viewer_archive_folder)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun FileRow(node: ArchiveNode.File, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val item = node.item
    val details = listOfNotNull(
        item.size?.let { Formatter.formatShortFileSize(context, it) },
        item.modified?.let {
            DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
        },
    ).joinToString(" · ")
    val reason = item.problem?.reason()?.let { stringResource(it) }
    ListItem(
        headlineContent = { Text(node.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (details.isEmpty()) null else ({ Text(details) }),
        leadingContent = {
            val icon = when (item.problem) {
                ArchiveProblem.PASSWORD -> Icons.Outlined.Lock
                ArchiveProblem.LINK -> Icons.Outlined.Link
                else -> Icons.AutoMirrored.Outlined.InsertDriveFile
            }
            Icon(icon, contentDescription = reason)
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

/** What the viewer says about [ArchiveProblem], under "This file can't be shown here"; nothing for a damaged one. */
internal fun ArchiveProblem.reason(): Int? = when (this) {
    ArchiveProblem.PASSWORD -> R.string.file_viewer_archive_password
    ArchiveProblem.UNSUPPORTED -> R.string.file_viewer_archive_unsupported
    ArchiveProblem.TOO_LARGE -> R.string.file_viewer_archive_too_large
    ArchiveProblem.TOO_MANY -> R.string.file_viewer_archive_too_many
    ArchiveProblem.LINK -> R.string.file_viewer_archive_link
    ArchiveProblem.BROKEN -> null
}

/**
 * [block] off the main thread, stopped when the screen leaves or [ArchiveLimits.timeLimitMillis]
 * runs out, even inside a read: the thread is interrupted, which closes the file it reads.
 */
private suspend fun <T> reading(block: () -> T): T =
    withTimeoutOrNull(ArchiveLimits().timeLimitMillis + 5_000) { runInterruptible(Dispatchers.IO) { block() } }
        ?: throw ArchiveProblemException(ArchiveProblem.TOO_LARGE)

/** Opens [file], taken out of an archive as [item], as a downloaded file of its name opens. */
private fun openTaken(context: Context, file: File, item: ArchiveItem) {
    val name = item.path.substringAfterLast('/')
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ArchiveNames.extension(name)) ?: "application/octet-stream"
    val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    val opened = FileViewer.open(context, uri, type, name) {
        val elsewhere = if (FileViewing.opening(type, name) == FileOpening.ShareOnly) FileViewer.shareIntent(uri, type, name)
        else FileViewer.elsewhereIntent(uri, type, name)
        runCatching { context.startActivity(elsewhere) }.isSuccess
    }
    if (!opened) Toast.makeText(context, R.string.browser_download_no_app, Toast.LENGTH_SHORT).show()
}

/**
 * [uri] as an archive: read where it is when it's a file, as the app's own downloads and the
 * system's are; a stream with no size, read once into [scratch], no larger than an archive may be.
 */
private fun Context.archiveSource(uri: Uri, name: String, scratch: File): ArchiveSource {
    val size = contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: throw FileNotFoundException(name)
    if (size >= 0) {
        return ArchiveSource(name, size) {
            val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException(name)
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel
        }
    }
    val copy = File(scratch, "archive")
    val input = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(name)
    input.use { copy.outputStream().use { out -> copyAtMost(it, out, MAX_STREAMED_ARCHIVE) } }
    return ArchiveSource.of(copy).let { ArchiveSource(name, it.size, it.open) }
}

private fun copyAtMost(input: InputStream, output: OutputStream, limit: Long) {
    val buffer = ByteArray(64 shl 10)
    var copied = 0L
    while (true) {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Stopped")
        val read = input.read(buffer)
        if (read < 0) return
        copied += read
        if (copied > limit) throw ArchiveProblemException(ArchiveProblem.TOO_LARGE)
        output.write(buffer, 0, read)
    }
}

/** The largest archive read through a stream, copied first: files are read where they are, at any size. */
private const val MAX_STREAMED_ARCHIVE = 512L shl 20

/**
 * The folders archive viewers take files out into, one each, under `cache/archive_entries`,
 * which the app's FileProvider hands out (`res/xml/file_provider_paths.xml`).
 */
internal object ArchiveCache {
    private const val DIR = "archive_entries"
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun folder(context: Context, session: String): File = File(File(context.cacheDir, DIR), session).apply { mkdirs() }

    /** Folders a viewer the system closed without telling it left behind: a day on, they go. */
    fun sweep(context: Context, keep: File, now: Long = System.currentTimeMillis()) {
        File(context.cacheDir, DIR).listFiles()?.forEach { old ->
            if (old != keep && now - old.lastModified() > DAY_MILLIS) old.deleteRecursively()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
