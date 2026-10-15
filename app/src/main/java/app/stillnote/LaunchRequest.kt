package app.stillnote

import android.content.Context
import android.content.Intent
import android.net.Uri

/** What a tap on the widget or a notification asks the app to show. */
sealed interface LaunchRequest {
    data object Today : LaunchRequest
    data object NewTask : LaunchRequest

    /** The task in the note, to change it. */
    data class EditTask(val taskId: String) : LaunchRequest

    /** Brings the running app forward, or starts it, with this request. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(
            when (this) {
                Today -> TODAY
                NewTask -> NEW_TASK
                is EditTask -> EDIT_TASK
            }
        )
        // The task goes in the data, which also keeps each row's PendingIntent apart from the others'.
        .setData((this as? EditTask)?.let { Uri.Builder().scheme("stillnote").authority("task").appendPath(it.taskId).build() })
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    companion object {
        private const val TODAY = "app.stillnote.action.TODAY"
        private const val NEW_TASK = "app.stillnote.action.NEW_TASK"
        private const val EDIT_TASK = "app.stillnote.action.EDIT_TASK"

        fun from(intent: Intent?): LaunchRequest? = when (intent?.action) {
            TODAY -> Today
            NEW_TASK -> NewTask
            EDIT_TASK -> intent.data?.lastPathSegment?.let(::EditTask)
            else -> null
        }
    }
}
