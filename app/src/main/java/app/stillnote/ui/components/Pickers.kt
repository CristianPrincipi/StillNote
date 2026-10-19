package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.stillnote.ui.theme.LocalInk

/**
 * `[‹] value [›]`: the note's day, time and reminder, and the morning list's time in Settings.
 * With [onValueClick] the value is a button too, [valueLabel] is what a screen reader hears, and
 * its main text should carry a [fillInUnderline].
 */
@Composable
fun Stepper(
    onPrevious: () -> Unit,
    previousLabel: String,
    onNext: () -> Unit,
    nextLabel: String,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onValueClick: (() -> Unit)? = null,
    valueLabel: String = "",
    value: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
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
                .then(
                    if (onValueClick == null) Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    else Modifier
                        .heightIn(min = 60.dp)
                        .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onValueClick)
                        .clearAndSetSemantics {
                            contentDescription = valueLabel
                            liveRegion = LiveRegionMode.Polite
                        }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            value()
        }
        TapTarget(
            onClick = onNext,
            description = nextLabel,
            modifier = Modifier
                .size(TapSize)
                .alpha(if (nextEnabled) 1f else 0.3f),
            enabled = nextEnabled,
        ) {
            InkIcon(StillIcons.ChevronRight, 22.dp, strokeWidth = 1.8f)
        }
    }
}

/** The dashed fill-in-the-blank line under a value that opens a picker, with 6 dp of room either side. */
fun Modifier.fillInUnderline(ink: Color): Modifier =
    dashedBottomBorder(ink.copy(alpha = 0.5f), 1.5.dp).padding(start = 6.dp, end = 6.dp, bottom = 1.5.dp)

/** A choice among a wrapping row of them: the note's project pills, and the reminder times in Settings. */
@Composable
fun Pill(name: String, selected: Boolean, onPick: () -> Unit) {
    val ink = LocalInk.current
    // The pill is 44 tall, as in the mockup; its 48 dp touch target overflows the row.
    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .requiredHeight(TapSize)
                .selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onPick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .background(if (selected) ink.ink else ink.wash, CircleShape)
                    .border(1.4.dp, ink.ink, CircleShape)
                    .padding(horizontal = 17.4.dp),
                contentAlignment = Alignment.Center,
            ) {
                HandText(name, 18f, color = if (selected) ink.onInk else ink.ink, lineHeight = 1f, maxLines = 1)
            }
        }
    }
}
