package app.stillnote.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** What to tell the user about syncing, if anything. */
enum class SyncStatus {
    /** Nothing to report: up to date, or not using Notion at all. */
    Idle,
    Syncing,
    Offline,

    /** Notion answered, but the sync didn't go through. */
    Failed,
}

interface TaskRepository {
    /** Tasks dated from [from] to [to], both inclusive. */
    fun observeTasks(from: LocalDate, to: LocalDate): Flow<List<Task>>

    suspend fun create(draft: TaskDraft): Task

    /** The task with [id], or null if it is gone or deleted. */
    suspend fun task(id: String): Task?

    /** Gives the task with [id] the title, date, time, priority and project of [draft]. */
    suspend fun update(id: String, draft: TaskDraft)

    suspend fun setDone(id: String, done: Boolean)

    suspend fun trash(id: String)

    suspend fun restore(id: String)

    /** Every project in card order, including ones with no tasks. */
    fun observeProjects(): Flow<List<Project>>

    /** Adds an empty project; a blank or existing name is ignored. */
    suspend fun createProject(name: String)

    val features: Flow<Features>

    val syncStatus: Flow<SyncStatus>

    /** True while some change was refused by Notion and is waiting for [retryFailedChanges]. */
    val hasFailedChanges: Flow<Boolean>

    /** Asks for fresh tasks dated from [from] to [to]. Returns at once; the result arrives through [observeTasks]. */
    fun refresh(from: LocalDate, to: LocalDate)

    /** Asks for the current projects and their tasks, whatever their dates. */
    fun refreshProjects()

    /**
     * Sends every waiting change now, then pulls the tasks dated [from] to [to] and the projects.
     * Returns when that is over, however it went; [syncStatus] tells how.
     */
    suspend fun syncNow(from: LocalDate, to: LocalDate)

    fun retryFailedChanges()
}
