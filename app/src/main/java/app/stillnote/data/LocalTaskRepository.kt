package app.stillnote.data

import app.stillnote.data.local.TaskDao
import app.stillnote.data.local.TaskRow
import app.stillnote.data.local.toTask
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/**
 * Tasks live in the phone's database, so every screen reads and writes there and never waits
 * for the network. With Notion connected, each change is queued for a push and [refresh] pulls.
 */
class LocalTaskRepository(
    private val tasks: TaskDao,
    private val sync: NotionSync,
    account: NotionAccount,
    online: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val schedulePush: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : TaskRepository {

    override fun observeTasks(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        tasks.observe(from.toString(), to.toString()).map { rows -> rows.map(TaskRow::toTask) }

    override suspend fun create(draft: TaskDraft): Task {
        val row = TaskRow(
            id = UUID.randomUUID().toString(),
            title = draft.title,
            date = draft.date.toString(),
            minutes = draft.time?.let { it.hour * 60 + it.minute },
            priority = draft.priority.name,
            created = now(),
            dirty = true,
            changedAt = now(),
        )
        tasks.insert(row)
        schedulePush()
        return row.toTask()
    }

    override suspend fun setDone(id: String, done: Boolean) {
        tasks.setDone(id, done, now())
        schedulePush()
    }

    override suspend fun trash(id: String) {
        tasks.setTrashed(id, true, now())
        schedulePush()
    }

    override suspend fun restore(id: String) {
        tasks.setTrashed(id, false, now())
        schedulePush()
    }

    override val syncStatus: Flow<SyncStatus> = combine(
        account.database,
        online,
        sync.running,
        sync.last,
        tasks.observePendingCount(),
    ) { database, online, running, last, pending ->
        when {
            database == null -> SyncStatus.Idle
            !online || last == NotionSync.Outcome.Offline -> SyncStatus.Offline
            running || (pending > 0 && last == NotionSync.Outcome.Done) -> SyncStatus.Syncing
            last != NotionSync.Outcome.Done -> SyncStatus.Failed
            else -> SyncStatus.Idle
        }
    }.distinctUntilChanged()

    override val hasFailedChanges: Flow<Boolean> = tasks.observeFailedCount().map { it > 0 }.distinctUntilChanged()

    override fun refresh(from: LocalDate, to: LocalDate) {
        scope.launch { sync.pull(from, to) }
    }

    override fun retryFailedChanges() {
        scope.launch {
            tasks.retryFailed()
            schedulePush()
        }
    }
}
