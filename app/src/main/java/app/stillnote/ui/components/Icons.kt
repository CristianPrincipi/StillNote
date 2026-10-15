package app.stillnote.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stillnote.ui.theme.LocalInk

/** A pen-line icon: SVG path data on a 24×24 grid, stroked with round caps and joins. */
class IconPaths(private val name: String, private vararg val paths: String) {
    private val vectors = HashMap<Float, ImageVector>()

    /** [strokeWidth] is in grid units, as in the mockup SVGs. */
    fun vector(strokeWidth: Float): ImageVector = vectors.getOrPut(strokeWidth) {
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
}

@Composable
fun InkIcon(
    icon: IconPaths,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalInk.current.ink,
    strokeWidth: Float = 1.6f,
) {
    Image(
        painter = rememberVectorPainter(icon.vector(strokeWidth)),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

object StillIcons {
    val Settings = IconPaths(
        "settings",
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73" +
            "l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73" +
            "l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44" +
            "a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39" +
            "a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73" +
            "l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        circle(12f, 12f, 3f),
    )
    val Trash = IconPaths(
        "trash",
        "M4 7h16",
        "M10 11v6",
        "M14 11v6",
        "M6 7l1 12.2A2 2 0 0 0 9 21h6a2 2 0 0 0 2-1.8L18 7",
        "M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7",
    )
    val Add = IconPaths("add", "M12 5v14", "M5 12h14")
    val Clock = IconPaths("clock", circle(12f, 12f, 8.6f), "M12 7.2V12l3.2 2")
    val Flag = IconPaths("priority", "M5.5 21V3.8", "M5.5 4h12l-2.6 4.4 2.6 4.4h-12")
    val Send = IconPaths("send", "M5 12h14", "M13 6l6 6-6 6")
    val Check = IconPaths("check", "M5 12.5l4.5 4.5L19 7.5")
    val Calendar = IconPaths("calendar", roundRect(3.5f, 5f, 17f, 15.5f, 1.5f), "M3.5 10h17", "M8 3v4", "M16 3v4")
    val Tasks = IconPaths("tasks", "M5 4h14v10.5L13.5 20H5z", "M13.5 20v-5.5H19")
    val Projects = IconPaths("projects", roundRect(3.5f, 8.5f, 13f, 12f, 1f), "M7.5 8.5V4.5h13v11.5h-4")
    val ChevronLeft = IconPaths("chevron left", "M15 5.5L8.5 12l6.5 6.5")
    val ChevronRight = IconPaths("chevron right", "M9 5.5l6.5 6.5L9 18.5")
    val ChevronDown = IconPaths("chevron down", "M6 9l6 6 6-6")
    val Back = IconPaths("back", "M19 12H5", "M11 6l-6 6 6 6")
    val Refresh = IconPaths(
        "refresh",
        "M20 11a8 8 0 0 0-14.5-4.5L4 8",
        "M4 4v4h4",
        "M4 13a8 8 0 0 0 14.5 4.5L20 16",
        "M20 20v-4h-4",
    )
    val ExternalLink = IconPaths("external link", "M7 17L17 7", "M8 7h9v9")
    val Eye = IconPaths("show token", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f))
}
