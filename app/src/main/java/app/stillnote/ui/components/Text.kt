package app.stillnote.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import kotlin.math.roundToInt
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.monoStyle

/**
 * Compose never makes the first and last lines shorter than the font's own height, so a
 * handwritten face with tall metrics takes more room than its CSS line box. This is the
 * difference in pixels, for [trimLineBox] to take off the top and off the bottom.
 */
@Composable
fun rememberLineBoxTrim(style: TextStyle): Int {
    if (style.lineHeight.isUnspecified) return 0
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val fontHeight = measurer.measure("x", style.copy(lineHeight = TextUnit.Unspecified)).size.height
        val lineHeight = with(density) { style.lineHeight.toPx() }
        ((fontHeight - lineHeight) / 2f).roundToInt().coerceAtLeast(0)
    }
}

/** Shrinks text to its CSS line box; the glyphs still draw outside it, as in CSS. */
fun Modifier.trimLineBox(trim: Int): Modifier = if (trim == 0) this else layout { measurable, constraints ->
    val text = measurable.measure(constraints)
    layout(text.width, (text.height - 2 * trim).coerceAtLeast(0)) { text.place(0, -trim) }
}

/** The user's words, in the handwritten face. [size] is in sp. */
@Composable
fun HandText(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = StillColors.ink,
    lineHeight: Float = 1.2f,
    decoration: TextDecoration? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = handStyle(LocalHandFont.current, size, weight, color, lineHeight).copy(textDecoration = decoration)
    BasicText(
        text = text,
        modifier = modifier.trimLineBox(rememberLineBoxTrim(style)),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/** The system's words (dates, counts, labels), in the mono face. [size] is in sp. */
@Composable
fun MonoText(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    color: Color = StillColors.ink2,
    letterSpacing: TextUnit = 0.em,
    lineHeight: Float? = null,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    val style = monoStyle(size, weight, color, letterSpacing, lineHeight).copy(textAlign = textAlign)
    BasicText(
        text = text,
        modifier = modifier.trimLineBox(rememberLineBoxTrim(style)),
        style = style,
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/** The small uppercase label above a title or section. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, size: Float = 11f, weight: FontWeight = FontWeight.Medium) {
    MonoText(text.uppercase(), size, modifier, weight = weight, letterSpacing = 0.16.em, maxLines = 1)
}

/**
 * A 46 sp screen title. With [highlighted], it sits on a canary highlighter band covering
 * 58–90% of the font's own height, reaching 6 dp past the text on each side. The setup titles
 * are the only ones the mockups don't tighten, so they pass a [letterSpacing] of zero.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    size: Float = 46f,
    highlighted: Boolean = false,
    letterSpacing: TextUnit = (-0.01).em,
) {
    val style = handStyle(LocalHandFont.current, size, FontWeight.Bold, lineHeight = 1.15f)
        .copy(letterSpacing = letterSpacing)
    val measurer = rememberTextMeasurer()
    val band = if (!highlighted) Modifier else Modifier.drawWithCache {
        val fontHeight = measurer.measure("x", style.copy(lineHeight = TextUnit.Unspecified)).size.height
        val fontTop = (this.size.height - fontHeight) / 2f
        val reach = 6.dp.toPx()
        onDrawBehind {
            drawRect(
                color = StillColors.canary,
                topLeft = Offset(-reach, fontTop + 0.58f * fontHeight),
                size = Size(this.size.width + 2 * reach, 0.32f * fontHeight),
            )
        }
    }
    BasicText(
        text = text,
        modifier = modifier.then(band).trimLineBox(rememberLineBoxTrim(style)),
        style = style,
        overflow = TextOverflow.Visible,
        softWrap = false,
        maxLines = 1,
    )
}

/** Handwritten text struck through with a 1.5 dp line, for completed tasks. */
@Composable
fun StruckHandText(
    text: String,
    size: Float,
    modifier: Modifier = Modifier,
    color: Color = StillColors.ink3,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val style = handStyle(LocalHandFont.current, size, color = color)
    BasicText(
        text = text,
        modifier = modifier.trimLineBox(rememberLineBoxTrim(style)).drawWithContent {
            drawContent()
            val lines = layout ?: return@drawWithContent
            for (line in 0 until lines.lineCount) {
                // The middle of the line box sits close to half the x-height.
                val y = (lines.getLineTop(line) + lines.getLineBottom(line)) / 2f
                drawLine(color, Offset(lines.getLineLeft(line), y), Offset(lines.getLineRight(line), y), 1.5.dp.toPx())
            }
        },
        style = style,
        onTextLayout = { layout = it },
    )
}
