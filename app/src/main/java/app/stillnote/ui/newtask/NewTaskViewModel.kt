package app.stillnote.ui.newtask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.Priority
import app.stillnote.data.TaskDraft
import app.stillnote.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class Picker { When, Priority }

/**
 * The note being written. [minutes] is the time of day, null until the clock is first opened;
 * [priority] is null until one is picked, which keeps the note canary and saves as None.
 */
data class NewTaskDraft(
    val today: LocalDate,
    val text: String = "",
    val dayOffset: Int = 0,
    val minutes: Int? = null,
    val priority: Priority? = null,
    val picker: Picker? = null,
) {
    val date: LocalDate get() = today.plusDays(dayOffset.toLong())
    val time: LocalTime? get() = minutes?.let { LocalTime.of(it / 60, it % 60) }
}

class NewTaskViewModel(private val tasks: TaskRepository, private val clock: AppClock) : ViewModel() {
    /** Null while the note is closed. */
    private val _draft = MutableStateFlow<NewTaskDraft?>(null)
    val draft: StateFlow<NewTaskDraft?> = _draft.asStateFlow()

    fun open() {
        _draft.value = NewTaskDraft(today = clock.today())
    }

    fun dismiss() {
        _draft.value = null
    }

    fun setText(text: String) = edit { copy(text = text.replace('\n', ' ')) }

    /** Opens [picker], or closes it if it is already open. The clock starts at the next half hour. */
    fun toggle(picker: Picker) = edit {
        when {
            this.picker == picker -> copy(picker = null)
            picker == Picker.When && minutes == null -> {
                val now = clock.now()
                val next = ((now.hour * 60 + now.minute) / STEP_MINUTES + 1) * STEP_MINUTES
                copy(picker = picker, minutes = next % DAY_MINUTES, dayOffset = dayOffset + next / DAY_MINUTES)
            }
            else -> copy(picker = picker)
        }
    }

    fun shiftDay(days: Int) = edit { copy(dayOffset = (dayOffset + days).coerceAtLeast(0)) }

    fun shiftTime(steps: Int) = edit {
        copy(minutes = ((minutes ?: 0) + steps * STEP_MINUTES).mod(DAY_MINUTES))
    }

    fun setPriority(priority: Priority) = edit { copy(priority = priority) }

    /** Creates the task and closes the note. Does nothing while the title is blank. */
    fun save() {
        val draft = _draft.value ?: return
        val title = draft.text.trim()
        if (title.isEmpty()) return
        _draft.value = null
        viewModelScope.launch {
            tasks.create(TaskDraft(title, draft.date, draft.time, draft.priority ?: Priority.None))
        }
    }

    private fun edit(change: NewTaskDraft.() -> NewTaskDraft) = _draft.update { it?.change() }

    private companion object {
        const val STEP_MINUTES = 30
        const val DAY_MINUTES = 24 * 60
    }
}
