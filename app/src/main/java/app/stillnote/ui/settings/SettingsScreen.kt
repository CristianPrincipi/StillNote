package app.stillnote.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.HandFont
import app.stillnote.data.Reminder
import app.stillnote.reminders.notificationsAllowed
import app.stillnote.ui.components.DoneMark
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.PaletteSwatches
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.Pill
import app.stillnote.ui.components.RadioRow
import app.stillnote.ui.components.ScreenTitle
import app.stillnote.ui.components.Stepper
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.TextLink
import app.stillnote.ui.components.TokenField
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.LocalInk
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
            PaletteSection(viewModel)
            TypographySection(handFont, viewModel::setHandFont)
            RemindersSection(viewModel)
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
            MonoText(text, 12f, color = LocalInk.current.ink)
        }
        // The pill is 44 tall, as in the mockup; its 48 dp touch target overflows the row.
        Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
            TapTarget(onAction, description = null, Modifier.requiredHeight(TapSize)) {
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .background(LocalInk.current.ink, CircleShape)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MonoText(action, 13f, weight = FontWeight.Medium, color = LocalInk.current.onInk, letterSpacing = 0.08.em)
                }
            }
        }
    }
}

/** Canary, or Night for the dark mode. The mockup's Sage and Blush were dropped. */
@Composable
private fun PaletteSection(viewModel: SettingsViewModel) {
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline("Color palette")
        PaletteSwatches(palette, viewModel::setPalette)
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
        val sample = handStyle(font.family, 21f, color = LocalInk.current.ink, lineHeight = 1.25f)
        BasicText("Water the plants", Modifier.trimLineBox(rememberLineBoxTrim(sample)), style = sample)
        MonoText(font.label, 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.08.em)
    }
}

/**
 * Not in the mockups. How long before a task's time to remind, as pills like the note's
 * projects; the morning list's time on a stepper like the note's clock; and a way to let
 * Android show them, while it won't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(viewModel: SettingsViewModel) {
    val rules = viewModel.reminders.collectAsStateWithLifecycle().value ?: return
    val asked by viewModel.notificationsAsked.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    // Back from Android's settings page, the answer may have changed.
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = notificationsAllowed(context) }
    val canAsk = Build.VERSION.SDK_INT >= 33 && !asked
    fun ask() {
        if (Build.VERSION.SDK_INT < 33) return
        viewModel.markNotificationsAsked()
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // Choosing to be reminded is the moment to ask, the first time.
    fun askIfNeeded() {
        if (!allowed && canAsk) ask()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline("Reminders")
        HandText("Before a task's time", 19f)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Off", selected = rules.before == Reminder.None) { viewModel.setReminderBefore(Reminder.None) }
            Reminder.choices.forEach { minutes ->
                Pill("$minutes min", selected = rules.before == minutes) {
                    viewModel.setReminderBefore(minutes)
                    askIfNeeded()
                }
            }
        }
        HandText("Morning list", 19f, Modifier.padding(top = 12.dp))
        MonoText("Today's tasks with no time, in one notification.", 11.5f, lineHeight = 1.55f)
        val step = MorningSteps.indexOf(rules.morningAt).coerceAtLeast(0)
        Stepper(
            onPrevious = { viewModel.setMorningList(MorningSteps[step - 1]) },
            previousLabel = "Earlier",
            onNext = {
                viewModel.setMorningList(MorningSteps[step + 1])
                askIfNeeded()
            },
            nextLabel = "Later",
            previousEnabled = step > 0,
            nextEnabled = step < MorningSteps.lastIndex,
        ) {
            val at = rules.morningAt
            MonoText(
                at?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Off",
                30f,
                weight = FontWeight.Medium,
                color = LocalInk.current.ink,
                letterSpacing = 0.04.em,
                lineHeight = 1.1f,
            )
            MonoText(if (at == null) " " else "EVERY DAY", 11f, Modifier.padding(top = 2.dp), letterSpacing = 0.16.em)
        }
        if (!allowed && (rules.before != Reminder.None || rules.morningAt != null)) {
            MonoText("Notifications are off for StillNote, so reminders can't show.", 12f, color = LocalInk.current.alert, lineHeight = 1.5f)
            TextLink(
                "Turn them on",
                onClick = {
                    if (canAsk) ask()
                    else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                },
            )
        }
    }
}

/** The morning list's times: off, then 05:00 to 12:00 every half hour. */
private val MorningSteps: List<Int?> = listOf(null) + (5 * 60..12 * 60 step 30).toList()
