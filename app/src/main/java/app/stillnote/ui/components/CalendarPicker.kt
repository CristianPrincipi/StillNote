package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.DayPostItShape
import app.stillnote.ui.theme.HandDrawnCircle
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.StillColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of days to pick one from, on a card (DESIGN.md §3, CalendarPicker). It opens on [initial],
 * or on [today]'s month with nothing picked when that is null. Days before [earliest] can't be
 * picked, nor months before its month. [openTasks] counts the open tasks on each day, for the
 * squares; [onMonthShown] tells which month to count, whenever the month changes.
 * "No date" calls [onNoDate]; with [noDateEnabled] false it is drawn disabled and a screen reader
 * hears [noDateHint].
 */
@Composable
fun CalendarPicker(
    initial: LocalDate?,
    today: LocalDate,
    earliest: LocalDate,
    openTasks: Map<LocalDate, Int>,
    onMonthShown: (YearMonth) -> Unit,
    onSet: (LocalDate) -> Unit,
    onNoDate: () -> Unit,
    modifier: Modifier = Modifier,
    noDateEnabled: Boolean = true,
    noDateHint: String? = null,
) {
    var picked by rememberSaveable { mutableStateOf(initial) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial ?: today)) }
    LaunchedEffect(month) { onMonthShown(month) }
    val firstMonth = YearMonth.from(earliest)

    PickerCard(
        title = "Pick a date",
        tilt = 0.8f,
        tapeTilt = -2f,
        contentPadding = PaddingValues(start = 14.dp, top = 24.dp, end = 14.dp, bottom = 14.dp),
        modifier = modifier,
        spacing = 8.dp,
    ) {
        MonthHeader(
            month = month,
            canGoBack = month > firstMonth,
            onPrevious = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
        )
        Row(
            Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics {},
        ) {
            "MTWTFSS".forEach { weekday ->
                MonoText(weekday.toString(), 11f, Modifier.weight(1f), letterSpacing = 0.12.em, textAlign = TextAlign.Center)
            }
        }
        DayGrid(month, today, earliest, picked, openTasks, onPick = { picked = it })
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerTextButton(
                "No date",
                onNoDate,
                enabled = noDateEnabled,
                description = if (noDateEnabled) null else noDateHint,
            )
            val day = picked
            SetPill(
                text = if (day == null) "Set date" else "Set " + day.format(PickedDayFormat),
                onClick = { if (day != null) onSet(day) },
                enabled = day != null,
            )
        }
    }
}

/** The year, the month in the hand font and the month arrows, over the index card's red rule. */
@Composable
private fun MonthHeader(month: YearMonth, canGoBack: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardHeader()
            // The mockup's rule is a CSS border below the padding, so it adds its 1.2 dp.
            .padding(start = 8.dp, bottom = 5.2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            MonoText(month.year.toString(), 11f, letterSpacing = 0.16.em)
            HandText(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), 32f, weight = FontWeight.Bold, lineHeight = 1.15f)
        }
        Row {
            MonthArrow(StillIcons.ChevronLeft, "Previous month", onPrevious, enabled = canGoBack)
            MonthArrow(StillIcons.ChevronRight, "Next month", onNext)
        }
    }
}

/** Drawn 44 wide, as in the mockup; the touch target overflows it to 48. */
@Composable
private fun MonthArrow(icon: IconPaths, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        TapTarget(
            onClick = onClick,
            description = label,
            modifier = Modifier
                .requiredSize(TapSize)
                .alpha(if (enabled) 1f else 0.3f),
            enabled = enabled,
        ) {
            InkIcon(icon, 22.dp, strokeWidth = 1.8f)
        }
    }
}

/** Monday-first weeks of [month]; days of other months are left blank. */
@Composable
private fun DayGrid(
    month: YearMonth,
    today: LocalDate,
    earliest: LocalDate,
    picked: LocalDate?,
    openTasks: Map<LocalDate, Int>,
    onPick: (LocalDate) -> Unit,
) {
    val lead = month.atDay(1).dayOfWeek.value - 1
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(weeks) { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { weekday ->
                    val day = week * 7 + weekday - lead + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(DayHeight))
                    } else {
                        val date = month.atDay(day)
                        val pickable = date >= earliest
                        DayButton(
                            date = date,
                            isToday = date == today,
                            picked = date == picked,
                            enabled = pickable,
                            // Only days that can be picked show their tasks: the point is to spot a free one.
                            open = if (pickable) openTasks[date] ?: 0 else 0,
                            onPick = { onPick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayButton(
    date: LocalDate,
    isToday: Boolean,
    picked: Boolean,
    enabled: Boolean,
    open: Int,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val paper = if (!picked) Modifier else Modifier
        .rotate(-3f)
        .paperShadow(DayPostItShape, Shadows.pickedDay)
        .background(StillColors.canary, DayPostItShape)
    Column(
        modifier = modifier
            .height(DayHeight)
            .then(paper)
            .selectable(selected = picked, interactionSource = null, indication = null, enabled = enabled, onClick = onPick)
            .clearAndSetSemantics { contentDescription = date.description(isToday, open) }
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .then(if (isToday) Modifier.inkBorder(1.6.dp, ink.ink, HandDrawnCircle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            MonoText(
                date.dayOfMonth.toString(),
                15f,
                weight = if (isToday || picked) FontWeight.Medium else FontWeight.Normal,
                color = if (enabled) ink.ink else ink.inkDisabled,
            )
        }
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(minOf(open, MAX_SQUARES)) {
                Box(
                    Modifier
                        .size(5.dp)
                        .border(1.dp, ink.ink),
                )
            }
        }
    }
}

private val DayHeight = 46.dp
private const val MAX_SQUARES = 3
private val PickedDayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "13 October, today, 2 tasks". */
private fun LocalDate.description(isToday: Boolean, open: Int): String = buildString {
    append("$dayOfMonth ${month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}")
    if (isToday) append(", today")
    if (open > 0) append(if (open == 1) ", 1 task" else ", $open tasks")
}
