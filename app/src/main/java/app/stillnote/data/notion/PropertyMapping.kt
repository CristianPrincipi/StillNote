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
    /** The task on [page], with its date and time in the phone's time zone. */
    fun toRow(page: RemotePage, zone: ZoneId): TaskRow {
        val start = page.properties[date]?.obj("date")?.let { parseStart(it, zone) }
        val priorityName = priority?.let { page.properties[it]?.obj("select")?.string("name") }
        val projectName = project?.let { page.properties[it]?.obj("select")?.string("name") }
        return TaskRow(
            id = UUID.randomUUID().toString(),
            pageId = page.id,
            title = page.properties[title]?.plainText("title") ?: "Untitled",
            date = start?.toLocalDate()?.toString(),
            minutes = start?.takeIf { it.hasTime }?.toLocalTime()?.let { it.hour * 60 + it.minute },
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
        row.startText(zone)?.let { start -> putJsonObject(date) { putJsonObject("date") { put("start", start) } } }
        putJsonObject(done) { put("checkbox", row.done) }
        if (priority != null) {
            val option = priorityOptions[Priority.valueOf(row.priority)]
            putJsonObject(priority) {
                if (option == null) put("select", JsonNull) else putJsonObject("select") { put("name", option) }
            }
        }
        // A project made on the phone becomes an option in Notion the first time it is written.
        val projectName = row.project ?: noProjectOption
        if (project != null && projectName != null) {
            putJsonObject(project) { putJsonObject("select") { put("name", projectName) } }
        }
    }

    /** Pages dated [from]..[to], both inclusive. Notion compares these in UTC. */
    fun dateFilter(from: LocalDate, to: LocalDate): JsonObject = allOf(
        condition(date, "date", "on_or_after", from.toString()),
        condition(date, "date", "on_or_before", to.toString()),
    )

    /** Pages that belong to a real project, or null when the database has no project property. */
    fun projectFilter(): JsonObject? {
        val property = project ?: return null
        val real = buildJsonObject {
            put("property", property)
            putJsonObject("select") { put("is_not_empty", true) }
        }
        return allOf(*listOfNotNull(real, noProjectOption?.let { condition(property, "select", "does_not_equal", it) }).toTypedArray())
    }

    /** The project names the database offers, in its own order. */
    fun projects(schema: Schema): List<String> =
        schema.properties.firstOrNull { it.id == project }?.options.orEmpty().filter { it != noProjectOption }

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
         * The property the app would pick for each field: first by name among properties of a
         * usable type, then, for the fields a database must have, any property of that type.
         * Priority is also recognised by its options. A property is never used twice.
         */
        fun guess(schema: Schema): Map<Field, String> {
            val picked = mutableMapOf<Field, SchemaProperty>()
            fun free(field: Field) = field.candidates(schema).filter { it !in picked.values }
            for (field in Field.entries) {
                free(field).firstOrNull { it.name.trim().lowercase() in field.names }?.let { picked[field] = it }
            }
            for (field in Field.entries.filter { it !in picked }) {
                val byType = when {
                    field.required -> free(field).minByOrNull { it.name }
                    field == Field.Priority -> free(field).firstOrNull { priorityOptions(it).isNotEmpty() }
                    else -> null
                }
                byType?.let { picked[field] = it }
            }
            return picked.mapValues { it.value.id }
        }

        /** The mapping for [choices] (field to property id), or null while a required field is missing. */
        fun from(schema: Schema, choices: Map<Field, String>): PropertyMapping? {
            fun property(field: Field) = field.candidates(schema).firstOrNull { it.id == choices[field] }
            val priority = property(Field.Priority)
            val project = property(Field.Project)
            return PropertyMapping(
                title = property(Field.Title)?.id ?: return null,
                done = property(Field.Done)?.id ?: return null,
                date = property(Field.Date)?.id ?: return null,
                priority = priority?.id,
                priorityOptions = priority?.let(::priorityOptions).orEmpty(),
                project = project?.id,
                noProjectOption = project?.options?.firstOrNull { it.equals(NO_PROJECT, ignoreCase = true) },
            )
        }

        private fun priorityOptions(property: SchemaProperty): Map<Priority, String> =
            PRIORITY_NAMES.mapNotNull { (level, names) ->
                property.options.firstOrNull { it.trim().lowercase() in names }?.let { level to it }
            }.toMap()

        private val PRIORITY_NAMES = mapOf(
            Priority.High to setOf("high", "alta"),
            Priority.Medium to setOf("medium", "media"),
            Priority.Low to setOf("low", "bassa"),
            Priority.None to setOf("none", "nessuna"),
        )

        /** In the user's database, the default Project value "Task" stands for "no project". */
        private const val NO_PROJECT = "Task"

        private fun condition(property: String, type: String, test: String, value: String) = buildJsonObject {
            put("property", property)
            putJsonObject(type) { put(test, value) }
        }

        private fun allOf(vararg conditions: JsonObject) = buildJsonObject {
            putJsonArray("and") { conditions.forEach { add(it) } }
        }
    }
}

/**
 * What the app needs from a database, as the setup screen lists it. [names] are the property
 * names it recognises, in English and Italian; [types] are the Notion types that can hold it.
 */
enum class Field(val label: String, val required: Boolean, val types: Set<String>, val names: Set<String>) {
    Title("Task name", true, setOf("title"), setOf("name", "title", "task", "nome", "titolo", "attività")),
    Done("Done", true, setOf("checkbox"), setOf("done", "", "complete", "completed", "fatto", "completato")),
    Date("Date", true, setOf("date"), setOf("due", "date", "due date", "when", "data", "scadenza", "quando")),
    Priority("Priority", false, setOf("select"), setOf("priority", "priorità", "priorita")),
    Project("Project", false, setOf("select"), setOf("project", "progetto")),
    ;

    /** The properties of [schema] that could play this role. */
    fun candidates(schema: Schema): List<SchemaProperty> = schema.properties.filter { it.type in types }
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

private fun TaskRow.startText(zone: ZoneId): String? {
    val day = LocalDate.parse(date ?: return null)
    val time = minutes ?: return day.toString()
    return day.atTime(time / 60, time % 60).atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
