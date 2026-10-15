package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stillnote.ui.theme.HandDrawnCircle
import app.stillnote.ui.theme.StillColors

/** Android's minimum touch target; the mockups draw 44. */
val TapSize = 48.dp

/**
 * A button with no ripple: its content dims while pressed. [modifier] sets the size, which
 * should be at least [TapSize]. [description] is what a screen reader announces.
 */
@Composable
fun TapTarget(
    onClick: () -> Unit,
    description: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(interactions, indication = null, enabled = enabled, role = role, onClick = onClick)
            .then(if (description == null) Modifier else Modifier.semantics { contentDescription = description })
            .graphicsLayer { alpha = if (pressed) 0.5f else 1f },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Makes a task's post-it or row open it in the note, to change it. The buttons on it keep their
 * own taps. Like the rest of the paper it has no ripple; screen readers hear "Edit".
 */
fun Modifier.tapToEdit(onEdit: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, onClickLabel = "Edit", role = Role.Button, onClick = onEdit)

/** The hand-drawn circle of check buttons and radio buttons: an outline, or solid when [filled]. */
@Composable
fun HandCircle(
    size: Dp,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    stroke: Dp = 1.6.dp,
    color: Color = StillColors.ink,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(size)
            .then(
                if (filled) Modifier.background(color, HandDrawnCircle)
                else Modifier.inkBorder(stroke, color, HandDrawnCircle)
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A solid hand-drawn circle with a tick in the paper colour. */
@Composable
fun DoneMark(size: Dp, tickSize: Dp, tickStroke: Float, modifier: Modifier = Modifier) {
    HandCircle(size, modifier, filled = true) {
        InkIcon(StillIcons.Check, tickSize, tint = StillColors.paper, strokeWidth = tickStroke)
    }
}
