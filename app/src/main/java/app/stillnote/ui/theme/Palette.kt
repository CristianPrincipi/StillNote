package app.stillnote.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.stillnote.data.Palette

/**
 * The colours to draw with on whatever the content sits on: the page, or a sheet of paper.
 * Text, icons and rules take them from [LocalInk], so the same component reads on both.
 */
@Immutable
data class Ink(
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val inkDisabled: Color,
    val rule: Color,
    val ruleDashed: Color,
    val outlineDashed: Color,
    /** What the ink is on, for marks cut out of ink, such as a done tick. */
    val ground: Color,
    /** Text and icons on an ink fill: the main pill, a chosen pill. */
    val onInk: Color,
    /** The highlighter band under a title and the chosen tab's label. */
    val highlight: Color,
    /** The faint fill of a pill that isn't chosen. */
    val wash: Color,
    /** Errors and warnings in text. */
    val alert: Color,
)

/** The ink of paper, and of the Canary page: the tokens of design/DESIGN.md §2. */
val PaperInk = Ink(
    ink = StillColors.ink,
    ink2 = StillColors.ink2,
    ink3 = StillColors.ink3,
    inkDisabled = StillColors.inkDisabled,
    rule = StillColors.rule,
    ruleDashed = StillColors.ruleDashed,
    outlineDashed = StillColors.outlineDashed,
    ground = StillColors.paper,
    onInk = StillColors.canary,
    highlight = StillColors.canary,
    wash = StillColors.card.copy(alpha = 0.45f),
    alert = StillColors.inkHigh,
)

private val NightGround = Color(0xFF26231F)

/**
 * Light ink for the Night page. Not in the design, which only gives the page and its dots:
 * the paper colour for text, stepping down towards the page for the quieter tones.
 */
val NightInk = Ink(
    ink = Color(0xFFEFE8DC),
    ink2 = Color(0xFFC9C0B3),
    ink3 = Color(0xFF9C9388),
    inkDisabled = Color(0xFF6B6359),
    rule = Color(0xFF48423A),
    ruleDashed = Color(0xFF544D44),
    outlineDashed = Color(0xFF9A8F82),
    ground = NightGround,
    onInk = NightGround,
    // A full canary band would glare behind light text; a highlighter on dark paper shows less.
    highlight = StillColors.canary.copy(alpha = 0.35f),
    wash = Color.Transparent,
    alert = Color(0xFFF2A285),
)

/** The page under the screens: its colour, its dots, and the ink on it. */
@Immutable
data class Page(val ground: Color, val dot: Color, val ink: Ink, val dark: Boolean)

/** Night is the dark mode. Only the page goes dark; post-its, cards and sheets keep their colours and dark ink. */
val Palette.page: Page
    get() = when (this) {
        Palette.Canary -> Page(StillColors.paper, StillColors.dot, PaperInk, dark = false)
        Palette.Night -> Page(NightGround, Color(0xFF3D3832), NightInk, dark = true)
    }

val LocalPage = staticCompositionLocalOf { Palette.Canary.page }
val LocalInk = staticCompositionLocalOf { PaperInk }

/** Draws [content] with the dark ink of paper: whatever is on a post-it, card, note or sheet. */
@Composable
fun OnPaper(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInk provides PaperInk, content = content)
}
