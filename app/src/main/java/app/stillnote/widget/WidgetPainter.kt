package app.stillnote.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.data.HandFont
import app.stillnote.data.Priority
import app.stillnote.data.Task
import app.stillnote.ui.components.PaperShadow
import app.stillnote.ui.components.drawPaperShadow
import app.stillnote.ui.theme.CurlShape
import app.stillnote.ui.theme.HandDrawnCircle
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.monoStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import java.time.format.TextStyle as DateStyle

/** What the widget shows: today's open tasks in the widget's order, in the user's hand font. */
data class WidgetNote(val today: LocalDate, val open: List<Task>, val font: HandFont)

/** Where a tap on the widget leads. */
sealed interface WidgetTap {
    data object OpenToday : WidgetTap
    data object NewTask : WidgetTap
    data class Complete(val taskId: String) : WidgetTap
}

/** A tap area, in dp from the widget's top-left corner. */
data class TapArea(val left: Dp, val top: Dp, val width: Dp, val height: Dp, val tap: WidgetTap, val label: String)

class WidgetDrawing(val bitmap: Bitmap, val areas: List<TapArea>)

/** The two drawn sizes, with the values of mockups 15 and 16 in dp and sp. */
enum class WidgetKind(
    val width: Float,
    val height: Float,
    val padding: Padding,
    val tilt: Float,
    val shape: Shape,
    val shadows: List<PaperShadow>,
    val tape: Tape,
    val titleSize: Float,
    val asideGap: Float,
    val plusBox: Float,
    val rows: Int,
    val rowHeight: Float,
    val checkWidth: Float,
    val circle: Float,
    val rowGap: Float,
    val rowTitleSize: Float,
    val wide: Boolean,
) {
    Square(
        width = 172f, height = 172f,
        padding = Padding(start = 14f, top = 14f, end = 8f, bottom = 10f),
        tilt = -1.5f,
        shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 16.dp, curlY = 6.dp),
        shadows = noteShadows(dy = 16.dp, blur = 18.dp),
        tape = Tape(left = 58f, width = 56f, angle = 4f),
        titleSize = 22f, asideGap = 6f, plusBox = 36f,
        rows = 3, rowHeight = 30f, checkWidth = 28f, circle = 16f, rowGap = 2f, rowTitleSize = 16f,
        wide = false,
    ),
    Wide(
        width = 356f, height = 184f,
        padding = Padding(start = 16f, top = 14f, end = 10f, bottom = 10f),
        tilt = -0.8f,
        shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 20.dp, curlY = 7.dp),
        shadows = noteShadows(dy = 18.dp, blur = 20.dp),
        tape = Tape(left = 150f, width = 64f, angle = -3f),
        titleSize = 24f, asideGap = 10f, plusBox = 40f,
        rows = 4, rowHeight = 31f, checkWidth = 30f, circle = 17f, rowGap = 4f, rowTitleSize = 17f,
        wide = true,
    ),
}

class Padding(val start: Float, val top: Float, val end: Float, val bottom: Float)

/** A strip of tape [width] wide across the top edge, [left] from the note's left edge. */
class Tape(val left: Float, val width: Float, val angle: Float)

private fun noteShadows(dy: Dp, blur: Dp) = listOf(
    PaperShadow(Color.Black.copy(alpha = 0.12f), dy = 1.dp, blur = 1.dp),
    PaperShadow(Color.Black.copy(alpha = 0.65f), dy = dy, blur = blur, spread = (-12).dp),
)

/**
 * Draws the widget's post-it into one bitmap with the app's own fonts, shapes and shadows,
 * none of which RemoteViews can show; the widget lays its tap areas over it.
 */
class WidgetPainter(private val context: Context) {
    private val fonts = createFontFamilyResolver(context)

    /**
     * The widget at [size]: the note fills it, less room for the tape above and the shadow below.
     * A box smaller than the mockup shrinks everything evenly, but never below [MinScale], where
     * the text stops being readable; past that the note just holds fewer rows. A bigger box
     * stretches the note and leaves the text at the mockup's size.
     */
    fun paint(note: WidgetNote, size: DpSize): WidgetDrawing {
        // Past 3.5× the text is sharp enough, and the launcher limits how much bitmap a widget may send.
        val density = minOf(context.resources.displayMetrics.density, MaxDensity)
        val kind = if (size.width.value >= size.height.value * 1.5f) WidgetKind.Wide else WidgetKind.Square
        val fit = minOf(
            1f,
            size.width.value / (kind.width + 2 * SideMargin),
            size.height.value / (kind.height + TopMargin + BottomMargin),
        )
        val scale = max(fit, MinScale)
        val frame = Frame(
            pixels = Size(ceil(size.width.value * density), ceil(size.height.value * density)),
            noteLeft = SideMargin * scale,
            noteTop = TopMargin * scale,
            noteSize = Size(size.width.value / scale - 2 * SideMargin, size.height.value / scale - TopMargin - BottomMargin),
            density = density,
            scale = scale,
        )
        return render(note, kind, frame, background = Color.Transparent)
    }

    /** [kind]'s mockup frame: the note at its own size on the mockup's home-screen colour, at 2×. */
    fun paintMockup(note: WidgetNote, kind: WidgetKind): Bitmap {
        val canvas = if (kind.wide) Size(460f, 300f) else Size(300f, 300f)
        val frame = Frame(
            pixels = Size(canvas.width * 2, canvas.height * 2),
            noteLeft = (canvas.width - kind.width) / 2,
            noteTop = (canvas.height - kind.height) / 2,
            noteSize = Size(kind.width, kind.height),
            density = 2f,
            scale = 1f,
        )
        return render(note, kind, frame, background = MockupHome).bitmap
    }

    /** The picker's preview: the 2×2 note with room for its shadow, on nothing. */
    fun paintPreview(note: WidgetNote): Bitmap {
        val kind = WidgetKind.Square
        val frame = Frame(
            pixels = Size((kind.width + 2 * SideMargin) * 3, (kind.height + TopMargin + BottomMargin) * 3),
            noteLeft = SideMargin,
            noteTop = TopMargin,
            noteSize = Size(kind.width, kind.height),
            density = 3f,
            scale = 1f,
        )
        return render(note, kind, frame, background = Color.Transparent).bitmap
    }

    /**
     * Where and how big the note is. Positions are in dp of the widget; [noteSize] is in the
     * mockup's dp, which [scale] shrinks to fit.
     */
    private class Frame(val pixels: Size, val noteLeft: Float, val noteTop: Float, val noteSize: Size, val density: Float, val scale: Float)

    private fun render(note: WidgetNote, kind: WidgetKind, frame: Frame, background: Color): WidgetDrawing {
        val image = ImageBitmap(frame.pixels.width.roundToInt(), frame.pixels.height.roundToInt())
        val drawDensity = Density(frame.density * frame.scale, fontScale = 1f)
        val measurer = TextMeasurer(fonts, drawDensity, LayoutDirection.Ltr, cacheSize = 0)
        val areas = mutableListOf<TapArea>()
        CanvasDrawScope().draw(drawDensity, LayoutDirection.Ltr, Canvas(image), frame.pixels) {
            drawRect(background)
            translate(frame.noteLeft * frame.density, frame.noteTop * frame.density) {
                val size = Size(frame.noteSize.width.dp.toPx(), frame.noteSize.height.dp.toPx())
                // The mockups turn the whole note, text included, about its centre.
                rotate(kind.tilt, pivot = size.center) {
                    drawPaperShadow(kind.shape, kind.shadows, size)
                    drawOutline(kind.shape.createOutline(size, layoutDirection, this), StillColors.canary)
                    val tapAreas = drawContent(note, kind, size, measurer)
                    drawTape(kind.tape)
                    tapAreas.mapTo(areas) { (rect, tap, label) ->
                        val toDp = { px: Float -> (px / frame.density).dp }
                        TapArea(
                            left = (frame.noteLeft).dp + toDp(rect.left),
                            top = (frame.noteTop).dp + toDp(rect.top),
                            width = toDp(rect.width),
                            height = toDp(rect.height),
                            tap = tap,
                            label = label,
                        )
                    }
                }
            }
        }
        return WidgetDrawing(image.asAndroidBitmap(), areas)
    }

    /** Draws the header, the rows and the "+N more" line; returns the tap areas in pixels of the note. */
    private fun DrawScope.drawContent(note: WidgetNote, kind: WidgetKind, size: Size, measurer: TextMeasurer): List<Triple<Rect, WidgetTap, String>> {
        val areas = mutableListOf<Triple<Rect, WidgetTap, String>>()
        val left = kind.padding.start.dp.toPx()
        val top = kind.padding.top.dp.toPx()
        val right = size.width - kind.padding.end.dp.toPx()
        val bottom = size.height - kind.padding.bottom.dp.toPx()
        val count = note.open.size

        // Header: "Today" with the count or the date beside it on one baseline, and the "+" on the right.
        val plus = kind.plusBox.dp.toPx()
        val today = measurer.measure("Today", handStyle(note.font.family, kind.titleSize, FontWeight.Bold, lineHeight = 1.1f))
        val todayTop = top + (plus - today.size.height) / 2f
        drawText(today, topLeft = Offset(left, todayTop))
        val aside = if (kind.wide) "${note.today.short()} · $count to do".uppercase(Locale.ENGLISH) else "$count"
        val asideStyle = if (kind.wide) monoStyle(11f, letterSpacing = 0.14.em) else monoStyle(12f)
        val asideLeft = left + today.size.width + kind.asideGap.dp.toPx()
        val asideText = measurer.line(aside, asideStyle, right - plus - asideLeft)
        drawText(asideText, topLeft = Offset(asideLeft, todayTop + today.firstBaseline - asideText.firstBaseline))
        drawPlus(Offset(right - plus / 2, top + plus / 2), 22.dp.toPx())
        areas += Triple(Rect(0f, 0f, right - plus, top + plus), WidgetTap.OpenToday, "Today, $count to do")
        areas += Triple(Rect(right - plus, 0f, size.width, top + plus), WidgetTap.NewTask, "Add a task")

        // Rows: the mockup's number, less one for each row's height the note is shorter than the
        // mockup. The 4×2 has no room under its rows, so when more are waiting the "+N more" line
        // takes the last row's place.
        val listTop = top + plus + 4.dp.toPx()
        val rowHeight = kind.rowHeight.dp.toPx()
        val pitch = rowHeight + if (kind.wide) 1.dp.toPx() else 0f
        // A few dp short still leaves room: the rows and the "+N more" line have that much to spare.
        val missing = kind.height.dp.toPx() - size.height - 4.dp.toPx()
        val rows = (kind.rows - ceil(max(0f, missing) / pitch).toInt()).coerceAtLeast(1)
        val shown = when {
            count <= rows -> count
            kind.wide -> max(1, rows - 1)
            else -> rows
        }
        note.open.take(shown).forEachIndexed { index, task ->
            val y = listTop + index * pitch
            val middle = y + rowHeight / 2
            val ink = task.priority.ink
            val circle = kind.circle.dp.toPx()
            drawHandCircle(Offset(left, middle - circle / 2), circle, 1.6.dp.toPx(), ink)

            var titleRight = right
            if (kind.wide) {
                val marksWidth = 26.dp.toPx()
                val gap = kind.rowGap.dp.toPx()
                val marks = measurer.line(task.priority.marks, monoStyle(11f, FontWeight.Medium, ink), marksWidth)
                drawText(marks, topLeft = Offset(right - marks.size.width, middle - marks.size.height / 2f))
                val time = measurer.line(task.time?.format(TimeFormat).orEmpty(), monoStyle(11f, letterSpacing = 0.03.em), right - left)
                val timeRight = right - marksWidth - gap
                drawText(time, topLeft = Offset(timeRight - time.size.width, middle - time.size.height / 2f))
                titleRight = timeRight - time.size.width - gap
                drawLine(
                    color = StillColors.ink.copy(alpha = 0.18f),
                    start = Offset(left, y + rowHeight + 0.5.dp.toPx()),
                    end = Offset(right, y + rowHeight + 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            }
            val titleLeft = left + kind.checkWidth.dp.toPx() + kind.rowGap.dp.toPx()
            val title = measurer.line(task.title, handStyle(note.font.family, kind.rowTitleSize, color = ink), titleRight - titleLeft)
            drawText(title, topLeft = Offset(titleLeft, middle - title.size.height / 2f))
            areas += Triple(Rect(0f, y, size.width, y + pitch), WidgetTap.Complete(task.id), "Mark ${task.title} as done")
        }

        val more = count - shown
        val listBottom = listTop + shown * pitch
        if (more > 0) {
            val line = measurer.line("+$more more", monoStyle(10.5f, letterSpacing = 0.08.em), right - left)
            drawText(line, topLeft = Offset(left + kind.checkWidth.dp.toPx(), bottom - line.size.height))
            areas += Triple(Rect(0f, listBottom, size.width, size.height), WidgetTap.OpenToday, "$more more, open Today")
        }
        if (count == 0) {
            val empty = measurer.line(if (kind.wide) "All clear for today." else "All clear.", handStyle(note.font.family, 20f), right - left)
            drawText(empty, topLeft = Offset(left + (right - left - empty.size.width) / 2f, listTop + (bottom - listTop - empty.size.height) / 2f))
        }
        return areas
    }

    /** One line of text, cut off with "…" at [width] pixels. */
    private fun TextMeasurer.line(text: String, style: TextStyle, width: Float): TextLayoutResult =
        measure(text, style, TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = max(0, width.toInt())))

    /** The check button's ring: [size] across, its [stroke] inside that, as CSS borders are. */
    private fun DrawScope.drawHandCircle(topLeft: Offset, size: Float, stroke: Float, color: Color) {
        val ring = HandDrawnCircle.createOutline(Size(size - stroke, size - stroke), layoutDirection, this)
        translate(topLeft.x + stroke / 2, topLeft.y + stroke / 2) { drawOutline(ring, color, style = Stroke(stroke)) }
    }

    /** The "+" icon, [size] across: the mockup's two strokes on a 24-unit grid, 2.2 units thick. */
    private fun DrawScope.drawPlus(center: Offset, size: Float) {
        val unit = size / 24f
        val stroke = 2.2f * unit
        drawLine(StillColors.ink, center + Offset(0f, -7 * unit), center + Offset(0f, 7 * unit), stroke, StrokeCap.Round)
        drawLine(StillColors.ink, center + Offset(-7 * unit, 0f), center + Offset(7 * unit, 0f), stroke, StrokeCap.Round)
    }

    private fun DrawScope.drawTape(tape: Tape) {
        val topLeft = Offset(tape.left.dp.toPx(), (-8).dp.toPx())
        val size = Size(tape.width.dp.toPx(), 16.dp.toPx())
        rotate(tape.angle, pivot = topLeft + size.center) {
            drawRect(StillColors.card.copy(alpha = 0.6f), topLeft, size)
        }
    }

    private companion object {
        /** The smallest share of the mockup's size the note is drawn at. */
        const val MinScale = 0.75f
        const val MaxDensity = 3.5f

        /** Room around the note in the widget for the tape, the tilt and the shadow, in the mockup's dp. */
        const val SideMargin = 8f
        const val TopMargin = 12f
        const val BottomMargin = 14f

        val MockupHome = Color(0xFF3D4744)
        val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** The priority's ink, readable on canary. */
private val Priority.ink: Color
    get() = when (this) {
        Priority.High -> StillColors.inkHigh
        Priority.Medium -> StillColors.inkMed
        Priority.Low -> StillColors.inkLow
        Priority.None -> StillColors.ink
    }

/** "Mon 12 Oct" */
private fun LocalDate.short(): String =
    "${dayOfWeek.getDisplayName(DateStyle.SHORT, Locale.ENGLISH)} $dayOfMonth ${month.getDisplayName(DateStyle.SHORT, Locale.ENGLISH)}"
