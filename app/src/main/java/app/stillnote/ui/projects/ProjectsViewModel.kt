package app.stillnote.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.Features
import app.stillnote.data.Project
import app.stillnote.data.SyncStatus
import app.stillnote.data.Task
import app.stillnote.data.TaskRepository
import app.stillnote.ui.SyncOnPull
import app.stillnote.ui.calm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class ProjectsUiState(
    val projects: List<Project> = emptyList(),
    val features: Features = Features(),
    val sync: SyncStatus = SyncStatus.Idle,
    val loaded: Boolean = false,
) {
    val openNotes: Int get() = projects.sumOf { it.open.size }
}

class ProjectsViewModel(private val tasks: TaskRepository, private val clock: AppClock) : ViewModel() {
    val state: StateFlow<ProjectsUiState> =
        combine(tasks.observeProjects(), tasks.features, tasks.syncStatus.calm()) { projects, features, sync ->
            ProjectsUiState(projects, features, sync, loaded = true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectsUiState())

    /** Asks for the projects again; called each time the tab is shown. */
    fun refresh() = tasks.refreshProjects()

    /** Pulls the projects, and the month around today as Today does. */
    private val pull = SyncOnPull(viewModelScope) {
        val month = YearMonth.from(clock.today.value)
        tasks.syncNow(month.atDay(1), month.atEndOfMonth())
    }
    val syncing: StateFlow<Boolean> = pull.running

    fun syncNow() = pull.start()

    fun complete(task: Task) {
        viewModelScope.launch { tasks.setDone(task.id, true) }
    }

    fun create(name: String) {
        viewModelScope.launch { tasks.createProject(name) }
    }
}
