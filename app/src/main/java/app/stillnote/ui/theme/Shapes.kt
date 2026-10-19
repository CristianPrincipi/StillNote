package app.stillnote.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** A sheet of paper whose bottom-left corner curls: that corner is an ellipse of [curlX] by [curlY]. */
class CurlShape(
    private val top: Dp,
    private val bottomEnd: Dp,
    private val curlX: Dp,
    private val curlY: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            Outline.Rounded(
                RoundRect(
                    rect = size.toRect(),
                    topLeft = CornerRadius(top.toPx()),
                    topRight = CornerRadius(top.toPx()),
                    bottomRight = CornerRadius(bottomEnd.toPx()),
                    bottomLeft = CornerRadius(curlX.toPx(), curlY.toPx()),
                )
            )
        }
}

val PostItShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 18.dp, curlY = 7.dp)
val NoteShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 26.dp, curlY = 9.dp)
val AddPadShape: Shape = CurlShape(top = 3.dp, bottomEnd = 3.dp, curlX = 13.dp, curlY = 6.dp)
val MiniPostItShape: Shape = CurlShape(top = 2.dp, bottomEnd = 2.dp, curlX = 12.dp, curlY = 5.dp)
val DayPostItShape: Shape = CurlShape(top = 2.dp, bottomEnd = 2.dp, curlX = 9.dp, curlY = 4.dp)
val MenuSlipShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 14.dp, curlY = 6.dp)
val PileSheetShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 22.dp, curlY = 8.dp)
val PreviewPostItShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 16.dp, curlY = 6.dp)
val PickerCardShape: Shape = CurlShape(top = 2.dp, bottomEnd = 3.dp, curlX = 22.dp, curlY = 8.dp)

/** The slightly irregular circle of check buttons, the today ring and radio buttons. */
val HandDrawnCircle: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val (w, h) = size
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = CornerRadius(0.54f * w, 0.47f * h),
                topRight = CornerRadius(0.46f * w, 0.55f * h),
                bottomRight = CornerRadius(0.52f * w, 0.45f * h),
                bottomLeft = CornerRadius(0.48f * w, 0.53f * h),
            )
        )
    }
}

/** Tilt in degrees for the task post-it at [index]. */
fun postItTilt(index: Int): Float = PostItTilts[index % PostItTilts.size]

/** Tilt in degrees for the mini post-it at [index] on a project card. */
fun miniPostItTilt(index: Int): Float = MiniPostItTilts[index % MiniPostItTilts.size]

/** Tilt in degrees for the project card at [index]. */
fun projectCardTilt(index: Int): Float = ProjectCardTilts[index % ProjectCardTilts.size]

private val PostItTilts = floatArrayOf(-0.7f, 0.5f, -0.3f, 0.8f, -0.5f)
private val MiniPostItTilts = floatArrayOf(-0.6f, 0.5f, -0.3f, 0.7f, -0.5f, 0.4f)
private val ProjectCardTilts = floatArrayOf(-0.6f, 0.5f, -0.4f, 0.6f)
