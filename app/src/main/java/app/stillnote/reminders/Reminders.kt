package app.stillnote.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import app.stillnote.data.ReminderSettings
import app.stillnote.data.SettingsStore
import app.stillnote.data.local.TaskDao
import app.stillnote.data.local.TaskRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Keeps one exact alarm on the next reminder or morning list, and shows each when it comes.
 * [update] does both: it shows whatever has come due and hasn't been shown, then sets the alarm.
 * It runs after any change to the tasks or Settings while the app runs, when the alarm goes
 * off, and after a restart, an app update, or a change of clock or time zone.
 */
class Reminders(
    private val context: Context,
    private val tasks: TaskDao,
    private val settings: SettingsStore,
    /** Ticks a task off the way the app does, so the change reaches Notion. */
    private val complete: suspend (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    val notifications = ReminderNotifications(context)
    private val lock = Mutex()

    /** What has been shown, by [Notice.key], with when it was due; kept for two days. */
    private val shown = context.getSharedPreferences("reminders_shown", Context.MODE_PRIVATE)

    fun watch(scope: CoroutineScope) {
        notifications.createChannels()
        scope.launch {
            // Conflated rather than cancelled, so an update is never cut off between showing a reminder and noting it.
            combine(tasks.observeOpenDated(), settings.reminders) { open, rules -> open to rules }
                .conflate()
                .collect { (open, rules) -> update(open, rules) }
        }
    }

    suspend fun update() = update(tasks.openDated(), settings.reminders.first())

    /** The Done button: ticks the task off and takes its reminder down. */
    suspend fun done(taskId: String) {
        complete(taskId)
        notifications.cancel(taskId)
    }

    /** The Later button: back at the task's time if it's still to come, otherwise in 10 minutes. */
    suspend fun later(taskId: String) {
        val taskAt = tasks.byId(taskId)?.timeAt(zone()) ?: return
        val time = now()
        val until = if (taskAt - time > 30_000) taskAt else time + LATER_MILLIS
        tasks.snooze(taskId, until)
        notifications.cancel(taskId)
        update()
    }

    private suspend fun update(open: List<TaskRow>, rules: ReminderSettings) = lock.withLock {
        val time = now()
        val zone = zone()
        val plan = ReminderPlan(rules, zone)
        val due = plan.due(open, time).filter { !shown.contains(it.key) }
        due.forEach { notifications.show(it, time, zone) }
        shown.edit {
            due.forEach { putLong(it.key, it.at) }
            shown.all.forEach { (key, at) -> if (at is Long && at < time - KEEP_SHOWN_MILLIS) remove(key) }
        }
        notifications.dropAllBut(open.mapTo(HashSet()) { it.id })
        setAlarm(plan.next(open, time))
    }

    private fun setAlarm(at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        when {
            at == null -> alarms.cancel(intent)
            // Granted with USE_EXACT_ALARM; an inexact alarm, a few minutes late at worst, if it ever isn't.
            Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms() -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private companion object {
        const val LATER_MILLIS = 10 * 60_000L
        const val KEEP_SHOWN_MILLIS = 2 * 24 * 60 * 60_000L
    }
}
