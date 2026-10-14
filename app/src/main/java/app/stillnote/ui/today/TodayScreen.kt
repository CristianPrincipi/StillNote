package app.stillnote.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.Task
import app.stillnote.ui.after
import app.stillnote.ui.components.DoneMark
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.PullToSync
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StayAtTop
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.StruckHandText
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.dashedBottomBorder
import app.stillnote.ui.components.inkBorder
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.theme.AddPadShape
import app.stillnote.ui.theme.PostItShape
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.paper
import app.stillnote.ui.theme.postItTilt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onAddTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val couldNotSave by viewModel.couldNotSave.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        PaperPage {
            TodayHeader(state, onOpenSettings)
            PullToSync(syncing, state.canSync, viewModel::syncNow, Modifier.weight(1f)) {
                TaskList(state, viewModel, Modifier.fillMaxSize())
            }
        }
        val strip = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 20.dp, end = 100.dp, bottom = 29.dp)
        val task = deleted
        when {
            task != null -> PaperStrip("Deleted “${task.title}”", "Undo", viewModel::undoDelete, strip)
            couldNotSave -> PaperStrip("Couldn't save.", "Retry", viewModel::retrySaving, strip)
        }
        AddPad(
            onClick = onAddTask,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 26.dp, bottom = 23.dp),
        )
    }
}

@Composable
private fun TodayHeader(state: TodayUiState, onOpenSettings: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 4.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Overline(state.date.overline(), size = 12f, weight = FontWeight.Normal)
            ScreenTitle("Today", highlighted = true)
            MonoText(state.sync.after("${state.open.size} to do · ${state.done.size} done"), 12f)
        }
        TapTarget(
            onClick = onOpenSettings,
            description = "Settings",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-6).dp)
                .size(TapSize),
        ) {
            InkIcon(StillIcons.Settings, 22.dp)
        }
    }
}

@Composable
private fun TaskList(state: TodayUiState, viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val allClear = state.loaded && state.open.isEmpty()
    val list = rememberLazyListState()
    list.StayAtTop(firstKey = (state.open + state.done).firstOrNull()?.id)
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = list,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 132.dp),
    ) {
        if (allClear) {
            item(key = "all-clear") { AllClear(Modifier.animateItem()) }
        }
        itemsIndexed(state.open, key = { _, task -> task.id }) { index, task ->
            TaskPostIt(
                task = task,
                tilt = postItTilt(index),
                onDone = { viewModel.setDone(task, true) },
                onDelete = { viewModel.delete(task) },
                modifier = Modifier
                    .animateItem()
                    .padding(top = if (index == 0) 0.dp else 14.dp),
            )
        }
        if (state.done.isNotEmpty()) {
            item(key = "done-heading") {
                Overline(
                    "Done · ${state.done.size}",
                    Modifier
                        .animateItem()
                        .padding(start = 10.dp, top = if (allClear) 60.dp else 30.dp, bottom = 4.dp),
                )
            }
            items(state.done, key = { it.id }) { task ->
                DoneRow(
                    task = task,
                    onNotDone = { viewModel.setDone(task, false) },
                    onDelete = { viewModel.delete(task) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun AllClear(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 72.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HandText("All clear for today.", 30f)
        MonoText("Nothing left on the desk.", 12f)
    }
}

@Composable
private fun TaskPostIt(
    task: Task,
    tilt: Float,
    onDone: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rotate(tilt)
            .paperShadow(PostItShape, Shadows.postIt)
            .background(task.priority.paper, PostItShape)
            .heightIn(min = 70.dp)
            .padding(start = 4.dp, top = 10.dp, end = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTarget(onDone, "Mark ${task.title} as done", Modifier.size(TapSize)) {
            HandCircle(22.dp)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            HandText(task.title, 21f)
            val meta = task.meta()
            if (meta.isNotEmpty()) MonoText(meta, 11.5f, letterSpacing = 0.03.em)
        }
        TapTarget(onDelete, "Delete ${task.title}", Modifier.size(TapSize)) {
            InkIcon(StillIcons.Trash, 19.dp, tint = StillColors.ink2)
        }
    }
}

@Composable
private fun DoneRow(task: Task, onNotDone: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 53.dp)
            .dashedBottomBorder()
            .padding(start = 4.dp, end = 2.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTarget(onNotDone, "Mark ${task.title} as not done", Modifier.size(TapSize)) {
            DoneMark(22.dp, tickSize = 13.dp, tickStroke = 3f)
        }
        StruckHandText(
            task.title,
            19f,
            Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
        )
        TapTarget(onDelete, "Delete ${task.title}", Modifier.size(TapSize)) {
            InkIcon(StillIcons.Trash, 18.dp, tint = StillColors.ink2)
        }
    }
}

/** The "+" drawn as the top sheet of a pad of post-its. */
@Composable
private fun AddPad(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TapTarget(
        onClick = onClick,
        description = "Add a task",
        modifier = modifier
            .size(60.dp)
            .rotate(-4f)
            .paperShadow(AddPadShape, Shadows.addPad)
            .background(StillColors.canary, AddPadShape)
            .inkBorder(1.6.dp, StillColors.ink, AddPadShape),
    ) {
        InkIcon(StillIcons.Add, 26.dp, strokeWidth = 2f)
    }
}

/**
 * A strip of paper beside the "+" with one action on canary tape: "Undo" after a delete, or
 * "Retry" when a change couldn't be saved. Not in the mockups.
 */
@Composable
private fun PaperStrip(message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .rotate(-0.6f)
            .paperShadow(shape, Shadows.postIt)
            .background(StillColors.card, shape)
            .heightIn(min = TapSize)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoText(message, 12f, Modifier.weight(1f), maxLines = 1)
        TapTarget(onAction, description = null, Modifier.heightIn(min = TapSize).padding(horizontal = 12.dp)) {
            HandText(
                action,
                19f,
                Modifier
                    .rotate(-2f)
                    .background(StillColors.canary)
                    .padding(horizontal = 6.dp),
                weight = FontWeight.Bold,
            )
        }
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** "10:30 · Work · !!!", skipping the parts a task doesn't have. */
private fun Task.meta(): String =
    listOfNotNull(time?.format(TimeFormat), project, priority.marks.ifEmpty { null }).joinToString(" · ")

/** "Monday · 12 October" */
private fun LocalDate.overline(): String {
    val weekday = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val month = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "$weekday · $dayOfMonth $month"
}
