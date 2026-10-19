package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.OnPaper
import app.stillnote.ui.theme.PickerCardShape
import app.stillnote.ui.theme.StillColors

/**
 * The card the calendar and clock pickers are drawn on, tilted [tilt] degrees, with a strip of tape
 * tilted [tapeTilt]. Taps on the card stay on it. [title] names it for screen readers.
 */
@Composable
fun PickerCard(
    title: String,
    tilt: Float,
    tapeTilt: Float,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnPaper {
        Box(
            modifier
                .rotate(tilt)
                .paperShadow(PickerCardShape, Shadows.note)
                .background(StillColors.card, PickerCardShape)
                .semantics { paneTitle = title }
                // A tap on the card between its buttons must not fall through to the scrim, which closes it.
                .pointerInput(Unit) { detectTapGestures {} },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            Tape(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 1.dp, y = (-13).dp),
                width = 88.dp,
                height = 26.dp,
                color = StillColors.paper.copy(alpha = 0.8f),
                tilt = tapeTilt,
            )
        }
    }
}

/**
 * A picker's quiet uppercase action, such as "NO DATE" or "CANCEL": [height] tall, as the mockups
 * draw it, with a touch target overflowing it to 48. Disabled, it fades to `inkDisabled`.
 */
@Composable
fun PickerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Float = 12f,
    letterSpacing: Float = 0.1f,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    description: String? = null,
) {
    val ink = LocalInk.current
    Box(modifier.height(height), contentAlignment = Alignment.Center) {
        TapTarget(
            onClick = onClick,
            description = description,
            modifier = Modifier
                .requiredHeight(TapSize)
                .padding(horizontal = 6.dp),
            enabled = enabled,
        ) {
            MonoText(text.uppercase(), size, color = if (enabled) ink.ink2 else ink.inkDisabled, letterSpacing = letterSpacing.em, maxLines = 1)
        }
    }
}

/** The ink pill that commits a picker's choice, naming it: "Set Tue 13 Oct ✓". */
@Composable
fun SetPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = LocalInk.current
    TapTarget(
        onClick = onClick,
        description = null,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .heightIn(min = 50.dp)
            .background(ink.ink, CircleShape)
            .padding(start = 24.dp, end = 22.dp),
        enabled = enabled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoText(text, 14f, weight = FontWeight.Medium, color = ink.onInk, letterSpacing = 0.06.em, maxLines = 1)
            InkIcon(StillIcons.Check, 18.dp, tint = ink.onInk, strokeWidth = 2.2f)
        }
    }
}
