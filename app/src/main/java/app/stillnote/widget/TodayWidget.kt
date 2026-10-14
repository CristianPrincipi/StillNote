package app.stillnote.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import app.stillnote.MainActivity
import app.stillnote.StillNoteApp
import kotlinx.coroutines.flow.first

/**
 * Today's open tasks on a post-it on the home screen (DESIGN.md §4.7). The note is one drawn
 * image; invisible tap areas over it complete a task, add one, or open Today.
 */
class TodayWidget : GlanceAppWidget() {
    // Each size the launcher lays the widget out at gets its own drawing. Some launchers then
    // shrink the whole view to fit small cells (OnePlus's lays out 148 dp and shows about 110);
    // the drawing and its tap areas shrink together.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as StillNoteApp
        val notes = app.widgetNotes()
        val first = notes.first()
        val painter = WidgetPainter(context)
        provideContent {
            val note by notes.collectAsState(first)
            val size = LocalSize.current
            TodayNote(remember(note, size) { painter.paint(note, size) }, size)
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayNote(drawing: WidgetDrawing, size: DpSize) {
    val context = LocalContext.current
    // Centred in whatever room is left over, at exactly the size it was drawn for.
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(GlanceModifier.size(size.width, size.height)) {
            Image(
                provider = ImageProvider(drawing.bitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            // Glance has no absolute placement, so each area is padded into place on its own layer.
            for (area in drawing.areas) {
                Box(GlanceModifier.fillMaxSize().padding(start = area.left, top = area.top)) {
                    Box(
                        GlanceModifier
                            .size(area.width, area.height)
                            .clickable(area.tap.action(context))
                            .semantics { contentDescription = area.label },
                    ) {}
                }
            }
        }
    }
}

private fun WidgetTap.action(context: Context): Action = when (this) {
    WidgetTap.OpenToday -> actionStartActivity(WidgetRequest.Today.intent(context))
    WidgetTap.NewTask -> actionStartActivity(WidgetRequest.NewTask.intent(context))
    is WidgetTap.Complete -> actionRunCallback<CompleteTask>(actionParametersOf(CompleteTask.TaskId to taskId))
}

/** Ticks a task off from its row; it leaves the widget once the note is drawn again. */
class CompleteTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskId] ?: return
        (context.applicationContext as StillNoteApp).tasks.setDone(id, true)
        TodayWidget().update(context, glanceId)
    }

    companion object {
        val TaskId = ActionParameters.Key<String>("task")
    }
}

/** What a tap on the widget asks the app to show. */
enum class WidgetRequest(private val action: String) {
    Today("app.stillnote.action.TODAY"),
    NewTask("app.stillnote.action.NEW_TASK"),
    ;

    /** Brings the running app forward, or starts it, with this request. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    companion object {
        fun from(intent: Intent?): WidgetRequest? = entries.firstOrNull { it.action == intent?.action }
    }
}
