package app.stillnote.data.notion

import app.stillnote.data.Priority
import app.stillnote.data.local.TaskRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Which property of the user's database plays each role, by property id, so the mapping
 * survives a rename and works for a property whose name is blank.
 */
data class PropertyMapping(
    val title: String,
    val done: String,
    val date: String,
    val priority: String? = null,
    /** The database's own option name for each priority it has an option for. */
    val priorityOptions: Map<Priority, String> = emptyMap(),
    val project: String? = null,
    /** The Project option that means "no project", if the database has one. */
    val noProjectOption: String? = null,
) {
    /** The task on [page], in the phone's time zone, or null when the page has no date. */
    fun toRow(page: RemotePage, zone: ZoneId): TaskRow? {
        val start = page.properties[date]?.obj("date")?.let { parseStart(it, zone) } ?: return null
        val priorityName = priority?.let { page.properties[it]?.obj("select")?.string("name") }
        val projectName = project?.let { page.properties[it]?.obj("select")?.string("name") }
        return TaskRow(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            title = page.properties[title]?.plainText("title") ?: "Untitled",
            date = start.toLocalDate().toString(),
            minutes = start.toLocalTime().takeIf { start.hasTime }?.let { it.hour * 60 + it.minute },
            priority = (priorityOptions.entries.firstOrNull { it.value == priorityName }?.key ?: Priority.None).name,
            project = projectName?.takeIf { it != noProjectOption },
            done = page.properties[done]?.boolean("checkbox") == true,
            created = page.createdTime,
        )
    }

    /** Everything the app sets when it creates a page. */
    fun newPageProperties(row: TaskRow, zone: ZoneId): JsonObject = buildJsonObject {
        putJsonObject(title) {
            putJsonArray("title") {
                add(buildJsonObject { putJsonObject("text") { put("content", row.title.take(MAX_TEXT)) } })
            }
        }
        putJsonObject(date) { putJsonObject("date") { put("start", row.startText(zone)) } }
        putJsonObject(done) { put("checkbox", row.done) }
        if (priority != null) {
            val option = priorityOptions[Priority.valueOf(row.priority)]
            putJsonObject(priority) {
                if (option == null) put("select", JsonNull) else putJsonObject("select") { put("name", option) }
            }
        }
        if (project != null && noProjectOption != null) {
            putJsonObject(project) { putJsonObject("select") { put("name", noProjectOption) } }
        }
    }

    /** The only property the app changes on an existing page. */
    fun doneProperty(done: Boolean): JsonObject = buildJsonObject {
        putJsonObject(this@PropertyMapping.done) { put("checkbox", done) }
    }

    fun toJson(): String = buildJsonObject {
        put("title", title)
        put("done", done)
        put("date", date)
        priority?.let { put("priority", it) }
        putJsonObject("priorityOptions") { priorityOptions.forEach { (key, option) -> put(key.name, option) } }
        project?.let { put("project", it) }
        noProjectOption?.let { put("noProjectOption", it) }
    }.toString()

    companion object {
        private const val MAX_TEXT = 2_000

        fun fromJson(text: String): PropertyMapping? = runCatching {
            val json = Json.parseToJsonElement(text) as JsonObject
            PropertyMapping(
                title = json.string("title")!!,
                done = json.string("done")!!,
                date = json.string("date")!!,
                priority = json.string("priority"),
                priorityOptions = json.obj("priorityOptions").orEmpty().keys.associate { key ->
                    Priority.valueOf(key) to json.obj("priorityOptions")!!.string(key)!!
                },
                project = json.string("project"),
                noProjectOption = json.string("noProjectOption"),
            )
        }.getOrNull()

        /**
         * Finds each role in [schema] by type first and name second, or reports which
         * required roles ("a date", "a checkbox") the database has no property for.
         */
        fun detect(schema: Schema): Detected {
            fun pick(type: String, vararg names: String): SchemaProperty? {
                val candidates = schema.properties.filter { it.type == type }.sortedBy { it.name }
                return candidates.firstOrNull { it.name.trim().lowercase() in names } ?: candidates.firstOrNull()
            }
            fun named(type: String, name: String) =
                schema.properties.firstOrNull { it.type == type && it.name.trim().equals(name, ignoreCase = true) }

            val title = pick("title")
            val done = pick("checkbox", "done", "", "complete", "completed")
            val date = pick("date", "date", "due", "due date", "when")
            val missing = listOfNotNull("a date".takeIf { date == null }, "a checkbox".takeIf { done == null })
            if (title == null || done == null || date == null) return Detected.Missing(missing)

            val priority = named("select", "priority")
            val project = named("select", "project")
            return Detected.Ok(
                PropertyMapping(
                    title = title.id,
                    done = done.id,
                    date = date.id,
                    priority = priority?.id,
                    priorityOptions = Priority.entries.mapNotNull { level ->
                        priority?.options?.firstOrNull { it.equals(level.label, ignoreCase = true) }?.let { level to it }
                    }.toMap(),
                    project = project?.id,
                    noProjectOption = project?.options?.firstOrNull { it.equals(NO_PROJECT, ignoreCase = true) },
                )
            )
        }

        /** In the user's database, the default Project value "Task" stands for "no project". */
        private const val NO_PROJECT = "Task"
    }

    sealed interface Detected {
        data class Ok(val mapping: PropertyMapping) : Detected
        data class Missing(val what: List<String>) : Detected
    }
}

private class Start(val at: LocalDateTime, val hasTime: Boolean) {
    fun toLocalDate(): LocalDate = at.toLocalDate()
    fun toLocalTime(): LocalTime = at.toLocalTime()
}

/** Reads a date property's `start`: a plain date, a time with an offset, or a time in `time_zone`. */
private fun parseStart(date: JsonObject, zone: ZoneId): Start? {
    val text = date.string("start") ?: return null
    return runCatching {
        when {
            'T' !in text -> Start(LocalDate.parse(text).atStartOfDay(), hasTime = false)
            date.string("time_zone") != null -> {
                val there = LocalDateTime.parse(text.take(19)).atZone(ZoneId.of(date.string("time_zone")))
                Start(there.withZoneSameInstant(zone).toLocalDateTime(), hasTime = true)
            }
            else -> Start(OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime(), hasTime = true)
        }
    }.getOrNull()
}

private fun TaskRow.startText(zone: ZoneId): String {
    val day = LocalDate.parse(date)
    val time = minutes ?: return day.toString()
    return day.atTime(time / 60, time % 60).atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
