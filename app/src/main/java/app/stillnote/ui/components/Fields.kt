package app.stillnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.data.Palette
import app.stillnote.ui.theme.LocalInk
import app.stillnote.ui.theme.OnPaper
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.monoStyle

/**
 * The box the Notion secret is typed or pasted into: masked, with an eye to show it.
 * With [onPaste], it also has a "PASTE" button, as in setup.
 */
@Composable
fun TokenField(
    token: String,
    visible: Boolean,
    onChange: (String) -> Unit,
    onToggleVisible: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    onPaste: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(3.dp)
    // A sheet of card on the page, with the dark ink of paper on the Night page too.
    OnPaper {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .background(StillColors.card, shape)
                .border(1.4.dp, StillColors.ink, shape)
                .padding(start = 15.4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = token,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = label },
                textStyle = monoStyle(14f, color = StillColors.ink, letterSpacing = 0.04.em),
                singleLine = true,
                cursorBrush = SolidColor(StillColors.ink),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (token.isEmpty()) MonoText("ntn_…", 14f, color = StillColors.ink3, letterSpacing = 0.04.em)
                        field()
                    }
                },
            )
            if (onPaste != null) {
                LabelButton("Paste", onPaste, Modifier.padding(horizontal = 10.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(46.dp)
                        .background(StillColors.ink.copy(alpha = 0.18f)),
                )
            }
            TapTarget(
                onClick = onToggleVisible,
                description = if (visible) "Hide secret" else "Show secret",
                modifier = Modifier
                    .offset(x = if (onPaste == null) (-0.4).dp else 0.dp)
                    .size(if (onPaste == null) TapSize else 44.dp, TapSize),
            ) {
                InkIcon(StillIcons.Eye, 20.dp)
            }
        }
    }
}

/** A swatch's drawing: the page's colour and the two most visible note colours (DESIGN.md §2). */
private class Swatch(val palette: Palette, val ground: Color, val noteA: Color, val noteB: Color)

private val Swatches = listOf(
    Swatch(Palette.Canary, Color(0xFFF3EEE4), Color(0xFFF7E08C), Color(0xFFF4B795)),
    Swatch(Palette.Night, Color(0xFF26231F), Color(0xFFF7E08C), Color(0xFF8FB3C9)),
)

/**
 * The palette tiles: Canary, and Night for the dark mode. The tiles keep the size the
 * mockups give four of, so two leave the rest of the row empty. [compact] is the smaller tile used in setup.
 */
@Composable
fun PaletteSwatches(selected: Palette?, onPick: (Palette) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Swatches.forEach { swatch ->
            PaletteSwatch(swatch, selected = swatch.palette == selected, onPick = { onPick(swatch.palette) }, compact, Modifier.weight(1f))
        }
        repeat(4 - Swatches.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun PaletteSwatch(swatch: Swatch, selected: Boolean, onPick: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val border = if (selected) 2.dp else 1.2.dp
    val note = if (compact) 26.dp else 30.dp
    val ink = LocalInk.current
    Column(
        modifier.selectable(
            selected = selected,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.RadioButton,
            onClick = onPick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (compact) 62.dp else 74.dp)
                .clip(shape)
                .background(swatch.ground)
                .border(border, if (selected) ink.ink else ink.ink.copy(alpha = 0.3f), shape),
        ) {
            MiniNote(swatch.noteA, note, -6f, Modifier.offset(border + 12.dp, border + if (compact) 10.dp else 12.dp))
            MiniNote(swatch.noteB, note, 5f, Modifier.offset(border + if (compact) 30.dp else 32.dp, border + if (compact) 24.dp else 28.dp))
        }
        // The chosen name sits on canary tape, in the dark ink of paper.
        HandText(
            swatch.palette.name,
            17f,
            Modifier
                .background(if (selected) StillColors.canary else Color.Transparent)
                .padding(horizontal = 5.dp),
            color = if (selected) StillColors.ink else ink.ink,
        )
    }
}

@Composable
private fun MiniNote(color: Color, size: Dp, tilt: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .rotate(tilt)
            .paperShadow(RectangleShape, Shadows.swatch)
            .background(color),
    )
}
