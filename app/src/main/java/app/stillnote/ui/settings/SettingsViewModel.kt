package app.stillnote.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.HandFont
import app.stillnote.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: SettingsStore) : ViewModel() {
    val handFont: StateFlow<HandFont?> =
        settings.handFont.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Milestone 1 has no Notion client: the field holds a stand-in and Save does nothing yet.
    // The real token will live in the Keystore, never in code or logs.
    var token by mutableStateOf(DEMO_TOKEN)
        private set
    var tokenVisible by mutableStateOf(false)
        private set
    val connectedDatabase: String? = "Tasks"

    fun updateToken(value: String) {
        token = value
    }

    fun toggleTokenVisible() {
        tokenVisible = !tokenVisible
    }

    fun save() = Unit

    fun setHandFont(font: HandFont) {
        viewModelScope.launch { settings.setHandFont(font) }
    }

    private companion object {
        const val DEMO_TOKEN = "demo-token-not-a-secret!"
    }
}
