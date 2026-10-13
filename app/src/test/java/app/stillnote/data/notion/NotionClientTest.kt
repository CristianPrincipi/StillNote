package app.stillnote.data.notion

import app.stillnote.data.notion.NotionException.Kind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class NotionClientTest {
    private val server = MockWebServer()
    private val waits = mutableListOf<Long>()
    private val logged = mutableListOf<String>()
    private lateinit var client: NotionClient

    @Before
    fun start() {
        server.start()
        client = NotionClient(
            baseUrl = { server.url("/").toString().trimEnd('/') },
            log = { logged += it },
            pause = { waits += it },
        )
    }

    @After
    fun stop() = server.close()

    private fun respond(code: Int, body: String, vararg headers: Pair<String, String>) {
        val response = MockResponse.Builder().code(code).body(body)
        headers.forEach { (name, value) -> response.addHeader(name, value) }
        server.enqueue(response.build())
    }

    private fun RecordedRequest.json() = Json.parseToJsonElement(body!!.utf8()) as JsonObject

    private suspend fun failure(call: suspend () -> Unit): NotionException {
        try {
            call()
        } catch (e: NotionException) {
            return e
        }
        fail("expected a NotionException")
        throw AssertionError()
    }

    @Test
    fun `sends the token and the pinned version, and never logs the token`() = runTest {
        respond(401, """{"object": "error", "status": 401, "code": "unauthorized", "message": "API token is invalid."}""")
        val error = failure { client.checkToken("ntn_secret") }

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/users/me", request.url.encodedPath)
        assertEquals("Bearer ntn_secret", request.headers["Authorization"])
        assertEquals("2026-03-11", request.headers["Notion-Version"])
        assertEquals(Kind.Unauthorized, error.kind)
        assertEquals(listOf("GET /v1/users/me: 401 unauthorized API token is invalid."), logged)
    }

    @Test
    fun `lists data sources across pages of results, leaving out trashed ones`() = runTest {
        respond(
            200,
            """{"object": "list", "has_more": true, "next_cursor": "abc", "results": [
                {"object": "data_source", "id": "ds-1", "in_trash": false, "title": [{"plain_text": "Tasks"}]},
                {"object": "data_source", "id": "ds-2", "in_trash": true, "title": [{"plain_text": "Old"}]}
            ]}""",
        )
        respond(200, """{"object": "list", "has_more": false, "next_cursor": null, "results": [{"object": "data_source", "id": "ds-3", "title": []}]}""")

        assertEquals(listOf(DataSource("ds-1", "Tasks"), DataSource("ds-3", "Untitled")), client.dataSources("t"))

        val first = server.takeRequest()
        assertEquals("POST", first.method)
        assertEquals("/v1/search", first.url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"filter": {"property": "object", "value": "data_source"}, "page_size": 100}"""), first.json())
        assertEquals("abc", server.takeRequest().json().string("start_cursor"))
    }

    @Test
    fun `reads a schema with select options`() = runTest {
        respond(
            200,
            """{"object": "data_source", "id": "ds-1", "title": [{"plain_text": "Tasks"}], "properties": {
                "Name": {"id": "title", "name": "Name", "type": "title", "title": {}},
                " ": {"id": "chk", "name": " ", "type": "checkbox", "checkbox": {}},
                "Priority": {"id": "pri", "name": "Priority", "type": "select", "select": {"options": [{"id": "1", "name": "High"}, {"id": "2", "name": "Low"}]}}
            }}""",
        )
        val schema = client.schema("t", "ds-1")

        assertEquals("/v1/data_sources/ds-1", server.takeRequest().url.encodedPath)
        assertEquals("Tasks", schema.name)
        assertEquals(
            listOf(
                SchemaProperty("title", "Name", "title"),
                SchemaProperty("chk", " ", "checkbox"),
                SchemaProperty("pri", "Priority", "select", listOf("High", "Low")),
            ),
            schema.properties,
        )
    }

    @Test
    fun `queries a date range and keys each page's values by property id`() = runTest {
        respond(
            200,
            """{"object": "list", "has_more": false, "next_cursor": null, "results": [
                {"object": "page", "id": "p-1", "created_time": "2026-10-13T08:00:00.000Z", "in_trash": false,
                 "properties": {"Name": {"id": "title", "type": "title", "title": []}, " ": {"id": "chk", "type": "checkbox", "checkbox": true}}},
                {"object": "page", "id": "p-2", "created_time": "2026-10-13T08:00:00.000Z", "in_trash": true, "properties": {}}
            ]}""",
        )
        val pages = client.query("t", "ds-1", "dt", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        val request = server.takeRequest()
        assertEquals("/v1/data_sources/ds-1/query", request.url.encodedPath)
        assertEquals(
            Json.parseToJsonElement(
                """{"filter": {"and": [
                    {"property": "dt", "date": {"on_or_after": "2026-10-01"}},
                    {"property": "dt", "date": {"on_or_before": "2026-10-31"}}
                ]}, "page_size": 100}"""
            ),
            request.json(),
        )
        assertEquals(listOf("p-1"), pages.map { it.id })
        assertEquals(1_791_878_400_000L, pages[0].createdTime)
        assertEquals(setOf("title", "chk"), pages[0].properties.keys)
    }

    @Test
    fun `creates a page in the data source and returns its id`() = runTest {
        respond(200, """{"object": "page", "id": "p-9"}""")
        val properties = Json.parseToJsonElement("""{"title": {"title": []}}""") as JsonObject

        assertEquals("p-9", client.createPage("t", "ds-1", properties))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/pages", request.url.encodedPath)
        assertEquals(
            Json.parseToJsonElement("""{"parent": {"type": "data_source_id", "data_source_id": "ds-1"}, "properties": {"title": {"title": []}}}"""),
            request.json(),
        )
    }

    @Test
    fun `trashes a page without touching its properties, and updates one that isn't trashed`() = runTest {
        respond(200, "{}")
        respond(200, "{}")
        client.updatePage("t", "p-1", properties = null, inTrash = true)
        client.updatePage("t", "p-1", Json.parseToJsonElement("""{"chk": {"checkbox": true}}""") as JsonObject, inTrash = false)

        val trash = server.takeRequest()
        assertEquals("PATCH", trash.method)
        assertEquals("/v1/pages/p-1", trash.url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"in_trash": true}"""), trash.json())
        assertEquals(Json.parseToJsonElement("""{"in_trash": false, "properties": {"chk": {"checkbox": true}}}"""), server.takeRequest().json())
    }

    @Test
    fun `waits as long as Notion asks when rate limited, then carries on`() = runTest {
        respond(429, """{"code": "rate_limited", "message": "slow down"}""", "Retry-After" to "2")
        respond(200, "{}")

        client.checkToken("t")

        assertEquals(listOf(2_000L), waits)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `gives up on a server error after three tries`() = runTest {
        repeat(3) { respond(502, """{"code": "bad_gateway", "message": "oops"}""") }

        assertEquals(Kind.Busy, failure { client.checkToken("t") }.kind)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test
    fun `does not repeat a create after a server error, since it may have gone through`() = runTest {
        respond(500, """{"code": "internal_server_error", "message": "oops"}""")

        assertEquals(Kind.Busy, failure { client.createPage("t", "ds-1", JsonObject(emptyMap())) }.kind)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `takes the page id from a create that timed out but was saved`() = runTest {
        respond(503, """{"code": "service_unavailable", "message": "saved", "additional_data": {"committed_resource_id": "p-7"}}""")

        assertEquals("p-7", client.createPage("t", "ds-1", JsonObject(emptyMap())))
    }

    @Test
    fun `tells apart a missing page, a refused request and no network`() = runTest {
        respond(404, """{"code": "object_not_found", "message": "gone"}""")
        respond(400, """{"code": "validation_error", "message": "bad"}""")
        assertEquals(Kind.NotFound, failure { client.updatePage("t", "p-1", null, true) }.kind)
        assertEquals(Kind.Rejected, failure { client.updatePage("t", "p-1", null, true) }.kind)

        server.close()
        assertEquals(Kind.Network, failure { client.checkToken("t") }.kind)
    }
}
