package app.stillnote.ui.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.HandFont
import app.stillnote.data.SettingsStore
import app.stillnote.data.notion.DataSource
import app.stillnote.data.notion.Field
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionException
import app.stillnote.data.notion.PropertyMapping
import app.stillnote.data.notion.Schema
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SetupStep { Welcome, Token, Database, Look }

/**
 * The first-run flow: Welcome, then either the two Notion steps or straight to "Choose a look".
 * Settings reuses the two Notion steps on their own to connect or change database.
 */
class SetupViewModel(
    private val settings: SettingsStore,
    private val account: NotionAccount,
    /** Starts a sync once there is something to sync with. */
    private val startSync: () -> Unit,
) : ViewModel() {
    var step by mutableStateOf(SetupStep.Welcome)
        private set

    /** True when opened from Settings: only the Notion steps are shown, and finishing returns there. */
    var fromSettings by mutableStateOf(false)
        private set

    /** Set when the flow has done its job and should be left. */
    var finished by mutableStateOf(false)
        private set

    // Step 1: the secret. It is held here only while the step is open, and never logged.
    var token by mutableStateOf("")
        private set
    var tokenVisible by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set
    var tokenError by mutableStateOf<String?>(null)
        private set

    // Step 2: the database and its fields.
    var databases by mutableStateOf<List<DataSource>>(emptyList())
        private set
    var loadingDatabases by mutableStateOf(false)
        private set
    var databaseError by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<DataSource?>(null)
        private set

    /** The chosen database's properties; null while they are being read. */
    var schema by mutableStateOf<Schema?>(null)
        private set

    /** Field to property id, guessed first and then the user's to change. */
    var choices by mutableStateOf<Map<Field, String>>(emptyMap())
        private set

    /** The field whose property is being picked, while that sheet is open. */
    var picking by mutableStateOf<Field?>(null)
        private set

    val handFont: StateFlow<HandFont?> =
        settings.handFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val mapping: PropertyMapping? get() = schema?.let { PropertyMapping.from(it, choices) }
    val canContinue: Boolean get() = mapping != null

    private var reading: Job? = null

    /** Opens the two Notion steps on their own, with the saved secret already in the field. */
    fun startFromSettings() {
        fromSettings = true
        finished = false
        step = SetupStep.Token
        tokenError = null
        viewModelScope.launch { token = account.token().orEmpty() }
    }

    fun connectNotion() {
        step = SetupStep.Token
    }

    fun useWithoutNotion() {
        step = SetupStep.Look
    }

    fun back() {
        when (step) {
            SetupStep.Welcome -> Unit
            SetupStep.Token -> if (fromSettings) finished = true else step = SetupStep.Welcome
            SetupStep.Database -> step = SetupStep.Token
            // "Choose a look" is reached from both paths; go back to where the user came from.
            SetupStep.Look -> step = if (selected != null) SetupStep.Database else SetupStep.Welcome
        }
    }

    fun updateToken(value: String) {
        token = value.trim()
        tokenError = null
    }

    fun toggleTokenVisible() {
        tokenVisible = !tokenVisible
    }

    /** Checks the secret with Notion; on success moves to the database step. */
    fun connect() {
        if (token.isEmpty() || checking) return
        checking = true
        tokenError = null
        viewModelScope.launch {
            try {
                account.signIn(token)
                step = SetupStep.Database
                loadDatabases()
            } catch (e: NotionException) {
                tokenError = e.explain()
            } finally {
                checking = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { loadDatabases() }
    }

    private suspend fun loadDatabases() {
        loadingDatabases = true
        databaseError = null
        try {
            databases = account.dataSources()
            val current = account.database.first()?.id
            // Keep the current choice if there is one; otherwise start on the likeliest database.
            val start = databases.firstOrNull { it.id == (selected?.id ?: current) }
                ?: databases.firstOrNull { it.name.trim().lowercase() in LIKELY_NAMES }
                ?: databases.firstOrNull()
            if (start == null) selected = null else if (start.id != selected?.id || schema == null) select(start)
        } catch (e: NotionException) {
            databaseError = e.explain()
        } finally {
            loadingDatabases = false
        }
    }

    fun select(database: DataSource) {
        selected = database
        schema = null
        choices = emptyMap()
        reading?.cancel()
        reading = viewModelScope.launch {
            try {
                val read = account.schema(database) ?: return@launch
                // The saved mapping wins for the database already in use.
                val saved = account.database.first()?.takeIf { it.id == database.id }?.mapping
                choices = saved?.choices() ?: PropertyMapping.guess(read)
                schema = read
            } catch (e: NotionException) {
                databaseError = e.explain()
            }
        }
    }

    fun pick(field: Field) {
        picking = field
    }

    /** Sets the property for the field being picked; null clears an optional field. */
    fun choose(propertyId: String?) {
        val field = picking ?: return
        choices = if (propertyId == null) choices - field else choices.filterValues { it != propertyId } + (field to propertyId)
        picking = null
    }

    fun closePicker() {
        picking = null
    }

    /** Saves the database and its fields, starts syncing, and moves on. */
    fun next() {
        val database = selected ?: return
        val mapping = mapping ?: return
        viewModelScope.launch {
            account.choose(database, mapping)
            startSync()
            if (fromSettings) finished = true else step = SetupStep.Look
        }
    }

    fun setHandFont(font: HandFont) {
        viewModelScope.launch { settings.setHandFont(font) }
    }

    /** "Start writing": setup is done and never shown again. */
    fun finish() {
        viewModelScope.launch {
            settings.setSetupDone()
            finished = true
        }
    }

    private fun NotionException.explain(): String = when (kind) {
        NotionException.Kind.Unauthorized -> "Notion didn't accept this secret. Check that you copied all of it."
        NotionException.Kind.Network -> "Couldn't reach Notion. Check your connection and try again."
        else -> "Notion couldn't answer just now. Try again in a moment."
    }

    private fun PropertyMapping.choices(): Map<Field, String> = listOfNotNull(
        Field.Title to title,
        Field.Done to done,
        Field.Date to date,
        priority?.let { Field.Priority to it },
        project?.let { Field.Project to it },
    ).toMap()

    private companion object {
        val LIKELY_NAMES = setOf("tasks", "task", "to-do", "to do", "todo", "attività", "compiti")
    }
}
