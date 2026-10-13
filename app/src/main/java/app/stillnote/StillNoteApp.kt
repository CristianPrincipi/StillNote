package app.stillnote

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.stillnote.data.AppClock
import app.stillnote.data.Connectivity
import app.stillnote.data.DeviceClock
import app.stillnote.data.FakeData
import app.stillnote.data.FakeTaskRepository
import app.stillnote.data.FixedDateClock
import app.stillnote.data.LocalTaskRepository
import app.stillnote.data.SettingsStore
import app.stillnote.data.TaskRepository
import app.stillnote.data.local.StillDatabase
import app.stillnote.data.notion.KeystoreTokenVault
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionClient
import app.stillnote.data.notion.NotionSync
import app.stillnote.data.notion.PreferencesDatabaseStore
import app.stillnote.data.notion.PushWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.time.YearMonth

/** Holds the app's long-lived objects; the ViewModels are built from them. */
class StillNoteApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { StillDatabase.open(this) }
    private val connectivity by lazy { Connectivity(this) }
    private val notion by lazy {
        NotionClient(
            baseUrl = { debugNotionUrl() ?: "https://api.notion.com" },
            log = { if (BuildConfig.DEBUG) Log.d("StillNote", it) },
        )
    }

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val account: NotionAccount by lazy {
        NotionAccount(notion, KeystoreTokenVault(File(filesDir, "notion_token")), PreferencesDatabaseStore(this), database.tasks())
    }
    val sync: NotionSync by lazy { NotionSync(account, notion, database.tasks()) }

    lateinit var clock: AppClock
        private set
    lateinit var tasks: TaskRepository
        private set

    override fun onCreate() {
        super.onCreate()
        clock = DeviceClock(scope)
        tasks = LocalTaskRepository(
            tasks = database.tasks(),
            sync = sync,
            account = account,
            online = connectivity.online,
            scope = scope,
            schedulePush = { PushWorker.schedule(this) },
        )
        scope.launch {
            database.tasks().purgeTrashed(before = System.currentTimeMillis())
            // Catch up as soon as the network comes back.
            connectivity.online.drop(1).filter { it }.collect { syncNow() }
        }
    }

    /** Sends whatever is waiting, and pulls the month around today. */
    fun syncNow() {
        PushWorker.schedule(this)
        refreshThisMonth()
    }

    /** Pulls the month around today, which covers Today and the Calendar's first page. */
    fun refreshThisMonth() {
        val month = YearMonth.from(clock.today.value)
        tasks.refresh(month.atDay(1), month.atEndOfMonth())
    }

    // Debug builds only, for tools/screens.py and tools/fake_notion.py.

    /** Swaps in the mockups' sample tasks and their fixed "today". Nothing is saved or synced. */
    fun enterDemo() {
        check(BuildConfig.DEBUG)
        clock = FixedDateClock(FakeData.today)
        tasks = FakeTaskRepository()
    }

    /** Points the Notion client at another server, or back at Notion when [url] is empty. */
    fun setDebugNotionUrl(url: String) {
        check(BuildConfig.DEBUG)
        getSharedPreferences("debug", Context.MODE_PRIVATE).edit { putString("notion_url", url) }
    }

    private fun debugNotionUrl(): String? =
        if (!BuildConfig.DEBUG) null
        else getSharedPreferences("debug", Context.MODE_PRIVATE).getString("notion_url", null)?.takeIf { it.isNotEmpty() }
}
