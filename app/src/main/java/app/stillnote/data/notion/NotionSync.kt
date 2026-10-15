package app.stillnote.data.notion

import app.stillnote.data.local.TaskDao
import app.stillnote.data.notion.NotionException.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Moves changes between the phone's database and Notion. Pushes and pulls never overlap,
 * which is what lets a pull trust that a clean row really matches what Notion was last sent.
 */
class NotionSync(
    private val account: NotionAccount,
    private val api: NotionApi,
    private val tasks: TaskDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    enum class Outcome {
        Done,
        NotConnected,

        /** Notion couldn't be reached; the work is still waiting. */
        Offline,

        /** Notion is rate limiting or failing; the work is still waiting. */
        Busy,

        /** Notion refused the token. */
        Unauthorized,
    }

    private val lock = Mutex()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** How the latest push or pull that actually talked to Notion ended. */
    private val _last = MutableStateFlow(Outcome.Done)
    val last: StateFlow<Outcome> = _last.asStateFlow()

    /** Sends every waiting local change, oldest first. A change Notion refuses is marked failed and skipped. */
    suspend fun push(): Outcome = exclusive { connection ->
        val mapping = connection.database.mapping
        tasks.purgeTrashed(before = now() - SETTLE_MILLIS)
        val pending = tasks.pending()
        for (row in pending) {
            try {
                val pageId = if (row.pageId == null) {
                    api.createPage(connection.token, connection.database.id, mapping.pageProperties(row, zone()))
                } else {
                    val properties = when {
                        row.trashed -> null
                        row.edited -> mapping.pageProperties(row, zone())
                        else -> mapping.doneProperty(row.done)
                    }
                    row.pageId.also { api.updatePage(connection.token, it, properties, row.trashed) }
                }
                tasks.markPushed(row.id, row.rev, pageId, now())
            } catch (e: NotionException) {
                when (e.kind) {
                    Kind.NotFound -> if (row.pageId != null) tasks.delete(row.id) else tasks.markFailed(row.id, row.rev)
                    Kind.Rejected -> tasks.markFailed(row.id, row.rev)
                    else -> throw e
                }
            }
        }
        pending.isNotEmpty()
    }

    /** Brings the tasks dated [from]..[to] in line with Notion. */
    suspend fun pull(from: LocalDate, to: LocalDate): Outcome = exclusive { connection ->
        val mapping = connection.database.mapping
        // Notion compares dates in UTC, so ask for a day more on each side and sort it out locally.
        val pages = api.query(connection.token, connection.database.id, mapping.dateFilter(from.minusDays(1), to.plusDays(1)))
        val zone = zone()
        tasks.applyMonthPull(from.toString(), to.toString(), pages.map { mapping.toRow(it, zone) }, now() - SETTLE_MILLIS)
        true
    }

    /** Brings the list of projects, and every task that has one, in line with Notion. */
    suspend fun pullProjects(): Outcome = exclusive { connection ->
        val mapping = connection.database.mapping
        val filter = mapping.projectFilter() ?: return@exclusive false
        tasks.applyProjects(mapping.projects(api.schema(connection.token, connection.database.id)))
        val pages = api.query(connection.token, connection.database.id, filter)
        val zone = zone()
        tasks.applyProjectPull(pages.map { mapping.toRow(it, zone) }, now() - SETTLE_MILLIS)
        true
    }

    /** Stops retrying the changes that are still waiting; they show as failed until the user retries. */
    suspend fun giveUp() = tasks.failPending()

    /**
     * Runs [work], which returns whether it had anything to ask Notion. A push with nothing
     * to send says nothing about Notion, so it must not hide that the last pull failed.
     */
    private suspend fun exclusive(work: suspend (Connection) -> Boolean): Outcome = lock.withLock {
        val connection = account.connection() ?: return@withLock Outcome.NotConnected
        _running.value = true
        var askedNotion = true
        val outcome = try {
            askedNotion = work(connection)
            Outcome.Done
        } catch (e: NotionException) {
            when (e.kind) {
                Kind.Network -> Outcome.Offline
                Kind.Unauthorized -> Outcome.Unauthorized.also { account.markTokenRejected() }
                else -> Outcome.Busy
            }
        } finally {
            _running.value = false
        }
        outcome.also { if (askedNotion) _last.value = it }
    }

    private companion object {
        /** How long after a push Notion's query results are not yet trusted for that row. */
        const val SETTLE_MILLIS = 30_000L
    }
}
