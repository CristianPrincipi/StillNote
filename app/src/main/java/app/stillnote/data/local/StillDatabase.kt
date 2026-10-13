package app.stillnote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TaskRow::class], version = 1)
abstract class StillDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao

    companion object {
        fun open(context: Context): StillDatabase =
            Room.databaseBuilder(context, StillDatabase::class.java, "stillnote.db").build()
    }
}
