package app.stillnote.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.stillnote.StillNoteApp
import app.stillnote.ui.calendar.CalendarViewModel
import app.stillnote.ui.newtask.NewTaskViewModel
import app.stillnote.ui.settings.SettingsViewModel
import app.stillnote.ui.today.TodayViewModel

private val CreationExtras.app: StillNoteApp get() = this[APPLICATION_KEY] as StillNoteApp

/** Builds every ViewModel from the repositories held by [StillNoteApp]. */
val StillViewModels: ViewModelProvider.Factory = viewModelFactory {
    initializer { TodayViewModel(app.tasks, app.clock) }
    initializer { NewTaskViewModel(app.tasks, app.clock) }
    initializer { CalendarViewModel(app.tasks, app.clock) }
    initializer {
        val app = app
        SettingsViewModel(app.settings, app.account, startSync = app::syncNow)
    }
}
