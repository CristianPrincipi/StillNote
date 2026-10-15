package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.PaperInk
import app.stillnote.ui.theme.StillColors

/** Index-card paper: `card` with a pale blue rule on the last dp of every [every]. */
fun Modifier.ruledPaper(every: Dp): Modifier = drawBehind {
    drawRect(StillColors.card)
    val stroke = 1.dp.toPx()
    var y = every.toPx() - stroke / 2
    while (y < size.height) {
        drawLine(StillColors.indexBlue, Offset(0f, y), Offset(size.width, y), stroke)
        y += every.toPx()
    }
}

/** The plain band at the top of an index card, closed by its red rule. */
fun Modifier.cardHeader(): Modifier = drawBehind {
    drawRect(StillColors.card)
    val stroke = 1.2.dp.toPx()
    val y = size.height - stroke / 2
    drawLine(StillColors.indexRed, Offset(0f, y), Offset(size.width, y), stroke)
}

/** A strip of tape across the top edge of a note or card. [edges] draws its torn, dashed ends. */
@Composable
fun Tape(
    modifier: Modifier = Modifier,
    width: Dp = 114.dp,
    height: Dp = 28.dp,
    color: Color = StillColors.tape,
    tilt: Float = -3f,
    edges: Boolean = true,
) {
    Box(
        modifier
            .size(width, height)
            .rotate(tilt)
            .background(color)
            .drawBehind {
                if (!edges) return@drawBehind
                val stroke = 1.dp.toPx()
                val edge = StillColors.ink.copy(alpha = 0.14f)
                val dashes = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2))
                for (x in listOf(stroke / 2, size.width - stroke / 2)) {
                    drawLine(edge, Offset(x, 0f), Offset(x, size.height), stroke, pathEffect = dashes)
                }
            },
    )
}

/**
 * One of a set of choices, with a hand-drawn radio button on the right. The chosen row becomes
 * a sheet of card; with [dashed], the others are separated by a dashed line.
 */
@Composable
fun RadioRow(
    selected: Boolean,
    onPick: () -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 6.dp,
    tilt: Float = 0f,
    dashed: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(2.dp)
    val sheet = when {
        selected -> Modifier.rotate(tilt).paperShadow(shape, Shadows.postIt).background(StillColors.card, shape)
        dashed -> Modifier.dashedBottomBorder(LocalInk.current.ruleDashed)
        else -> Modifier
    }
    // The chosen row is a sheet of card, so it takes the dark ink of paper, also on the Night page.
    CompositionLocalProvider(LocalInk provides if (selected) PaperInk else LocalInk.current) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(sheet)
                .selectable(
                    selected = selected,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.RadioButton,
                    onClick = onPick,
                )
                .heightIn(min = minHeight)
                .padding(horizontal = 14.dp, vertical = verticalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), content = content)
            HandCircle(22.dp) {
                if (selected) Box(Modifier.size(10.dp).background(LocalInk.current.ink, CircleShape))
            }
        }
    }
}

/** The top of a setup step: progress squares with "STEP 1 OF 3" (or just "LAST STEP"), a title and a line of intro. */
@Composable
fun StepHeader(title: String, intro: String, modifier: Modifier = Modifier, step: Int? = null, of: Int = 3) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 30.dp, end = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(of) { index -> ProgressSquare(filled = index < step) }
                }
            }
            MonoText(
                if (step == null) "LAST STEP" else "STEP $step OF $of",
                11f,
                letterSpacing = 0.16.em,
            )
        }
        ScreenTitle(title, size = 40f, letterSpacing = 0.em)
        MonoText(intro, 12.5f, lineHeight = 1.6f)
    }
}

@Composable
private fun ProgressSquare(filled: Boolean) {
    Box(
        Modifier
            .size(9.dp)
            .then(if (filled) Modifier.background(LocalInk.current.ink) else Modifier.inkBorder(1.2.dp, LocalInk.current.ink, RoundedCornerShape(0.dp))),
    )
}
