package app.stillnote.ui.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.stillnote.ui.components.MonoText
import app.stillnote.ui.components.PaperPage
import app.stillnote.ui.components.ScreenTitle

/** A blank page for now: the Projects screen is being redesigned. */
@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) {
    PaperPage(modifier) {
        Column(
            modifier = Modifier.padding(start = 24.dp, top = 30.dp, end = 24.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // An empty overline keeps the title at the same height as on the other tabs.
            MonoText("", 12f, letterSpacing = 0.16.em)
            ScreenTitle("Projects")
        }
    }
}
