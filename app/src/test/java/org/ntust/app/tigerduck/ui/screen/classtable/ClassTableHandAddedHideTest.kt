package org.ntust.app.tigerduck.ui.screen.classtable

import android.app.Application
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ntust.app.tigerduck.academic.AcademicCalendarStore
import org.ntust.app.tigerduck.data.CourseTombstoneKeys
import org.ntust.app.tigerduck.data.cache.DataCache
import org.ntust.app.tigerduck.data.preferences.AppPreferences
import org.ntust.app.tigerduck.push.ServerCourse
import org.ntust.app.tigerduck.shared.Course
import org.ntust.app.tigerduck.ui.screen.home.CourseSyncReconciler
import org.ntust.app.tigerduck.ui.screen.home.commitReconciledTerm
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * A hand-added course the student deletes and later really enrolls in (tigerduck-app-android#425,
 * as iOS tigerduck-app#771). The backend lifts its tombstone once this device's roster upload
 * names the course, so the course selection answer that lists it has to bring it back here first;
 * every other hide keeps binding.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class ClassTableHandAddedHideTest {

    private lateinit var cache: DataCache

    /** Only its fold is used: the lookups' answers are made here. */
    private val termFetch = ClassTableTermFetch { _, _, _, _, _ -> null }

    private val enrolled = Course.fromSchedule("CS1010001", "Algorithms", moodleIdNumber = "${TERM}CS1010001")
    private val handAdded = Course.fromSchedule("GE1010009", "Art", schedule = mapOf(3 to listOf("2")), isManual = true)

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        cache = DataCache(context, AcademicCalendarStore(context, OkHttpClient(), AppPreferences(context)))
    }

    /** As the class table's delete leaves the store and the term, reading the course off the term. */
    private suspend fun delete(course: Course) {
        val held = cache.loadCourses(TERM).first { it.courseNo == course.courseNo }
        cache.hideDeletedCourse(held.courseNo, TERM)
        cache.saveCourses(cache.loadCourses(TERM).filter { it.courseNo != held.courseNo }, TERM)
        cache.markDeletedCourse(held.courseNo, TERM, held)
    }

    /** The sync's reconcile as HomeBackendSync stores it, for a tombstone another device wrote for [course]. */
    private suspend fun tombstonedElsewhere(course: Course) {
        val localCourses = cache.loadCourses(TERM)
        val restored = cache.restoredCoursesIn(TERM, Long.MAX_VALUE)
        val stored = CourseSyncReconciler.withoutRestored(TERM, cache.loadDeletedCourseNos(), restored)
        val outcome = CourseSyncReconciler.reconcileSemester(
            semester = TERM,
            localCourses = localCourses,
            serverRows = listOf(ServerCourse(enrolled.courseNo, enrolled.courseName, TERM, moodleId = enrolled.moodleIdNumber)),
            tombstoneNos = setOf(course.courseNo),
            tombstones = stored,
            serverKnownNos = setOf(enrolled.courseNo, course.courseNo),
            restoredNos = restored,
        )
        cache.handAddedHidesTombstoned(TERM, setOf(course.courseNo))
        cache.updateDeletedCourseNos(stored, outcome.tombstones)
        CourseSyncReconciler.pruneHidden(TERM, cache.loadCourses(TERM), outcome.tombstones)?.let { cache.saveCourses(it, TERM) }
    }

    /**
     * A full sync whose snapshot, fetched at [fetchedAtMs] (by default after everything so far),
     * lists [serverNos] for the term, with no tombstone, as HomeBackendSync makes it: the hides read
     * first ([hides], when the sync read them earlier), then the term's courses, reconciled, pruned
     * and committed.
     */
    private suspend fun syncListing(
        vararg serverNos: String,
        fetchedAtMs: Long = Long.MAX_VALUE,
        hides: Set<String>? = null,
        beforeCommit: suspend () -> Unit = {},
    ) {
        val localCourses = cache.loadCourses(TERM)
        val restored = cache.restoredCoursesIn(TERM, fetchedAtMs)
        val stored = CourseSyncReconciler.withoutRestored(TERM, hides ?: cache.loadDeletedCourseNos(), restored)
        val outcome = CourseSyncReconciler.reconcileSemester(
            semester = TERM,
            localCourses = localCourses,
            serverRows = serverNos.map { ServerCourse(it, it, TERM, moodleId = "$TERM$it") },
            tombstoneNos = emptySet(),
            tombstones = stored,
            serverKnownNos = cache.loadServerKnownNos()[TERM].orEmpty().toSet(),
            restoredNos = restored,
        )
        beforeCommit()
        cache.restoredCoursesListed(TERM, serverNos.toList(), fetchedAtMs)
        cache.saveServerKnownNos(mapOf(TERM to outcome.serverKnownNos.sorted()))
        val kept = CourseSyncReconciler.pruneHidden(TERM, localCourses, outcome.tombstones)
        commitReconciledTerm(cache, TERM, stored, outcome.tombstones, localCourses, kept, outcome.merged)
    }

    /**
     * A fetch of the term whose course selection answer lists [courseNos], as the class table makes
     * it: the answer recorded before the merge, then the roster's rows folded onto the term. What it
     * returns is what the class table shows, saves and uploads.
     */
    private suspend fun fetchListing(vararg courseNos: String): List<String> {
        cache.recordSelectionRoster(TERM, courseNos.toList())
        val fetched = courseNos.map { Course.fromSchedule(it, it, moodleIdNumber = "$TERM$it") }
        val merged = termFetch.foldLatest(
            ClassTableTermFetch.Answers(fetched, emptyMap(), answered = true), TERM,
            editGeneration = { 0 },
            cachedCourses = { cache.loadCourses(TERM) },
            storedTombstones = { cache.loadDeletedCourseNos() },
            shownCourses = { null },
            withPendingEdits = { it },
            names = { emptyMap() },
            locale = "zh",
        )!!
        cache.saveCourses(merged, TERM)
        return merged.map { it.courseNo }.sorted()
    }

    /** What Home and the background refresh leave out of the term. */
    private suspend fun hidden(): Set<String> = CourseTombstoneKeys.hiddenIn(TERM, cache.loadDeletedCourseNos())

    @Test
    fun `a deleted hand-added course shows again and goes up with the roster once course selection lists it`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)

        delete(handAdded)
        assertEquals(setOf(handAdded.courseNo), hidden())

        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun `a deleted enrolled course stays hidden when course selection lists it again`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)

        delete(enrolled)

        assertEquals(listOf(handAdded.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        assertEquals(setOf(enrolled.courseNo), hidden())
    }

    @Test
    fun `a hide from another device's tombstone stays when course selection lists the course`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        tombstonedElsewhere(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        assertEquals(setOf(handAdded.courseNo), hidden())
    }

    @Test
    fun `a hand-added course that course selection already listed when it was deleted stays hidden`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        // Enrolled in since it was added by hand: the row keeps its manual flag.
        fetchListing(enrolled.courseNo, handAdded.courseNo)

        delete(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        assertEquals(setOf(handAdded.courseNo), hidden())
    }

    @Test
    fun `a hide lifted by adding the course back is not lifted again once another device's tombstone hides it`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        // Added back by hand, which lifts the hide as ClassTableViewModel.addCourse does.
        cache.saveDeletedCourseNos(CourseTombstoneKeys.unhide(handAdded.courseNo, TERM, cache.loadDeletedCourseNos()))
        cache.saveCourses(cache.loadCourses(TERM) + handAdded, TERM)

        tombstonedElsewhere(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        assertEquals(setOf(handAdded.courseNo), hidden())
    }

    @Test
    fun `a deleted hand-added course comes back only once its delete has reached the backend`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        // Its DELETE has not landed, as PendingCourseDeletes keeps it: landing after the roster
        // upload, it would take the course off the server again.
        cache.savePendingCourseDeletes(mapOf(TERM to mapOf(handAdded.courseNo to 1L)))

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))

        cache.savePendingCourseDeletes(emptyMap())
        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a sign-out takes the marked hides with it`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)

        cache.clearAllUserData()
        // The next student's own sync hides the same course for another of their devices.
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        tombstonedElsewhere(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a sign-out takes the course selection answers with it`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)

        cache.clearAllUserData()
        // The next student added the same course by hand and deletes it before any answer of theirs:
        // the last one's answer says nothing of what theirs lists.
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        delete(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a hand-added course deleted before any course selection answer stays hidden`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)

        delete(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a course merged from another device's row stays hidden once deleted`() = runTest {
        cache.saveCourses(listOf(enrolled), TERM)
        // Merged down as CourseSyncReconciler.toCourse makes it: manual, with the Moodle id the
        // server holds. The server may not have it as a hand-added course's row.
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)

        delete(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a mark that lost its hide does not lift a later hide of the course`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        // The hides gone from under the mark, as a damaged file leaves them.
        File(RuntimeEnvironment.getApplication().filesDir, "TigerDuckData/deleted_courses.json").delete()

        tombstonedElsewhere(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a sync that read the hides before a course came back does not hide it again`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        val read = cache.loadDeletedCourseNos()

        // While the sync reconciles what it read, a fetch brings the course back.
        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
        // The sync commits what it made of its read: another course hidden for a tombstone.
        cache.updateDeletedCourseNos(read, CourseTombstoneKeys.hide("ZZ1010008", TERM, read))

        assertEquals(setOf("ZZ1010008"), hidden())
    }

    @Test
    fun `a sync that read the hides before a course came back keeps it in the term`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)

        // Home's sync reads the hides, and is held there while a fetch brings the course back,
        // before it reads the term's courses, reconciles and commits.
        val read = cache.loadDeletedCourseNos()
        fetchListing(enrolled.courseNo, handAdded.courseNo)
        syncListing(enrolled.courseNo, hides = read)

        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), cache.loadCourses(TERM).map { it.courseNo }.sorted())
        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun `a sync that read a course as brought back does not undo its delete made since`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)
        val read = cache.loadDeletedCourseNos()
        fetchListing(enrolled.courseNo, handAdded.courseNo)

        // The sync has read the course as brought back and is held before its commit, while the
        // student deletes it again.
        syncListing(enrolled.courseNo, hides = read, beforeCommit = { delete(handAdded) })

        assertEquals(setOf(handAdded.courseNo), hidden())
    }

    @Test
    fun `a delete made while a sync merges another course down stays out of the term`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)
        val read = cache.loadDeletedCourseNos()
        fetchListing(enrolled.courseNo, handAdded.courseNo)

        // The sync merges down a course another device added, which writes the term's courses,
        // and is held before its commit while the student deletes the course again.
        syncListing(enrolled.courseNo, MERGED, hides = read, beforeCommit = { delete(handAdded) })

        assertEquals(setOf(handAdded.courseNo), hidden())
        assertEquals(listOf(enrolled.courseNo, MERGED), cache.loadCourses(TERM).map { it.courseNo }.sorted())
    }

    @Test
    fun `a course hidden here that another device adds back returns with one sync`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        delete(enrolled)
        syncListing(handAdded.courseNo)

        // Another device adds the course back, and the next snapshot lists it.
        syncListing(enrolled.courseNo, handAdded.courseNo)

        assertEquals(emptySet<String>(), hidden())
        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo).sorted(), cache.loadCourses(TERM).map { it.courseNo }.sorted())
    }

    @Test
    fun `a newer sync that finds a brought-back course listed leaves an older sync its hold`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)
        // Home's sync fetches its snapshot and reads the hides, then waits while the rest happens.
        val olderFetchedAt = System.currentTimeMillis() - 1_000
        val read = cache.loadDeletedCourseNos()
        fetchListing(enrolled.courseNo, handAdded.courseNo)
        // The background sync's snapshot, fetched since, lists the course: its upload landed.
        syncListing(enrolled.courseNo, handAdded.courseNo, fetchedAtMs = System.currentTimeMillis() + 1_000)

        // Home's older snapshot, from before the upload, does not list it.
        syncListing(enrolled.courseNo, hides = read, fetchedAtMs = olderFetchedAt)

        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), cache.loadCourses(TERM).map { it.courseNo }.sorted())
        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun `an older sync that resumes long after a newer one listed a brought-back course still leaves it be`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)
        val olderFetchedAt = System.currentTimeMillis() - 1_000
        val read = cache.loadDeletedCourseNos()
        fetchListing(enrolled.courseNo, handAdded.courseNo)
        val now = System.currentTimeMillis()
        syncListing(enrolled.courseNo, handAdded.courseNo, fetchedAtMs = now + 1_000)
        // Newer syncs go on while the older one is held, past any grace a minute long.
        syncListing(enrolled.courseNo, handAdded.courseNo, fetchedAtMs = now + 600_000)

        syncListing(enrolled.courseNo, hides = read, fetchedAtMs = olderFetchedAt)

        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), cache.loadCourses(TERM).map { it.courseNo }.sorted())
        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun `a course a sync merges down that is fetched and deleted while it runs stays out of the term`() = runTest {
        cache.saveCourses(listOf(enrolled), TERM)

        // The sync is to merge down a course another device added; held before its commit, a
        // fetch brings the same course and the student deletes it.
        syncListing(enrolled.courseNo, MERGED, beforeCommit = {
            cache.saveCourses(cache.loadCourses(TERM) + Course.fromSchedule(MERGED, MERGED, moodleIdNumber = "$TERM$MERGED"), TERM)
            delete(Course.fromSchedule(MERGED, MERGED))
        })

        assertEquals(setOf(MERGED), hidden())
        assertEquals(listOf(enrolled.courseNo), cache.loadCourses(TERM).map { it.courseNo })
    }

    @Test
    fun `a snapshot fetched before a course came back does not end its hold`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        syncListing(enrolled.courseNo)
        fetchListing(enrolled.courseNo, handAdded.courseNo)

        // Fetched before its DELETE landed, so it still lists the old row, and reconciled only now.
        syncListing(enrolled.courseNo, handAdded.courseNo, fetchedAtMs = 0L)
        syncListing(enrolled.courseNo)

        assertEquals(emptySet<String>(), hidden())
    }

    @Test
    fun `another device's tombstone makes a marked hide bind`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        fetchListing(enrolled.courseNo)
        delete(handAdded)

        // The backend leaves this device's own tombstone out of its snapshot: one there is another's.
        tombstonedElsewhere(handAdded)

        assertEquals(listOf(enrolled.courseNo), fetchListing(enrolled.courseNo, handAdded.courseNo))
    }

    @Test
    fun `a course brought back stays through a sync that does not list it before its upload lands`() = runTest {
        cache.saveCourses(listOf(enrolled, handAdded), TERM)
        // The server has had both: the hand-added course went up when it was added.
        syncListing(enrolled.courseNo, handAdded.courseNo)
        fetchListing(enrolled.courseNo)
        delete(handAdded)
        // Its DELETE landed; the backend leaves the tombstone out of this device's snapshot.
        syncListing(enrolled.courseNo)
        fetchListing(enrolled.courseNo, handAdded.courseNo)

        // Home's fetch uploads nothing, and the class table's upload can fail: the next snapshot
        // does not list it yet, which is no delete elsewhere.
        syncListing(enrolled.courseNo)
        assertEquals(emptySet<String>(), hidden())
        assertEquals(listOf(enrolled.courseNo, handAdded.courseNo), cache.loadCourses(TERM).map { it.courseNo }.sorted())

        // Once a snapshot lists it, the upload has landed, and its absence is a delete elsewhere again.
        syncListing(enrolled.courseNo, handAdded.courseNo)
        syncListing(enrolled.courseNo)
        assertEquals(setOf(handAdded.courseNo), hidden())
    }

    private companion object {
        const val TERM = "1151"

        /** A course another device added, which a sync merges down. */
        const val MERGED = "EE1010007"
    }
}
