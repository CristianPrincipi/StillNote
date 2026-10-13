package app.stillnote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The Settings → Typography choice: which face plays the handwritten role. */
enum class HandFont(val key: String) {
    Handwritten("hand"),
    Typewriter("type"),
    Bookish("book"),
}

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val handFontKey = stringPreferencesKey("hand_font")
    private val setupDoneKey = booleanPreferencesKey("setup_done")

    val handFont: Flow<HandFont> = context.settingsDataStore.data.map { prefs ->
        HandFont.entries.firstOrNull { it.key == prefs[handFontKey] } ?: HandFont.Handwritten
    }

    suspend fun setHandFont(font: HandFont) {
        context.settingsDataStore.edit { it[handFontKey] = font.key }
    }

    /** False until the first-run flow has been finished once. */
    val setupDone: Flow<Boolean> = context.settingsDataStore.data.map { it[setupDoneKey] == true }

    suspend fun setSetupDone() {
        context.settingsDataStore.edit { it[setupDoneKey] = true }
    }
}
