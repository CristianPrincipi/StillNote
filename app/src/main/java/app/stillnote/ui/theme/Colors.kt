package app.stillnote.ui.theme

import androidx.compose.ui.graphics.Color
import app.stillnote.data.Priority

/** The colour tokens from design/DESIGN.md §2. */
object StillColors {
    val paper = Color(0xFFF3EEE4)
    val dot = Color(0xFFD8CEBD)
    val ink = Color(0xFF2A2621)
    val ink2 = Color(0xFF4F463D)
    val ink3 = Color(0xFF6A6056)
    val inkDisabled = Color(0xFFA39888)
    val rule = Color(0xFFD9CFBD)
    val ruleDashed = Color(0xFFCDC2AF)
    val outlineDashed = Color(0xFF8A7F72)
    val card = Color(0xFFFCFAF5)
    val canary = Color(0xFFF7E08C)
    val apricot = Color(0xFFF4B795)
    val sky = Color(0xFFCFE1EA)
    val indexRed = Color(0xFFE39C86)
    val indexBlue = Color(0xFFD3E2EA)
    val scrim = ink.copy(alpha = 0.42f)
    val scrimLight = ink.copy(alpha = 0.32f)
    val tape = card.copy(alpha = 0.58f)

    // Darker versions of the post-it colours, readable as text on canary (widget rows).
    val inkHigh = Color(0xFFA63D22)
    val inkMed = Color(0xFF7A5A00)
    val inkLow = Color(0xFF2C5F80)
}

/** The post-it colour for a priority. */
val Priority.paper: Color
    get() = when (this) {
        Priority.High -> StillColors.apricot
        Priority.Medium -> StillColors.canary
        Priority.Low -> StillColors.sky
        Priority.None -> StillColors.card
    }
