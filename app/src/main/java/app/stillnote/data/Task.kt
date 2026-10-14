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
)

data class TaskDraft(
    val title: String,
    val date: LocalDate,
    val time: LocalTime? = null,
    val priority: Priority = Priority.None,
    val project: String? = null,
)

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

    /** Soonest date first with undated tasks last, then High to None. */
    val project: Comparator<Task> = compareBy<Task> { it.date == null }
        .thenBy { it.date ?: LocalDate.MIN }
        .thenBy { it.priority.ordinal }
}
