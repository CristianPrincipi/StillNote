package app.stillnote.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.Priority
import app.stillnote.data.Task
import app.stillnote.ui.after
import app.stillnote.ui.components.DoneMark
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.dashedBottomBorder
import app.stillnote.ui.components.inkBorder
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.theme.DayPostItShape
import app.stillnote.ui.theme.HandDrawnCircle
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.paper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PaperPage(modifier) {
        CalendarHeader(state, viewModel::previousMonth, viewModel::nextMonth)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MonthGrid(state, viewModel::select)
            SelectedDay(state)
        }
    }
}

@Composable
private fun CalendarHeader(state: CalendarUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        MonoText(state.month.year.toString(), 12f, letterSpacing = 0.16.em)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScreenTitle(state.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            Row(
                modifier = Modifier.offset(x = 12.dp),
                horizontalArrangement = Arrangement.spacedBy((-4).dp),
            ) {
                TapTarget(onPrevious, "Previous month", Modifier.size(TapSize)) {
                    InkIcon(StillIcons.ChevronLeft, 22.dp, strokeWidth = 1.8f)
                }
                TapTarget(
                    onClick = onNext,
                    description = "Next month",
                    modifier = Modifier
                        .size(TapSize)
                        .alpha(if (state.isCurrentMonth) 0.3f else 1f),
                    enabled = !state.isCurrentMonth,
                ) {
                    InkIcon(StillIcons.ChevronRight, 22.dp, strokeWidth = 1.8f)
                }
            }
        }
        MonoText(state.sync.after(state.summary()), 12f)
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.clearAndSetSemantics {}) {
            "MTWTFSS".forEach { weekday ->
                MonoText(weekday.toString(), 11f, Modifier.weight(1f), letterSpacing = 0.12.em, textAlign = TextAlign.Center)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { cell -> DayCellView(cell, { onSelect(cell.date) }, Modifier.weight(1f)) }
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Legend(done = true, "done")
            Legend(done = false, "not done")
        }
    }
}

@Composable
private fun DayCellView(cell: DayCell, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val selectedPaper = if (!cell.selected) Modifier else Modifier
        .rotate(-2f)
        .paperShadow(DayPostItShape, Shadows.miniPostIt)
        .background(StillColors.canary, DayPostItShape)
    Column(
        modifier = modifier
            .height(54.dp)
            .then(selectedPaper)
            .selectable(
                selected = cell.selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = cell.inMonth,
                onClick = onSelect,
            )
            .clearAndSetSemantics { contentDescription = cell.description() }
            .padding(top = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .then(if (cell.isToday) Modifier.inkBorder(1.6.dp, StillColors.ink, HandDrawnCircle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            MonoText(
                cell.date.dayOfMonth.toString(),
                14f,
                weight = if (cell.isToday) FontWeight.Medium else FontWeight.Normal,
                color = if (cell.inMonth) StillColors.ink else StillColors.inkDisabled,
            )
        }
        Row(Modifier.height(7.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            cell.squares.forEach { done -> TaskSquare(done, 7.dp) }
        }
    }
}

/** One task on a day: solid when done, an outline when not. */
@Composable
private fun TaskSquare(done: Boolean, size: Dp) {
    Box(
        Modifier
            .size(size)
            .then(if (done) Modifier.background(StillColors.ink) else Modifier.border(1.2.dp, StillColors.ink)),
    )
}

@Composable
private fun Legend(done: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        TaskSquare(done, 8.dp)
        MonoText(label, 11f)
    }
}

@Composable
private fun SelectedDay(state: CalendarUiState) {
    val day = state.selected
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val y = size.height - stroke / 2
                    drawLine(StillColors.ink, Offset(0f, y), Offset(size.width, y), stroke)
                }
                .padding(start = 6.dp, end = 4.dp, bottom = 5.5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HandText(
                text = day?.let { "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${it.dayOfMonth}" } ?: "Pick a day",
                size = 26f,
                modifier = Modifier.alignByBaseline(),
                weight = FontWeight.Bold,
            )
            if (day != null) {
                MonoText(taskCount(state.selectedTasks.size).uppercase(), 11f, Modifier.alignByBaseline(), letterSpacing = 0.1.em)
            }
        }
        if (day != null && state.selectedTasks.isEmpty()) {
            HandText("Nothing written down.", 19f, Modifier.padding(horizontal = 6.dp, vertical = 14.dp), color = StillColors.ink3)
        }
        Column {
            state.selectedTasks.forEach { task -> DayTaskRow(task, missed = !task.done && day != null && day < state.today) }
        }
    }
}

@Composable
private fun DayTaskRow(task: Task, missed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 47.dp)
            .dashedBottomBorder()
            .semantics(mergeDescendants = true) {}
            .padding(start = 6.dp, end = 6.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (task.done) {
            DoneMark(18.dp, tickSize = 11.dp, tickStroke = 3.2f, Modifier.semantics { contentDescription = "Done" })
        } else {
            HandCircle(18.dp, Modifier.semantics { contentDescription = "Not done" }, stroke = 1.5.dp)
        }
        HandText(
            text = task.title,
            size = 18f,
            modifier = Modifier.weight(1f),
            color = if (task.done) StillColors.ink3 else StillColors.ink,
            decoration = if (task.done) TextDecoration.LineThrough else null,
        )
        MonoText(task.dayMeta(missed), 11f, letterSpacing = 0.04.em)
        Box(
            Modifier
                .size(12.dp)
                .rotate(-4f)
                .background(task.priority.paper)
                .border(1.dp, StillColors.ink)
                .semantics { contentDescription = task.priority.description() },
        )
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** The time, or "missed" for a past task left undone, or "any time". */
private fun Task.dayMeta(missed: Boolean): String = when {
    done -> time?.format(TimeFormat).orEmpty()
    missed -> "missed"
    else -> time?.format(TimeFormat) ?: "any time"
}

private fun Priority.description(): String = if (this == Priority.None) "No priority" else "$label priority"

private fun taskCount(count: Int): String = if (count == 1) "1 task" else "$count tasks"

private fun DayCell.description(): String =
    if (inMonth) "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}, ${taskCount(taskCount)}"
    else date.dayOfMonth.toString()

private fun CalendarUiState.summary(): String =
    if (isCurrentMonth) "$done done · $missed missed · $toCome to come" else "$done done · $missed missed"
