package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.HandDrawnCircle
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.StillColors
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ClockMode { Hours, Minutes }

/**
 * A 24-hour clock face to pick a time on, on a card (DESIGN.md §3, ClockPicker). It opens on
 * [initial] in [initialMode]. Picking an hour moves on to the minutes. A tap on the minutes takes
 * the nearest five; dragging the hand sets any minute, and it stays where it is let go.
 */
@Composable
fun ClockPicker(
    initial: LocalTime,
    onSet: (LocalTime) -> Unit,
    onNoTime: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    initialMode: ClockMode = ClockMode.Hours,
) {
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }

    PickerCard(
        title = "Pick a time",
        tilt = -0.8f,
        tapeTilt = 2f,
        contentPadding = PaddingValues(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 14.dp),
        modifier = modifier,
        spacing = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoText(
                if (mode == ClockMode.Hours) "TIME · PICK THE HOUR" else "TIME · PICK THE MINUTES",
                11f,
                letterSpacing = 0.16.em,
                maxLines = 1,
            )
            PickerTextButton("No time", onNoTime, size = 11f, letterSpacing = 0.12f, height = 40.dp)
        }
        TimeDisplay(hour, minute, mode, onMode = { mode = it })
        ClockFace(
            mode = mode,
            hour = hour,
            minute = minute,
            onHour = {
                hour = it
                mode = ClockMode.Minutes
            },
            onMinute = { minute = it },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PickerTextButton("Cancel", onCancel)
            SetPill("Set ${twoDigits(hour)}:${twoDigits(minute)}", onClick = { onSet(LocalTime.of(hour, minute)) })
        }
    }
}

/** "Morning" before 12, "Afternoon" before 17, "Evening" before 21, otherwise "Night". */
fun partOfDay(hour: Int): String = when {
    hour < 12 -> "Morning"
    hour < 17 -> "Afternoon"
    hour < 21 -> "Evening"
    else -> "Night"
}

/** "18 : 00", each part a button; the part being picked sits on canary tape. */
@Composable
private fun TimeDisplay(hour: Int, minute: Int, mode: ClockMode, onMode: (ClockMode) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            TimePart(twoDigits(hour), "Hours", mode == ClockMode.Hours) { onMode(ClockMode.Hours) }
            MonoText(
                ":",
                40f,
                Modifier
                    .padding(bottom = 6.dp)
                    .clearAndSetSemantics {},
                weight = FontWeight.Medium,
                color = LocalInk.current.ink,
                lineHeight = 1f,
            )
            TimePart(twoDigits(minute), "Minutes", mode == ClockMode.Minutes) { onMode(ClockMode.Minutes) }
        }
        MonoText(partOfDay(hour).uppercase(), 11f, letterSpacing = 0.16.em)
    }
}

@Composable
private fun TimePart(value: String, name: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 58.dp)
            .rotate(if (active) -2f else 0f)
            .background(if (active) StillColors.canary else Color.Transparent, RoundedCornerShape(2.dp))
            .selectable(selected = active, interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$name, $value" }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoText(value, 46f, weight = FontWeight.Medium, color = LocalInk.current.ink, lineHeight = 1f)
    }
}

/** One number on the face: where it sits and what picking it does. */
private class Mark(val label: String, val description: String, val radius: Dp, val index: Int, val selected: Boolean, val pick: () -> Unit)

/**
 * The hand-drawn face. Taps and drags land on the face as a whole, which works out the nearest
 * number, so every part of it belongs to one: the inner ring's numbers are only 36 dp apart, too
 * close for a 48 dp target each. Each number is still its own button for screen readers.
 */
@Composable
private fun ClockFace(
    mode: ClockMode,
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val currentOnHour by rememberUpdatedState(onHour)
    val currentOnMinute by rememberUpdatedState(onMinute)
    val marks = if (mode == ClockMode.Hours) {
        List(12) { i ->
            val value = if (i == 0) 12 else i
            Mark(value.toString(), "Hour $value", OuterRadius, i, hour == value) { onHour(value) }
        } + List(12) { i ->
            val value = if (i == 0) 0 else i + 12
            Mark(twoDigits(value), "Hour ${twoDigits(value)}", InnerRadius, i, hour == value) { onHour(value) }
        }
    } else {
        List(12) { i ->
            val value = i * 5
            Mark(twoDigits(value), "${twoDigits(value)} minutes", OuterRadius, i, minute == value) { onMinute(value) }
        }
    }
    // The hand points at the hour, on its ring, or at the minute.
    val handAngle = if (mode == ClockMode.Hours) (hour % 12) * 30f else minute * 6f
    val handLength = if (mode == ClockMode.Hours && hour !in 1..12) InnerRadius else OuterRadius
    // A minute between the marks has no number to circle, so the hand ends in a dot.
    val handDot = mode == ClockMode.Minutes && minute % 5 != 0

    Box(
        modifier = modifier
            .size(FaceSize)
            .background(StillColors.paper, HandDrawnCircle)
            .inkBorder(1.6.dp, ink.ink, HandDrawnCircle)
            .drawBehind {
                val center = Offset(Centre.toPx(), Centre.toPx())
                val tip = center + polar(handLength.toPx(), handAngle)
                val stroke = 2.dp.toPx()
                drawLine(ink.ink, center + polar(stroke / 2, handAngle), tip - polar(stroke / 2, handAngle), stroke, StrokeCap.Round)
                if (handDot) {
                    val radius = 7.dp.toPx()
                    drawCircle(StillColors.canary, radius, tip)
                    drawCircle(ink.ink, radius - 0.8.dp.toPx(), tip, style = Stroke(1.6.dp.toPx()))
                }
            }
            .pointerInput(mode) {
                val centre = Offset(Centre.toPx(), Centre.toPx())
                val dead = PinSize.toPx()
                val ringSplit = ((InnerRadius + OuterRadius) / 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var dragging = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        val offset = change.position - centre
                        val distance = offset.getDistance()
                        val angle = angleOf(offset)
                        if (!change.pressed) {
                            change.consume()
                            if (dragging || distance < dead) break
                            if (mode == ClockMode.Minutes) {
                                currentOnMinute(((angle / 30f).roundToInt() % 12) * 5)
                            } else {
                                val index = (angle / 30f).roundToInt() % 12
                                currentOnHour(
                                    when {
                                        distance > ringSplit -> if (index == 0) 12 else index
                                        index == 0 -> 0
                                        else -> index + 12
                                    }
                                )
                            }
                            break
                        }
                        // Only the minutes follow a drag; the hours are picked by a tap.
                        if (mode == ClockMode.Minutes) {
                            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (dragging && distance >= dead) currentOnMinute((angle / 6f).roundToInt() % 60)
                        }
                        change.consume()
                    }
                }
            },
    ) {
        marks.forEach { mark -> ClockMark(mark, mode) }
        Box(
            Modifier
                .offset(Centre - PinSize / 2, Centre - PinSize / 2)
                .size(PinSize)
                .background(StillColors.canary, CircleShape)
                .inkBorder(1.6.dp, ink.ink, CircleShape),
        )
    }
}

@Composable
private fun ClockMark(mark: Mark, mode: ClockMode) {
    val ink = LocalInk.current
    val angle = mark.index * 30f
    val radians = Math.toRadians(angle.toDouble())
    val x = Centre + mark.radius * sin(radians).toFloat() - MarkSize / 2
    val y = Centre - mark.radius * cos(radians).toFloat() - MarkSize / 2
    Box(
        modifier = Modifier
            .offset(x, y)
            .size(MarkSize)
            .then(
                if (mark.selected) Modifier
                    .background(StillColors.canary, HandDrawnCircle)
                    .inkBorder(1.6.dp, ink.ink, HandDrawnCircle)
                else Modifier
            )
            .clearAndSetSemantics {
                contentDescription = mark.description
                role = Role.Button
                selected = mark.selected
                onClick { mark.pick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            mark.radius == InnerRadius -> MonoText(mark.label, 13f, color = if (mark.selected) ink.ink else ink.ink2, lineHeight = 1f)
            else -> HandText(mark.label, if (mode == ClockMode.Hours) 21f else 20f, lineHeight = 1f)
        }
    }
}

private val FaceSize = 272.dp
private val OuterRadius = 106.dp
private val InnerRadius = 68.dp
private val MarkSize = 40.dp
private val PinSize = 14.dp

// The mockup places the numbers, the hand and the pin from inside the face's 1.6 dp border.
private val Centre = 1.6.dp + 136.dp

/** Degrees clockwise from twelve o'clock, 0 to 360. */
private fun angleOf(offset: Offset): Float {
    val degrees = Math.toDegrees(atan2(offset.x.toDouble(), -offset.y.toDouble())).toFloat()
    return (degrees + 360f) % 360f
}

/** The point [length] from the centre at [degrees] clockwise from twelve o'clock. */
private fun polar(length: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(length * sin(radians).toFloat(), -length * cos(radians).toFloat())
}

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')
