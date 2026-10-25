package org.ntust.app.tigerduck.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ntust.app.tigerduck.data.storage.DownloadedFiles
import org.ntust.app.tigerduck.di.ApplicationScope
import javax.inject.Inject

/**
 * Settings' "Downloaded files": what each kind takes, measured each time the page shows, and
 * deleting a kind or all of them. One at a time, so a measure that started before a delete
 * cannot show the sizes from before it. A delete runs in the app's scope: once confirmed, it
 * finishes even if the page closes first.
 */
@HiltViewModel
class DownloadedFilesViewModel @Inject constructor(
    private val files: DownloadedFiles,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _sizes = MutableStateFlow<Map<DownloadedFiles.Kind, Long>?>(null)

    /** Null until the first measure ends, so the rows say "…" rather than sizes not measured. */
    val sizes: StateFlow<Map<DownloadedFiles.Kind, Long>?> = _sizes.asStateFlow()

    private val busy = Mutex()

    fun measure() {
        viewModelScope.launch { thenMeasure { } }
    }

    fun delete(kind: DownloadedFiles.Kind) {
        appScope.launch { thenMeasure { files.delete(kind) } }
    }

    fun deleteAll() {
        appScope.launch { thenMeasure { files.deleteAll() } }
    }

    /** Runs [change], then measures, after whatever ran before. */
    private suspend fun thenMeasure(change: suspend () -> Unit) {
        busy.withLock {
            change()
            _sizes.value = files.measure()
        }
    }
}
