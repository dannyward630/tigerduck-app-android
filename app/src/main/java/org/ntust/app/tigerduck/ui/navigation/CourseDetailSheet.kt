// The class table's selected course as iOS presents it: a sheet that opens at
// half height and drags to full. The Moodle pages it opens, and a member's mail,
// are pages of the app's own navigation (coursePages), full screen over the page
// the sheet is on: the sheet steps aside while they show, and a back to that page
// brings it back.

package org.ntust.app.tigerduck.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.ntust.app.tigerduck.ui.screen.classtable.ClassTableViewModel
import org.ntust.app.tigerduck.ui.screen.classtable.CourseDetailSheetPage
import org.ntust.app.tigerduck.ui.screen.coursemoodle.MoodleRoutes

/** What the course sheet does as the app's navigation moves. */
internal object CourseDetailSheetRules {
    /**
     * Whether the sheet shows on its page: while a course is [picked] and the page is the one on
     * top, so a page the sheet opens covers it, and a back to the page brings it back.
     */
    fun shows(picked: Boolean, pageOnTop: Boolean): Boolean = picked && pageOnTop
}

/** Whether [entry] is the page on top: false from the moment a page is opened over it. */
@Composable
internal fun NavController.isOnTop(entry: NavBackStackEntry): Boolean {
    // Seeded with the page on top now: the flow's first answer comes a frame late, and a sheet
    // open across a change of dark mode would close for that frame.
    val current by currentBackStackEntryFlow.collectAsState(initial = currentBackStackEntry)
    return current?.id == entry.id
}

/**
 * Holds the view models of the sheet's page: past the activity being recreated, as for a change
 * of dark mode, which keeps the sheet open, but not past the sheet. A NavHost keeps its pages' view models with the owner around it, and one that
 * leaves the screen without popping them leaves them there, so the class table page's own would
 * keep every closed sheet's.
 */
internal class CourseDetailSheetModels : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    /** Once the sheet has closed or stepped aside: the next one builds a navigation of its own. */
    fun close() = viewModelStore.clear()

    override fun onCleared() = viewModelStore.clear()
}

/**
 * The selected course's sheet, while there is one and [pageOnTop]; closing it clears the selection.
 * [onOpenPage] opens a page the sheet links to, over the page the sheet is on.
 */
@Composable
internal fun CourseDetailSheetHost(
    classTable: ClassTableViewModel,
    browserPreference: String,
    pageOnTop: Boolean,
    onOpenPage: (String) -> Unit,
) {
    val models: CourseDetailSheetModels = viewModel()
    val selected by classTable.selectedCourse.collectAsStateWithLifecycle()
    val shown = CourseDetailSheetRules.shows(picked = selected != null, pageOnTop = pageOnTop)
    LaunchedEffect(shown) { if (!shown) models.close() }
    if (shown) {
        CourseDetailSheet(classTable, browserPreference, models, onOpenPage, onClosed = classTable::clearSelection)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDetailSheet(
    classTable: ClassTableViewModel,
    browserPreference: String,
    models: CourseDetailSheetModels,
    onOpenPage: (String) -> Unit,
    onClosed: () -> Unit,
) {
    val navController = rememberNavController()
    ModalBottomSheet(
        onDismissRequest = onClosed,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        // A navigation of the course detail alone: its entry gives the page's view models a home
        // in [models], which goes with the sheet.
        CompositionLocalProvider(LocalViewModelStoreOwner provides models) {
            NavHost(
                navController = navController,
                startDestination = MoodleRoutes.COURSE_DETAIL,
                // The full height whatever the page, so the sheet opens at half the screen as iOS's
                // medium detent does, and a short page does not shrink it.
                modifier = Modifier.fillMaxSize(),
            ) {
                page(MoodleRoutes.COURSE_DETAIL) {
                    CourseDetailSheetPage(
                        viewModel = classTable,
                        browserPreference = browserPreference,
                        onOpenRoute = onOpenPage,
                    )
                }
            }
        }
    }
}
