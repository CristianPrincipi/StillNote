package app.stillnote.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.HandFont
import app.stillnote.data.ReminderSettings
import app.stillnote.data.SettingsStore
import app.stillnote.data.notion.NotionAccount
import app.stillnote.data.notion.NotionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What Save last found out about the token, when it is something other than "connected". */
enum class TokenProblem { Checking, Rejected, Unreachable }

class SettingsViewModel(
    private val settings: SettingsStore,
    private val account: NotionAccount,
    /** Starts a sync once there is something to sync with. */
    private val startSync: () -> Unit,
) : ViewModel() {
    val handFont: StateFlow<HandFont?> =
        settings.handFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The connected database's name, or null without Notion. */
    val database: StateFlow<String?> =
        account.database.map { it?.name }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // The token is shown back to the user here and in setup, and nowhere else; it is never logged.
    var token by mutableStateOf("")
        private set
    var tokenVisible by mutableStateOf(false)
        private set
    var problem by mutableStateOf<TokenProblem?>(null)
        private set

    init {
        reload()
    }

    /** Re-reads the saved token, for when the setup steps may have changed it. */
    fun reload() {
        viewModelScope.launch {
            token = account.token().orEmpty()
            problem = if (account.tokenRejected.value) TokenProblem.Rejected else null
        }
    }

    fun updateToken(value: String) {
        token = value
    }

    fun toggleTokenVisible() {
        tokenVisible = !tokenVisible
    }

    /** Checks the token with Notion again. An empty token disconnects. */
    fun save() {
        val entered = token.trim()
        viewModelScope.launch {
            if (entered.isEmpty()) {
                account.signOut()
                problem = null
                return@launch
            }
            problem = TokenProblem.Checking
            problem = try {
                account.signIn(entered)
                startSync()
                null
            } catch (e: NotionException) {
                if (e.kind == NotionException.Kind.Unauthorized) TokenProblem.Rejected else TokenProblem.Unreachable
            }
        }
    }

    fun setHandFont(font: HandFont) {
        viewModelScope.launch { settings.setHandFont(font) }
    }

    val reminders: StateFlow<ReminderSettings?> =
        settings.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setReminderBefore(minutes: Int) {
        viewModelScope.launch { settings.setReminderBefore(minutes) }
    }

    /** [at] in minutes after midnight, or null for off. */
    fun setMorningList(at: Int?) {
        viewModelScope.launch { settings.setMorningList(at) }
    }

    /** True once Android has been asked for permission to notify; after that only its settings page can give it. */
    val notificationsAsked: Flow<Boolean> = settings.notificationsAsked

    fun markNotificationsAsked() {
        viewModelScope.launch { settings.setNotificationsAsked() }
    }
}
