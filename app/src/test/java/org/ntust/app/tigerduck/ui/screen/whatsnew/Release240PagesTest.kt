package org.ntust.app.tigerduck.ui.screen.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ntust.app.tigerduck.data.model.AppFeature
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.ntust.app.tigerduck.update.WhatsNewGate.Plan

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
                "mail-bottom-bar",
                "library-bottom-bar",
                "bottom-bar-editor",
                "mail-notifications",
                "empty-classroom",
                "display-size",
            ),
            pages.map { it.id },
        )
    }

    @Test
    fun `a replay from Settings opens on where to see it again too`() {
        val replay = WhatsNewFlow.from(Plan.Show(listOf(30), summaryVersion = null, replay = true), mapOf(30 to pages), emptyMap())

        assertEquals("whats-new-replay", replay?.pageIds?.first())
    }

    private val withCalendar = listOf(AppFeature.HOME, AppFeature.CLASS_TABLE, AppFeature.CALENDAR, AppFeature.LIBRARY)

    private fun runFor(bar: List<AppFeature>, asked: Boolean = false): List<String>? {
        var tabs = bar
        val pages = release240Pages(
            isLibrarySignedIn = { false }, tabs = { tabs }, setTabs = { tabs = it },
            mailBarQuestionAsked = { asked },
        )
        return WhatsNewFlow.from(Plan.Show(listOf(30), summaryVersion = null, replay = false), mapOf(30 to pages), emptyMap())?.pageIds
    }

    @Test
    fun `2_4_0 asks too whether Mail takes Calendar's place, of a bar with Calendar and no Mail`() {
        assertEquals(true, runFor(withCalendar)?.contains("mail-bottom-bar"))

        val withMail = listOf(AppFeature.HOME, AppFeature.CLASS_TABLE, AppFeature.SCHOOL_MAIL, AppFeature.LIBRARY)
        assertEquals(false, runFor(withMail)?.contains("mail-bottom-bar"))
    }

    @Test
    fun `whoever was asked before, in 2_3 or here, is not asked again, Mail in the bar or not`() {
        assertEquals(false, runFor(withCalendar, asked = true)?.contains("mail-bottom-bar"))
    }

    @Test
    fun `either answer to the Mail question marks it asked`() {
        var marked = 0
        val page = release240Pages(
            isLibrarySignedIn = { false }, tabs = { withCalendar }, setTabs = {},
            markMailBarQuestionAsked = { marked++ },
        ).single { it.id == "mail-bottom-bar" } as WhatsNewPage.OptIn

        page.apply()
        page.onDecline()

        assertEquals(2, marked)
    }

    @Test
    fun `an install that last showed 2_3_0 or 2_3_1 was asked`() {
        assertEquals(true, ranRelease23x(28))
        assertEquals(true, ranRelease23x(29))
        assertEquals(false, ranRelease23x(27))
        assertEquals(false, ranRelease23x(30))
        assertEquals(false, ranRelease23x(AppPreferences.WHATS_NEW_UNSET))
        assertEquals(false, ranRelease23x(AppPreferences.WHATS_NEW_REPLAY))
    }
}
