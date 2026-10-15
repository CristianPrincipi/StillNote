package app.stillnote.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

/** Settings → Color palette. Night is the dark mode. */
enum class Palette(val key: String) {
    Canary("canary"),
    Night("night"),
}

/**
 * Settings → Reminders: [before] is how many minutes before a task's time to remind, or
 * [Reminder.None]; [morningAt] is the morning list's time in minutes after midnight, or null for off.
 */
data class ReminderSettings(val before: Int = Reminder.DEFAULT, val morningAt: Int? = MORNING_DEFAULT) {
    companion object {
        const val MORNING_DEFAULT = 8 * 60
    }
}

private val Context.settingsDataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val handFontKey = stringPreferencesKey("hand_font")
    private val paletteKey = stringPreferencesKey("palette")
    private val setupDoneKey = booleanPreferencesKey("setup_done")
    private val reminderBeforeKey = intPreferencesKey("reminder_before")
    private val morningListKey = intPreferencesKey("morning_list_at")
    private val notificationsAskedKey = booleanPreferencesKey("notifications_asked")

    val handFont: Flow<HandFont> = context.settingsDataStore.data.map { prefs ->
        HandFont.entries.firstOrNull { it.key == prefs[handFontKey] } ?: HandFont.Handwritten
    }

    suspend fun setHandFont(font: HandFont) {
        context.settingsDataStore.edit { it[handFontKey] = font.key }
    }

    val palette: Flow<Palette> = context.settingsDataStore.data.map { prefs ->
        Palette.entries.firstOrNull { it.key == prefs[paletteKey] } ?: Palette.Canary
    }

    suspend fun setPalette(palette: Palette) {
        context.settingsDataStore.edit { it[paletteKey] = palette.key }
    }

    /** False until the first-run flow has been finished once. */
    val setupDone: Flow<Boolean> = context.settingsDataStore.data.map { it[setupDoneKey] == true }

    suspend fun setSetupDone() {
        context.settingsDataStore.edit { it[setupDoneKey] = true }
    }

    val reminders: Flow<ReminderSettings> = context.settingsDataStore.data.map { prefs ->
        ReminderSettings(
            before = prefs[reminderBeforeKey] ?: Reminder.DEFAULT,
            // Stored as -1 for off, since a missing key means the default.
            morningAt = (prefs[morningListKey] ?: ReminderSettings.MORNING_DEFAULT).takeIf { it >= 0 },
        )
    }

    suspend fun setReminderBefore(minutes: Int) {
        context.settingsDataStore.edit { it[reminderBeforeKey] = minutes }
    }

    suspend fun setMorningList(at: Int?) {
        context.settingsDataStore.edit { it[morningListKey] = at ?: -1 }
    }

    /** True once the app has asked Android for permission to notify, so it asks by itself only once. */
    val notificationsAsked: Flow<Boolean> = context.settingsDataStore.data.map { it[notificationsAskedKey] == true }

    suspend fun setNotificationsAsked() {
        context.settingsDataStore.edit { it[notificationsAskedKey] = true }
    }
}
