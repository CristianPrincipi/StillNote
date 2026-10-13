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
    private val mapping = (PropertyMapping.detect(schema) as PropertyMapping.Detected.Ok).mapping

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
    fun `reports what a database lacks`() {
        val bare = Schema("Books", listOf(SchemaProperty("title", "Name", "title"), SchemaProperty("a", "Author", "rich_text")))
        assertEquals(PropertyMapping.Detected.Missing(listOf("a date", "a checkbox")), PropertyMapping.detect(bare))
    }

    @Test
    fun `prefers a checkbox called Done over others, and works without priority or project`() {
        val schema = Schema(
            "Todo",
            listOf(
                SchemaProperty("title", "Task", "title"),
                SchemaProperty("a", "Archived", "checkbox"),
                SchemaProperty("b", "Done", "checkbox"),
                SchemaProperty("c", "Due", "date"),
            ),
        )
        val detected = (PropertyMapping.detect(schema) as PropertyMapping.Detected.Ok).mapping
        assertEquals("b", detected.done)
        assertNull(detected.priority)
        assertNull(detected.project)
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
        )!!
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
        )!!
        assertEquals("2026-10-14", row.date)
        assertEquals(5 * 60 + 30, row.minutes)
    }

    @Test
    fun `reads a time given with a named time zone`() {
        val row = mapping.toRow(
            page("""{"dt%7B": {"id": "dt%7B", "type": "date", "date": {"start": "2026-10-13T09:00:00.000", "time_zone": "Europe/London"}}}"""),
            rome,
        )!!
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
        )!!
        assertEquals("Untitled", row.title)
        assertEquals("None", row.priority)
        assertNull(row.project)
        assertEquals(false, row.done)
    }

    @Test
    fun `skips a page with no date`() {
        assertNull(mapping.toRow(page("""{"dt%7B": {"id": "dt%7B", "type": "date", "date": null}}"""), rome))
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
        assertEquals(expected, mapping.newPageProperties(row, rome))
    }

    @Test
    fun `writes a plain date, and clears the priority when the database has no None option`() {
        val noNone = mapping.copy(priorityOptions = mapping.priorityOptions - Priority.None)
        val row = TaskRow(id = "1", title = "Read", date = "2026-10-13", priority = "None", created = 0)
        val properties = noNone.newPageProperties(row, rome)
        assertEquals(Json.parseToJsonElement("""{"date": {"start": "2026-10-13"}}"""), properties["dt%7B"])
        assertEquals(Json.parseToJsonElement("""{"select": null}"""), properties["W%5BNU"])
    }

    @Test
    fun `survives being saved and loaded`() {
        assertEquals(mapping, PropertyMapping.fromJson(mapping.toJson()))
        assertNull(PropertyMapping.fromJson("not json"))
    }
}
