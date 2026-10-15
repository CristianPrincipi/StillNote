package app.stillnote.ui.projects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.Priority
import app.stillnote.data.Project
import app.stillnote.data.Task
import app.stillnote.ui.after
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.IconPaths
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.PullToSync
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.cardHeader
import app.stillnote.ui.components.inkBorder
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.ruledPaper
import app.stillnote.ui.components.tapToEdit
import app.stillnote.ui.theme.AddPadShape
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.MenuSlipShape
import app.stillnote.ui.theme.MiniPostItShape
import app.stillnote.ui.theme.OnPaper
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.miniPostItTilt
import app.stillnote.ui.theme.paper
import app.stillnote.ui.theme.projectCardTilt

/**
 * One index card per project, each listing its open tasks. The "+" pad opens a two-item menu:
 * [onNewTask] gets the project whose card is at the top of the screen, and [onNewProject]
 * opens the new-project sheet.
 */
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    menuOpen: Boolean,
    onMenuToggle: () -> Unit,
    onNewTask: (project: String?) -> Unit,
    onNewProject: () -> Unit,
    onEditTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val cards = rememberLazyListState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier.fillMaxSize()) {
        PaperPage {
            Column(
                modifier = Modifier.padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Overline("Groups of notes", size = 12f, weight = FontWeight.Normal)
                ScreenTitle("Projects")
                MonoText(state.sync.after("${count(state.projects.size, "project")} · ${count(state.openNotes, "open note")}"), 12f)
            }
            PullToSync(syncing, state.features.notion, viewModel::syncNow, Modifier.weight(1f)) {
                when {
                    !state.features.projects -> Hint("This database has no project field.")
                    state.loaded && state.projects.isEmpty() -> Hint("No projects yet.")
                    else -> ProjectCards(state.projects, cards, viewModel::complete, onEditTask, Modifier.fillMaxSize())
                }
            }
        }
        if (!state.features.projects) return@Box

        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(StillColors.scrimLight)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onMenuToggle)
                    .semantics { contentDescription = "Close menu" },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 105.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MenuSlip("New project", StillIcons.Projects, tilt = 1.5f, ruled = true, onClick = onNewProject)
                MenuSlip("New task", StillIcons.Tasks, tilt = -2f, ruled = false) {
                    onNewTask(state.projects.getOrNull(cards.firstVisibleItemIndex)?.name)
                }
            }
        }
        AddMenuPad(
            open = menuOpen,
            onClick = onMenuToggle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 23.dp),
        )
    }
}

@Composable
private fun ProjectCards(
    projects: List<Project>,
    cards: LazyListState,
    onComplete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        state = cards,
        contentPadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        // No keys on purpose: with them the list holds on to the card it was showing, and when
        // Notion's projects arrive above a phone-made one it ends up scrolled to the bottom.
        itemsIndexed(projects) { index, project ->
            ProjectCard(project, projectCardTilt(index), onComplete, onEdit)
        }
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

@Composable
private fun Hint(text: String) {
    // Scrolls only so that it can be pulled down to sync.
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HandText(text, 18f, Modifier.padding(horizontal = 24.dp, vertical = 30.dp), color = LocalInk.current.ink3)
    }
}

@Composable
private fun ProjectCard(project: Project, tilt: Float, onComplete: (Task) -> Unit, onEdit: (Task) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    OnPaper {
        Column(
            modifier
                .fillMaxWidth()
                .rotate(tilt)
                .paperShadow(shape, Shadows.indexCard)
                .clip(shape)
                .ruledPaper(every = 24.dp)
                .semantics { contentDescription = project.name },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .cardHeader()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HandText(project.name, 25f, Modifier.weight(1f).alignByBaseline(), weight = FontWeight.Bold, lineHeight = 1.3f, maxLines = 1)
                MonoText("${project.open.size} open · ${project.done} done", 11.5f, Modifier.alignByBaseline(), letterSpacing = 0.04.em)
            }
            if (project.open.isEmpty()) {
                HandText("Nothing left here.", 18f, Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp), color = StillColors.ink3, lineHeight = 1.3f)
            } else {
                // About four notes show; the fifth peeks out to say the card scrolls.
                Column(
                    modifier = Modifier
                        .heightIn(max = 258.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    project.open.forEachIndexed { index, task ->
                        MiniPostIt(task, miniPostItTilt(index), onComplete = { onComplete(task) }, onEdit = { onEdit(task) })
                    }
                }
            }
        }
    }
}

/** A task on a project card: only its check and its title. */
@Composable
private fun MiniPostIt(task: Task, tilt: Float, onComplete: () -> Unit, onEdit: () -> Unit) {
    val outline = if (task.priority == Priority.None) Modifier.border(1.dp, StillColors.ink.copy(alpha = 0.14f), MiniPostItShape) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(tilt)
            .paperShadow(MiniPostItShape, Shadows.cardPostIt)
            .background(task.priority.paper, MiniPostItShape)
            .then(outline)
            .tapToEdit(onEdit)
            // The mockup's 1 px border sits outside its 44 px row and 2/12 px padding.
            .heightIn(min = 46.dp)
            .padding(start = 3.dp, end = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Drawn 40 by 44, as in the mockup; the touch target overflows it to 48.
        Box(Modifier.size(40.dp, 44.dp), contentAlignment = Alignment.Center) {
            TapTarget(onComplete, "Mark ${task.title} as done", Modifier.requiredSize(TapSize)) {
                HandCircle(18.dp, stroke = 1.5.dp)
            }
        }
        HandText(task.title, 17f, Modifier.weight(1f), maxLines = 1)
    }
}

@Composable
private fun MenuSlip(label: String, icon: IconPaths, tilt: Float, ruled: Boolean, onClick: () -> Unit) {
    val paper = if (ruled) Modifier.clip(MenuSlipShape).ruledPaper(every = 17.dp) else Modifier.background(StillColors.canary, MenuSlipShape)
    TapTarget(
        onClick = onClick,
        description = null,
        modifier = Modifier
            .rotate(tilt)
            .paperShadow(MenuSlipShape, Shadows.menuSlip)
            .then(paper)
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 22.dp),
    ) {
        OnPaper {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                InkIcon(icon, 22.dp, strokeWidth = 1.7f)
                HandText(label, 20f)
            }
        }
    }
}

/** The "+" pad; its plus turns into a "×" while the menu is open. */
@Composable
private fun AddMenuPad(open: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(if (open) 45f else 0f, tween(200), label = "plus")
    TapTarget(
        onClick = onClick,
        description = if (open) "Close menu" else "Add a task or project",
        modifier = modifier
            .size(60.dp)
            .rotate(-4f)
            .paperShadow(AddPadShape, Shadows.addPad)
            .background(StillColors.canary, AddPadShape)
            .inkBorder(1.6.dp, StillColors.ink, AddPadShape),
    ) {
        OnPaper { InkIcon(StillIcons.Add, 26.dp, Modifier.rotate(turn), strokeWidth = 2f) }
    }
}
