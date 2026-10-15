package app.stillnote.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.components.InkIcon
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.Shadows
import app.stillnote.ui.components.StillIcons
import app.stillnote.ui.components.TapTarget
import app.stillnote.ui.components.Tape
import app.stillnote.ui.components.cardHeader
import app.stillnote.ui.components.paperShadow
import app.stillnote.ui.components.rememberLineBoxTrim
import app.stillnote.ui.components.ruledPaper
import app.stillnote.ui.components.trimLineBox
import app.stillnote.ui.theme.LocalHandFont
import app.stillnote.ui.theme.OnPaper
import app.stillnote.ui.theme.StillColors
import app.stillnote.ui.theme.handStyle

/**
 * An index card for naming a new project, over the scrim. Tapping outside closes it without
 * saving. [synced] chooses the helper line: with Notion the project will show up there too.
 */
@Composable
fun NewProjectSheet(synced: Boolean, onCreate: (String) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val create = { if (name.isNotBlank()) onCreate(name) }
    val shape = RoundedCornerShape(2.dp)

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(StillColors.scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .semantics { contentDescription = "Close without saving" },
        )
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(start = 24.dp, top = 200.dp, end = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .rotate(-1f)
                    .paperShadow(shape, Shadows.note)
                    .semantics { paneTitle = "New project" }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focus.requestFocus()
                            keyboard?.show()
                        }
                    },
            ) {
                OnPaper {
                    Column(Modifier.clip(shape).ruledPaper(every = 24.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .cardHeader()
                                .padding(start = 20.dp, top = 26.dp, end = 18.dp, bottom = 1.2.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            MonoText("NEW PROJECT", 11f, letterSpacing = 0.16.em)
                            val style = handStyle(LocalHandFont.current, 32f, FontWeight.Bold, lineHeight = 1.25f)
                            BasicTextField(
                                value = name,
                                onValueChange = { name = it.replace('\n', ' ') },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .trimLineBox(rememberLineBoxTrim(style))
                                    .focusRequester(focus),
                                textStyle = style,
                                singleLine = true,
                                cursorBrush = SolidColor(StillColors.ink),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { create() }),
                                decorationBox = { field ->
                                    Box {
                                        if (name.isEmpty()) BasicText("Name it…", style = style.copy(color = StillColors.ink3))
                                        field()
                                    }
                                },
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 12.dp, end = 18.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            MonoText(
                                if (synced) "It gets its own card on this page, and syncs to your Notion projects." else "It gets its own card on this page.",
                                11.5f,
                                lineHeight = 24f / 11.5f,
                            )
                            TapTarget(
                                onClick = create,
                                description = "Create project",
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .size(52.dp)
                                    .background(StillColors.ink, CircleShape),
                                enabled = name.isNotBlank(),
                            ) {
                                InkIcon(StillIcons.Send, 22.dp, tint = StillColors.canary, strokeWidth = 2f)
                            }
                        }
                    }
                }
                Tape(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp),
                    color = StillColors.paper.copy(alpha = 0.75f),
                    tilt = 2f,
                )
            }
        }
    }
}
