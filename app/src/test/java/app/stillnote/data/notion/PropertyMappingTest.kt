package app.stillnote.data.notion

import app.stillnote.data.Priority
import app.stillnote.data.local.TaskRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PropertyMappingTest {
    private val rome = ZoneId.of("Europe/Rome")

    // The user's Tasks database: note the checkbox whose name is a single space.
    private val schema = Schema(
        "Tasks",
        listOf(
            SchemaProperty("title", "Name", "title"),
            SchemaProperty("chk%3A", " ", "checkbox"),
            SchemaProperty("dt%7B", "Date", "date"),
            SchemaProperty("W%5BNU", "Priority", "select", listOf("High", "Medium", "Low", "None")),
            SchemaProperty("%7CbqX", "Project", "select", listOf("Task", "David", "Arch", "Steady")),
        ),
    )
    private val mapping = PropertyMapping.from(schema, PropertyMapping.guess(schema))!!

    private fun page(properties: String) = RemotePage(
        id = "page-1",
        createdTime = 1_000,
        properties = (Json.parseToJsonElement(properties) as JsonObject).mapValues { it.value as JsonObject },
    )

    @Test
    fun `detects every role in the user's database by id`() {
        assertEquals("title", mapping.title)
        assertEquals("chk%3A", mapping.done)
        assertEquals("dt%7B", mapping.date)
        assertEquals("W%5BNU", mapping.priority)
        assertEquals(mapOf(Priority.High to "High", Priority.Medium to "Medium", Priority.Low to "Low", Priority.None to "None"), mapping.priorityOptions)
        assertEquals("%7CbqX", mapping.project)
        assertEquals("Task", mapping.noProjectOption)
    }

    @Test
    fun `needs a title, a checkbox and a date before it gives a mapping`() {
        val books = Schema("Books", listOf(SchemaProperty("title", "Name", "title"), SchemaProperty("a", "Author", "rich_text")))
        val guess = PropertyMapping.guess(books)
        assertEquals(mapOf(Field.Title to "title"), guess)
        assertNull(PropertyMapping.from(books, guess))
    }

    @Test
    fun `matches by name first, then by type for the fields a database must have`() {
        val schema = Schema(
            "Todo",
            listOf(
                SchemaProperty("title", "Task", "title"),
                SchemaProperty("a", "Archived", "checkbox"),
                SchemaProperty("b", "Done", "checkbox"),
                SchemaProperty("c", "Created", "date"),
                SchemaProperty("d", "Area", "select", listOf("Home", "Work")),
            ),
        )
        // "Done" wins over "Archived" by name; the only date is taken by type; "Area" is not guessed to be a project.
        assertEquals(mapOf(Field.Title to "title", Field.Done to "b", Field.Date to "c"), PropertyMapping.guess(schema))
    }

    @Test
    fun `recognises Italian names, and a priority by its options`() {
        val schema = Schema(
            "Attività",
            listOf(
                SchemaProperty("title", "Nome", "title"),
                SchemaProperty("a", "Fatto", "checkbox"),
                SchemaProperty("b", "Scadenza", "date"),
                SchemaProperty("c", "Urgenza", "select", listOf("Alta", "Media", "Bassa")),
                SchemaProperty("d", "Progetto", "select", listOf("Casa")),
            ),
        )
        val guess = PropertyMapping.guess(schema)
        assertEquals(mapOf(Field.Title to "title", Field.Done to "a", Field.Date to "b", Field.Priority to "c", Field.Project to "d"), guess)
        assertEquals(
            mapOf(Priority.High to "Alta", Priority.Medium to "Media", Priority.Low to "Bassa"),
            PropertyMapping.from(schema, guess)!!.priorityOptions,
        )
    }

    @Test
    fun `lists only properties of a usable type for each field, and honours the user's choice`() {
        assertEquals(listOf("W%5BNU", "%7CbqX"), Field.Project.candidates(schema).map { it.id })
        assertEquals(listOf("chk%3A"), Field.Done.candidates(schema).map { it.id })

        // Left without a priority, and with Project pointed at the other select.
        val chosen = PropertyMapping.from(schema, mapOf(Field.Title to "title", Field.Done to "chk%3A", Field.Date to "dt%7B", Field.Project to "W%5BNU"))!!
        assertNull(chosen.priority)
        assertEquals("W%5BNU", chosen.project)
        assertNull(chosen.noProjectOption)
    }

    @Test
    fun `reads a page with a plain date`() {
        val row = mapping.toRow(
            page(
                """{
                  "title": {"id": "title", "type": "title", "title": [{"plain_text": "Call "}, {"plain_text": "the dentist"}]},
                  "chk%3A": {"id": "chk%3A", "type": "checkbox", "checkbox": true},
                  "dt%7B": {"id": "dt%7B", "type": "date", "date": {"start": "2026-10-13", "end": null, "time_zone": null}},
                  "W%5BNU": {"id": "W%5BNU", "type": "select", "select": {"id": "x", "name": "High", "color": "red"}},
                  "%7CbqX": {"id": "%7CbqX", "type": "select", "select": {"name": "David"}}
                }"""
            ),
            rome,
        )
        assertEquals("page-1", row.pageId)
        assertEquals("Call the dentist", row.title)
        assertEquals("2026-10-13", row.date)
        assertNull(row.minutes)
        assertEquals("High", row.priority)
        assertEquals("David", row.project)
        assertTrue(row.done)
        assertEquals(1_000L, row.created)
    }

    @Test
    fun `converts a time to the phone's zone, which can change the day`() {
        // 23:30 in New York is 05:30 the next morning in Rome.
        val row = mapping.toRow(
            page("""{"dt%7B": {"id": "dt%7B", "type": "date", "date": {"start": "2026-10-13T23:30:00.000-04:00"}}}"""),
            rome,
        )
        assertEquals("2026-10-14", row.date)
        assertEquals(5 * 60 + 30, row.minutes)
    }

    @Test
    fun `reads a time given with a named time zone`() {
        val row = mapping.toRow(
            page("""{"dt%7B": {"id": "dt%7B", "type": "date", "date": {"start": "2026-10-13T09:00:00.000", "time_zone": "Europe/London"}}}"""),
            rome,
        )
        assertEquals("2026-10-13", row.date)
        assertEquals(10 * 60, row.minutes)
    }

    @Test
    fun `treats the Task project, an empty priority and a missing title as no project, None and Untitled`() {
        val row = mapping.toRow(
            page(
                """{
                  "title": {"id": "title", "type": "title", "title": []},
                  "dt%7B": {"id": "dt%7B", "type": "date", "date": {"start": "2026-10-13"}},
                  "W%5BNU": {"id": "W%5BNU", "type": "select", "select": null},
                  "%7CbqX": {"id": "%7CbqX", "type": "select", "select": {"name": "Task"}}
                }"""
            ),
            rome,
        )
        assertEquals("Untitled", row.title)
        assertEquals("None", row.priority)
        assertNull(row.project)
        assertEquals(false, row.done)
    }

    @Test
    fun `keeps a page with no date, as an undated task`() {
        val row = mapping.toRow(page("""{"dt%7B": {"id": "dt%7B", "type": "date", "date": null}}"""), rome)
        assertNull(row.date)
        assertNull(row.minutes)
    }

    @Test
    fun `builds the properties of a new page`() {
        val row = TaskRow(id = "1", title = "Buy a card", date = "2026-10-13", minutes = 18 * 60, priority = "Medium", created = 0)
        val expected = Json.parseToJsonElement(
            """{
              "title": {"title": [{"text": {"content": "Buy a card"}}]},
              "dt%7B": {"date": {"start": "2026-10-13T18:00:00+02:00"}},
              "chk%3A": {"checkbox": false},
              "W%5BNU": {"select": {"name": "Medium"}},
              "%7CbqX": {"select": {"name": "Task"}}
            }"""
        )
        assertEquals(expected, mapping.pageProperties(row, rome))
    }

    @Test
    fun `writes a plain date, and clears the priority when the database has no None option`() {
        val noNone = mapping.copy(priorityOptions = mapping.priorityOptions - Priority.None)
        val row = TaskRow(id = "1", title = "Read", date = "2026-10-13", priority = "None", created = 0)
        val properties = noNone.pageProperties(row, rome)
        assertEquals(Json.parseToJsonElement("""{"date": {"start": "2026-10-13"}}"""), properties["dt%7B"])
        assertEquals(Json.parseToJsonElement("""{"select": null}"""), properties["W%5BNU"])
    }

    @Test
    fun `files a new task under its project, which Notion adds as an option if it is new`() {
        val row = TaskRow(id = "1", title = "Water the tomatoes", date = "2026-10-13", priority = "None", project = "Garden", created = 0)
        assertEquals(Json.parseToJsonElement("""{"select": {"name": "Garden"}}"""), mapping.pageProperties(row, rome)["%7CbqX"])
    }

    @Test
    fun `takes a task out of its project, even when the database has no option for no project`() {
        val row = TaskRow(id = "1", title = "Read", date = "2026-10-13", priority = "None", created = 0)
        assertEquals(Json.parseToJsonElement("""{"select": {"name": "Task"}}"""), mapping.pageProperties(row, rome)["%7CbqX"])
        val noTask = mapping.copy(noProjectOption = null)
        assertEquals(Json.parseToJsonElement("""{"select": null}"""), noTask.pageProperties(row, rome)["%7CbqX"])
    }

    @Test
    fun `builds the filters for a range of dates and for tasks that have a project`() {
        assertEquals(
            Json.parseToJsonElement(
                """{"and": [
                    {"property": "dt%7B", "date": {"on_or_after": "2026-10-01"}},
                    {"property": "dt%7B", "date": {"on_or_before": "2026-10-31"}}
                ]}"""
            ),
            mapping.dateFilter(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31)),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"and": [
                    {"property": "%7CbqX", "select": {"is_not_empty": true}},
                    {"property": "%7CbqX", "select": {"does_not_equal": "Task"}}
                ]}"""
            ),
            mapping.projectFilter(),
        )
        assertNull(mapping.copy(project = null).projectFilter())
        assertEquals(listOf("David", "Arch", "Steady"), mapping.projects(schema))
    }

    @Test
    fun `survives being saved and loaded`() {
        assertEquals(mapping, PropertyMapping.fromJson(mapping.toJson()))
        assertNull(PropertyMapping.fromJson("not json"))
    }
}
