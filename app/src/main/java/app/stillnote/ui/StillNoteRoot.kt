package app.stillnote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stillnote.ui.calendar.CalendarScreen
import app.stillnote.ui.components.BottomNav
import app.stillnote.ui.components.Tab
import app.stillnote.ui.newtask.NewTaskViewModel
import app.stillnote.ui.newtask.NoteSheetOverlay
import app.stillnote.ui.projects.ProjectsScreen
import app.stillnote.ui.settings.SettingsScreen
import app.stillnote.ui.settings.SettingsViewModel
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.today.TodayScreen

/** The whole app: three tabs with Tasks as home, Settings as a sub-page, and the new-task note on top. */
@Composable
fun StillNoteRoot() {
    val settings: SettingsViewModel = viewModel(factory = StillViewModels)
    val newTask: NewTaskViewModel = viewModel(factory = StillViewModels)
    val handFont by settings.handFont.collectAsStateWithLifecycle()
    val draft by newTask.draft.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Tasks) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = draft != null || settingsOpen || tab != Tab.Tasks) {
        when {
            draft != null -> newTask.dismiss()
            settingsOpen -> settingsOpen = false
            else -> tab = Tab.Tasks
        }
    }

    Box(Modifier.fillMaxSize().background(StillColors.paper)) {
        // Wait for the saved typography choice, so the app never flashes the wrong face.
        val font = handFont ?: return@Box
        CompositionLocalProvider(LocalHandFont provides font.family) {
            val behindNote = if (draft == null) Modifier else Modifier.blur(1.5.dp).clearAndSetSemantics {}
            Box(Modifier.fillMaxSize().then(behindNote)) {
                if (settingsOpen) {
                    SettingsScreen(settings, onBack = { settingsOpen = false })
                } else {
                    Column {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Calendar -> CalendarScreen(viewModel(factory = StillViewModels))
                                Tab.Tasks -> TodayScreen(
                                    viewModel = viewModel(factory = StillViewModels),
                                    onOpenSettings = { settingsOpen = true },
                                    onAddTask = newTask::open,
                                )
                                Tab.Projects -> ProjectsScreen()
                            }
                        }
                        BottomNav(tab, onSelect = { tab = it })
                    }
                }
            }
            draft?.let { NoteSheetOverlay(it, newTask) }
        }
    }
}
