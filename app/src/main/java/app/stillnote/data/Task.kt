package app.stillnote.data

import java.time.LocalDate
import java.time.LocalTime

enum class Priority(val label: String, val marks: String) {
    High("High", "!!!"),
    Medium("Medium", "!!"),
    Low("Low", "!"),
    None("None", ""),
}

data class Task(
    val id: String,
    val title: String,
    /** Null for a task that came from Notion with no date; it only shows in its project. */
    val date: LocalDate?,
    val time: LocalTime? = null,
    val priority: Priority = Priority.None,
    val project: String? = null,
    val done: Boolean = false,
    /** This task's own reminder: see [Reminder]. It stays on the phone; Notion has no field for it. */
    val reminder: Int? = null,
)

data class TaskDraft(
    val title: String,
    /** Null only when an undated task from Notion is changed without giving it a date. */
    val date: LocalDate?,
    val time: LocalTime? = null,
    val priority: Priority = Priority.None,
    val project: String? = null,
    val reminder: Int? = null,
)

/**
 * How long before a task's time to remind: minutes from [choices], or [None]. A task's own
 * reminder is null when it follows the default in Settings.
 */
object Reminder {
    const val None = -1
    val choices = listOf(5, 10, 15, 30, 60)
    const val DEFAULT = 10
}

/** A project and what its card shows: the tasks still open, and how many are done. */
data class Project(val name: String, val open: List<Task>, val done: Int)

/**
 * What the connected database can hold. Without Notion, everything is on.
 * [notion] is true while a database is connected, which is what makes pulling down sync.
 */
data class Features(val priority: Boolean = true, val projects: Boolean = true, val notion: Boolean = false)

object TaskOrder {
    /** Time ascending with untimed tasks last, then High to None. */
    val today: Comparator<Task> = compareBy<Task> { it.time == null }
        .thenBy { it.time ?: LocalTime.MIN }
        .thenBy { it.priority.ordinal }

    /** Done tasks first, then the Today order. */
    val calendarDay: Comparator<Task> = compareByDescending<Task> { it.done }.then(today)

    /** The widget's order: High to None, then by time with untimed tasks last. */
    val widget: Comparator<Task> = compareBy<Task> { it.priority.ordinal }
        .thenBy { it.time == null }
        .thenBy { it.time ?: LocalTime.MIN }

    /** Soonest date first with undated tasks last, then High to None. */
    val project: Comparator<Task> = compareBy<Task> { it.date == null }
        .thenBy { it.date ?: LocalDate.MIN }
        .thenBy { it.priority.ordinal }
}
