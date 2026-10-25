package org.ntust.app.tigerduck.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.ntust.app.tigerduck.data.storage.DownloadedFiles
import org.ntust.app.tigerduck.di.ApplicationScope
import javax.inject.Inject

/**
 * Settings' "Downloaded files": what each kind takes, measured each time the page shows, and
 * deleting a kind or all of them. The sizes and the turns are [DownloadedFiles]', shared with
 * every page. A delete runs in the app's scope: once confirmed, it finishes even if the page
 * closes first, and the page open then shows it.
 */
@HiltViewModel
class DownloadedFilesViewModel @Inject constructor(
    private val files: DownloadedFiles,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** Null until the first measure ends, so the rows say "…" rather than sizes not measured. */
    val sizes: StateFlow<Map<DownloadedFiles.Kind, Long>?> = files.sizes

    fun measure() {
        viewModelScope.launch { files.refresh() }
    }

    fun delete(kind: DownloadedFiles.Kind) {
        appScope.launch { files.delete(kind) }
    }

    fun deleteAll() {
        appScope.launch { files.deleteAll() }
    }
}
