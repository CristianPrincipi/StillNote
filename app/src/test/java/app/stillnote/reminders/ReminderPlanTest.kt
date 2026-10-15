package app.stillnote.reminders

import app.stillnote.data.Reminder
import app.stillnote.data.ReminderSettings
import app.stillnote.data.local.TaskRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderPlanTest {
    private val rome = ZoneId.of("Europe/Rome")
    private val plan = ReminderPlan(ReminderSettings(before = 10, morningAt = null), rome)

    private fun at(time: String, day: String = "2026-10-15") = LocalDateTime.parse("${day}T$time").atZone(rome).toInstant().toEpochMilli()

    private fun task(
        id: String,
        time: String?,
        day: String = "2026-10-15",
        reminder: Int? = null,
        done: Boolean = false,
        changedAt: Long = 0,
        snoozedUntil: Long = 0,
        project: String? = null,
    ) = TaskRow(
        id = id,
        title = "Task $id",
        date = day,
        minutes = time?.let { it.take(2).toInt() * 60 + it.takeLast(2).toInt() },
        priority = "None",
        project = project,
        done = done,
        created = 0,
        changedAt = changedAt,
        reminder = reminder,
        snoozedUntil = snoozedUntil,
    )

    private fun List<Notice>.ids() = map { (it as Notice.TaskReminder).task.id }

    @Test
    fun `reminds the default minutes before a task's time, and a task's own choice wins`() {
        val tasks = listOf(task("a", "10:30"), task("b", "10:30", reminder = 30), task("c", "10:30", reminder = Reminder.None))

        assertEquals(at("10:00"), plan.next(tasks, at("09:00")))
        assertEquals(listOf("b"), plan.due(tasks, at("10:00")).ids())
        assertEquals(listOf("a", "b"), plan.due(tasks, at("10:20")).ids())
        assertNull(plan.next(tasks, at("10:20")))

        val off = ReminderPlan(ReminderSettings(before = Reminder.None, morningAt = null), rome)
        assertEquals(listOf("b"), off.due(tasks, at("10:20")).ids())
    }

    @Test
    fun `leaves out tasks with no time, done ones, and ones written after their reminder's time`() {
        val tasks = listOf(
            task("untimed", null),
            task("done", "10:30", done = true),
            task("late", "10:30", changedAt = at("10:25")),
            task("early", "10:30", changedAt = at("10:15")),
        )
        assertEquals(listOf("early"), plan.due(tasks, at("10:25")).ids())
    }

    @Test
    fun `a late reminder still shows until just after its task's time`() {
        val tasks = listOf(task("a", "10:30"))
        assertEquals(listOf("a"), plan.due(tasks, at("10:35")).ids())
        assertTrue(plan.due(tasks, at("10:36")).isEmpty())
    }

    @Test
    fun `Later brings a reminder back on its own key`() {
        val tasks = listOf(task("a", "10:30", snoozedUntil = at("10:30")))
        assertEquals(at("10:30"), plan.next(tasks, at("10:21")))
        val due = plan.due(tasks, at("10:30")).filterIsInstance<Notice.TaskReminder>()
        assertEquals(listOf(false, true), due.map { it.snoozed })
        assertEquals(2, due.map { it.key }.toSet().size)
        // A snooze past the task's time shows for a while after it is due.
        val after = listOf(task("a", "10:30", snoozedUntil = at("10:40")))
        assertEquals(listOf(true), plan.due(after, at("10:50")).map { (it as Notice.TaskReminder).snoozed })
        assertTrue(plan.due(after, at("10:56")).isEmpty())
    }

    @Test
    fun `the morning list holds today's untimed tasks, and comes back the next morning`() {
        val morning = ReminderPlan(ReminderSettings(before = Reminder.None, morningAt = 8 * 60), rome)
        val tasks = listOf(task("a", null), task("b", "12:00"), task("c", null, day = "2026-10-16"))

        assertEquals(at("08:00"), morning.next(tasks, at("06:00")))
        val due = morning.due(tasks, at("08:00")).single() as Notice.MorningList
        assertEquals(listOf("a"), due.tasks.map { it.id })
        assertEquals("morning@2026-10-15", due.key)
        assertEquals(at("08:00", day = "2026-10-16"), morning.next(tasks, at("08:00")))
        // Nothing to list still counts as due, so the day is settled; an hour late it's too late.
        assertEquals(1, morning.due(emptyList(), at("08:30")).size)
        assertTrue(morning.due(tasks, at("09:01")).isEmpty())
    }

    @Test
    fun `says how long is left, and that it's time once it is`() {
        val reminder = plan.due(listOf(task("a", "10:30", project = "Work")), at("10:20")).single() as Notice.TaskReminder
        assertEquals("Almost time: Task a", reminder.title(at("10:20")))
        assertEquals("10:30 · in 10 minutes · Work", reminder.text(at("10:20"), rome))
        assertEquals("10:30 · in 1 hour · Work", reminder.text(at("09:30"), rome))
        assertEquals("10:30 · in 1 minute · Work", reminder.text(at("10:29"), rome))
        assertEquals("It's time: Task a", reminder.title(at("10:30")))
        assertEquals("10:30 · Work", reminder.text(at("10:30"), rome))

        val morning = Notice.MorningList(java.time.LocalDate.of(2026, 10, 15), listOf(task("a", null)), 0)
        assertEquals("Today: 1 task at any time", morning.title())
        assertEquals("Today: 2 tasks at any time", morning.copy(tasks = listOf(task("a", null), task("b", null))).title())
    }
}
