package app.stillnote.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.stillnote.StillNoteApp

/** The reminder alarm going off, and the Done and Later buttons on a reminder. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StillNoteApp
        val taskId = intent.data?.lastPathSegment
        app.finishInBackground(goAsync()) {
            when (intent.action) {
                ALARM -> app.reminders.update()
                DONE -> taskId?.let { app.reminders.done(it) }
                LATER -> taskId?.let { app.reminders.later(it) }
            }
        }
    }

    companion object {
        const val ALARM = "app.stillnote.action.REMINDER_ALARM"
        const val DONE = "app.stillnote.action.REMINDER_DONE"
        const val LATER = "app.stillnote.action.REMINDER_LATER"
    }
}

/**
 * After a restart or an app update the alarm is gone, and after a change of clock or time zone
 * it is in the wrong place, so the reminders are worked out again. A new clock or time zone
 * can also mean a new day, which the screens and the widget should show straight away.
 */
class ReminderRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTARTS) return
        val app = context.applicationContext as StillNoteApp
        app.clock.refresh()
        app.finishInBackground(goAsync()) { app.reminders.update() }
    }

    private companion object {
        // As in the manifest's filter.
        val RESTARTS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, from Android 12.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
