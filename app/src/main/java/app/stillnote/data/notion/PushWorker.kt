package app.stillnote.data.notion

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stillnote.StillNoteApp
import java.util.concurrent.TimeUnit

/**
 * Pushes waiting changes to Notion. WorkManager runs it once there is a network, even if the
 * app has been closed since, and backs off between attempts.
 */
class PushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as StillNoteApp).sync
        return when (sync.push()) {
            // Offline is not a failure: the changes wait for as long as it takes.
            NotionSync.Outcome.Offline -> Result.retry()
            NotionSync.Outcome.Busy -> if (runAttemptCount + 1 < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                sync.giveUp()
                Result.success()
            }
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "push"
        private const val MAX_ATTEMPTS = 5

        /** Queues a push behind any that is already running, so no change made meanwhile is missed. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
