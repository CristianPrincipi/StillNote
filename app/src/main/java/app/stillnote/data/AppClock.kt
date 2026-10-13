package app.stillnote.data

import java.time.LocalDate
import java.time.LocalTime

interface AppClock {
    fun today(): LocalDate
    fun now(): LocalTime
}

/** Pins "today" so the fake data lines up with the mockups; the time of day is real. */
class FixedDateClock(private val date: LocalDate) : AppClock {
    override fun today(): LocalDate = date
    override fun now(): LocalTime = LocalTime.now()
}
