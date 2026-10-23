package org.ntust.app.tigerduck.mail.index

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What [WorkManagerMailIndexScheduler] queues. No run starts here: none has its network, so each
 * stays where the policy put it.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one is a Hilt app these tests do not need.
@Config(application = Application::class)
class MailIndexSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerMailIndexScheduler

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerMailIndexScheduler(context)
    }

    private fun runs(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork("school_mail_index").get()

    @Test
    fun `a request while a run is queued joins it`() {
        scheduler.request()
        scheduler.request()

        assertEquals(1, runs().size)
    }

    @Test
    fun `a follow-up is a run of its own, after the one queued or under way`() {
        scheduler.request()
        val first = runs().single().id

        scheduler.requestAgain()

        val all = runs()
        assertEquals(2, all.size)
        assertEquals(WorkInfo.State.ENQUEUED, all.single { it.id == first }.state)
        assertEquals("waiting for the first to end", WorkInfo.State.BLOCKED, all.single { it.id != first }.state)
    }
}
