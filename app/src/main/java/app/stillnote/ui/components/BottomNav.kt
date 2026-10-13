package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.StillColors

enum class Tab(val label: String, val icon: IconPaths) {
    Calendar("Calendar", StillIcons.Calendar),
    Tasks("Tasks", StillIcons.Tasks),
    Projects("Projects", StillIcons.Projects),
}

/** [onDismissDim], when set, lays a light scrim over the bar; a tap on it calls back instead of switching tab. */
@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier, onDismissDim: (() -> Unit)? = null) {
    Box(modifier) {
        NavBar(current, onSelect)
        if (onDismissDim != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(StillColors.scrimLight)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismissDim)
                    .clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(StillColors.paper)
            .drawBehind {
                val y = 0.5.dp.toPx()
                drawLine(StillColors.rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            // Not safeDrawing: that includes the keyboard, and the nav must stay put behind the new-task note.
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(start = 16.dp, top = 7.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val color = if (selected) StillColors.ink else StillColors.ink2
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .selectable(
                        selected = selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(tab) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                InkIcon(tab.icon, 22.dp, tint = color, strokeWidth = if (selected) 1.7f else 1.6f)
                MonoText(
                    text = tab.label.uppercase(),
                    size = 10.5f,
                    modifier = Modifier
                        .then(if (selected) Modifier.rotate(-2f).background(StillColors.canary) else Modifier)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    weight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = color,
                    letterSpacing = 0.14.em,
                )
            }
        }
    }
}
