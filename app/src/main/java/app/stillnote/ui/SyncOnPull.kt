package app.stillnote.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sync started by pulling a screen down: one at a time, with [running] for the indicator. */
class SyncOnPull(private val scope: CoroutineScope, private val sync: suspend () -> Unit) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun start() {
        if (!_running.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                sync()
            } finally {
                _running.value = false
            }
        }
    }
}
