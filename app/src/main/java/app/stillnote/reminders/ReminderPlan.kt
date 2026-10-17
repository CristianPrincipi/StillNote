package app.stillnote.reminders

import app.stillnote.data.Reminder
import app.stillnote.data.ReminderSettings
import app.stillnote.data.local.TaskRow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Something to notify about, at [at] (epoch millis). [key] records that it was shown; a new time makes a new key. */
sealed interface Notice {
    val key: String
    val at: Long

    /** A task's reminder, at [taskAt] less its minutes, or back again after "Later" when [snoozed]. */
    data class TaskReminder(val task: TaskRow, val taskAt: Long, override val at: Long, val snoozed: Boolean) : Notice {
        override val key: String get() = if (snoozed) "${task.id}@later@$at" else "${task.id}@$at"
    }

    /** [day]'s open tasks that have no time. Due even when there are none, so it is settled for the day. */
    data class MorningList(val day: LocalDate, val tasks: List<TaskRow>, override val at: Long) : Notice {
        override val key: String get() = "morning@$day"
    }
}

/**
 * The reminder rules, worked out from the open dated tasks and Settings → Reminders as plain
 * data, away from Android: what is due by a given moment, and when the next thing is.
 */
class ReminderPlan(private val settings: ReminderSettings, private val zone: ZoneId) {
    /** Everything whose time has come by [now] and is still worth showing. */
    fun due(tasks: List<TaskRow>, now: Long): List<Notice> = notices(tasks, now).filter { it.at <= now && it.fresh(now) }

    /** When the next thing after [now] is due, for the alarm, or null if nothing is coming. */
    fun next(tasks: List<TaskRow>, now: Long): Long? = notices(tasks, now).map { it.at }.filter { it > now }.minOrNull()

    private fun notices(tasks: List<TaskRow>, now: Long): List<Notice> = buildList {
        for (task in tasks) {
            if (task.done || task.trashed) continue
            val taskAt = task.timeAt(zone) ?: continue
            val before = task.reminder ?: settings.before
            // A task written or changed after its reminder's time has nothing to remind: the user just saw it.
            val at = taskAt - before * MINUTE
            if (before != Reminder.None && at >= task.changedAt) add(Notice.TaskReminder(task, taskAt, at, snoozed = false))
            if (task.snoozedUntil > 0) add(Notice.TaskReminder(task, taskAt, task.snoozedUntil, snoozed = true))
        }
        val morning = settings.morningAt ?: return@buildList
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (day in listOf(today, today.plusDays(1))) {
            val at = day.atTime(LocalTime.of(morning / 60, morning % 60)).atZone(zone).toInstant().toEpochMilli()
            add(Notice.MorningList(day, tasks.filter { it.date == day.toString() && it.minutes == null && !it.done && !it.trashed }, at))
        }
    }

    /**
     * Late notices, after the phone was off or its clock moved, still show while they make sense:
     * a reminder until just after its task's time, a snoozed one and the morning list for a while.
     */
    private fun Notice.fresh(now: Long): Boolean = when (this) {
        is Notice.TaskReminder -> if (snoozed) now - at <= 15 * MINUTE else now <= taskAt + 5 * MINUTE
        is Notice.MorningList -> now - at <= 60 * MINUTE
    }

    companion object {
        const val MINUTE = 60_000L
    }
}

/** The alarm that brings the next notice: an alarm clock on the notice itself, or a wake-up ahead of it. */
sealed interface Alarm {
    val at: Long

    data class Clock(override val at: Long) : Alarm

    /** An ordinary exact alarm, which only wakes the app to set the alarm clock. */
    data class WakeUp(override val at: Long) : Alarm
}

/**
 * OnePlus's OxygenOS lets an ordinary exact alarm from most apps come late by up to three quarters
 * of the time left, an hour at most, and keeps only alarm clocks exact. Android shows an alarm
 * clock as the phone's next alarm, so the notice at [next] only gets one in its last
 * [ALARM_CLOCK_WITHIN]. Before that, a wake-up at the start of that stretch sets it; even an
 * hour late, the wake-up comes 10 minutes before the notice.
 */
fun alarmFor(next: Long, now: Long): Alarm =
    if (next - now <= ALARM_CLOCK_WITHIN) Alarm.Clock(next) else Alarm.WakeUp(next - ALARM_CLOCK_WITHIN)

const val ALARM_CLOCK_WITHIN = 70 * ReminderPlan.MINUTE

/** The task's date and time as epoch millis, or null if it has no time. */
fun TaskRow.timeAt(zone: ZoneId): Long? {
    val minutes = minutes ?: return null
    val day = date?.let(LocalDate::parse) ?: return null
    return day.atTime(minutes / 60, minutes % 60).atZone(zone).toInstant().toEpochMilli()
}

/** "Almost time: …" before the task's time, "It's time: …" from then on. */
fun Notice.TaskReminder.title(now: Long): String =
    (if (taskAt - now > 30_000) "Almost time: " else "It's time: ") + task.title

/** "10:30 · in 15 minutes · Work", without the parts that don't apply. */
fun Notice.TaskReminder.text(now: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(taskAt).atZone(zone).toLocalTime().format(TimeFormat)
    val left = ceil((taskAt - now) / ReminderPlan.MINUTE.toDouble()).toInt()
    return listOfNotNull(time, if (left > 0) "in ${duration(left)}" else null, task.project).joinToString(" · ")
}

/** "Today: 3 tasks at any time" */
fun Notice.MorningList.title(): String = "Today: ${tasks.size} ${if (tasks.size == 1) "task" else "tasks"} at any time"

private fun duration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    val hourPart = when (hours) {
        0 -> null
        1 -> "1 hour"
        else -> "$hours hours"
    }
    val minutePart = when (rest) {
        0 -> null
        1 -> "1 minute"
        else -> "$rest minutes"
    }
    return listOfNotNull(hourPart, minutePart).joinToString(" ")
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
