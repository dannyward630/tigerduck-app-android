package org.ntust.app.tigerduck.data.preferences

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.data.model.AppFeature
import org.ntust.app.tigerduck.data.model.AppFeature.ANNOUNCEMENTS
import org.ntust.app.tigerduck.data.model.AppFeature.CALENDAR
import org.ntust.app.tigerduck.data.model.AppFeature.CLASS_TABLE
import org.ntust.app.tigerduck.data.model.AppFeature.HOME
import org.ntust.app.tigerduck.data.model.AppFeature.LIBRARY
import org.ntust.app.tigerduck.data.model.AppFeature.SCHOOL_MAIL
import org.ntust.app.tigerduck.data.model.AppFeature.SCORE
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [LibraryTabKeepMigration] against the preferences an older build left behind:
 * the library opt-in switch, and a stored bottom bar or none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryTabKeepMigrationTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        raw().edit().clear().commit()
        prefs = AppPreferences(context)
    }

    private fun raw() = context.getSharedPreferences("tigerduck_prefs", Context.MODE_PRIVATE)

    private fun storeOptIn(on: Boolean) {
        raw().edit().putBoolean("libraryFeatureEnabled", on).commit()
    }

    @Test
    fun `an existing user's untouched bar keeps the bar it had, without library`() {
        prefs.hasCompletedOnboarding = true

        assertTrue(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertEquals(listOf(HOME, CLASS_TABLE, SCHOOL_MAIL), prefs.configuredTabs)
        assertFalse(LIBRARY in prefs.configuredTabs)
    }

    @Test
    fun `a new install's first launch stays unstored and follows the default, which has library`() {
        assertFalse(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertFalse(prefs.hasStoredConfiguredTabs)
        assertEquals(AppFeature.defaultTabs, prefs.configuredTabs)
        assertTrue(LIBRARY in prefs.configuredTabs)
    }

    @Test
    fun `a stored bar with room stays as it is, for What's New to ask about`() {
        prefs.hasCompletedOnboarding = true
        prefs.configuredTabs = listOf(HOME, CLASS_TABLE, SCHOOL_MAIL)

        assertFalse(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertEquals(listOf(HOME, CLASS_TABLE, SCHOOL_MAIL), prefs.configuredTabs)
    }

    @Test
    fun `a full bar stays as it is`() {
        val full = listOf(HOME, CLASS_TABLE, CALENDAR, SCORE)
        prefs.hasCompletedOnboarding = true
        prefs.configuredTabs = full

        assertFalse(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertEquals(full, prefs.configuredTabs)
    }

    @Test
    fun `a user who had the library on and took it off the bar keeps their bar`() {
        storeOptIn(true)
        prefs.hasCompletedOnboarding = true
        prefs.configuredTabs = listOf(HOME, ANNOUNCEMENTS)

        assertFalse(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertEquals(listOf(HOME, ANNOUNCEMENTS), prefs.configuredTabs)
    }

    @Test
    fun `the opt-in switch is deleted`() {
        storeOptIn(true)

        LibraryTabKeepMigration.runIfNeeded(prefs)

        assertFalse(raw().contains("libraryFeatureEnabled"))
    }

    @Test
    fun `it runs once, so a bar reset to the default afterwards gets library`() {
        LibraryTabKeepMigration.runIfNeeded(prefs)
        prefs.hasCompletedOnboarding = true

        assertFalse(LibraryTabKeepMigration.runIfNeeded(prefs))
        assertFalse(prefs.hasStoredConfiguredTabs)
        assertTrue(LIBRARY in prefs.configuredTabs)
    }
}
