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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.Priority
import app.stillnote.data.Reminder
import app.stillnote.ui.components.CalendarPicker
import app.stillnote.ui.components.ClockPicker
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.IconPaths
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.Pill
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.Stepper
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.Tape
import app.stillnote.ui.components.fillInUnderline
import app.stillnote.ui.components.inkBorder
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.partOfDay
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.MiniPostItShape
import app.stillnote.ui.theme.NoteShape
import app.stillnote.ui.theme.OnPaper
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.paper
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

/** The note for a new task, or for changing one, floating over the scrim. Tapping outside closes it without saving. */
@Composable
fun NoteSheetOverlay(draft: NewTaskDraft, viewModel: NewTaskViewModel, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // The pickers and the keyboard don't fit together, so a picker takes the keyboard's place:
    // a new note starts on the keyboard unless it was opened with a picker already showing.
    // A task being changed opens without it, so the whole note shows; a tap on the text brings it up.
    LaunchedEffect(Unit) { if (draft.picker == null && draft.editing == null) focus.requestFocus() }
    LaunchedEffect(draft.picker) { if (draft.picker != null) keyboard?.hide() }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(StillColors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = viewModel::dismiss)
                .semantics { contentDescription = "Close without saving" },
        )
        // Under the calendar or clock, the note is only there to be seen.
        val behindDialog = if (draft.dialog != null) Modifier.clearAndSetSemantics {} else Modifier
        SheetPlacement(top = draft.picker.noteTop(), margin = NoteMargin, maxWidth = MaxNoteWidth, modifier = behindDialog) {
            OnPaper { NoteSheet(draft, viewModel, focus) }
        }
        draft.dialog?.let { WhenDialogOverlay(it, draft, viewModel) }
    }
}

/** The calendar or the clock, over the note, behind a lighter scrim that closes it without changes. */
@Composable
private fun WhenDialogOverlay(dialog: WhenDialog, draft: NewTaskDraft, viewModel: NewTaskViewModel) {
    Box(
        Modifier
            .fillMaxSize()
            .background(StillColors.scrimPicker)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = viewModel::closeDialog)
            .semantics { contentDescription = "Close without changes" },
    )
    val top = if (dialog == WhenDialog.Calendar) 168.dp else 132.dp
    SheetPlacement(top = top, margin = PickerMargin, maxWidth = PickerWidth) {
        when (dialog) {
            WhenDialog.Calendar -> {
                val openTasks by viewModel.openTasksByDay.collectAsStateWithLifecycle()
                CalendarPicker(
                    initial = draft.date,
                    today = draft.today,
                    earliest = draft.earliest,
                    openTasks = openTasks,
                    onMonthShown = viewModel::showCalendarMonth,
                    onSet = viewModel::setDate,
                    onNoDate = { viewModel.setDate(null) },
                    noDateEnabled = draft.canClearDate,
                    noDateHint = "No date. Pick a project first: a task with neither shows nowhere.",
                )
            }
            WhenDialog.Clock -> ClockPicker(
                initial = remember { viewModel.clockStart() },
                onSet = viewModel::setTime,
                onNoTime = { viewModel.setTime(null) },
                onCancel = viewModel::closeDialog,
            )
        }
    }
}

// Where the mockups put the note for each state, measured from the top of the page.
private fun Picker?.noteTop(): Dp = when (this) {
    null -> 176.dp
    Picker.When -> 138.dp
    Picker.Priority -> 164.dp
    Picker.Project -> 150.dp
}

private val NoteMargin = 24.dp
private val MaxNoteWidth = 420.dp
private val PickerMargin = 20.dp
private val PickerWidth = 350.dp

/**
 * Puts a sheet [margin] from the sides, at most [maxWidth] wide, at [top], moving it up when the
 * keyboard or the bottom of the screen would otherwise cover it.
 */
@Composable
private fun SheetPlacement(top: Dp, margin: Dp, maxWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val animatedTop by animateDpAsState(top, tween(200), label = "sheet top")
    Layout(
        content = content,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) { measurables, constraints ->
        val width = min(constraints.maxWidth - 2 * margin.roundToPx(), maxWidth.roundToPx()).coerceAtLeast(0)
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
    val color by animateColorAsState(draft.shownPriority?.paper ?: StillColors.canary, tween(260), label = "note colour")
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        Modifier
            .rotate(-1.2f)
            .paperShadow(NoteShape, Shadows.note)
            .background(color, NoteShape)
            .semantics { paneTitle = if (draft.editing == null) "New task" else "Edit task" }
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
                MonoText(
                    if (draft.editing == null) "NEW TASK" else "EDIT TASK",
                    11f,
                    Modifier.padding(start = 22.dp, end = 18.dp),
                    letterSpacing = 0.16.em,
                )
                TitleField(draft, viewModel, focus, Modifier.padding(start = 22.dp, top = 8.dp, end = 18.dp))
                Chips(draft, Modifier.padding(start = 22.dp, top = 8.dp, end = 18.dp))
                when (draft.picker) {
                    Picker.When -> {
                        TearLine(Modifier.padding(top = 14.dp))
                        WhenPicker(draft, viewModel, Modifier.padding(top = 12.dp))
                    }
                    Picker.Priority -> {
                        TearLine(Modifier.padding(top = 14.dp))
                        PriorityPicker(draft.shownPriority, viewModel::setPriority, Modifier.padding(top = 12.dp))
                    }
                    Picker.Project -> {
                        TearLine(Modifier.padding(top = 14.dp))
                        ProjectPicker(draft, viewModel, Modifier.padding(top = 12.dp))
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
private fun TitleField(draft: NewTaskDraft, viewModel: NewTaskViewModel, focus: FocusRequester, modifier: Modifier = Modifier) {
    val style = handStyle(LocalHandFont.current, 27f, lineHeight = 1.3f)
    // Held here for the cursor, which starts at the end of a task's title rather than before it.
    var field by remember { mutableStateOf(TextFieldValue(draft.text, TextRange(draft.text.length))) }
    BasicTextField(
        value = if (field.text == draft.text) field else field.copy(text = draft.text),
        onValueChange = {
            field = it
            viewModel.setText(it.text)
        },
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
    // Today at any time is the default, so it needs no chip.
    val day = when {
        draft.date == null -> "No date"
        time != null -> "${draft.shortDay()}, ${time.format(TimeFormat)}" + (draft.reminder?.let { " · " + reminderName(it).lowercase() } ?: "")
        draft.date != draft.today -> draft.shortDay()
        else -> null
    }
    val priority = draft.priority.takeIf { draft.priorityPicked || it != Priority.None }
    if (day == null && priority == null && !draft.projectPicked) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (day != null) Chip(StillIcons.Clock, day)
        if (priority != null) {
            Chip(StillIcons.Flag, if (priority == Priority.None) "No priority" else "${priority.marks} ${priority.label}")
        }
        if (draft.projectPicked) Chip(StillIcons.Projects, draft.project ?: "No project")
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
        // The day and the time open the calendar and the clock; the arrows stay for a day or half an hour.
        val dayDate = draft.date?.format(DayDateFormat)?.uppercase()
        Stepper(
            onPrevious = { viewModel.shiftDay(-1) },
            previousLabel = "Previous day",
            modifier = StepperPadding,
            previousEnabled = (draft.date ?: draft.today) > draft.earliest,
            onNext = { viewModel.shiftDay(1) },
            nextLabel = "Next day",
            onValueClick = { viewModel.openDialog(WhenDialog.Calendar) },
            valueLabel = listOfNotNull(draft.dayName(), dayDate).joinToString(", ") + ". Open the calendar",
        ) {
            HandText(draft.dayName(), 28f, Modifier.fillInUnderline(StillColors.ink), weight = FontWeight.Bold, lineHeight = 1.15f)
            // Blank under "No date", but kept, so the note doesn't jump when a day is picked.
            MonoText(dayDate ?: " ", 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.16.em)
        }
        StepperRule()
        Stepper(
            onPrevious = { viewModel.shiftTime(-1) },
            previousLabel = "30 minutes earlier",
            modifier = StepperPadding,
            onNext = { viewModel.shiftTime(1) },
            nextLabel = "30 minutes later",
            onValueClick = { viewModel.openDialog(WhenDialog.Clock) },
            valueLabel = (draft.time?.let { "${it.format(TimeFormat)}, ${partOfDay(it.hour)}" } ?: "Any time") + ". Open the clock",
        ) {
            val time = draft.time
            MonoText(
                time?.format(TimeFormat) ?: "Any time",
                30f,
                Modifier.fillInUnderline(StillColors.ink),
                weight = FontWeight.Medium,
                color = StillColors.ink,
                letterSpacing = 0.04.em,
                lineHeight = 1.1f,
            )
            // Blank under "Any time", but kept, so the note doesn't jump when a time is picked.
            MonoText(time?.let { partOfDay(it.hour).uppercase() } ?: " ", 11f, Modifier.padding(top = 3.dp), letterSpacing = 0.16.em)
        }
        // Only a task with a time can be reminded of.
        if (draft.time != null) {
            val default by viewModel.reminderDefault.collectAsStateWithLifecycle()
            StepperRule()
            Stepper(
                onPrevious = { viewModel.shiftReminder(-1) },
                previousLabel = "Previous reminder choice",
                onNext = { viewModel.shiftReminder(1) },
                nextLabel = "Next reminder choice",
                modifier = StepperPadding,
                previousEnabled = draft.reminder != ReminderSteps.first(),
                nextEnabled = draft.reminder != ReminderSteps.last(),
            ) {
                HandText(reminderName(draft.reminder ?: default), 24f, weight = FontWeight.Bold, lineHeight = 1.15f)
                MonoText(if (draft.reminder == null) "DEFAULT REMINDER" else "REMINDER", 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.16.em)
            }
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

/** A wrapping row of pills: every project, then "No project". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectPicker(draft: NewTaskDraft, viewModel: NewTaskViewModel, modifier: Modifier = Modifier) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.padding(start = 22.dp, end = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Overline("Project")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            projects.forEach { name ->
                Pill(name, selected = draft.projectPicked && draft.project == name) { viewModel.setProject(name) }
            }
            Pill("No project", selected = draft.projectPicked && draft.project == null) { viewModel.setProject(null) }
        }
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
        val features by viewModel.features.collectAsStateWithLifecycle()
        Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
            PickerToggle(
                icon = StillIcons.Clock,
                active = draft.picker == Picker.When,
                label = if (draft.picker == Picker.When) "Hide date and time" else "Set date and time",
                onClick = { viewModel.toggle(Picker.When) },
            )
            // A database with no priority or project property has nowhere to keep them.
            if (features.priority) PickerToggle(
                icon = StillIcons.Flag,
                active = draft.picker == Picker.Priority,
                label = if (draft.picker == Picker.Priority) "Hide priority" else "Set priority",
                onClick = { viewModel.toggle(Picker.Priority) },
            )
            if (features.projects) PickerToggle(
                icon = StillIcons.Projects,
                active = draft.picker == Picker.Project,
                label = if (draft.picker == Picker.Project) "Hide project" else "Set project",
                onClick = { viewModel.toggle(Picker.Project) },
            )
        }
        TapTarget(
            onClick = viewModel::save,
            description = if (draft.editing == null) "Save task" else "Save changes",
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

private val StepperPadding = Modifier.padding(start = 20.dp, end = 16.dp)

/** The faint line between the clock's steppers. */
@Composable
private fun StepperRule() {
    Box(
        Modifier
            .padding(start = 56.dp, end = 52.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(StillColors.ink.copy(alpha = 0.16f)),
    )
}

/** "No reminder" or "10 min before". */
private fun reminderName(minutes: Int): String = if (minutes == Reminder.None) "No reminder" else "$minutes min before"

private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val DayDateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "Today", "Tomorrow" or "Yesterday", or null for another day. */
private fun NewTaskDraft.nearDay(): String? = when (date?.let { ChronoUnit.DAYS.between(today, it) }) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    else -> null
}

/** "Today", "Tomorrow" or "Yesterday", otherwise the weekday. */
private fun NewTaskDraft.dayName(): String =
    nearDay() ?: date?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: "No date"

/** "Today", "Tomorrow" or "Yesterday", otherwise "Wed 14 Oct". */
private fun NewTaskDraft.shortDay(): String = nearDay() ?: date?.format(DayDateFormat) ?: "No date"

