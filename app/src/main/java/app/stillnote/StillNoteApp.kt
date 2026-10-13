package app.stillnote

import android.app.Application
import app.stillnote.data.AppClock
import app.stillnote.data.FakeData
import app.stillnote.data.FakeTaskRepository
import app.stillnote.data.FixedDateClock
import app.stillnote.data.SettingsStore
import app.stillnote.data.TaskRepository

class StillNoteApp : Application() {
    // Milestone 1 runs on fake data; milestone 2 swaps these for Notion and the system clock.
    val clock: AppClock = FixedDateClock(FakeData.today)
    val tasks: TaskRepository = FakeTaskRepository()
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
