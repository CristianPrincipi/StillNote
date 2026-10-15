package app.stillnote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stillnote.StillNoteApp
import app.stillnote.ui.calendar.CalendarScreen
import app.stillnote.ui.components.BottomNav
import app.stillnote.ui.components.Tab
import app.stillnote.ui.newtask.NewTaskViewModel
import app.stillnote.ui.newtask.NoteSheetOverlay
import app.stillnote.ui.projects.NewProjectSheet
import app.stillnote.ui.projects.ProjectsScreen
import app.stillnote.ui.projects.ProjectsViewModel
import app.stillnote.ui.settings.SettingsScreen
import app.stillnote.ui.settings.SettingsViewModel
import app.stillnote.ui.setup.SetupFlow
import app.stillnote.ui.setup.SetupViewModel
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.today.TodayScreen
import app.stillnote.widget.WidgetRequest
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The whole app: the first-run flow until it has been finished once, then three tabs with
 * Tasks as home, Settings as a sub-page, and the new-task note and new-project card on top.
 */
@Composable
fun StillNoteRoot(widgetRequest: MutableStateFlow<WidgetRequest?>) {
    val app = LocalContext.current.applicationContext as StillNoteApp
    val settings: SettingsViewModel = viewModel(factory = StillViewModels)
    val setup: SetupViewModel = viewModel(factory = StillViewModels)
    val handFont by settings.handFont.collectAsStateWithLifecycle()
    val setupDone by app.settings.setupDone.collectAsStateWithLifecycle(initialValue = null)

    Box(Modifier.fillMaxSize().background(StillColors.paper)) {
        // Wait for the saved choices, so the app never flashes the wrong face or the wrong screen.
        val font = handFont ?: return@Box
        val firstRun = !(setupDone ?: return@Box) && !app.demo
        CompositionLocalProvider(LocalHandFont provides font.family) {
            if (firstRun) SetupFlow(setup, onDone = {}) else MainScreens(settings, setup, widgetRequest)
        }
    }
}

@Composable
private fun MainScreens(settings: SettingsViewModel, setup: SetupViewModel, widgetRequest: MutableStateFlow<WidgetRequest?>) {
    val newTask: NewTaskViewModel = viewModel(factory = StillViewModels)
    val projects: ProjectsViewModel = viewModel(factory = StillViewModels)
    val draft by newTask.draft.collectAsStateWithLifecycle()
    val database by settings.database.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.Tasks) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    var addMenuOpen by rememberSaveable { mutableStateOf(false) }
    var newProjectOpen by rememberSaveable { mutableStateOf(false) }
    val sheetOpen = draft != null || newProjectOpen

    // The widget's header and "+N more" open Today. Its "+" opens the note, and a row opens its task
    // in the note to change it, unless a note is already being written.
    val request by widgetRequest.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        val asked = request ?: return@LaunchedEffect
        tab = Tab.Tasks
        settingsOpen = false
        addMenuOpen = false
        newProjectOpen = false
        when (asked) {
            WidgetRequest.Today -> Unit
            WidgetRequest.NewTask -> if (newTask.draft.value == null) newTask.open()
            is WidgetRequest.EditTask -> newTask.openToEdit(asked.taskId)
        }
        widgetRequest.value = null
    }

    BackHandler(enabled = sheetOpen || addMenuOpen || settingsOpen || tab != Tab.Tasks) {
        when {
            draft != null -> newTask.dismiss()
            newProjectOpen -> newProjectOpen = false
            addMenuOpen -> addMenuOpen = false
            settingsOpen -> settingsOpen = false
            else -> tab = Tab.Tasks
        }
    }

    val behindSheet = if (sheetOpen) Modifier.blur(1.5.dp).clearAndSetSemantics {} else Modifier
    Box(Modifier.fillMaxSize().then(behindSheet)) {
        when {
            // Settings reuses setup's two Notion steps to connect or to change database.
            connecting -> SetupFlow(setup, onDone = {
                connecting = false
                settings.reload()
            })
            settingsOpen -> SettingsScreen(
                viewModel = settings,
                onBack = { settingsOpen = false },
                onConnectNotion = {
                    setup.startFromSettings()
                    connecting = true
                },
            )
            else -> Column {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Calendar -> CalendarScreen(viewModel(factory = StillViewModels), onEditTask = { newTask.openToEdit(it) })
                        Tab.Tasks -> TodayScreen(
                            viewModel = viewModel(factory = StillViewModels),
                            onOpenSettings = { settingsOpen = true },
                            onAddTask = newTask::open,
                            onEditTask = { newTask.openToEdit(it) },
                        )
                        Tab.Projects -> ProjectsScreen(
                            viewModel = projects,
                            menuOpen = addMenuOpen,
                            onMenuToggle = { addMenuOpen = !addMenuOpen },
                            onNewTask = { project ->
                                addMenuOpen = false
                                newTask.openForProject(project)
                            },
                            onNewProject = {
                                addMenuOpen = false
                                newProjectOpen = true
                            },
                            onEditTask = { newTask.openToEdit(it) },
                        )
                    }
                }
                BottomNav(
                    current = tab,
                    onSelect = { tab = it },
                    onDismissDim = if (addMenuOpen && tab == Tab.Projects) ({ addMenuOpen = false }) else null,
                )
            }
        }
    }
    draft?.let { NoteSheetOverlay(it, newTask) }
    if (newProjectOpen) {
        NewProjectSheet(
            synced = database != null,
            onCreate = { name ->
                projects.create(name)
                newProjectOpen = false
            },
            onDismiss = { newProjectOpen = false },
        )
    }
}
