package org.ntust.app.tigerduck.mail.index

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.ntust.app.tigerduck.mail.MailError
import org.ntust.app.tigerduck.mail.SchoolMailRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Starts and stops the background download of every mail into the search index. */
interface MailIndexScheduler {
    /** Brings the index up to the server soon, on any network; a run already queued or going stays. */
    fun request()

    /** Stops it, for a sign-out. */
    fun cancel()
}

class WorkManagerMailIndexScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MailIndexScheduler {
    override fun request() {
        val request = OneTimeWorkRequestBuilder<MailIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        // KEEP: a run going already fetches whatever the server has.
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }

    private companion object {
        const val NAME = "school_mail_index"
    }
}

/** Downloads every mail the search index lacks, see [SchoolMailRepository.updateSearchIndex]. */
@HiltWorker
class MailIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SchoolMailRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.updateSearchIndex { !isStopped }
        Result.success()
    } catch (e: MailError) {
        when (e) {
            // Tried again later: the network or the server's connection limit may let it through.
            is MailError.Network, is MailError.ServerBusy -> Result.retry()
            // A rejected password, a sign-out or the demo: nothing to fetch until the next request.
            else -> Result.success()
        }
    }
}
