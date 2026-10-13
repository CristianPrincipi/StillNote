package app.stillnote.data.notion

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.stillnote.data.Priority
import app.stillnote.data.local.StillDatabase
import app.stillnote.data.local.TaskDao
import app.stillnote.data.local.TaskRow
import app.stillnote.data.notion.NotionException.Kind
import app.stillnote.data.notion.NotionSync.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** The sync engine against the real SQLite schema and a scripted Notion. */
@RunWith(AndroidJUnit4::class)
class NotionSyncTest {
    private val mapping = PropertyMapping(
        title = "title",
        done = "chk",
        date = "dt",
        priority = "pri",
        priorityOptions = Priority.entries.associateWith { it.label },
        project = "prj",
        noProjectOption = "Task",
    )
    private val october = LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31)

    private lateinit var database: StillDatabase
    private lateinit var tasks: TaskDao
    private val notion = ScriptedNotion()
    private val vault = MemoryVault("token")
    private val store = MemoryStore(Database("ds-1", "Tasks", mapping))
    private var clock = 1_000_000L
    private lateinit var account: NotionAccount
    private lateinit var sync: NotionSync

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StillDatabase::class.java).build()
        tasks = database.tasks()
        account = NotionAccount(notion, vault, store, tasks)
        sync = NotionSync(account, notion, tasks, now = { clock }, zone = { ZoneId.of("Europe/Rome") })
    }

    @After
    fun close() = database.close()

    private fun local(id: String, title: String = id, date: String = "2026-10-13", done: Boolean = false) =
        TaskRow(id = id, title = title, date = date, priority = "None", done = done, created = clock, dirty = true, changedAt = clock)

    private fun synced(id: String, pageId: String, date: String? = "2026-10-13", done: Boolean = false, project: String? = null) =
        TaskRow(id = id, pageId = pageId, title = id, date = date, priority = "None", done = done, project = project, created = clock)

    private fun page(id: String, title: String, date: String? = "2026-10-13", done: Boolean = false, project: String? = null) = RemotePage(
        id = id,
        createdTime = 5,
        properties = listOfNotNull(
            "title" to buildJsonObject { put("title", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject { put("plain_text", title) }))) },
            "chk" to buildJsonObject { put("checkbox", done) },
            date?.let { "dt" to buildJsonObject { putJsonObject("date") { put("start", it) } } },
            project?.let { "prj" to buildJsonObject { putJsonObject("select") { put("name", it) } } },
        ).toMap(),
    )

    private val tasksSchema = Schema(
        "Tasks",
        listOf(
            SchemaProperty("title", "Name", "title"),
            SchemaProperty("chk", "Done", "checkbox"),
            SchemaProperty("dt", "Date", "date"),
            SchemaProperty("prj", "Project", "select", listOf("Task", "Work", "Home")),
        ),
    )

    private suspend fun all() = tasks.observe("0000-01-01", "9999-12-31").first()
    private suspend fun row(id: String) = database.query("SELECT pageId, dirty, failed, trashed FROM tasks WHERE id = ?", arrayOf(id)).use {
        if (!it.moveToFirst()) null else State(it.getString(0), it.getInt(1) == 1, it.getInt(2) == 1, it.getInt(3) == 1)
    }

    private data class State(val pageId: String?, val dirty: Boolean, val failed: Boolean, val trashed: Boolean)

    @Test
    fun aNewTaskBecomesAPageAndStopsBeingPending() = runBlocking {
        tasks.insert(local("a", title = "Buy a card"))

        assertEquals(Outcome.Done, sync.push())

        val created = notion.created.single()
        assertEquals("ds-1", created.first)
        assertEquals(mapping.newPageProperties(local("a", title = "Buy a card"), ZoneId.of("Europe/Rome")), created.second)
        assertEquals(State("page-1", dirty = false, failed = false, trashed = false), row("a"))
        assertEquals(0, tasks.observePendingCount().first())
    }

    @Test
    fun tickingAndDeletingASyncedTaskUpdatesItsPage() = runBlocking {
        tasks.insert(synced("a", "p-a"))
        tasks.insert(synced("b", "p-b"))
        tasks.setDone("a", true, clock)
        tasks.setTrashed("b", true, clock)

        assertEquals(Outcome.Done, sync.push())

        assertEquals(
            listOf(Update("p-a", mapping.doneProperty(true), inTrash = false), Update("p-b", null, inTrash = true)),
            notion.updates,
        )
        assertEquals(listOf("a"), all().map { it.id })
        // The deleted task stays until Undo can no longer bring it back, then goes.
        assertEquals(State("p-b", dirty = false, failed = false, trashed = true), row("b"))
        clock += 60_000
        sync.push()
        assertNull(row("b"))
    }

    @Test
    fun aTaskDeletedBeforeItReachedNotionIsNeverSent() = runBlocking {
        tasks.insert(local("a"))
        tasks.setTrashed("a", true, clock)

        assertEquals(Outcome.Done, sync.push())
        assertTrue(notion.created.isEmpty())
        assertEquals(0, tasks.observePendingCount().first())

        // Undo within the window brings it back, and then it is created.
        tasks.setTrashed("a", false, clock)
        sync.push()
        assertEquals(1, notion.created.size)
    }

    @Test
    fun aChangeMadeWhileItsPushIsInFlightIsPushedNext() = runBlocking {
        tasks.insert(local("a"))
        notion.duringCreate = { tasks.setDone("a", true, clock) }

        sync.push()
        assertEquals(State("page-1", dirty = true, failed = false, trashed = false), row("a"))

        notion.duringCreate = {}
        sync.push()
        assertEquals(listOf(Update("page-1", mapping.doneProperty(true), inTrash = false)), notion.updates)
        assertFalse(row("a")!!.dirty)
    }

    @Test
    fun aRefusedChangeIsSetAsideAndTheRestGoThrough() = runBlocking {
        tasks.insert(synced("a", "p-a"))
        tasks.insert(synced("b", "p-b"))
        tasks.setDone("a", true, clock)
        tasks.setDone("b", true, clock)
        notion.failUpdate["p-a"] = Kind.Rejected

        assertEquals(Outcome.Done, sync.push())
        assertEquals(State("p-a", dirty = true, failed = true, trashed = false), row("a"))
        assertFalse(row("b")!!.dirty)
        assertEquals(1, tasks.observeFailedCount().first())

        // Retry puts it back in the queue.
        notion.failUpdate.clear()
        tasks.retryFailed()
        sync.push()
        assertEquals(State("p-a", dirty = false, failed = false, trashed = false), row("a"))
    }

    @Test
    fun aPageThatIsGoneFromNotionTakesItsTaskWithIt() = runBlocking {
        tasks.insert(synced("a", "p-a"))
        tasks.setDone("a", true, clock)
        notion.failUpdate["p-a"] = Kind.NotFound

        assertEquals(Outcome.Done, sync.push())
        assertNull(row("a"))
    }

    @Test
    fun changesWaitWhileOfflineOrBusy_andARejectedTokenIsRemembered() = runBlocking {
        tasks.insert(local("a"))

        notion.failAll = Kind.Network
        assertEquals(Outcome.Offline, sync.push())
        notion.failAll = Kind.Busy
        assertEquals(Outcome.Busy, sync.push())
        assertEquals(State(null, dirty = true, failed = false, trashed = false), row("a"))
        assertFalse(account.tokenRejected.value)

        notion.failAll = Kind.Unauthorized
        assertEquals(Outcome.Unauthorized, sync.push())
        assertTrue(account.tokenRejected.value)
        assertEquals(1, tasks.observePendingCount().first())

        // Giving up turns what is still waiting into failed changes.
        sync.giveUp()
        assertEquals(0, tasks.observePendingCount().first())
        assertEquals(1, tasks.observeFailedCount().first())
    }

    @Test
    fun aPushWithNothingToSendDoesNotHideAFailedPull() = runBlocking {
        notion.failAll = Kind.Busy
        assertEquals(Outcome.Busy, sync.pull(october.first, october.second))
        notion.failAll = null

        assertEquals(Outcome.Done, sync.push())
        assertEquals(Outcome.Busy, sync.last.value)

        assertEquals(Outcome.Done, sync.pull(october.first, october.second))
        assertEquals(Outcome.Done, sync.last.value)
    }

    @Test
    fun withoutAConnectionNothingIsSentAndLocalTasksStay() = runBlocking {
        store.save(null)
        tasks.insert(local("a"))

        assertEquals(Outcome.NotConnected, sync.push())
        assertEquals(Outcome.NotConnected, sync.pull(october.first, october.second))
        assertEquals(0, notion.calls)
        assertEquals(listOf("a"), all().map { it.id })
    }

    @Test
    fun aPullAddsUpdatesAndRemovesToMatchNotion() = runBlocking {
        tasks.insert(synced("kept", "p-kept"))
        tasks.insert(synced("gone", "p-gone"))
        tasks.insert(synced("elsewhere", "p-september", date = "2026-09-30"))
        tasks.insert(local("mine"))
        notion.pages = listOf(page("p-kept", "Renamed", date = "2026-10-20", done = true), page("p-new", "From Notion"))

        assertEquals(Outcome.Done, sync.pull(october.first, october.second))

        // Asked for a day more on each side, because Notion compares in UTC.
        assertEquals(mapping.dateFilter(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 11, 1)), notion.queried.single())
        val byTitle = all().associateBy { it.title }
        assertEquals(setOf("Renamed", "From Notion", "elsewhere", "mine"), byTitle.keys)
        assertEquals("kept", byTitle.getValue("Renamed").id)
        assertEquals("2026-10-20", byTitle.getValue("Renamed").date)
        assertTrue(byTitle.getValue("Renamed").done)
        assertEquals("p-new", byTitle.getValue("From Notion").pageId)
    }

    @Test
    fun aPullLeavesUnpushedAndJustPushedTasksAlone() = runBlocking {
        tasks.insert(synced("ticked", "p-ticked"))
        tasks.setDone("ticked", true, clock)
        tasks.insert(local("fresh"))
        sync.push()
        tasks.insert(synced("dirty", "p-dirty"))
        tasks.setDone("dirty", true, clock)

        // Notion's query hasn't caught up: it still shows the old state and lacks the new page.
        notion.pages = listOf(page("p-ticked", "ticked", done = false), page("p-dirty", "dirty", done = false))
        clock += 5_000
        sync.pull(october.first, october.second)

        val now = all().associateBy { it.id }
        assertTrue(now.getValue("ticked").done)
        assertTrue(now.getValue("dirty").done)
        assertTrue("fresh" in now)

        // Once the push has settled, Notion is believed again.
        clock += 60_000
        notion.pages = listOf(page("p-ticked", "ticked", done = false), page("p-dirty", "dirty", done = false), page("page-1", "fresh"))
        sync.pull(october.first, october.second)
        assertFalse(all().first { it.id == "ticked" }.done)
        assertTrue(all().first { it.id == "dirty" }.done)
    }

    @Test
    fun aProjectPullBringsTheProjectsAndTheirTasksWhateverTheDate() = runBlocking {
        notion.schemas["ds-1"] = tasksSchema
        tasks.insert(synced("old", "p-old", date = "2026-03-02", project = "Work"))
        tasks.insert(synced("moved", "p-moved", date = null, project = "Work"))
        tasks.insert(synced("plain", "p-plain", project = null))
        tasks.addLocalProject("Garden")
        notion.pages = listOf(
            page("p-old", "Still here", date = "2026-03-02", done = true, project = "Work"),
            page("p-undated", "No date yet", date = null, project = "Home"),
        )

        assertEquals(Outcome.Done, sync.pullProjects())

        assertEquals(mapping.projectFilter(), notion.queried.single())
        // Notion's options in its order, minus "Task"; the project made here comes after them.
        assertEquals(listOf("Work", "Home", "Garden"), tasks.observeProjects().first().map { it.name })
        val byProject = tasks.observeProjectTasks().first().groupBy({ it.project }, { it.title })
        assertEquals(mapOf("Work" to listOf("Still here"), "Home" to listOf("No date yet")), byProject)
        assertNull(tasks.observeProjectTasks().first().first { it.title == "No date yet" }.date)
        // A task with no project is none of this pull's business.
        assertTrue(all().any { it.id == "plain" })

        // Once Notion has an option called Garden, the local project is just a project.
        notion.schemas["ds-1"] = tasksSchema.copy(properties = tasksSchema.properties.map { if (it.id == "prj") it.copy(options = it.options + "Garden") else it })
        sync.pullProjects()
        account.signOut()
        assertTrue(tasks.observeProjects().first().isEmpty())
    }

    @Test
    fun aProjectMadeOnThePhoneStaysWhenNotionIsDisconnected() = runBlocking {
        notion.schemas["ds-1"] = tasksSchema
        sync.pullProjects()
        tasks.addLocalProject("Garden")
        tasks.addLocalProject("garden")

        account.signOut()

        assertEquals(listOf("Garden"), tasks.observeProjects().first().map { it.name })
    }

    @Test
    fun choosingAnotherDatabaseOrSigningOutDropsNotionTasksAndKeepsLocalOnes() = runBlocking {
        tasks.insert(synced("theirs", "p-1"))
        tasks.insert(local("mine"))
        val other = mapping.copy(done = "c", date = "d", priority = null, project = null)

        account.choose(DataSource("ds-2", "Other"), other)
        assertEquals(Database("ds-2", "Other", other), store.database.first())
        assertEquals(listOf("mine"), all().map { it.id })

        // Changing only the fields of the same database keeps what is on the phone.
        tasks.insert(synced("kept", "p-2"))
        account.choose(DataSource("ds-2", "Other"), other.copy(priority = "pri"))
        assertEquals(setOf("mine", "kept"), all().map { it.id }.toSet())

        account.signOut()
        assertNull(vault.load())
        assertNull(account.connection())
        assertEquals(listOf("mine"), all().map { it.id })
    }
}

private data class Update(val pageId: String, val properties: JsonObject?, val inTrash: Boolean)

private class ScriptedNotion : NotionApi {
    var calls = 0
    var pages = emptyList<RemotePage>()
    val schemas = mutableMapOf<String, Schema>()
    val created = mutableListOf<Pair<String, JsonObject>>()
    val updates = mutableListOf<Update>()
    val queried = mutableListOf<JsonObject>()
    var failAll: Kind? = null
    val failUpdate = mutableMapOf<String, Kind>()
    var duringCreate: suspend () -> Unit = {}

    private fun called() {
        calls++
        failAll?.let { throw NotionException(it) }
    }

    override suspend fun checkToken(token: String) = called()

    override suspend fun dataSources(token: String): List<DataSource> = emptyList<DataSource>().also { called() }

    override suspend fun schema(token: String, dataSourceId: String): Schema = schemas.getValue(dataSourceId).also { called() }

    override suspend fun query(token: String, dataSourceId: String, filter: JsonObject): List<RemotePage> {
        called()
        queried += filter
        return pages
    }

    override suspend fun createPage(token: String, dataSourceId: String, properties: JsonObject): String {
        called()
        created += dataSourceId to properties
        duringCreate()
        return "page-${created.size}"
    }

    override suspend fun updatePage(token: String, pageId: String, properties: JsonObject?, inTrash: Boolean) {
        called()
        failUpdate[pageId]?.let { throw NotionException(it) }
        updates += Update(pageId, properties, inTrash)
    }
}

private class MemoryVault(private var token: String?) : TokenVault {
    override fun load() = token
    override fun save(token: String) {
        this.token = token
    }
    override fun clear() {
        token = null
    }
}

private class MemoryStore(initial: Database?) : DatabaseStore {
    private val state = MutableStateFlow(initial)
    override val database: Flow<Database?> = state
    override suspend fun save(database: Database?) {
        state.value = database
    }
}
