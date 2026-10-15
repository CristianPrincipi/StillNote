package app.stillnote

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.stillnote.ui.StillNoteRoot
import app.stillnote.ui.theme.Page
import app.stillnote.ui.theme.page
import app.stillnote.widget.TodayWidgetReceiver
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** What a tap on the widget or a notification asked for, until the screens have shown it. */
    private val launchRequest = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as StillNoteApp
        if (BuildConfig.DEBUG) {
            // adb shell am start -n app.stillnote.debug/app.stillnote.MainActivity --ez demo true --es notion_url http://127.0.0.1:8787
            intent.getStringExtra("notion_url")?.let(app::setDebugNotionUrl)
            if (intent.getBooleanExtra("demo", false)) app.enterDemo()
            if (intent.getBooleanExtra("render_widgets", false)) app.renderWidgetMockups(File(filesDir, "widgets"))
            if (intent.getBooleanExtra("pin_widget", false)) pinWidget()
        }
        // After a rotation the intent is the same old one, already handled.
        if (savedInstanceState == null) receive(intent)
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent { StillNoteRoot(launchRequest) }

        // Light or dark bars, and a window to match the page behind them.
        lifecycleScope.launch { app.settings.palette.collect { showPage(it.page) } }

        // While the app is on screen, keep "today" right and pick up changes made in Notion.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { app.syncWhenBackOnline() }
                app.clock.refresh()
                app.syncNow()
                while (true) {
                    delay(REFRESH_MILLIS)
                    app.clock.refresh()
                    app.refreshThisMonth()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    /** Takes in a request. A reminder whose task is opened to edit has done its job, so it goes. */
    private fun receive(intent: Intent) {
        val request = LaunchRequest.from(intent) ?: return
        if (request is LaunchRequest.EditTask) (application as StillNoteApp).reminders.notifications.cancel(request.taskId)
        launchRequest.value = request
    }

    private fun showPage(page: Page) {
        val bars = if (page.dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        window.setBackgroundDrawable(page.ground.toArgb().toDrawable())
    }

    /** Debug builds only: asks the launcher to place the widget, for tools/screens.py. */
    private fun pinWidget() {
        getSystemService(AppWidgetManager::class.java).requestPinAppWidget(ComponentName(this, TodayWidgetReceiver::class.java), null, null)
    }

    private companion object {
        const val REFRESH_MILLIS = 120_000L
    }
}
