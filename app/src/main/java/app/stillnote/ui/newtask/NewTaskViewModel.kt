package app.stillnote.ui.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.Features
import app.stillnote.data.Priority
import app.stillnote.data.Reminder
import app.stillnote.data.ReminderSettings
import app.stillnote.data.Task
import app.stillnote.data.TaskDraft
import app.stillnote.data.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class Picker { When, Priority, Project }

/** The cards that open over the note from the When picker's day and time. */
enum class WhenDialog { Calendar, Clock }

/** The reminder stepper's stops, in order: the default, none, then the minutes before. */
val ReminderSteps: List<Int?> = listOf(null, Reminder.None) + Reminder.choices

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
    /** The task's own reminder, or null for the default in Settings. */
    val reminder: Int? = null,
    val picker: Picker? = null,
    /** The calendar or clock lying over the note, if one is open. */
    val dialog: WhenDialog? = null,
) {
    val time: LocalTime? get() = minutes?.let { LocalTime.of(it / 60, it % 60) }

    /** The priority the note shows in its colour and its picker: always the task's when editing. */
    val shownPriority: Priority? get() = priority.takeIf { priorityPicked || editing != null }

    /** The clock can't go back before today, or before the day of an overdue task being changed. */
    val earliest: LocalDate get() = minOf(today, editing?.date ?: today)

    /** A task with no date and no project would show on no screen, so only a task with a project can lose its date. */
    val canClearDate: Boolean get() = project != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewTaskViewModel(
    private val tasks: TaskRepository,
    private val clock: AppClock,
    reminders: Flow<ReminderSettings>,
) : ViewModel() {
    /** Null while the note is closed. */
    private val _draft = MutableStateFlow<NewTaskDraft?>(null)
    val draft: StateFlow<NewTaskDraft?> = _draft.asStateFlow()

    /** The projects a task can be filed under, in card order. */
    val projects: StateFlow<List<String>> = tasks.observeProjects()
        .map { all -> all.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val features: StateFlow<Features> = tasks.features.stateIn(viewModelScope, SharingStarted.Eagerly, Features())

    /** The month the calendar picker shows, or null while it is closed. */
    private val calendarMonth = MutableStateFlow<YearMonth?>(null)

    /**
     * The open tasks on each day of the calendar picker's month, for its squares. The task being
     * changed isn't counted: the squares show what else is on a day.
     */
    val openTasksByDay: StateFlow<Map<LocalDate, Int>> =
        combine(calendarMonth, _draft.map { it?.editing?.id }.distinctUntilChanged(), ::Pair)
            .flatMapLatest { (month, editing) ->
                if (month == null) return@flatMapLatest flowOf(emptyMap())
                tasks.observeTasks(month.atDay(1), month.atEndOfMonth()).map { all ->
                    all.filter { !it.done && it.id != editing }.mapNotNull { it.date }.groupingBy { it }.eachCount()
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Settings' reminder, which a task follows unless it has its own. */
    val reminderDefault: StateFlow<Int> =
        reminders.map { it.before }.stateIn(viewModelScope, SharingStarted.Eagerly, Reminder.DEFAULT)

    /**
     * True once a task that will remind has been saved, until [notificationsAsked]: the moment to
     * ask Android for permission to notify, if it hasn't been given.
     */
    private val _askNotifications = MutableStateFlow(false)
    val askNotifications: StateFlow<Boolean> = _askNotifications.asStateFlow()

    fun notificationsAsked() {
        _askNotifications.value = false
    }

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
            reminder = task.reminder,
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
        val next = nextHalfHour()
        val first = if (steps > 0) next else next - STEP_MINUTES
        // Late in the evening the next half hour is tomorrow's.
        val day = date ?: today
        copy(minutes = first % DAY_MINUTES, date = if (first >= DAY_MINUTES && day == today) day.plusDays(1) else day)
    }

    fun openDialog(dialog: WhenDialog) = edit { copy(dialog = dialog) }

    /** Closes the calendar or clock without changing anything. */
    fun closeDialog() = edit { copy(dialog = null) }

    /** The day picked on the calendar, or null for "No date", which also takes the time away. */
    fun setDate(date: LocalDate?) = edit {
        if (date == null) copy(date = null, minutes = null, dialog = null)
        else copy(date = date.coerceAtLeast(earliest), dialog = null)
    }

    /** The time picked on the clock, or null for "No time". A time needs a day, so an undated task gets today. */
    fun setTime(time: LocalTime?) = edit {
        if (time == null) copy(minutes = null, dialog = null)
        else copy(minutes = time.hour * 60 + time.minute, date = date ?: today, dialog = null)
    }

    /** Where the clock opens: the task's time, or from "any time" the next half hour. */
    fun clockStart(): LocalTime {
        val start = _draft.value?.minutes ?: (nextHalfHour() % DAY_MINUTES)
        return LocalTime.of(start / 60, start % 60)
    }

    /** Counts the open tasks of [month] for the calendar picker, asking Notion for them too. */
    fun showCalendarMonth(month: YearMonth) {
        calendarMonth.value = month
        tasks.refresh(month.atDay(1), month.atEndOfMonth())
    }

    /** Moves along [ReminderSteps], stopping at either end. */
    fun shiftReminder(steps: Int) = edit {
        copy(reminder = ReminderSteps[(ReminderSteps.indexOf(reminder) + steps).coerceIn(ReminderSteps.indices)])
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
        val result = TaskDraft(title, draft.date, draft.time, draft.priority, draft.project, draft.reminder)
        val editing = draft.editing
        if (draft.time != null && (draft.reminder ?: reminderDefault.value) != Reminder.None) _askNotifications.value = true
        viewModelScope.launch {
            if (editing == null) {
                tasks.create(result)
                return@launch
            }
            // The reminder stays on the phone, so changing only that leaves Notion out of it.
            if (result.copy(reminder = null) != editing.asDraft()) tasks.update(editing.id, result)
            if (draft.reminder != editing.reminder) tasks.setReminder(editing.id, draft.reminder)
        }
    }

    private fun edit(change: NewTaskDraft.() -> NewTaskDraft) = _draft.update { it?.change() }

    /** The next half hour from now, in minutes from midnight; late in the evening that's past 24:00. */
    private fun nextHalfHour(): Int {
        val now = clock.now()
        return ((now.hour * 60 + now.minute) / STEP_MINUTES + 1) * STEP_MINUTES
    }

    private fun Task.asDraft() = TaskDraft(title, date, time, priority, project)

    private companion object {
        const val STEP_MINUTES = 30
        const val DAY_MINUTES = 24 * 60
    }
}
