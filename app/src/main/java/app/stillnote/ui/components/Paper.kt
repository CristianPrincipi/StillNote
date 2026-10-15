package app.stillnote.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stillnote.ui.theme.LocalPage
import app.stillnote.ui.theme.Page
import app.stillnote.ui.theme.StillColors

/** The dotted notebook page: 1 dp dots every 22 dp, counted from [top] so they line up under the status bar. */
fun Modifier.dottedPaper(page: Page, top: Dp = 0.dp): Modifier = drawWithCache {
    val step = 22.dp.toPx()
    val (width, height) = size
    val dots = buildList {
        var y = top.toPx() % step
        while (y <= height) {
            var x = 0f
            while (x <= width) {
                add(Offset(x, y))
                x += step
            }
            y += step
        }
    }
    val diameter = 2.4.dp.toPx()
    onDrawBehind {
        drawRect(page.ground)
        drawPoints(dots, PointMode.Points, page.dot, strokeWidth = diameter, cap = StrokeCap.Round)
    }
}

/** A full screen of dotted paper, with its content kept clear of the status bar and cutouts. */
@Composable
fun PaperPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    Column(
        modifier
            .fillMaxSize()
            .dottedPaper(LocalPage.current, top)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        content = content,
    )
}

/** A CSS-style dashed line along the bottom edge, inside the bounds. On the page, pass the page's [color]. */
fun Modifier.dashedBottomBorder(color: Color = StillColors.ruleDashed, width: Dp = 1.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val y = size.height - stroke / 2
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2)),
    )
}

/**
 * A pen outline just inside the edge of [shape]. `Modifier.border` draws the irregular
 * hand-drawn and curled shapes about twice as heavy as asked, so those use this instead.
 */
fun Modifier.inkBorder(width: Dp, color: Color, shape: Shape): Modifier = drawWithCache {
    val stroke = width.toPx()
    val inner = Size(size.width - stroke, size.height - stroke)
    val path = Path().apply { addOutline(shape.createOutline(inner, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        drawContent()
        translate(stroke / 2, stroke / 2) { drawPath(path, color, style = Stroke(stroke)) }
    }
}

/**
 * Keeps a keyed list at its top when rows arrive above the first one. Left alone, a lazy list
 * holds on to the row it was showing, so a task synced in above it would stay out of sight.
 * [firstKey] is the key of the first row; the list is only moved if the user hadn't scrolled.
 */
@Composable
fun LazyListState.StayAtTop(firstKey: Any?) {
    var userAtTop by remember { mutableStateOf(true) }
    LaunchedEffect(this) {
        snapshotFlow { isScrollInProgress }.collect { scrolling -> if (!scrolling) userAtTop = !canScrollBackward }
    }
    LaunchedEffect(firstKey) { if (userAtTop && canScrollBackward) scrollToItem(0) }
}
