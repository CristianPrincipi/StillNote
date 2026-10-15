package app.stillnote.ui.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.Features
import app.stillnote.data.Priority
import app.stillnote.data.Task
import app.stillnote.data.TaskDraft
import app.stillnote.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class Picker { When, Priority, Project }

/**
 * The note being written: a new task, or a change to [editing]. [minutes] is the time of day,
 * null for "any time". [date] is null only for an undated task from Notion, until the clock is opened.
 */
data class NewTaskDraft(
    val today: LocalDate,
    /** The task being changed, or null for a new one. */
    val editing: Task? = null,
    val text: String = "",
    val date: LocalDate? = today,
    val minutes: Int? = null,
    val priority: Priority = Priority.None,
    /** True once a priority has been picked. Until then a new note stays canary and shows no priority chip. */
    val priorityPicked: Boolean = false,
    /** True once a project, or "No project", has been picked; only then does its chip show. */
    val projectPicked: Boolean = false,
    val project: String? = null,
    val picker: Picker? = null,
) {
    val time: LocalTime? get() = minutes?.let { LocalTime.of(it / 60, it % 60) }

    /** The priority the note shows in its colour and its picker: always the task's when editing. */
    val shownPriority: Priority? get() = priority.takeIf { priorityPicked || editing != null }

    /** The clock can't go back before today, or before the day of an overdue task being changed. */
    val earliest: LocalDate get() = minOf(today, editing?.date ?: today)
}

class NewTaskViewModel(private val tasks: TaskRepository, private val clock: AppClock) : ViewModel() {
    /** Null while the note is closed. */
    private val _draft = MutableStateFlow<NewTaskDraft?>(null)
    val draft: StateFlow<NewTaskDraft?> = _draft.asStateFlow()

    /** The projects a task can be filed under, in card order. */
    val projects: StateFlow<List<String>> = tasks.observeProjects()
        .map { all -> all.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val features: StateFlow<Features> = tasks.features.stateIn(viewModelScope, SharingStarted.Eagerly, Features())

    fun open() {
        _draft.value = NewTaskDraft(today = clock.today.value)
    }

    /** Opens the note from the Projects page: the project picker is already open, on [project]. */
    fun openForProject(project: String?) {
        _draft.value = NewTaskDraft(
            today = clock.today.value,
            projectPicked = project != null,
            project = project,
            picker = Picker.Project,
        )
    }

    /** Opens the note filled in with [task], to change it. */
    fun openToEdit(task: Task) {
        _draft.value = NewTaskDraft(
            today = clock.today.value,
            editing = task,
            text = task.title,
            date = task.date,
            minutes = task.time?.let { it.hour * 60 + it.minute },
            priority = task.priority,
            projectPicked = task.project != null,
            project = task.project,
        )
    }

    /** Opens the task with [id] to change it, unless it is gone or a note is already being written. */
    fun openToEdit(id: String) {
        viewModelScope.launch {
            val task = tasks.task(id) ?: return@launch
            if (_draft.value == null) openToEdit(task)
        }
    }

    fun dismiss() {
        _draft.value = null
    }

    fun setText(text: String) = edit { copy(text = text.replace('\n', ' ')) }

    /** Opens [picker], or closes it if it is already open. A task with no date gets today. */
    fun toggle(picker: Picker) = edit {
        when {
            this.picker == picker -> copy(picker = null)
            picker == Picker.When -> copy(picker = picker, date = date ?: today)
            else -> copy(picker = picker)
        }
    }

    fun shiftDay(days: Int) = edit { copy(date = (date ?: today).plusDays(days.toLong()).coerceAtLeast(earliest)) }

    /** From "any time", › goes to the next half hour and ‹ to the one before it; after that, 30 minutes a step. */
    fun shiftTime(steps: Int) = edit {
        val current = minutes
        if (current != null) return@edit copy(minutes = (current + steps * STEP_MINUTES).mod(DAY_MINUTES))
        val now = clock.now()
        val next = ((now.hour * 60 + now.minute) / STEP_MINUTES + 1) * STEP_MINUTES
        val first = if (steps > 0) next else next - STEP_MINUTES
        // Late in the evening the next half hour is tomorrow's.
        val day = date ?: today
        copy(minutes = first % DAY_MINUTES, date = if (first >= DAY_MINUTES && day == today) day.plusDays(1) else day)
    }

    fun setPriority(priority: Priority) = edit { copy(priority = priority, priorityPicked = true) }

    /** [project] is null for "No project". */
    fun setProject(project: String?) = edit { copy(projectPicked = true, project = project) }

    /** Creates the task, or saves the changes to it, and closes the note. Does nothing while the title is blank. */
    fun save() {
        val draft = _draft.value ?: return
        val title = draft.text.trim()
        if (title.isEmpty()) return
        _draft.value = null
        val result = TaskDraft(title, draft.date, draft.time, draft.priority, draft.project)
        val editing = draft.editing
        viewModelScope.launch {
            when {
                editing == null -> tasks.create(result)
                result != editing.asDraft() -> tasks.update(editing.id, result)
            }
        }
    }

    private fun edit(change: NewTaskDraft.() -> NewTaskDraft) = _draft.update { it?.change() }

    private fun Task.asDraft() = TaskDraft(title, date, time, priority, project)

    private companion object {
        const val STEP_MINUTES = 30
        const val DAY_MINUTES = 24 * 60
    }
}
