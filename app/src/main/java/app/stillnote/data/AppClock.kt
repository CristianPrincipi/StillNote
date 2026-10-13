package app.stillnote.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface AppClock {
    /** Changes at midnight, so "today" on screen never goes stale. */
    val today: StateFlow<LocalDate>

    fun now(): LocalTime

    /** Re-reads the date, for when the app comes back after the clock or time zone may have changed. */
    fun refresh() = Unit
}

class DeviceClock(scope: CoroutineScope) : AppClock {
    private val _today = MutableStateFlow(LocalDate.now())
    override val today: StateFlow<LocalDate> = _today.asStateFlow()

    init {
        scope.launch {
            while (true) {
                val midnight = LocalDate.now().plusDays(1).atStartOfDay()
                delay(Duration.between(LocalDateTime.now(), midnight).toMillis() + 500)
                refresh()
            }
        }
    }

    override fun now(): LocalTime = LocalTime.now()

    override fun refresh() {
        _today.value = LocalDate.now()
    }
}

/** Pins "today" so the demo data lines up with the mockups; the time of day is real. */
class FixedDateClock(date: LocalDate) : AppClock {
    override val today: StateFlow<LocalDate> = MutableStateFlow(date)
    override fun now(): LocalTime = LocalTime.now()
}
