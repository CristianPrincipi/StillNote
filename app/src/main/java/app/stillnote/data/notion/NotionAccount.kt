package app.stillnote.data.notion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.stillnote.data.local.TaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** The database the app syncs with, and how its properties map to a task. */
data class Database(val id: String, val name: String, val mapping: PropertyMapping)

/** Everything a sync needs. */
data class Connection(val token: String, val database: Database)

/** Remembers the chosen database between launches. */
interface DatabaseStore {
    val database: Flow<Database?>
    suspend fun save(database: Database?)
}

private val Context.notionDataStore by preferencesDataStore("notion")

class PreferencesDatabaseStore(private val context: Context) : DatabaseStore {
    private val id = stringPreferencesKey("data_source_id")
    private val name = stringPreferencesKey("data_source_name")
    private val mapping = stringPreferencesKey("property_mapping")

    override val database: Flow<Database?> = context.notionDataStore.data.map { prefs ->
        val mapping = prefs[mapping]?.let(PropertyMapping::fromJson) ?: return@map null
        Database(prefs[id] ?: return@map null, prefs[name].orEmpty(), mapping)
    }

    override suspend fun save(database: Database?) {
        context.notionDataStore.edit { prefs ->
            if (database == null) {
                prefs.clear()
            } else {
                prefs[id] = database.id
                prefs[name] = database.name
                prefs[mapping] = database.mapping.toJson()
            }
        }
    }
}

/** The link to the user's Notion: the token, the database it points at, and changing either. */
class NotionAccount(
    private val api: NotionApi,
    private val vault: TokenVault,
    private val store: DatabaseStore,
    private val tasks: TaskDao,
) {
    val database: Flow<Database?> = store.database

    /** True once Notion has refused the saved token; cleared by the next successful sign-in. */
    private val _tokenRejected = MutableStateFlow(false)
    val tokenRejected: StateFlow<Boolean> = _tokenRejected.asStateFlow()

    // The vault decrypts with the Keystore and reads a file, so it stays off the main thread.
    suspend fun token(): String? = withContext(Dispatchers.IO) { vault.load() }

    suspend fun connection(): Connection? {
        val token = token() ?: return null
        return Connection(token, store.database.first() ?: return null)
    }

    fun markTokenRejected() {
        _tokenRejected.value = true
    }

    /**
     * Checks [token] with Notion, stores it, and returns the databases it can see. A chosen
     * database the new token can't see is forgotten, along with the tasks that came from it.
     */
    suspend fun signIn(token: String): List<DataSource> {
        api.checkToken(token)
        val sources = api.dataSources(token)
        withContext(Dispatchers.IO) { vault.save(token) }
        _tokenRejected.value = false
        val chosen = store.database.first()
        if (chosen != null && sources.none { it.id == chosen.id }) forgetDatabase()
        return sources
    }

    suspend fun dataSources(): List<DataSource> = api.dataSources(token() ?: return emptyList())

    /** Points the app at [source], or reports what the database lacks. */
    suspend fun choose(source: DataSource): PropertyMapping.Detected {
        val token = token() ?: return PropertyMapping.Detected.Missing(emptyList())
        val schema = api.schema(token, source.id)
        val detected = PropertyMapping.detect(schema)
        if (detected is PropertyMapping.Detected.Ok) {
            if (store.database.first()?.id != source.id) tasks.deleteSynced()
            store.save(Database(source.id, schema.name, detected.mapping))
        }
        return detected
    }

    /** Forgets the token and the database. Tasks that came from Notion leave the phone; they stay in Notion. */
    suspend fun signOut() {
        withContext(Dispatchers.IO) { vault.clear() }
        _tokenRejected.value = false
        forgetDatabase()
    }

    private suspend fun forgetDatabase() {
        store.save(null)
        tasks.deleteSynced()
    }
}
