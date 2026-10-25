package org.ntust.app.tigerduck.ui.screen.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.files.ViewerKind

/**
 * A downloaded file, shown by its kind's viewer under a bar with Share or Save and Open in
 * another app. A file its viewer can't read says so, with Open in another app beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileViewerScreen(
    file: ViewedFile,
    browserPreference: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenElsewhere: () -> Unit,
) {
    var failed by rememberSaveable(file.uri) { mutableStateOf(false) }
    val fail = { failed = true }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.school_mail_attachment_share))
                    }
                    IconButton(onClick = onOpenElsewhere) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.file_viewer_open_elsewhere),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (failed) {
                CannotShow(onOpenElsewhere)
            } else {
                when (file.kind) {
                    ViewerKind.PDF -> PdfViewer(file.uri, onFailed = fail)
                    ViewerKind.IMAGE -> ImageViewer(file.uri, onFailed = fail)
                    ViewerKind.VIDEO, ViewerKind.AUDIO -> MediaViewer(file.uri, onFailed = fail)
                    ViewerKind.TEXT -> TextViewer(file.uri, markdown = false, browserPreference, onFailed = fail)
                    ViewerKind.MARKDOWN -> TextViewer(file.uri, markdown = true, browserPreference, onFailed = fail)
                    ViewerKind.DOCX, ViewerKind.PPTX -> OfficeViewer(file.uri, file.kind, onFailed = fail)
                }
            }
        }
    }
}

/** The page while a viewer reads its file. */
@Composable
internal fun Reading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** A file its viewer couldn't read: saying so, and offering another app. */
@Composable
private fun CannotShow(onOpenElsewhere: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.file_viewer_cannot_show),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenElsewhere) { Text(stringResource(R.string.file_viewer_open_elsewhere)) }
    }
}
