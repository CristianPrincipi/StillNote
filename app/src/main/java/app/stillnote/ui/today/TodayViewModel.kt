package app.stillnote.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.SyncStatus
import app.stillnote.data.Task
import app.stillnote.data.TaskOrder
import app.stillnote.data.TaskRepository
import app.stillnote.ui.calm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val open: List<Task> = emptyList(),
    val done: List<Task> = emptyList(),
    val loaded: Boolean = false,
    val sync: SyncStatus = SyncStatus.Idle,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val tasks: TaskRepository, clock: AppClock) : ViewModel() {
    val state: StateFlow<TodayUiState> = clock.today
        .flatMapLatest { date ->
            combine(tasks.observeTasks(date, date), tasks.syncStatus.calm()) { all, sync ->
                val (done, open) = all.sortedWith(TaskOrder.today).partition { it.done }
                TodayUiState(date, open, done, loaded = true, sync = sync)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(clock.today.value))

    /** True while a change that Notion refused is waiting for a retry. */
    val couldNotSave: StateFlow<Boolean> =
        tasks.hasFailedChanges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The task just deleted, while it can still be brought back. */
    private val _deleted = MutableStateFlow<Task?>(null)
    val deleted: StateFlow<Task?> = _deleted.asStateFlow()
    private var undoWindow: Job? = null

    fun setDone(task: Task, done: Boolean) {
        viewModelScope.launch { tasks.setDone(task.id, done) }
    }

    fun delete(task: Task) {
        undoWindow?.cancel()
        _deleted.value = task
        viewModelScope.launch { tasks.trash(task.id) }
        undoWindow = viewModelScope.launch {
            delay(UNDO_MILLIS)
            _deleted.value = null
        }
    }

    fun undoDelete() {
        val task = _deleted.value ?: return
        undoWindow?.cancel()
        _deleted.value = null
        viewModelScope.launch { tasks.restore(task.id) }
    }

    fun retrySaving() = tasks.retryFailedChanges()

    private companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
