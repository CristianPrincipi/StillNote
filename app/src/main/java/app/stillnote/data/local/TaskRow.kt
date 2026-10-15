package app.stillnote.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.stillnote.data.Priority
import app.stillnote.data.Task
import java.time.LocalDate
import java.time.LocalTime

/**
 * A task as stored on the phone. The phone's copy is what the screens show; when Notion is
 * connected, the sync columns track how far it has caught up.
 */
@Entity(tableName = "tasks", indices = [Index(value = ["pageId"], unique = true), Index("date")])
data class TaskRow(
    @PrimaryKey val id: String,
    /** The Notion page, once the task exists there. */
    val pageId: String? = null,
    val title: String,
    /** ISO local date, so it sorts and compares as text. Null for a Notion task with no date. */
    val date: String?,
    /** Time of day in minutes, or null for "any time". */
    val minutes: Int? = null,
    val priority: String,
    val project: String? = null,
    val done: Boolean = false,
    /** Epoch millis; keeps equal tasks in a stable order. */
    val created: Long,
    val trashed: Boolean = false,
    /** Changed here since Notion last saw it. */
    val dirty: Boolean = false,
    /**
     * Edited here, not just ticked or deleted, since Notion last saw it: the next push sends every
     * field. A tick alone sends only the checkbox, so it can't undo an edit made in Notion meanwhile.
     */
    @ColumnInfo(defaultValue = "0")
    val edited: Boolean = false,
    /** Goes up with every local change, so a push can tell whether the row moved on meanwhile. */
    val rev: Int = 0,
    /** Notion refused the last push of this row; it waits for a retry. */
    val failed: Boolean = false,
    /** Epoch millis of the last successful push, 0 if never. */
    val pushedAt: Long = 0,
    /** Epoch millis of the last local change, 0 if it only ever came from Notion. */
    val changedAt: Long = 0,
    /** The task's own reminder, or null for the default. Only on the phone; see [app.stillnote.data.Reminder]. */
    val reminder: Int? = null,
    /** Epoch millis when "Later" on its reminder brings it back, 0 if not snoozed. Only on the phone. */
    @ColumnInfo(defaultValue = "0")
    val snoozedUntil: Long = 0,
)

fun TaskRow.toTask() = Task(
    id = id,
    title = title,
    date = date?.let(LocalDate::parse),
    time = minutes?.let { LocalTime.of(it / 60, it % 60) },
    priority = Priority.valueOf(priority),
    project = project,
    done = done,
    reminder = reminder,
)

/** A project card. Its name is the label tasks carry, which in Notion is a select option. */
@Entity(tableName = "projects")
data class ProjectRow(
    @PrimaryKey val name: String,
    val position: Int,
    /** Made on this phone and not (yet) an option in Notion. */
    val local: Boolean = false,
)
