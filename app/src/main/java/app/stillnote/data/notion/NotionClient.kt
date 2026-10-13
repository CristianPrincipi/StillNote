package app.stillnote.data.notion

import app.stillnote.data.notion.NotionException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Notion's REST API over OkHttp. The token is only ever sent in the Authorization header;
 * [log] receives paths, status codes and Notion's error messages, never the token.
 */
class NotionClient(
    private val baseUrl: () -> String = { "https://api.notion.com" },
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build(),
    private val log: (String) -> Unit = {},
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : NotionApi {

    override suspend fun checkToken(token: String) {
        call(token, "GET", "/v1/users/me")
    }

    override suspend fun dataSources(token: String): List<DataSource> =
        paged(MAX_DATA_SOURCES) { cursor ->
            call(token, "POST", "/v1/search", buildJsonObject {
                putJsonObject("filter") {
                    put("property", "object")
                    put("value", "data_source")
                }
                put("page_size", PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            })
        }
            .filter { it.string("object") == "data_source" && it.boolean("in_trash") != true }
            .mapNotNull { source -> source.string("id")?.let { DataSource(it, source.plainText("title")) } }

    override suspend fun schema(token: String, dataSourceId: String): Schema {
        val source = call(token, "GET", "/v1/data_sources/$dataSourceId")
        val properties = source.obj("properties")?.values.orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { property ->
            val id = property.string("id") ?: return@mapNotNull null
            val type = property.string("type") ?: return@mapNotNull null
            val options = property.obj(type)?.array("options").orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }
            SchemaProperty(id, property.string("name").orEmpty(), type, options)
        }
        return Schema(source.plainText("title"), properties)
    }

    override suspend fun query(
        token: String,
        dataSourceId: String,
        dateProperty: String,
        from: LocalDate,
        to: LocalDate,
    ): List<RemotePage> =
        paged(MAX_PAGES) { cursor ->
            call(token, "POST", "/v1/data_sources/$dataSourceId/query", buildJsonObject {
                putJsonObject("filter") {
                    putJsonArray("and") {
                        add(dateFilter(dateProperty, "on_or_after", from))
                        add(dateFilter(dateProperty, "on_or_before", to))
                    }
                }
                put("page_size", PAGE_SIZE)
                cursor?.let { put("start_cursor", it) }
            })
        }
            .filter { it.string("object") == "page" && it.boolean("in_trash") != true && it.boolean("is_archived") != true }
            .mapNotNull { page ->
                val id = page.string("id") ?: return@mapNotNull null
                val created = page.string("created_time")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0
                val values = page.obj("properties")?.values.orEmpty().mapNotNull { it as? JsonObject }
                RemotePage(id, created, values.mapNotNull { value -> value.string("id")?.let { it to value } }.toMap())
            }

    override suspend fun createPage(token: String, dataSourceId: String, properties: JsonObject): String {
        val page = call(token, "POST", "/v1/pages", idempotent = false, body = buildJsonObject {
            putJsonObject("parent") {
                put("type", "data_source_id")
                put("data_source_id", dataSourceId)
            }
            put("properties", properties)
        })
        return page.string("id") ?: throw NotionException(Kind.Busy, detail = "created page has no id")
    }

    override suspend fun updatePage(token: String, pageId: String, properties: JsonObject?, inTrash: Boolean) {
        call(token, "PATCH", "/v1/pages/$pageId", buildJsonObject {
            put("in_trash", inTrash)
            properties?.let { put("properties", it) }
        })
    }

    private fun dateFilter(property: String, condition: String, date: LocalDate) = buildJsonObject {
        put("property", property)
        putJsonObject("date") { put(condition, date.toString()) }
    }

    /** Follows `next_cursor` until the list ends or [limit] results are in. */
    private suspend fun paged(limit: Int, fetch: suspend (cursor: String?) -> JsonObject): List<JsonObject> {
        val results = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val page = fetch(cursor)
            page.array("results").orEmpty().mapNotNullTo(results) { it as? JsonObject }
            cursor = page.string("next_cursor").takeIf { page.boolean("has_more") == true }
        } while (cursor != null && results.size < limit)
        return results
    }

    /**
     * Sends one request, waiting and retrying when Notion says it is busy. A request that
     * isn't [idempotent] is not repeated after a server error, because it may have gone through.
     */
    private suspend fun call(
        token: String,
        method: String,
        path: String,
        body: JsonObject? = null,
        idempotent: Boolean = true,
    ): JsonObject {
        var attempt = 1
        while (true) {
            val request = Request.Builder()
                .url(baseUrl() + path)
                .header("Authorization", "Bearer $token")
                .header("Notion-Version", NOTION_VERSION)
                .method(method, body?.toString()?.toRequestBody(JSON))
                .build()
            val (status, retryAfter, text) = try {
                runInterruptible(Dispatchers.IO) {
                    http.newCall(request).execute().use { Triple(it.code, it.header("Retry-After"), it.body.string()) }
                }
            } catch (e: IOException) {
                log("$method $path: ${e.javaClass.simpleName}")
                throw NotionException(Kind.Network, cause = e)
            }
            val json = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (status in 200..299) return json ?: throw NotionException(Kind.Busy, status, detail = "unreadable response")

            val code = json?.string("code")
            val message = json?.string("message")
            log("$method $path: $status $code $message")

            // A write that timed out on Notion's side may still have been saved; it then names the page.
            if (status == 503) {
                json?.obj("additional_data")?.string("committed_resource_id")?.let { return buildJsonObject { put("id", it) } }
            }
            val busy = status == 429 || status == 529 || (idempotent && status in 500..599)
            val wait = retryAfter?.toLongOrNull()?.times(1000) ?: (1000L shl (attempt - 1))
            if (busy && attempt < MAX_ATTEMPTS && wait <= MAX_WAIT_MILLIS) {
                pause(wait)
                attempt++
                continue
            }
            throw NotionException(kindOf(status), status, code, message)
        }
    }

    private fun kindOf(status: Int) = when (status) {
        401 -> Kind.Unauthorized
        404 -> Kind.NotFound
        409, 429 -> Kind.Busy
        in 500..599 -> Kind.Busy
        else -> Kind.Rejected
    }

    private companion object {
        const val NOTION_VERSION = "2026-03-11"
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 2_000
        const val MAX_DATA_SOURCES = 300
        const val MAX_ATTEMPTS = 3
        const val MAX_WAIT_MILLIS = 20_000L
        val JSON = "application/json".toMediaType()
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

/** The text of a rich-text array such as a title. */
internal fun JsonObject.plainText(key: String): String =
    array(key).orEmpty().joinToString("") { part: JsonElement -> (part as? JsonObject)?.string("plain_text").orEmpty() }
        .ifBlank { "Untitled" }
