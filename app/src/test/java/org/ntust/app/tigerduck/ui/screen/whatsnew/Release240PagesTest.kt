package org.ntust.app.tigerduck.ui.screen.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ntust.app.tigerduck.data.model.AppFeature

class Release240PagesTest {

    private val pages = release240Pages(
        isLibrarySignedIn = { false },
        tabs = { listOf(AppFeature.HOME) },
        setTabs = {},
    )

    @Test
    fun `the run opens on where to see it again, then the features in order`() {
        assertEquals(
            listOf(
                "whats-new-replay",
                "moodle-course",
                "moodle-assignment",
                "class-table-share",
                "library",
                "library-bottom-bar",
                "bottom-bar-editor",
                "mail-notifications",
                "empty-classroom",
                "display-size",
            ),
            pages.map { it.id },
        )
    }
}
