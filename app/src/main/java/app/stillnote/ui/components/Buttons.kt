package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.monoStyle

/** The main action of a screen: an ink pill with canary text and a trailing arrow. */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 52.dp,
) {
    TapTarget(
        onClick = onClick,
        description = null,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .heightIn(min = minHeight)
            .background(StillColors.ink, CircleShape)
            .padding(start = 26.dp, end = 24.dp),
        enabled = enabled,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoText(text, 14f, weight = FontWeight.Medium, color = StillColors.canary, letterSpacing = 0.08.em)
            InkIcon(StillIcons.Send, 20.dp, tint = StillColors.canary, strokeWidth = 2f)
        }
    }
}

/** The quieter alternative next to a [PrimaryPill]: the same pill as an outline. */
@Composable
fun OutlinePill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TapTarget(
        onClick = onClick,
        description = null,
        modifier = modifier
            .heightIn(min = 52.dp)
            .border(1.4.dp, StillColors.ink, CircleShape)
            .padding(horizontal = 24.dp),
    ) {
        MonoText(text, 14f, weight = FontWeight.Medium, color = StillColors.ink, letterSpacing = 0.08.em)
    }
}

/** "← Back", at the bottom left of every setup step. */
@Composable
fun BackLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TapTarget(onClick, description = null, modifier.heightIn(min = TapSize).padding(start = 6.dp, end = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            InkIcon(StillIcons.Back, 18.dp, tint = StillColors.ink2, strokeWidth = 1.8f)
            MonoText("Back", 13f)
        }
    }
}

/** An underlined escape hatch in running text. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SmallTarget(onClick, modifier) {
        BasicText(text, style = monoStyle(12f, color = StillColors.ink).copy(textDecoration = TextDecoration.Underline))
    }
}

/** A control the mockups draw 44 tall: it takes that much room, and its touch target overflows to 48. */
@Composable
private fun SmallTarget(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.height(44.dp), contentAlignment = Alignment.Center) {
        TapTarget(onClick, description = null, Modifier.requiredHeight(TapSize)) { content() }
    }
}

/** A small uppercase action with an icon, such as "REFRESH" or "OPEN NOTION ↗". */
@Composable
fun LabelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: IconPaths? = null,
    trailing: IconPaths? = null,
    letterSpacing: Float = 0.12f,
) {
    SmallTarget(onClick, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.let { InkIcon(it, 16.dp, strokeWidth = 1.8f) }
            MonoText(text.uppercase(), 11f, weight = FontWeight.Medium, color = StillColors.ink, letterSpacing = letterSpacing.em)
            trailing?.let { InkIcon(it, 14.dp, strokeWidth = 2f) }
        }
    }
}
