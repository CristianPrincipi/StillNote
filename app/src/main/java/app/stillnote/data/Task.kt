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
    val date: LocalDate,
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
)

object TaskOrder {
    /** Time ascending with untimed tasks last, then High to None. */
    val today: Comparator<Task> = compareBy<Task> { it.time == null }
        .thenBy { it.time ?: LocalTime.MIN }
        .thenBy { it.priority.ordinal }

    /** Done tasks first, then the Today order. */
    val calendarDay: Comparator<Task> = compareByDescending<Task> { it.done }.then(today)
}
