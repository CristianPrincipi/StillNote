package app.stillnote.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    /** Tasks dated from [from] to [to], both inclusive. */
    fun observeTasks(from: LocalDate, to: LocalDate): Flow<List<Task>>

    suspend fun create(draft: TaskDraft): Task

    suspend fun setDone(id: String, done: Boolean)

    suspend fun trash(id: String)

    suspend fun restore(id: String)
}
