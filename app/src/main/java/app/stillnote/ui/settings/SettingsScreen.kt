package app.stillnote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import app.stillnote.ui.components.TokenField
import app.stillnote.ui.components.TextLink
import app.stillnote.ui.components.RadioRow
import app.stillnote.ui.components.PaletteSwatches
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.label

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onConnectNotion: () -> Unit, modifier: Modifier = Modifier) {
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
            NotionSection(viewModel, onConnectNotion)
            PaletteSection()
            TypographySection(handFont, viewModel::setHandFont)
        }
    }
}

@Composable
private fun NotionSection(viewModel: SettingsViewModel, onConnectNotion: () -> Unit) {
    val database by viewModel.database.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Notion")
        val connected = database
        if (connected == null) {
            // Not in the mockups: without Notion there is no token to show, only a way to connect.
            StatusRow(connected = false, text = "Not connected", action = "Connect", onAction = onConnectNotion)
            return@Column
        }
        HandText("Integration token", 19f)
        TokenField(viewModel.token, viewModel.tokenVisible, viewModel::updateToken, viewModel::toggleTokenVisible, label = "Integration token")
        MonoText(
            "Create an internal integration at notion.so/my-integrations, then share your tasks database with it.",
            11.5f,
            lineHeight = 1.55f,
        )
        val problem = viewModel.problem
        StatusRow(
            connected = problem == null,
            text = when (problem) {
                null -> "Connected to “$connected”"
                TokenProblem.Checking -> "Checking…"
                TokenProblem.Rejected -> "Notion rejected this token"
                TokenProblem.Unreachable -> "Couldn't reach Notion"
            },
            action = "Save",
            onAction = viewModel::save,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Not in the mockups: the way back to setup's two Notion steps.
        TextLink("Change database or fields", onConnectNotion)
    }
}

/** A hand-drawn mark and a line of status on the left, an ink pill on the right. */
@Composable
private fun StatusRow(connected: Boolean, text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connected) DoneMark(16.dp, tickSize = 10.dp, tickStroke = 3.4f) else HandCircle(16.dp, stroke = 1.5.dp)
            MonoText(text, 12f, color = StillColors.ink)
        }
        // The pill is 44 tall, as in the mockup; its 48 dp touch target overflows the row.
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            TapTarget(onAction, description = null, Modifier.requiredHeight(TapSize)) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(StillColors.ink, CircleShape)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoText(action, 13f, weight = FontWeight.Medium, color = StillColors.canary, letterSpacing = 0.08.em)
                }
            }
        }
    }
}

/** Drawn as in the mockup but not wired up yet: the palettes are on hold, so Canary stays selected. */
@Composable
private fun PaletteSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("Color palette")
        PaletteSwatches()
    }
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
    RadioRow(selected, onPick, minHeight = 66.dp, verticalPadding = 8.dp) {
        val sample = handStyle(font.family, 21f, lineHeight = 1.25f)
        BasicText("Water the plants", Modifier.trimLineBox(rememberLineBoxTrim(sample)), style = sample)
        MonoText(font.label, 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.08.em)
    }
}
