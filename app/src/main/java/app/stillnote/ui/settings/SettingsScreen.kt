package app.stillnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.HandFont
import app.stillnote.ui.components.DoneMark
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.label
import app.stillnote.ui.theme.monoStyle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val handFont by viewModel.handFont.collectAsStateWithLifecycle()
    PaperPage(modifier) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 23.dp, end = 24.dp, bottom = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapTarget(onBack, "Back to tasks", Modifier.size(TapSize)) {
                InkIcon(StillIcons.Back, 22.dp, strokeWidth = 1.8f)
            }
            ScreenTitle("Settings", size = 40f)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            NotionSection(viewModel)
            PaletteSection()
            TypographySection(handFont, viewModel::setHandFont)
        }
    }
}

@Composable
private fun NotionSection(viewModel: SettingsViewModel) {
    val chosenId by viewModel.chosenDatabaseId.collectAsStateWithLifecycle()
    val status = viewModel.status
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Notion")
        HandText("Integration token", 19f)
        TokenField(viewModel.token, viewModel.tokenVisible, viewModel::updateToken, viewModel::toggleTokenVisible)
        MonoText(
            "Create an internal integration at notion.so/my-integrations, then share your tasks database with it.",
            11.5f,
            lineHeight = 1.55f,
        )
        if (viewModel.databases.isNotEmpty()) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Overline("Database", Modifier.padding(bottom = 4.dp))
                viewModel.databases.forEach { database ->
                    ChoiceRow(selected = database.id == chosenId, onPick = { viewModel.choose(database) }, minHeight = 52.dp) {
                        HandText(database.name, 19f, maxLines = 1)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status is NotionStatus.Connected) {
                    DoneMark(16.dp, tickSize = 10.dp, tickStroke = 3.4f)
                } else {
                    HandCircle(16.dp, stroke = 1.5.dp)
                }
                MonoText(status.text(), 12f, color = StillColors.ink)
            }
            // The pill is 44 tall, as in the mockup; its 48 dp touch target overflows the row.
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                TapTarget(viewModel::save, description = null, Modifier.requiredHeight(TapSize)) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .background(StillColors.ink, CircleShape)
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MonoText("Save", 13f, weight = FontWeight.Medium, color = StillColors.canary, letterSpacing = 0.08.em)
                    }
                }
            }
        }
    }
}

private fun NotionStatus.text(): String = when (this) {
    NotionStatus.NotConnected -> "Not connected"
    NotionStatus.Checking -> "Checking…"
    NotionStatus.TokenRejected -> "Notion rejected this token"
    NotionStatus.Unreachable -> "Couldn't reach Notion"
    NotionStatus.NoDatabases -> "No database is shared with this token"
    NotionStatus.PickDatabase -> "Pick a database"
    is NotionStatus.Unusable -> "“$name” needs ${missing.joinToString(" and ")}"
    is NotionStatus.Connected -> "Connected to “$name”"
}

@Composable
private fun TokenField(token: String, visible: Boolean, onChange: (String) -> Unit, onToggleVisible: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
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
                .semantics { contentDescription = "Integration token" },
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
        TapTarget(
            onClick = onToggleVisible,
            description = if (visible) "Hide token" else "Show token",
            modifier = Modifier
                .offset(x = (-0.4).dp)
                .size(TapSize),
        ) {
            InkIcon(StillIcons.Eye, 20.dp)
        }
    }
}

private class Palette(val name: String, val ground: Color, val noteA: Color, val noteB: Color)

private val Palettes = listOf(
    Palette("Canary", Color(0xFFF3EEE4), Color(0xFFF7E08C), Color(0xFFF4B795)),
    Palette("Sage", Color(0xFFEEF0E6), Color(0xFFCFE3C4), Color(0xFFF1D9A0)),
    Palette("Blush", Color(0xFFF6EEEB), Color(0xFFF4C8C1), Color(0xFFE3D5EC)),
    Palette("Night", Color(0xFF26231F), Color(0xFFF7E08C), Color(0xFF8FB3C9)),
)

/** Drawn as in the mockup but not wired up yet: the palettes are on hold, so Canary stays selected. */
@Composable
private fun PaletteSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("Color palette")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.forEachIndexed { index, palette ->
                PaletteSwatch(palette, selected = index == 0, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteSwatch(palette: Palette, selected: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val border = if (selected) 2.dp else 1.2.dp
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(shape)
                .background(palette.ground)
                .border(border, if (selected) StillColors.ink else StillColors.ink.copy(alpha = 0.3f), shape),
        ) {
            MiniNote(palette.noteA, -6f, Modifier.offset(border + 12.dp, border + 12.dp))
            MiniNote(palette.noteB, 5f, Modifier.offset(border + 32.dp, border + 28.dp))
        }
        HandText(
            palette.name,
            17f,
            Modifier
                .background(if (selected) StillColors.canary else Color.Transparent)
                .padding(horizontal = 5.dp),
        )
    }
}

@Composable
private fun MiniNote(color: Color, tilt: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .rotate(tilt)
            .paperShadow(RectangleShape, Shadows.swatch)
            .background(color),
    )
}

@Composable
private fun TypographySection(current: HandFont?, onPick: (HandFont) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline("Typography", Modifier.padding(bottom = 4.dp))
        HandFont.entries.forEach { font ->
            FontOption(font, selected = font == current, onPick = { onPick(font) })
        }
    }
}

@Composable
private fun FontOption(font: HandFont, selected: Boolean, onPick: () -> Unit) {
    ChoiceRow(selected, onPick, minHeight = 66.dp) {
        val sample = handStyle(font.family, 21f, lineHeight = 1.25f)
        BasicText("Water the plants", Modifier.trimLineBox(rememberLineBoxTrim(sample)), style = sample)
        MonoText(font.label, 11f, letterSpacing = 0.08.em)
    }
}

/** One of a set of choices: the chosen one becomes a sheet of card with a filled radio button. */
@Composable
private fun ChoiceRow(selected: Boolean, onPick: () -> Unit, minHeight: Dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    val sheet = if (!selected) Modifier else Modifier
        .paperShadow(shape, Shadows.postIt)
        .background(StillColors.card, shape)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(sheet)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onPick,
            )
            .heightIn(min = minHeight)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        HandCircle(22.dp) {
            if (selected) Box(Modifier.size(10.dp).background(StillColors.ink, CircleShape))
        }
    }
}
