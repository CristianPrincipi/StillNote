package app.stillnote.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import app.stillnote.ui.theme.StillColors
import kotlin.math.ceil
import kotlin.math.max

/** One CSS `box-shadow` layer: offset, blur radius, spread and colour. */
data class PaperShadow(
    val color: Color,
    val dx: Dp = 0.dp,
    val dy: Dp = 0.dp,
    val blur: Dp = 0.dp,
    val spread: Dp = 0.dp,
)

object Shadows {
    /** A tight contact shadow plus a soft lift under the bottom edge. */
    val postIt = listOf(
        PaperShadow(StillColors.ink.copy(alpha = 0.07f), dy = 1.dp, blur = 1.dp),
        PaperShadow(StillColors.ink.copy(alpha = 0.55f), dy = 12.dp, blur = 14.dp, spread = (-12).dp),
    )
    val note = listOf(
        PaperShadow(StillColors.ink.copy(alpha = 0.10f), dy = 2.dp, blur = 2.dp),
        PaperShadow(Color(0xFF14100C).copy(alpha = 0.75f), dy = 34.dp, blur = 40.dp, spread = (-26).dp),
    )
    val miniPostIt = listOf(
        PaperShadow(StillColors.ink.copy(alpha = 0.6f), dy = 9.dp, blur = 10.dp, spread = (-8).dp),
    )

    /** The second sheet behind the "+" pad, then its lift. */
    val addPad = listOf(
        PaperShadow(StillColors.canary, dx = 4.dp, dy = 4.dp, spread = (-1.6).dp),
        PaperShadow(StillColors.ink, dx = 4.dp, dy = 4.dp),
        PaperShadow(StillColors.ink.copy(alpha = 0.55f), dy = 18.dp, blur = 20.dp, spread = (-12).dp),
    )
    val swatch = listOf(
        PaperShadow(Color.Black.copy(alpha = 0.45f), dy = 6.dp, blur = 6.dp, spread = (-5).dp),
    )
}

/**
 * Draws [shadows] behind the content like CSS `box-shadow`: the first layer is on top.
 *
 * Blurred layers are painted once into an alpha bitmap, because blur mask filters are not
 * reliable on hardware canvases before API 28.
 */
fun Modifier.paperShadow(shape: Shape, shadows: List<PaperShadow>): Modifier = drawWithCache {
    val layers = shadows.map { shadowLayer(it, shape) }
    onDrawBehind {
        for (i in layers.indices.reversed()) layers[i]()
    }
}

private fun CacheDrawScope.shadowLayer(shadow: PaperShadow, shape: Shape): DrawScope.() -> Unit {
    val spread = shadow.spread.toPx()
    val width = size.width + 2 * spread
    val height = size.height + 2 * spread
    if (width <= 0f || height <= 0f) return {}

    val path = Path().apply { addOutline(shape.createOutline(Size(width, height), layoutDirection, this@shadowLayer)) }
    val left = shadow.dx.toPx() - spread
    val top = shadow.dy.toPx() - spread
    val blur = shadow.blur.toPx()
    if (blur <= 0f) return { translate(left, top) { drawPath(path, shadow.color) } }

    // A CSS blur radius is twice the Gaussian sigma; BlurMaskFilter's sigma is 0.57735 * radius + 0.5.
    val sigma = blur / 2f
    val pad = ceil(sigma * 3f).toInt() + 1
    val bitmap = createBitmap(ceil(width).toInt() + 2 * pad, ceil(height).toInt() + 2 * pad, Bitmap.Config.ALPHA_8)
    val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(max((sigma - 0.5f) / 0.57735f, 0.1f), BlurMaskFilter.Blur.NORMAL)
    }
    android.graphics.Canvas(bitmap).apply {
        translate(pad.toFloat(), pad.toFloat())
        drawPath(path.asAndroidPath(), blurPaint)
    }
    val tint = Paint().apply { color = shadow.color.toArgb() }
    return { drawIntoCanvas { it.nativeCanvas.drawBitmap(bitmap, left - pad, top - pad, tint) } }
}
