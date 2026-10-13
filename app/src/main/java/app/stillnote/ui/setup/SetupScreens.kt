package app.stillnote.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stillnote.data.HandFont
import app.stillnote.data.notion.Field
import app.stillnote.ui.components.BackLink
import app.stillnote.ui.components.HandCircle
import app.stillnote.ui.components.HandText
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.LabelButton
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.OutlinePill
import app.stillnote.ui.components.Overline
import app.stillnote.ui.components.PaletteSwatches
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.PrimaryPill
import app.stillnote.ui.components.RadioRow
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StepHeader
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapSize
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.Tape
import app.stillnote.ui.components.TextLink
import app.stillnote.ui.components.TokenField
import app.stillnote.ui.components.cardHeader
import app.stillnote.ui.components.dottedPaper
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.ruledPaper
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.NoteShape
import app.stillnote.ui.theme.PileSheetShape
import app.stillnote.ui.theme.PostItShape
import app.stillnote.ui.theme.PreviewPostItShape
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.family
import app.stillnote.ui.theme.fontName
import app.stillnote.ui.theme.handStyle
import app.stillnote.ui.theme.title
import kotlinx.coroutines.launch

private const val INTEGRATIONS_URL = "https://www.notion.so/my-integrations"

/** The first-run flow, or just its two Notion steps when opened from Settings. [onDone] leaves it. */
@Composable
fun SetupFlow(viewModel: SetupViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(viewModel.finished) { if (viewModel.finished) onDone() }
    BackHandler(enabled = viewModel.step != SetupStep.Welcome) { viewModel.back() }

    Box(modifier.fillMaxSize()) {
        when (viewModel.step) {
            SetupStep.Welcome -> WelcomeScreen(viewModel::connectNotion, viewModel::useWithoutNotion)
            SetupStep.Token -> ConnectNotionScreen(viewModel)
            SetupStep.Database -> PickDatabaseScreen(viewModel)
            SetupStep.Look -> ChooseLookScreen(viewModel)
        }
        viewModel.picking?.let { FieldPickerSheet(it, viewModel) }
    }
}

/** Keeps a step's bottom row above the navigation bar and the keyboard. */
private fun Modifier.bottomInset(): Modifier = navigationBarsPadding().imePadding()

@Composable
private fun WelcomeScreen(onConnect: () -> Unit, onSkip: () -> Unit) {
    PaperPage {
        // The pile is drawn for a 390 dp wide page; it shrinks to fit a short screen.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val fit = (maxHeight / 430.dp).coerceAtMost(1f)
            Box(
                Modifier
                    .size(390.dp, 430.dp)
                    .graphicsLayer {
                        scaleX = fit
                        scaleY = fit
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
            ) {
                PileSheet(StillColors.sky, 9f, Modifier.offset(142.dp, 128.dp))
                PileSheet(StillColors.apricot, -8f, Modifier.offset(50.dp, 156.dp))
                Box(
                    modifier = Modifier
                        .offset(82.dp, 188.dp)
                        .size(226.dp)
                        .rotate(-1.5f)
                        .paperShadow(NoteShape, Shadows.pileFront)
                        .background(StillColors.canary, NoteShape),
                ) {
                    HandText(
                        "Hello.",
                        66f,
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 28.dp),
                        weight = FontWeight.Bold,
                        lineHeight = 1f,
                    )
                    Tape(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-12).dp),
                        width = 64.dp,
                        height = 24.dp,
                        color = StillColors.card.copy(alpha = 0.6f),
                        tilt = 3f,
                        edges = false,
                    )
                }
            }
        }
        Column(Modifier.bottomInset().padding(start = 28.dp, end = 28.dp, bottom = 30.dp)) {
            Column(Modifier.padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HandText("Your tasks, on little notes.", 30f, weight = FontWeight.Bold)
                MonoText(
                    "Keep them in sync with your Notion workspace, or keep everything on this phone.",
                    12.5f,
                    lineHeight = 1.6f,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryPill("Connect Notion", onConnect, Modifier.fillMaxWidth(), minHeight = 54.dp)
                OutlinePill("Use without Notion", onSkip, Modifier.fillMaxWidth())
            }
            MonoText(
                "Without Notion, tasks stay on this phone.\nYou can connect later in Settings.",
                11f,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                lineHeight = 1.6f,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PileSheet(color: Color, tilt: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(186.dp)
            .rotate(tilt)
            .paperShadow(PileSheetShape, Shadows.pileSheet)
            .background(color, PileSheetShape),
    )
}

/** A setup step: its header, a scrolling body, and Back with the step's main action at the bottom. */
@Composable
private fun StepPage(
    title: String,
    intro: String,
    step: Int?,
    of: Int,
    action: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    bodyPadding: PaddingValues,
    actionEnabled: Boolean = true,
    spacing: Int = 24,
    body: @Composable ColumnScope.() -> Unit,
) {
    PaperPage {
        StepHeader(title, intro, step = step, of = of)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bodyPadding),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = body,
        )
        Row(
            modifier = Modifier
                .bottomInset()
                .fillMaxWidth()
                .padding(start = 18.dp, top = 12.dp, end = 24.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackLink(onBack)
            PrimaryPill(action, onAction, enabled = actionEnabled)
        }
    }
}

@Composable
private fun ConnectNotionScreen(viewModel: SetupViewModel) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    StepPage(
        title = "Connect Notion",
        intro = "The app reads and writes your tasks through a private Notion integration that only you control.",
        step = 1,
        of = if (viewModel.fromSettings) 2 else 3,
        action = if (viewModel.checking) "Checking…" else "Connect",
        onAction = viewModel::connect,
        onBack = viewModel::back,
        actionEnabled = viewModel.token.isNotEmpty() && !viewModel.checking,
        bodyPadding = PaddingValues(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 16.dp),
    ) {
        HowToCard(onOpenNotion = { uriHandler.openUri(INTEGRATIONS_URL) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HandText("Paste your secret", 19f)
            TokenField(
                token = viewModel.token,
                visible = viewModel.tokenVisible,
                onChange = viewModel::updateToken,
                onToggleVisible = viewModel::toggleTokenVisible,
                label = "Paste your secret",
                height = 52.dp,
                onPaste = {
                    scope.launch {
                        val clip = clipboard.getClipEntry()?.clipData
                        clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { viewModel.updateToken(it.toString()) }
                    }
                },
            )
            val error = viewModel.tokenError
            if (error != null) {
                MonoText(error, 11f, color = StillColors.inkHigh, lineHeight = 1.6f)
            } else {
                MonoText(
                    "Starts with ntn_ or secret_. Stored encrypted on this phone and never sent anywhere except Notion.",
                    11f,
                    lineHeight = 1.6f,
                )
            }
        }
        if (!viewModel.fromSettings) TextLink("No Notion? Use the app without it", viewModel::useWithoutNotion)
    }
}

/** The index card that explains where the secret comes from. */
@Composable
private fun HowToCard(onOpenNotion: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .rotate(-0.5f)
            .paperShadow(shape, Shadows.indexCard)
            .clip(shape)
            .ruledPaper(every = 32.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .cardHeader()
                .padding(start = 16.dp, top = 6.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HandText("Get your secret", 21f, weight = FontWeight.Bold)
            LabelButton("Open Notion", onOpenNotion, Modifier.padding(horizontal = 10.dp), trailing = StillIcons.ExternalLink, letterSpacing = 0.1f)
        }
        Column(Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)) {
            HowToStep(1, "Go to notion.so/my-integrations")
            HowToStep(2, "Create a new internal integration")
            HowToStep(3, "Copy its Internal Integration Secret")
            HowToStep(4, "Give it access to your tasks database", "In Notion: ••• › Connections › your integration")
        }
    }
}

@Composable
private fun HowToStep(number: Int, text: String, detail: String? = null) {
    // Each step sits on one 32 dp rule of the card; the detail line takes a shorter one.
    Row(Modifier.heightIn(min = 32.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MonoText(number.toString(), 12f, Modifier.width(12.dp).alignByBaseline())
        Column(Modifier.alignByBaseline()) {
            HandText(text, 18f, lineHeight = 28f / 18f)
            if (detail != null) MonoText(detail, 11f, lineHeight = 20f / 11f)
        }
    }
}

@Composable
private fun PickDatabaseScreen(viewModel: SetupViewModel) {
    val of = if (viewModel.fromSettings) 2 else 3
    StepPage(
        title = "Pick your tasks",
        intro = "Databases your integration can see. Choose the one that holds your to-dos.",
        step = 2,
        of = of,
        action = if (viewModel.fromSettings) "Save" else "Next",
        onAction = viewModel::next,
        onBack = viewModel::back,
        actionEnabled = viewModel.canContinue,
        bodyPadding = PaddingValues(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 16.dp),
        spacing = 14,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            viewModel.databases.forEach { database ->
                RadioRow(
                    selected = database.id == viewModel.selected?.id,
                    onPick = { viewModel.select(database) },
                    minHeight = 54.dp,
                    tilt = -0.5f,
                    dashed = true,
                ) {
                    HandText(database.name, 20f, maxLines = 1)
                    database.parent?.let { MonoText("in $it", 11f, Modifier.padding(top = 1.dp), letterSpacing = 0.04.em, maxLines = 1) }
                }
            }
            when {
                viewModel.databases.isNotEmpty() -> Unit
                viewModel.loadingDatabases -> MonoText("Looking for your databases…", 11.5f)
                viewModel.databaseError == null -> HandText("Nothing is shared with it yet.", 19f, color = StillColors.ink3)
            }
        }
        viewModel.databaseError?.let { MonoText(it, 11f, color = StillColors.inkHigh, lineHeight = 1.6f) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoText("Missing one? Connect it in Notion, then refresh.", 11f, Modifier.weight(1f), lineHeight = 1.5f)
            LabelButton("Refresh", viewModel::refresh, Modifier.padding(start = 8.dp, end = 4.dp), leading = StillIcons.Refresh)
        }
        viewModel.selected?.let { FieldsBox(viewModel, it.name) }
    }
}

/** The dashed box where each thing the app needs is matched to one of the database's properties. */
@Composable
private fun FieldsBox(viewModel: SetupViewModel, database: String) {
    val schema = viewModel.schema
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.2.dp.toPx()
                drawRoundRect(
                    color = StillColors.outlineDashed,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3, stroke * 2))),
                )
            }
            // The mockup's 14, 12, 6 and 6 of padding, inside the 1.2 dp border.
            .padding(start = 15.2.dp, top = 13.2.dp, end = 7.2.dp, bottom = 7.2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Overline("Fields in “$database”", Modifier.weight(1f).alignByBaseline())
            if (schema != null) MonoText("${viewModel.choices.size} of ${Field.entries.size} found", 11f, Modifier.alignByBaseline())
        }
        if (schema == null) {
            MonoText("Reading its fields…", 11.5f, Modifier.padding(vertical = 12.dp))
            return@Column
        }
        Field.entries.forEach { field ->
            val property = schema.properties.firstOrNull { it.id == viewModel.choices[field] }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoText(field.label, 11.5f, letterSpacing = 0.04.em)
                val description = if (property == null) "${field.label}: not found, choose a property" else "${field.label}: ${property.shownName}, change"
                // 40 tall in the mockup; the touch target overflows the row to 48.
                TapTarget({ viewModel.pick(field) }, description, Modifier.requiredHeight(TapSize).padding(horizontal = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (property == null) {
                            MonoText("Not found · choose", 11.5f, color = StillColors.inkHigh)
                        } else {
                            HandText(property.shownName, 17f, maxLines = 1)
                        }
                        InkIcon(StillIcons.ChevronDown, 14.dp, tint = if (property == null) StillColors.inkHigh else StillColors.ink, strokeWidth = 2f)
                    }
                }
            }
        }
    }
}

/** Notion lets a property have a blank name; give it something to tap. */
private val app.stillnote.data.notion.SchemaProperty.shownName: String
    get() = name.ifBlank { "(no name)" }

/**
 * The properties that could play [field], on a sheet of card over the scrim. Not in the
 * mockups. An optional field can also be set to "Don't use".
 */
@Composable
private fun FieldPickerSheet(field: Field, viewModel: SetupViewModel) {
    val schema = viewModel.schema ?: return
    val candidates = field.candidates(schema)
    val chosen = viewModel.choices[field]
    val shape = RoundedCornerShape(2.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(StillColors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = viewModel::closePicker)
                .semantics { contentDescription = "Close without choosing" },
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .rotate(-0.6f)
                .paperShadow(shape, Shadows.note)
                .background(StillColors.card, shape)
                .semantics { paneTitle = field.label }
                .padding(top = 20.dp, bottom = 10.dp),
        ) {
            Overline(field.label, Modifier.padding(start = 14.dp, bottom = 8.dp))
            if (candidates.isEmpty()) {
                HandText("This database has no ${field.kind} property.", 18f, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = StillColors.ink3)
            }
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                candidates.forEach { property ->
                    RadioRow(property.id == chosen, { viewModel.choose(property.id) }, minHeight = 50.dp, dashed = true) {
                        HandText(property.shownName, 19f, maxLines = 1)
                    }
                }
                if (!field.required) {
                    RadioRow(chosen == null, { viewModel.choose(null) }, minHeight = 50.dp, dashed = true) {
                        HandText("Don't use", 19f, color = StillColors.ink3)
                    }
                }
            }
        }
    }
}

private val Field.kind: String
    get() = when (this) {
        Field.Title -> "title"
        Field.Done -> "checkbox"
        Field.Date -> "date"
        Field.Priority, Field.Project -> "select"
    }

@Composable
private fun ChooseLookScreen(viewModel: SetupViewModel) {
    val handFont by viewModel.handFont.collectAsStateWithLifecycle()
    StepPage(
        title = "Choose a look",
        intro = "You can change this any time in Settings.",
        step = null,
        of = 3,
        action = "Start writing",
        onAction = viewModel::finish,
        onBack = viewModel::back,
        bodyPadding = PaddingValues(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 16.dp),
        spacing = 22,
    ) {
        LookPreview()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Overline("Color palette")
            PaletteSwatches(compact = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Overline("Typography", Modifier.padding(bottom = 6.dp))
            HandFont.entries.forEach { font ->
                RadioRow(font == handFont, { viewModel.setHandFont(font) }, minHeight = 52.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val style = handStyle(font.family, 22f)
                        BasicText(font.title, Modifier.alignByBaseline().trimLineBox(rememberLineBoxTrim(style)), style = style)
                        MonoText(font.fontName, 11f, Modifier.alignByBaseline(), letterSpacing = 0.06.em)
                    }
                }
            }
        }
    }
}

private val PreviewBorder = 1.2.dp

/** Two sample post-its on the page background, in the chosen face. */
@Composable
private fun LookPreview() {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .fillMaxWidth()
            // 168 inside its border, which the mockup's box adds to its size.
            .height(168.dp + PreviewBorder * 2)
            .clip(shape)
            .dottedPaper()
            .border(PreviewBorder, StillColors.ink.copy(alpha = 0.22f), shape)
            .padding(PreviewBorder)
            .semantics { contentDescription = "Preview" },
    ) {
        Column(
            modifier = Modifier
                .padding(start = 52.dp, top = 92.dp, end = 20.dp)
                .fillMaxWidth()
                .rotate(1.6f)
                .paperShadow(PreviewPostItShape, Shadows.previewPostIt)
                .background(StillColors.apricot, PreviewPostItShape)
                .heightIn(min = 58.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            HandText("Call grandma", 19f)
            MonoText("19:00 · Personal · !!", 11f)
        }
        Row(
            modifier = Modifier
                .padding(start = 20.dp, top = 18.dp, end = 46.dp)
                .fillMaxWidth()
                .rotate(-1f)
                .paperShadow(PostItShape, Shadows.previewPostIt)
                .background(StillColors.canary, PostItShape)
                .heightIn(min = 62.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HandCircle(20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HandText("Water the plants", 20f)
                MonoText("08:00 · Personal · !", 11f)
            }
        }
    }
}
