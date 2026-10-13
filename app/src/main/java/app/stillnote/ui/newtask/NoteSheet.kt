package app.stillnote.ui.newtask

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.data.Priority
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.IconPaths
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.inkBorder
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.MiniPostItShape
import app.stillnote.ui.theme.NoteShape
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.paper
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

/** The new-task note floating over the scrim. Tapping outside closes it without saving. */
@Composable
fun NoteSheetOverlay(draft: NewTaskDraft, viewModel: NewTaskViewModel, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    // The steppers and the keyboard don't fit together, so a picker takes the keyboard's place.
    LaunchedEffect(draft.picker) { if (draft.picker != null) keyboard?.hide() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(StillColors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = viewModel::dismiss)
                .semantics { contentDescription = "Close without saving" },
        )
        NotePlacement(top = draft.picker.noteTop()) {
            NoteSheet(draft, viewModel, focus)
        }
    }
}

// Where the mockups put the note for each state, measured from the top of the page.
private fun Picker?.noteTop(): Dp = when (this) {
    null -> 176.dp
    Picker.When -> 138.dp
    Picker.Priority -> 164.dp
}

private val NoteMargin = 24.dp
private val MaxNoteWidth = 420.dp

/** Puts the note at [top], moving it up when the keyboard would otherwise cover it. */
@Composable
private fun NotePlacement(top: Dp, content: @Composable () -> Unit) {
    val animatedTop by animateDpAsState(top, tween(200), label = "note top")
    Layout(
        content = content,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) { measurables, constraints ->
        val width = min(constraints.maxWidth - 2 * NoteMargin.roundToPx(), MaxNoteWidth.roundToPx()).coerceAtLeast(0)
        val note = measurables.first().measure(Constraints.fixedWidth(width))
        val lowest = constraints.maxHeight - note.height - 20.dp.roundToPx()
        val y = min(animatedTop.roundToPx(), lowest).coerceAtLeast(22.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            note.place((constraints.maxWidth - width) / 2, y)
        }
    }
}

@Composable
private fun NoteSheet(draft: NewTaskDraft, viewModel: NewTaskViewModel, focus: FocusRequester) {
    val color by animateColorAsState(draft.priority?.paper ?: StillColors.canary, tween(260), label = "note colour")
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        Modifier
            .rotate(-1.2f)
            .paperShadow(NoteShape, Shadows.note)
            .background(color, NoteShape)
            .semantics { paneTitle = "New task" }
            // A tap on the paper goes to the text field instead of falling through to the scrim.
            .pointerInput(Unit) {
                detectTapGestures {
                    focus.requestFocus()
                    keyboard?.show()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 300.dp)
                .padding(top = 28.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                MonoText("NEW TASK", 11f, Modifier.padding(start = 22.dp, end = 18.dp), letterSpacing = 0.16.em)
                TitleField(draft, viewModel, focus, Modifier.padding(start = 22.dp, top = 8.dp, end = 18.dp))
                Chips(draft, Modifier.padding(start = 22.dp, top = 8.dp, end = 18.dp))
                when (draft.picker) {
                    Picker.When -> {
                        TearLine(Modifier.padding(top = 14.dp))
                        WhenPicker(draft, viewModel, Modifier.padding(top = 12.dp))
                    }
                    Picker.Priority -> {
                        TearLine(Modifier.padding(top = 14.dp))
                        PriorityPicker(draft.priority, viewModel::setPriority, Modifier.padding(top = 12.dp))
                    }
                    null -> Unit
                }
            }
            NoteActions(
                draft = draft,
                noteColor = color,
                viewModel = viewModel,
                modifier = Modifier.padding(top = if (draft.picker == null) 8.dp else 12.dp),
            )
        }
        Tape(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp),
        )
    }
}

@Composable
private fun Tape(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(114.dp, 28.dp)
            .rotate(-3f)
            .background(StillColors.tape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                val edge = StillColors.ink.copy(alpha = 0.14f)
                val dashes = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2))
                for (x in listOf(stroke / 2, size.width - stroke / 2)) {
                    drawLine(edge, Offset(x, 0f), Offset(x, size.height), stroke, pathEffect = dashes)
                }
            },
    )
}

@Composable
private fun TitleField(draft: NewTaskDraft, viewModel: NewTaskViewModel, focus: FocusRequester, modifier: Modifier = Modifier) {
    val style = handStyle(LocalHandFont.current, 27f, lineHeight = 1.3f)
    BasicTextField(
        value = draft.text,
        onValueChange = viewModel::setText,
        modifier = modifier
            .fillMaxWidth()
            .trimLineBox(rememberLineBoxTrim(style))
            .focusRequester(focus),
        textStyle = style,
        cursorBrush = SolidColor(StillColors.ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
        minLines = if (draft.picker == null) 1 else 2,
        decorationBox = { field ->
            Box {
                if (draft.text.isEmpty()) BasicText("What needs doing?", style = style.copy(color = StillColors.ink3))
                field()
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(draft: NewTaskDraft, modifier: Modifier = Modifier) {
    val time = draft.time
    val priority = draft.priority
    if (time == null && priority == null) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (time != null) Chip(StillIcons.Clock, "${draft.shortDay()}, ${time.format(TimeFormat)}")
        if (priority != null) {
            Chip(StillIcons.Flag, if (priority == Priority.None) "No priority" else "${priority.marks} ${priority.label}")
        }
    }
}

@Composable
private fun Chip(icon: IconPaths, text: String) {
    Row(
        modifier = Modifier
            .background(StillColors.card.copy(alpha = 0.45f), CircleShape)
            .border(1.2.dp, StillColors.ink, CircleShape)
            // The mockup's padding sits inside the border, so the border width is added to it.
            .padding(start = 9.2.dp, top = 6.2.dp, end = 11.2.dp, bottom = 6.2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIcon(icon, 14.dp, strokeWidth = 2f)
        MonoText(text, 12f, color = StillColors.ink)
    }
}

/** The dashed line the picker area hangs below, running the full width of the note. */
@Composable
private fun TearLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .drawBehind {
                val stroke = size.height
                drawLine(
                    color = StillColors.ink.copy(alpha = 0.38f),
                    start = Offset(0f, stroke / 2),
                    end = Offset(size.width, stroke / 2),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2)),
                )
            },
    )
}

@Composable
private fun WhenPicker(draft: NewTaskDraft, viewModel: NewTaskViewModel, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline("When", Modifier.padding(start = 22.dp))
        Stepper(
            onPrevious = { viewModel.shiftDay(-1) },
            previousLabel = "Previous day",
            previousEnabled = draft.dayOffset > 0,
            onNext = { viewModel.shiftDay(1) },
            nextLabel = "Next day",
        ) {
            HandText(draft.dayName(), 28f, weight = FontWeight.Bold, lineHeight = 1.15f)
            MonoText(draft.date.format(DayDateFormat).uppercase(), 11f, letterSpacing = 0.16.em)
        }
        Box(
            Modifier
                .padding(start = 56.dp, end = 52.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(StillColors.ink.copy(alpha = 0.16f)),
        )
        Stepper(
            onPrevious = { viewModel.shiftTime(-1) },
            previousLabel = "30 minutes earlier",
            onNext = { viewModel.shiftTime(1) },
            nextLabel = "30 minutes later",
        ) {
            val time = draft.time ?: return@Stepper
            MonoText(
                time.format(TimeFormat),
                30f,
                weight = FontWeight.Medium,
                color = StillColors.ink,
                letterSpacing = 0.04.em,
                lineHeight = 1.1f,
            )
            MonoText(partOfDay(time.hour).uppercase(), 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.16.em)
        }
    }
}

/** `[‹] value [›]` */
@Composable
private fun Stepper(
    onPrevious: () -> Unit,
    previousLabel: String,
    onNext: () -> Unit,
    nextLabel: String,
    previousEnabled: Boolean = true,
    value: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapTarget(
            onClick = onPrevious,
            description = previousLabel,
            modifier = Modifier
                .size(TapSize)
                .alpha(if (previousEnabled) 1f else 0.3f),
            enabled = previousEnabled,
        ) {
            InkIcon(StillIcons.ChevronLeft, 22.dp, strokeWidth = 1.8f)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            value()
        }
        TapTarget(onNext, nextLabel, Modifier.size(TapSize)) {
            InkIcon(StillIcons.ChevronRight, 22.dp, strokeWidth = 1.8f)
        }
    }
}

@Composable
private fun PriorityPicker(selected: Priority?, onPick: (Priority) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = 22.dp, end = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline("Priority")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Priority.entries.forEach { priority ->
                PriorityOption(priority, priority == selected, { onPick(priority) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PriorityOption(priority: Priority, selected: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .rotate(if (selected) priority.pickedTilt else 0f)
                .paperShadow(MiniPostItShape, Shadows.miniPostIt)
                .background(priority.paper, MiniPostItShape)
                .then(
                    if (selected) Modifier.inkBorder(2.dp, StillColors.ink, MiniPostItShape)
                    else Modifier.inkBorder(1.2.dp, StillColors.ink.copy(alpha = 0.4f), MiniPostItShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) InkIcon(StillIcons.Check, 22.dp, strokeWidth = 2.4f)
        }
        HandText(priority.label, 17f)
        MonoText(priority.marks, 10.5f, Modifier.heightIn(min = 14.dp), letterSpacing = 0.08.em)
    }
}

private val Priority.pickedTilt: Float
    get() = when (this) {
        Priority.High -> -3f
        Priority.Medium -> 2f
        Priority.Low -> -1.5f
        Priority.None -> 2.5f
    }

/** The clock and flag toggles on the left, the send button on the right. */
@Composable
private fun NoteActions(draft: NewTaskDraft, noteColor: Color, viewModel: NewTaskViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            PickerToggle(
                icon = StillIcons.Clock,
                active = draft.picker == Picker.When,
                label = if (draft.picker == Picker.When) "Hide date and time" else "Set date and time",
                onClick = { viewModel.toggle(Picker.When) },
            )
            PickerToggle(
                icon = StillIcons.Flag,
                active = draft.picker == Picker.Priority,
                label = if (draft.picker == Picker.Priority) "Hide priority" else "Set priority",
                onClick = { viewModel.toggle(Picker.Priority) },
            )
        }
        TapTarget(
            onClick = viewModel::save,
            description = "Save task",
            modifier = Modifier
                .size(52.dp)
                .background(StillColors.ink, CircleShape),
            enabled = draft.text.isNotBlank(),
        ) {
            InkIcon(StillIcons.Send, 22.dp, tint = noteColor, strokeWidth = 2f)
        }
    }
}

@Composable
private fun PickerToggle(icon: IconPaths, active: Boolean, label: String, onClick: () -> Unit) {
    TapTarget(onClick, label, Modifier.size(TapSize)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(if (active) StillColors.ink.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            InkIcon(icon, 23.dp, strokeWidth = 1.7f)
        }
    }
}

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Today", "Tomorrow", then the weekday. */
private fun NewTaskDraft.dayName(): String = when (dayOffset) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
}

/** "Today", "Tomorrow", then "Wed 14 Oct". */
private fun NewTaskDraft.shortDay(): String = if (dayOffset <= 1) dayName() else date.format(DayDateFormat)

private fun partOfDay(hour: Int): String = when {
    hour < 12 -> "Morning"
    hour < 17 -> "Afternoon"
    hour < 21 -> "Evening"
    else -> "Night"
}
