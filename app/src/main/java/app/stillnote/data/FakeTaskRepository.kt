package app.stillnote.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** In-memory tasks for milestone 1: the sample data from design/DESIGN.md §6 and Calendar.dc.html. */
class FakeTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow(FakeData.tasks())
    private val trashed = mutableMapOf<String, Pair<Int, Task>>()
    private var created = 0

    override fun observeTasks(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        tasks.map { all -> all.filter { it.date in from..to } }.distinctUntilChanged()

    override suspend fun create(draft: TaskDraft): Task {
        val task = Task(
            id = "new-${created++}",
            title = draft.title,
            date = draft.date,
            time = draft.time,
            priority = draft.priority,
        )
        tasks.update { it + task }
        return task
    }

    override suspend fun setDone(id: String, done: Boolean) {
        tasks.update { all -> all.map { if (it.id == id) it.copy(done = done) else it } }
    }

    override suspend fun trash(id: String) {
        tasks.update { all ->
            val index = all.indexOfFirst { it.id == id }
            if (index < 0) return@update all
            trashed[id] = index to all[index]
            all.filterNot { it.id == id }
        }
    }

    override suspend fun restore(id: String) {
        val (index, task) = trashed.remove(id) ?: return
        tasks.update { all -> all.toMutableList().apply { add(index.coerceAtMost(size), task) } }
    }
}

internal object FakeData {
    val today: LocalDate = LocalDate.of(2026, 10, 12)

    private const val MONTHS_BACK = 12L

    private class Row(
        val title: String,
        val priority: Priority,
        val done: Boolean,
        val time: String = "",
        val project: String? = null,
    )

    private val october: Map<Int, List<Row>> = mapOf(
        1 to listOf(
            Row("Pay the electricity bill", Priority.High, true, "09:00"),
            Row("Call grandma", Priority.Medium, true, "19:00"),
        ),
        2 to listOf(Row("Gym", Priority.Low, true, "18:30")),
        3 to listOf(Row("Farmers market", Priority.None, true, "10:00")),
        5 to listOf(
            Row("Reply to the landlord", Priority.High, true, "09:30"),
            Row("Weekly review in Notion", Priority.Medium, true, "13:00"),
            Row("Fix the bike light", Priority.Low, true),
        ),
        6 to listOf(Row("Read 20 pages", Priority.None, true)),
        7 to listOf(
            Row("Back up the laptop", Priority.Low, true),
            Row("Buy coffee beans", Priority.None, true),
        ),
        8 to listOf(
            Row("Prepare slides for Thursday", Priority.High, true, "10:00"),
            Row("Water the plants", Priority.Low, true, "08:00"),
            Row("Read 20 pages", Priority.None, false),
        ),
        9 to listOf(
            Row("Gym", Priority.Low, false, "18:30"),
            Row("Dinner with Luca", Priority.None, true, "20:30"),
        ),
        10 to listOf(Row("Clean the desk", Priority.None, true)),
        12 to listOf(
            Row("Water the plants", Priority.Low, true, "08:00", "Personal"),
            Row("Back up the laptop", Priority.None, true, project = "Personal"),
            Row("Reply to Marco about Friday", Priority.High, false, "10:30", "Work"),
            Row("Weekly review in Notion", Priority.Medium, false, "13:00", "Personal"),
            Row("Pick up the parcel", Priority.Low, false, "18:00", "Errands"),
            Row("Read 20 pages", Priority.None, false, project = "Reading"),
        ),
        13 to listOf(
            Row("Buy a birthday card for Giulia", Priority.High, false, "18:00"),
            Row("Gym", Priority.Low, false, "18:30"),
        ),
        14 to listOf(Row("Book the car service", Priority.Medium, false, "09:00")),
        16 to listOf(Row("Gym", Priority.Low, false, "18:30")),
        19 to listOf(Row("Weekly review in Notion", Priority.Medium, false, "13:00")),
        21 to listOf(Row("Car service", Priority.High, false, "08:30")),
        23 to listOf(Row("Gym", Priority.Low, false, "18:30")),
        24 to listOf(Row("Giulia’s birthday", Priority.High, false)),
        26 to listOf(Row("Weekly review in Notion", Priority.Medium, false, "13:00")),
        29 to listOf(Row("Renew the train pass", Priority.Medium, false)),
    )

    private val pool = listOf(
        Row("Weekly review in Notion", Priority.Medium, true, "13:00"),
        Row("Read 20 pages", Priority.None, true),
        Row("Gym", Priority.Low, true, "18:30"),
        Row("Groceries", Priority.Low, true, "19:00"),
        Row("Call grandma", Priority.Medium, true, "19:00"),
        Row("Back up the laptop", Priority.None, true),
        Row("Reply to emails", Priority.High, true, "09:30"),
    )
    private val perDay = intArrayOf(0, 1, 2, 1, 3, 0, 2)

    fun tasks(): List<Task> {
        val current = YearMonth.from(today)
        val out = mutableListOf<Task>()
        var month = current.minusMonths(MONTHS_BACK)
        while (month < current) {
            for (day in 1..month.lengthOfMonth()) out += pastDay(month.atDay(day))
            month = month.plusMonths(1)
        }
        october.forEach { (day, rows) ->
            val date = current.atDay(day)
            rows.forEachIndexed { i, row -> out += row.toTask("$date-$i", date, row.done) }
        }
        return out
    }

    // The same pseudo-random filler the Calendar mockup uses for past months.
    private fun pastDay(date: LocalDate): List<Task> {
        val seed = date.dayOfMonth + (date.monthValue - 1) * 3
        return List(perDay[seed % perDay.size]) { k ->
            val row = pool[(seed + k * 3) % pool.size]
            row.toTask("$date-$k", date, done = (date.dayOfMonth + k) % 6 != 0)
        }
    }

    private fun Row.toTask(id: String, date: LocalDate, done: Boolean) = Task(
        id = id,
        title = title,
        date = date,
        time = time.takeIf { it.isNotEmpty() }?.let(LocalTime::parse),
        priority = priority,
        project = project,
        done = done,
    )
}
