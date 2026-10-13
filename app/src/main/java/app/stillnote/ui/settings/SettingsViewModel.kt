package app.stillnote.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.HandFont
import app.stillnote.data.SettingsStore
import app.stillnote.data.notion.DataSource
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionException
import app.stillnote.data.notion.PropertyMapping
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the status row under the token field says. */
sealed interface NotionStatus {
    data object NotConnected : NotionStatus
    data object Checking : NotionStatus
    data object TokenRejected : NotionStatus
    data object Unreachable : NotionStatus
    data object NoDatabases : NotionStatus
    data object PickDatabase : NotionStatus

    /** The database [name] lacks [missing], for example "a date". */
    data class Unusable(val name: String, val missing: List<String>) : NotionStatus
    data class Connected(val name: String) : NotionStatus
}

class SettingsViewModel(
    private val settings: SettingsStore,
    private val account: NotionAccount,
    /** Starts a sync once there is something to sync with. */
    private val startSync: () -> Unit,
) : ViewModel() {
    val handFont: StateFlow<HandFont?> =
        settings.handFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // The token is shown back to the user here and nowhere else; it is never logged.
    var token by mutableStateOf("")
        private set
    var tokenVisible by mutableStateOf(false)
        private set
    var status by mutableStateOf<NotionStatus>(NotionStatus.NotConnected)
        private set

    /** The databases the token can see. */
    var databases by mutableStateOf<List<DataSource>>(emptyList())
        private set
    val chosenDatabaseId: StateFlow<String?> =
        account.database.map { it?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            token = account.token() ?: return@launch
            // Show what is already known, then ask Notion for the current list.
            val chosen = account.database.first()
            databases = listOfNotNull(chosen?.let { DataSource(it.id, it.name) })
            status = when {
                account.tokenRejected.value -> NotionStatus.TokenRejected
                chosen != null -> NotionStatus.Connected(chosen.name)
                else -> NotionStatus.PickDatabase
            }
            runCatching { account.dataSources() }.onSuccess { found ->
                databases = found
                if (chosen == null && found.isEmpty()) status = NotionStatus.NoDatabases
            }
        }
    }

    fun updateToken(value: String) {
        token = value
    }

    fun toggleTokenVisible() {
        tokenVisible = !tokenVisible
    }

    /** Checks the token with Notion and lists its databases. An empty token disconnects. */
    fun save() {
        val entered = token.trim()
        viewModelScope.launch {
            if (entered.isEmpty()) {
                account.signOut()
                databases = emptyList()
                status = NotionStatus.NotConnected
                return@launch
            }
            asking {
                databases = account.signIn(entered)
                val chosen = account.database.first()
                status = when {
                    chosen != null -> NotionStatus.Connected(chosen.name).also { startSync() }
                    databases.isEmpty() -> NotionStatus.NoDatabases
                    else -> NotionStatus.PickDatabase
                }
            }
        }
    }

    fun choose(database: DataSource) {
        viewModelScope.launch {
            asking {
                status = when (val detected = account.choose(database)) {
                    is PropertyMapping.Detected.Ok -> NotionStatus.Connected(database.name).also { startSync() }
                    is PropertyMapping.Detected.Missing -> NotionStatus.Unusable(database.name, detected.what)
                }
            }
        }
    }

    fun setHandFont(font: HandFont) {
        viewModelScope.launch { settings.setHandFont(font) }
    }

    private suspend fun asking(request: suspend () -> Unit) {
        status = NotionStatus.Checking
        try {
            request()
        } catch (e: NotionException) {
            status = if (e.kind == NotionException.Kind.Unauthorized) NotionStatus.TokenRejected else NotionStatus.Unreachable
        }
    }
}
