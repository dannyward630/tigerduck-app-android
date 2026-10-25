package org.ntust.app.tigerduck.ui.screen.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.data.storage.DownloadedFiles.Kind
import org.ntust.app.tigerduck.ui.component.ContentCard
import org.ntust.app.tigerduck.ui.component.NoTopBarInsets
import org.ntust.app.tigerduck.ui.component.TigerDuckDialog
import org.ntust.app.tigerduck.ui.theme.ContentAlpha
import org.ntust.app.tigerduck.util.replaceIosArg

/**
 * 已下載的檔案, under 其他設定: what the files the app downloaded take, the total and each kind,
 * and deleting a kind (it downloads again when next opened, so no question) or all of them, which
 * asks first. The TigerDuck browser's downloads are in the phone's Downloads folder, the user's,
 * and the footnote says so. As iOS's Downloaded files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedFilesScreen(
    onBack: () -> Unit,
    viewModel: DownloadedFilesViewModel = hiltViewModel(),
) {
    val sizes by viewModel.sizes.collectAsStateWithLifecycle()
    // Measured each time the page shows, as files come and go from the Moodle and mail screens.
    LifecycleResumeEffect(viewModel) {
        viewModel.measure()
        onPauseOrDispose { }
    }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val total = sizes?.values?.sum()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = NoTopBarInsets,
                title = { Text(stringResource(R.string.settings_downloaded_files_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                expandedHeight = SubSettingsBarHeight,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        ) {
            item {
                ContentCard {
                    SizeRow(stringResource(R.string.settings_downloaded_files_total), total)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                ContentCard {
                    Column {
                        Kind.entries.forEachIndexed { index, kind ->
                            if (index > 0) HorizontalDivider()
                            val label = stringResource(kind.label)
                            SizeRow(
                                label,
                                sizes?.get(kind),
                                deleteDescription = stringResource(R.string.settings_downloaded_files_delete_kind).replaceIosArg(label),
                                onDelete = { viewModel.delete(kind) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_downloaded_files_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.SECONDARY),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                ContentCard {
                    val enabled = (total ?: 0L) > 0L
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = SettingRowHeight)
                            .clickable(enabled = enabled, role = Role.Button) { confirming = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.settings_downloaded_files_delete_all),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error.copy(
                                alpha = if (enabled) 1f else ContentAlpha.DISABLED,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        TigerDuckDialog(
            onDismissRequest = { confirming = false },
            title = stringResource(R.string.settings_downloaded_files_delete_all_title),
            message = stringResource(R.string.settings_downloaded_files_delete_all_message),
            confirmText = stringResource(R.string.action_delete),
            confirmColors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            onConfirm = {
                confirming = false
                viewModel.deleteAll()
            },
            dismissText = stringResource(R.string.action_cancel),
        )
    }
}

private val Kind.label: Int
    get() = when (this) {
        Kind.MOODLE_FILES -> R.string.settings_downloaded_files_moodle
        Kind.MAIL_ATTACHMENTS -> R.string.settings_downloaded_files_mail
        Kind.CLASS_TABLE_IMAGES -> R.string.settings_downloaded_files_images
    }

/**
 * A label, its size ("…" until measured), and Delete when [onDelete] is given, which TalkBack
 * hears as [deleteDescription]: "Delete Moodle files", not one of three alike.
 */
@Composable
private fun SizeRow(label: String, bytes: Long?, deleteDescription: String? = null, onDelete: (() -> Unit)? = null) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SettingRowHeight)
            .padding(start = 16.dp, end = if (onDelete != null) 8.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            bytes?.let { Formatter.formatFileSize(context, it) } ?: "…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.SECONDARY),
        )
        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                enabled = (bytes ?: 0L) > 0L,
                modifier = Modifier.semantics { deleteDescription?.let { contentDescription = it } },
            ) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}
