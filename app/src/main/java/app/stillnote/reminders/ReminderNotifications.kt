package app.stillnote.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.stillnote.LaunchRequest
import app.stillnote.R
import app.stillnote.data.TaskOrder
import app.stillnote.data.local.toTask
import java.time.ZoneId

/** False while Android won't show StillNote's notifications: no permission yet, or turned off. */
fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * The reminders as Android notifications, in Android's own style: a task's reminder with Done,
 * Later and Edit, and the morning list. Each reminder is tagged with its task's id.
 */
class ReminderNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A note shortly before a task's time"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(MORNING, "Morning list", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Today's tasks that have no time, once each morning"
            }
        )
    }

    fun show(notice: Notice, now: Long, zone: ZoneId) {
        when (notice) {
            is Notice.TaskReminder -> post(notice.task.id, reminder(notice, now, zone))
            // Nothing to list today; the morning list was due all the same, and that settles it.
            is Notice.MorningList -> if (notice.tasks.isNotEmpty()) post(MORNING_TAG, morningList(notice))
        }
    }

    fun cancel(taskId: String) = manager.cancel(taskId, ID)

    /** Takes down the reminders of tasks that are no longer open: done, deleted or gone. */
    fun dropAllBut(openTaskIds: Set<String>) {
        for (shown in manager.activeNotifications) {
            val tag = shown.tag ?: continue
            if (tag != MORNING_TAG && tag !in openTaskIds) manager.cancel(tag, ID)
        }
    }

    private fun reminder(notice: Notice.TaskReminder, now: Long, zone: ZoneId) =
        NotificationCompat.Builder(context, REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(INK)
            .setContentTitle(notice.title(now))
            .setContentText(notice.text(now, zone))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(edit(notice.task.id))
            .setAutoCancel(true)
            .addAction(0, "Done", button(ReminderReceiver.DONE, notice.task.id))
            .addAction(0, "Later", button(ReminderReceiver.LATER, notice.task.id))
            .addAction(0, "Edit", edit(notice.task.id))
            .build()

    private fun morningList(notice: Notice.MorningList): android.app.Notification {
        val titles = notice.tasks.map { it.toTask() }.sortedWith(TaskOrder.today).map { it.title }
        val lines = NotificationCompat.InboxStyle()
        titles.take(MORNING_LINES).forEach(lines::addLine)
        if (titles.size > MORNING_LINES) lines.setSummaryText("+${titles.size - MORNING_LINES} more")
        return NotificationCompat.Builder(context, MORNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(INK)
            .setContentTitle(notice.title())
            .setContentText(titles.joinToString(", "))
            .setStyle(lines)
            .setContentIntent(open(LaunchRequest.Today))
            .setAutoCancel(true)
            .build()
    }

    private fun post(tag: String, notification: android.app.Notification) {
        // Without the permission Android drops it anyway; the check keeps that explicit.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(tag, ID, notification)
    }

    private fun edit(taskId: String) = open(LaunchRequest.EditTask(taskId))

    private fun open(request: LaunchRequest): PendingIntent =
        PendingIntent.getActivity(context, 0, request.intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // The task goes in the data, which also keeps each task's buttons apart from the others'.
    private fun button(action: String, taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.Builder().scheme("stillnote").authority("task").appendPath(taskId).build()),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REMINDERS = "reminders"
        const val MORNING = "morning"
        const val MORNING_TAG = "morning-list"
        const val ID = 1
        const val MORNING_LINES = 6
        const val INK = 0xFF2A2621.toInt()
    }
}
