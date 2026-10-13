package app.stillnote.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.stillnote.R
import app.stillnote.data.HandFont

val KalamFamily = FontFamily(
    Font(R.font.kalam_regular, FontWeight.Normal),
    Font(R.font.kalam_bold, FontWeight.Bold),
)

val CourierPrimeFamily = FontFamily(
    Font(R.font.courier_prime_regular, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
)

val NewsreaderFamily = FontFamily(
    Font(
        R.font.newsreader_variable,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.Setting("opsz", 24f)),
    ),
    Font(
        R.font.newsreader_variable,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("opsz", 44f)),
    ),
)

val DmMonoFamily = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

val HandFont.family: FontFamily
    get() = when (this) {
        HandFont.Handwritten -> KalamFamily
        HandFont.Typewriter -> CourierPrimeFamily
        HandFont.Bookish -> NewsreaderFamily
    }

val HandFont.label: String
    get() = when (this) {
        HandFont.Handwritten -> "Handwritten · Kalam"
        HandFont.Typewriter -> "Typewriter · Courier Prime"
        HandFont.Bookish -> "Bookish · Newsreader"
    }

/** The face used for the user's own words; Settings → Typography swaps it. */
val LocalHandFont = staticCompositionLocalOf<FontFamily> { KalamFamily }

// CSS centres the glyphs in the line box, so every explicit line height does the same.
private val CssLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

// Static text rounds each glyph advance to a whole pixel, which visibly tightens letter-spaced
// mono labels at low densities. This keeps the advances fractional, as the mockups have them.
private val UnroundedAdvances = TextMotion.Animated

/** [size] is in sp; [lineHeight] is the CSS multiplier. */
fun handStyle(
    family: FontFamily,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = StillColors.ink,
    lineHeight: Float = 1.2f,
): TextStyle = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = (size * lineHeight).sp,
    lineHeightStyle = CssLineHeight,
    textMotion = UnroundedAdvances,
)

/** [lineHeight] is the CSS multiplier; null keeps the font's own line height. */
fun monoStyle(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = StillColors.ink2,
    letterSpacing: TextUnit = 0.em,
    lineHeight: Float? = null,
): TextStyle = TextStyle(
    fontFamily = DmMonoFamily,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    lineHeightStyle = CssLineHeight,
    textMotion = UnroundedAdvances,
)
