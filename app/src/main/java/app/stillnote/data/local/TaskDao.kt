package app.stillnote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {
    @Query("SELECT * FROM tasks WHERE trashed = 0 AND date BETWEEN :from AND :to ORDER BY created, id")
    abstract fun observe(from: String, to: String): Flow<List<TaskRow>>

    @Insert
    abstract suspend fun insert(row: TaskRow)

    @Update
    abstract suspend fun update(row: TaskRow)

    @Query("DELETE FROM tasks WHERE id = :id")
    abstract suspend fun delete(id: String)

    // Local changes: each one marks the row for the next push and clears an earlier refusal.

    @Query("UPDATE tasks SET done = :done, dirty = 1, failed = 0, rev = rev + 1, changedAt = :now WHERE id = :id")
    abstract suspend fun setDone(id: String, done: Boolean, now: Long)

    @Query("UPDATE tasks SET trashed = :trashed, dirty = 1, failed = 0, rev = rev + 1, changedAt = :now WHERE id = :id")
    abstract suspend fun setTrashed(id: String, trashed: Boolean, now: Long)

    // Pushing to Notion. A deleted task that never reached Notion has nothing to push.

    @Query("SELECT * FROM tasks WHERE dirty = 1 AND failed = 0 AND NOT (trashed = 1 AND pageId IS NULL) ORDER BY created, id")
    abstract suspend fun pending(): List<TaskRow>

    @Query("SELECT COUNT(*) FROM tasks WHERE dirty = 1 AND failed = 0 AND NOT (trashed = 1 AND pageId IS NULL)")
    abstract fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE failed = 1")
    abstract fun observeFailedCount(): Flow<Int>

    /** Records a successful push. The row stays dirty if it changed again while the push was in flight. */
    @Query(
        "UPDATE tasks SET pageId = :pageId, pushedAt = :now, " +
            "dirty = CASE WHEN rev = :rev THEN 0 ELSE dirty END WHERE id = :id"
    )
    abstract suspend fun markPushed(id: String, rev: Int, pageId: String, now: Long)

    @Query("UPDATE tasks SET failed = 1 WHERE id = :id AND rev = :rev")
    abstract suspend fun markFailed(id: String, rev: Int)

    @Query("UPDATE tasks SET failed = 1 WHERE dirty = 1 AND NOT (trashed = 1 AND pageId IS NULL)")
    abstract suspend fun failPending()

    @Query("UPDATE tasks SET failed = 0 WHERE failed = 1")
    abstract suspend fun retryFailed()

    // Housekeeping.

    /**
     * Drops tasks deleted before [before] that have nothing left to push. The delay keeps a
     * deleted task around for as long as it can still be brought back with Undo.
     */
    @Query("DELETE FROM tasks WHERE trashed = 1 AND changedAt < :before AND (pageId IS NULL OR dirty = 0)")
    abstract suspend fun purgeTrashed(before: Long)

    @Query("DELETE FROM tasks WHERE pageId IS NOT NULL")
    protected abstract suspend fun deleteSyncedTasks()

    @Query("DELETE FROM projects WHERE local = 0")
    protected abstract suspend fun deleteSyncedProjects()

    /** Forgets everything that came from Notion; tasks and projects that only exist here stay. */
    @Transaction
    open suspend fun deleteSynced() {
        deleteSyncedTasks()
        deleteSyncedProjects()
    }

    // Projects.

    @Query("SELECT * FROM projects ORDER BY position, name")
    abstract fun observeProjects(): Flow<List<ProjectRow>>

    @Query("SELECT * FROM tasks WHERE trashed = 0 AND project IS NOT NULL ORDER BY created, id")
    abstract fun observeProjectTasks(): Flow<List<TaskRow>>

    @Query("SELECT * FROM projects")
    protected abstract suspend fun projects(): List<ProjectRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putProject(row: ProjectRow)

    @Query("DELETE FROM projects WHERE name = :name")
    protected abstract suspend fun deleteProject(name: String)

    /** Adds a project made on this phone at the end of the list, unless the name is taken. */
    @Transaction
    open suspend fun addLocalProject(name: String) {
        val existing = projects()
        if (existing.any { it.name.equals(name, ignoreCase = true) }) return
        putProject(ProjectRow(name, position = (existing.maxOfOrNull { it.position } ?: -1) + 1, local = true))
    }

    /**
     * Makes the list match Notion's [names], in Notion's order. A project made here stays,
     * after Notion's, until Notion has an option of that name.
     */
    @Transaction
    open suspend fun applyProjects(names: List<String>) {
        val existing = projects()
        existing.filter { !it.local && it.name !in names }.forEach { deleteProject(it.name) }
        names.forEachIndexed { index, name -> putProject(ProjectRow(name, position = index)) }
        existing.filter { it.local && it.name !in names }.sortedBy { it.position }.forEachIndexed { index, row ->
            putProject(row.copy(position = names.size + index))
        }
    }

    // Pulling from Notion.

    @Query("SELECT * FROM tasks WHERE pageId IN (:pageIds)")
    protected abstract suspend fun byPageIds(pageIds: List<String>): List<TaskRow>

    @Query("SELECT * FROM tasks WHERE pageId IS NOT NULL AND date BETWEEN :from AND :to")
    protected abstract suspend fun syncedBetween(from: String, to: String): List<TaskRow>

    @Query("SELECT * FROM tasks WHERE pageId IS NOT NULL AND project IS NOT NULL")
    protected abstract suspend fun syncedWithProject(): List<TaskRow>

    /**
     * Makes the local copy match what Notion returned for the dates [from]..[to]: new pages are
     * added, known ones updated, and tasks in that range that Notion no longer has are removed.
     */
    @Transaction
    open suspend fun applyMonthPull(from: String, to: String, pages: List<TaskRow>, settledBefore: Long) =
        merge(pages, syncedBetween(from, to), settledBefore)

    /** The same for every task that has a project, whatever its date. */
    @Transaction
    open suspend fun applyProjectPull(pages: List<TaskRow>, settledBefore: Long) =
        merge(pages, syncedWithProject(), settledBefore)

    /**
     * [expected] are the local tasks that [pages] would contain if Notion still had them.
     *
     * Rows with unpushed changes are left alone, and so are rows pushed after [settledBefore]:
     * a query made right after a write can still return the old state.
     */
    private suspend fun merge(pages: List<TaskRow>, expected: List<TaskRow>, settledBefore: Long) {
        val incoming = pages.associateBy { it.pageId!! }
        val known = (incoming.keys.chunked(500).flatMap { byPageIds(it) } + expected).associateBy { it.pageId!! }

        for ((pageId, page) in incoming) {
            val row = known[pageId]
            when {
                row == null -> insert(page)
                row.dirty || row.pushedAt >= settledBefore -> Unit
                else -> update(
                    row.copy(
                        title = page.title,
                        date = page.date,
                        minutes = page.minutes,
                        priority = page.priority,
                        project = page.project,
                        done = page.done,
                        created = page.created,
                        trashed = false,
                        failed = false,
                    )
                )
            }
        }
        for (row in expected) {
            if (row.pageId !in incoming && !row.dirty && row.pushedAt < settledBefore) delete(row.id)
        }
    }
}
