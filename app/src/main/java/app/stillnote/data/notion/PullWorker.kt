package app.stillnote.data.notion

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.stillnote.StillNoteApp
import app.stillnote.widget.TodayWidget
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Brings Notion's changes in while the app is closed, so the phone doesn't wait to be opened:
 * every 15 minutes, as often as Android allows, and just after midnight, when "today" moves on.
 * Doze and battery savers can hold either back.
 */
class PullWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as StillNoteApp).syncInBackground()
        // Before the process may go away; also what turns the widget to a new day at midnight.
        TodayWidget().updateAll(applicationContext)
        if (MIDNIGHT in tags) scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        // A run that couldn't reach Notion just waits for the next one.
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "pull"
        private const val MIDNIGHT = "pull-at-midnight"

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Starts both schedules, keeping their timing if they are already running. */
        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<PullWorker>(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(online)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
            scheduleMidnight(context, ExistingWorkPolicy.KEEP)
        }

        fun stop(context: Context) {
            val work = WorkManager.getInstance(context)
            work.cancelUniqueWork(PERIODIC)
            work.cancelUniqueWork(MIDNIGHT)
        }

        /** Queues a run for half a minute past the coming midnight. */
        private fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
            val now = LocalDateTime.now()
            val wait = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).plusSeconds(30)
            val request = OneTimeWorkRequestBuilder<PullWorker>()
                .setInitialDelay(wait.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(online)
                .addTag(MIDNIGHT)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT, policy, request)
        }
    }
}
