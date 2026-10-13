package app.stillnote.data.notion

import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** A Notion database the token can see. Since API version 2025-09-03 the rows live in its data source. */
data class DataSource(val id: String, val name: String)

data class SchemaProperty(
    val id: String,
    val name: String,
    val type: String,
    /** Option names, for a select property. */
    val options: List<String> = emptyList(),
)

data class Schema(val name: String, val properties: List<SchemaProperty>)

/** A page, with its property values keyed by property id rather than by name. */
data class RemotePage(val id: String, val createdTime: Long, val properties: Map<String, JsonObject>)

/** The calls the app makes to Notion. Every one throws [NotionException] when it fails. */
interface NotionApi {
    suspend fun checkToken(token: String)

    suspend fun dataSources(token: String): List<DataSource>

    suspend fun schema(token: String, dataSourceId: String): Schema

    /** Pages whose [dateProperty] falls between [from] and [to], both inclusive, in UTC. */
    suspend fun query(token: String, dataSourceId: String, dateProperty: String, from: LocalDate, to: LocalDate): List<RemotePage>

    /** Returns the new page's id. */
    suspend fun createPage(token: String, dataSourceId: String, properties: JsonObject): String

    suspend fun updatePage(token: String, pageId: String, properties: JsonObject?, inTrash: Boolean)
}

class NotionException(
    val kind: Kind,
    val status: Int = 0,
    val code: String? = null,
    detail: String? = null,
    cause: Throwable? = null,
) : Exception(listOfNotNull("Notion", kind.name, status.takeIf { it != 0 }, code, detail).joinToString(" "), cause) {
    enum class Kind {
        /** No answer: offline, timed out, or the connection dropped. */
        Network,

        /** The token is invalid or was revoked. */
        Unauthorized,

        /** The page or database is gone, or isn't shared with the token. */
        NotFound,

        /** Notion understood the request and refused it; sending it again won't help. */
        Rejected,

        /** Rate limited or a server-side failure; worth trying again later. */
        Busy,
    }
}
