package app.stillnote

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import app.stillnote.data.AppClock
import app.stillnote.data.Connectivity
import app.stillnote.data.DeviceClock
import app.stillnote.data.FakeData
import app.stillnote.data.FakeTaskRepository
import app.stillnote.data.FixedDateClock
import app.stillnote.data.HandFont
import app.stillnote.data.LocalTaskRepository
import app.stillnote.data.SettingsStore
import app.stillnote.data.TaskOrder
import app.stillnote.data.TaskRepository
import app.stillnote.data.local.StillDatabase
import app.stillnote.data.notion.KeystoreTokenVault
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionClient
import app.stillnote.data.notion.NotionSync
import app.stillnote.data.notion.PreferencesDatabaseStore
import app.stillnote.data.notion.PullWorker
import app.stillnote.data.notion.PushWorker
import app.stillnote.reminders.Reminders
import app.stillnote.widget.TodayWidget
import app.stillnote.widget.WidgetKind
import app.stillnote.widget.WidgetNote
import app.stillnote.widget.WidgetPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
    lateinit var reminders: Reminders
        private set

    private var widgetWatch: Job? = null

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
        // Always the phone's own tasks, also in demo mode, which swaps [tasks] for the sample ones.
        val stored = tasks
        reminders = Reminders(this, database.tasks(), settings, complete = { stored.setDone(it, true) })
        reminders.watch(scope)
        scope.launch { database.tasks().purgeTrashed(before = System.currentTimeMillis()) }
        scope.launch {
            // Pull in the background only while a database is connected.
            account.database.map { it != null }.distinctUntilChanged().collect { connected ->
                if (connected) PullWorker.start(this@StillNoteApp) else PullWorker.stop(this@StillNoteApp)
            }
        }
        watchWidget()
    }

    /** What the widget shows, kept current: today's open tasks in the widget's order, and the hand font. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun widgetNotes(): Flow<WidgetNote> {
        val tasks = tasks
        val today = clock.today.flatMapLatest { day -> tasks.observeTasks(day, day).map { day to it } }
        return combine(today, settings.handFont) { (day, all), font ->
            WidgetNote(day, all.filter { !it.done }.sortedWith(TaskOrder.widget), font)
        }.distinctUntilChanged()
    }

    /**
     * Redraws the widget whenever what it shows changes, for as long as the app runs: after a
     * change made in the app or the widget, a sync, a new font, or midnight. A widget that is on
     * screen already redraws by itself; this reaches the ones whose drawing has gone to sleep.
     */
    private fun watchWidget() {
        widgetWatch?.cancel()
        widgetWatch = scope.launch { widgetNotes().collect { TodayWidget().updateAll(this@StillNoteApp) } }
    }

    /** Runs [work] for a broadcast receiver, which Android keeps alive until [result] is finished. */
    fun finishInBackground(result: BroadcastReceiver.PendingResult, work: suspend () -> Unit) {
        scope.launch {
            try {
                work()
            } finally {
                result.finish()
            }
        }
    }

    /** Sends whatever is waiting, and pulls the month around today and the projects. */
    fun syncNow() {
        PushWorker.schedule(this)
        refreshThisMonth()
        tasks.refreshProjects()
    }

    /**
     * Syncs each time the network comes back, for as long as the caller runs. Only while the app
     * is on screen: with it closed the workers wait for the network themselves, and a worker
     * starting the app would otherwise set off a second sync as its network appears.
     */
    suspend fun syncWhenBackOnline() {
        connectivity.online.drop(1).filter { it }.collect { syncNow() }
    }

    /** What [PullWorker] does with the app closed: the same as [syncNow], and returns when it's over. */
    suspend fun syncInBackground() {
        clock.refresh()
        val month = YearMonth.from(clock.today.value)
        tasks.syncNow(month.atDay(1), month.atEndOfMonth())
    }

    /** Pulls the month around today, which covers Today and the Calendar's first page. */
    fun refreshThisMonth() {
        val month = YearMonth.from(clock.today.value)
        tasks.refresh(month.atDay(1), month.atEndOfMonth())
    }

    // Debug builds only, for tools/screens.py and tools/fake_notion.py.

    /** True in demo mode, which also skips the first-run flow. */
    var demo = false
        private set

    /** Swaps in the mockups' sample tasks and their fixed "today". Nothing is saved or synced. */
    fun enterDemo() {
        check(BuildConfig.DEBUG)
        demo = true
        clock = FixedDateClock(FakeData.today)
        tasks = FakeTaskRepository()
        watchWidget()
    }

    /** Draws the widget's two mockup frames, and the picker preview, from the sample tasks into [dir]. */
    fun renderWidgetMockups(dir: File) {
        check(BuildConfig.DEBUG)
        val open = FakeData.tasks().filter { it.date == FakeData.today && !it.done }.sortedWith(TaskOrder.widget)
        val note = WidgetNote(FakeData.today, open, HandFont.Handwritten)
        val painter = WidgetPainter(this)
        dir.mkdirs()
        val drawings = mapOf(
            "15-widget-2x2" to painter.paintMockup(note, WidgetKind.Square),
            "16-widget-4x2" to painter.paintMockup(note, WidgetKind.Wide),
            "widget_preview" to painter.paintPreview(note),
        )
        for ((name, bitmap) in drawings) {
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
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
