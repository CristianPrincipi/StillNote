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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.stillnote.ui.StillNoteRoot
import app.stillnote.widget.TodayWidgetReceiver
import app.stillnote.widget.WidgetRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    /** What a tap on the widget asked for, until the screens have shown it. */
    private val widgetRequest = MutableStateFlow<WidgetRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as StillNoteApp
        if (BuildConfig.DEBUG) {
            // adb shell am start -n app.stillnote/.MainActivity --ez demo true --es notion_url http://127.0.0.1:8787
            intent.getStringExtra("notion_url")?.let(app::setDebugNotionUrl)
            if (intent.getBooleanExtra("demo", false)) app.enterDemo()
            if (intent.getBooleanExtra("render_widgets", false)) app.renderWidgetMockups(File(filesDir, "widgets"))
            if (intent.getBooleanExtra("pin_widget", false)) pinWidget()
        }
        // After a rotation the intent is the same old one, already handled.
        if (savedInstanceState == null) widgetRequest.value = WidgetRequest.from(intent)
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent { StillNoteRoot(widgetRequest) }

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
        WidgetRequest.from(intent)?.let { widgetRequest.value = it }
    }

    /** Debug builds only: asks the launcher to place the widget, for tools/screens.py. */
    private fun pinWidget() {
        getSystemService(AppWidgetManager::class.java).requestPinAppWidget(ComponentName(this, TodayWidgetReceiver::class.java), null, null)
    }

    private companion object {
        const val REFRESH_MILLIS = 120_000L
    }
}
