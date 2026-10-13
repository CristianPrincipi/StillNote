package app.stillnote

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as StillNoteApp
        if (BuildConfig.DEBUG) {
            // adb shell am start -n app.stillnote/.MainActivity --ez demo true --es notion_url http://127.0.0.1:8787
            intent.getStringExtra("notion_url")?.let(app::setDebugNotionUrl)
            if (intent.getBooleanExtra("demo", false)) app.enterDemo()
        }
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        setContent { StillNoteRoot() }

        // While the app is on screen, keep "today" right and pick up changes made in Notion.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
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

    private companion object {
        const val REFRESH_MILLIS = 120_000L
    }
}
