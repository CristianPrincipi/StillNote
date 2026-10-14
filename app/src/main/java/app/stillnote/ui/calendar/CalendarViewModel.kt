package app.stillnote.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stillnote.data.AppClock
import app.stillnote.data.SyncStatus
import app.stillnote.data.Task
import app.stillnote.data.TaskOrder
import app.stillnote.data.TaskRepository
import app.stillnote.ui.SyncOnPull
import app.stillnote.ui.calm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean = false,
    val selected: Boolean = false,
    /** One entry per task, true when done; at most [MAX_SQUARES]. */
    val squares: List<Boolean> = emptyList(),
    val taskCount: Int = 0,
) {
    companion object {
        const val MAX_SQUARES = 4
    }
}

data class CalendarUiState(
    val month: YearMonth,
    val today: LocalDate,
    val isCurrentMonth: Boolean = true,
    val done: Int = 0,
    val missed: Int = 0,
    val toCome: Int = 0,
    val weeks: List<List<DayCell>> = emptyList(),
    val selected: LocalDate? = null,
    val selectedTasks: List<Task> = emptyList(),
    val sync: SyncStatus = SyncStatus.Idle,
    val canSync: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val tasks: TaskRepository, private val clock: AppClock) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.from(clock.today.value))
    private val selected = MutableStateFlow<LocalDate?>(clock.today.value)

    val state: StateFlow<CalendarUiState> = combine(clock.today, month, ::Pair)
        .flatMapLatest { (today, month) ->
            combine(
                tasks.observeTasks(month.atDay(1), month.atEndOfMonth()),
                selected,
                tasks.syncStatus.calm(),
                tasks.features,
            ) { all, day, sync, features ->
                build(today, month, day?.takeIf { YearMonth.from(it) == month }, all).copy(sync = sync, canSync = features.notion)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(month.value, clock.today.value),
        )

    fun previousMonth() = show(month.value.minusMonths(1))

    /** Past months only: the arrow stops at the current month, which reselects today. */
    fun nextMonth() {
        if (month.value < YearMonth.from(clock.today.value)) show(month.value.plusMonths(1))
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    /** Pulls the month on screen. */
    private val pull = SyncOnPull(viewModelScope) {
        val shown = month.value
        tasks.syncNow(shown.atDay(1), shown.atEndOfMonth())
    }
    val syncing: StateFlow<Boolean> = pull.running

    fun syncNow() = pull.start()

    private fun show(next: YearMonth) {
        val today = clock.today.value
        selected.value = today.takeIf { YearMonth.from(it) == next }
        month.value = next
        tasks.refresh(next.atDay(1), next.atEndOfMonth())
    }

    private fun build(today: LocalDate, month: YearMonth, selected: LocalDate?, all: List<Task>): CalendarUiState {
        val byDay = all.groupBy { it.date }.mapValues { (_, tasks) -> tasks.sortedWith(TaskOrder.calendarDay) }
        val first = month.atDay(1)
        val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val weekCount = (first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7
        val weeks = List(weekCount) { week ->
            List(7) { weekday ->
                val date = gridStart.plusDays((week * 7 + weekday).toLong())
                if (YearMonth.from(date) != month) return@List DayCell(date, inMonth = false)
                val dayTasks = byDay[date].orEmpty()
                DayCell(
                    date = date,
                    inMonth = true,
                    isToday = date == today,
                    selected = date == selected,
                    squares = dayTasks.take(DayCell.MAX_SQUARES).map { it.done },
                    taskCount = dayTasks.size,
                )
            }
        }
        return CalendarUiState(
            month = month,
            today = today,
            isCurrentMonth = month == YearMonth.from(today),
            done = all.count { it.done },
            missed = all.count { !it.done && it.date?.isBefore(today) == true },
            toCome = all.count { !it.done && it.date?.isBefore(today) == false },
            weeks = weeks,
            selected = selected,
            selectedTasks = selected?.let { byDay[it] }.orEmpty(),
        )
    }
}
